"""Map a deserialized ``TicketAction`` (v1) Avro record into the normalized
PostgreSQL schema defined in ``cr_ticket.sql``.

The Avro message is a denormalized ``{ticket, article}`` tree where every
user/group/priority is embedded as a full record. ``persist_action`` fans that
tree out into the relational tables, upserting on primary key so re-consuming
the same (or an updated) message is idempotent.

Foreign-key safety:
- Users carry self-references (``created_by_id``/``updated_by_id``/
  ``out_of_office_replacement_id``). Users are upserted in two phases: first
  with those columns NULL, then a second pass sets them once every user exists.
- Any user/group FK pointing at an id we did not receive as a full record is
  written as NULL rather than violating the constraint.
- ``ticket.customer_id`` is a plain id (the Avro ``Ticket`` carries no customer
  record, and the SQL column has no FK).
"""

from __future__ import annotations

from datetime import datetime
from decimal import Decimal, InvalidOperation
from typing import Any


# --------------------------------------------------------------------------- #
# Value coercion helpers
# --------------------------------------------------------------------------- #
def ts(value: Any):
    """Parse an ISO-8601 string into a timezone-aware datetime, or None."""
    if value is None or value == "":
        return None
    if isinstance(value, datetime):
        return value
    text = str(value).replace("Z", "+00:00")
    return datetime.fromisoformat(text)


def num(value: Any):
    """Parse a numeric value (used for NUMERIC columns), or None."""
    if value is None or value == "":
        return None
    if isinstance(value, (int, float, Decimal)):
        return value
    try:
        return Decimal(str(value))
    except (InvalidOperation, ValueError):
        return None


# --------------------------------------------------------------------------- #
# Generic upsert
# --------------------------------------------------------------------------- #
def upsert(cur, table: str, row: dict, pk_cols: list[str]) -> None:
    cols = list(row.keys())
    placeholders = ", ".join(["%s"] * len(cols))
    conflict = ", ".join(pk_cols)
    update_cols = [c for c in cols if c not in pk_cols]
    if update_cols:
        set_clause = ", ".join(f"{c} = EXCLUDED.{c}" for c in update_cols)
        action = f"DO UPDATE SET {set_clause}"
    else:
        action = "DO NOTHING"
    sql = (
        f"INSERT INTO {table} ({', '.join(cols)}) VALUES ({placeholders}) "
        f"ON CONFLICT ({conflict}) {action}"
    )
    cur.execute(sql, [row[c] for c in cols])


# --------------------------------------------------------------------------- #
# Row builders
# --------------------------------------------------------------------------- #
def _user_row(u: dict) -> dict:
    """User columns WITHOUT the self-referencing FKs (set in phase B)."""
    return {
        "id": u["id"],
        "login": u["login"],
        "email": u.get("email") or "",
        "firstname": u.get("firstname"),
        "lastname": u.get("lastname"),
        "image": u.get("image"),
        "image_source": u.get("image_source"),
        "source": u.get("source"),
        "active": u.get("active", True),
        "verified": u.get("verified", False),
        "out_of_office": u.get("out_of_office", False),
        "out_of_office_start_at": ts(u.get("out_of_office_start_at")),
        "out_of_office_end_at": ts(u.get("out_of_office_end_at")),
        "created_at": ts(u["created_at"]),
        "updated_at": ts(u["updated_at"]),
    }


def _collect_users(action: dict) -> dict[int, dict]:
    """Every embedded full User record in the message, keyed by id."""
    ticket = action["ticket"]
    article = action.get("article") or {}
    users: dict[int, dict] = {}
    for u in (
        ticket.get("owner"),
        ticket.get("created_by"),
        ticket.get("updated_by"),
        article.get("created_by"),
        article.get("updated_by"),
    ):
        if u:
            users[u["id"]] = u
    return users


# --------------------------------------------------------------------------- #
# Main persistence routine (one transaction per message, caller commits)
# --------------------------------------------------------------------------- #
def persist_action(cur, action: dict) -> None:
    ticket = action["ticket"]
    article = action.get("article")  # optional: null on article-less events

    users = _collect_users(action)
    known_user_ids = set(users)

    def fk_user(uid):
        return uid if uid in known_user_ids else None

    # --- users, phase A (no self-FKs) ------------------------------------- #
    for u in users.values():
        upsert(cur, "agent_user", _user_row(u), ["id"])

    # --- users, phase B (resolve self-FKs now that all users exist) ------- #
    for u in users.values():
        cur.execute(
            "UPDATE agent_user SET created_by_id = %s, updated_by_id = %s, "
            "out_of_office_replacement_id = %s WHERE id = %s",
            (
                fk_user(u.get("created_by_id")),
                fk_user(u.get("updated_by_id")),
                fk_user(u.get("out_of_office_replacement_id")),
                u["id"],
            ),
        )

    p = ticket["priority"]

    # --- ticket_group ----------------------------------------------------- #
    g = ticket["group"]
    known_group_ids = {g["id"]}
    parent_id = g.get("parent_id")
    upsert(
        cur,
        "ticket_group",
        {
            "id": g["id"],
            "name": g["name"],
            "name_last": g.get("name_last"),
            "note": g.get("note"),
            "email_address_id": g.get("email_address_id"),
            "parent_id": parent_id if parent_id in known_group_ids else None,
            "assignment_timeout": g.get("assignment_timeout"),
            "reopen_time_in_days": g.get("reopen_time_in_days"),
            "active": g.get("active", True),
            "follow_up_assignment": g.get("follow_up_assignment", True),
            "follow_up_possible": g.get("follow_up_possible", "yes"),
            "shared_drafts": g.get("shared_drafts", True),
            "signature": g.get("signature"),
            "signature_id": g.get("signature_id"),
            "summary_generation": g.get("summary_generation", "global_default"),
            "created_at": ts(g["created_at"]),
            "updated_at": ts(g["updated_at"]),
            "created_by_id": fk_user(g.get("created_by_id")),
            "updated_by_id": fk_user(g.get("updated_by_id")),
        },
        ["id"],
    )

    # --- ticket ----------------------------------------------------------- #
    # The Avro Ticket embeds group/priority/owner records instead of carrying
    # group_id/owner_id, so the FK ids are taken from those records. Priority
    # and article type/sender are stored by name (no lookup tables).
    upsert(
        cur,
        "ticket",
        {
            "id": ticket["id"],
            "number": ticket["number"],
            "title": ticket["title"],
            "state": ticket["state"],
            "group_id": g["id"],
            "priority_name": p["name"],
            "priority_note": p.get("note"),
            "owner_id": ticket["owner"]["id"],
            "customer_id": ticket["customer_id"],
            "create_article_type": ticket["create_article_type"],
            "create_article_sender": ticket["create_article_sender"],
            "ai_agent_running": ticket.get("ai_agent_running", False),
            "checklist_id": ticket.get("checklist_id"),
            "note": ticket.get("note"),
            "pending_time": ts(ticket.get("pending_time")),
            "escalation_at": ts(ticket.get("escalation_at")),
            "close_escalation_at": ts(ticket.get("close_escalation_at")),
            "first_response_escalation_at": ts(ticket.get("first_response_escalation_at")),
            "update_escalation_at": ts(ticket.get("update_escalation_at")),
            "last_contact_at": ts(ticket.get("last_contact_at")),
            "last_contact_agent_at": ts(ticket.get("last_contact_agent_at")),
            "last_contact_customer_at": ts(ticket.get("last_contact_customer_at")),
            "last_owner_update_at": ts(ticket.get("last_owner_update_at")),
            "first_response_at": ts(ticket.get("first_response_at")),
            "close_at": ts(ticket.get("close_at")),
            "last_close_at": ts(ticket.get("last_close_at")),
            "first_response_in_min": ticket.get("first_response_in_min"),
            "first_response_diff_in_min": ticket.get("first_response_diff_in_min"),
            "close_in_min": ticket.get("close_in_min"),
            "close_diff_in_min": ticket.get("close_diff_in_min"),
            "update_in_min": ticket.get("update_in_min"),
            "update_diff_in_min": ticket.get("update_diff_in_min"),
            "time_unit": num(ticket.get("time_unit")),
            "created_at": ts(ticket["created_at"]),
            "updated_at": ts(ticket["updated_at"]),
            "created_by_id": fk_user(ticket.get("created_by_id")),
            "updated_by_id": fk_user(ticket.get("updated_by_id")),
        },
        ["id"],
    )

    # --- article (skipped when the event carried no article) -------------- #
    if not article:
        return
    upsert(
        cur,
        "article",
        {
            "id": article["id"],
            "ticket_id": article["ticket_id"],
            "article_type": article["type"],
            "article_sender": article["sender"],
            "body": article.get("body", ""),
            "content_type": article.get("content_type", "text/html"),
            "from_address": article.get("from"),
            "to_address": article.get("to"),
            "subject": article.get("subject"),
            "cc": article.get("cc"),
            "reply_to": article.get("reply_to"),
            "in_reply_to": article.get("in_reply_to"),
            "message_id": article.get("message_id"),
            "message_id_md5": article.get("message_id_md5"),
            "internal": article.get("internal", False),
            "body_rendering_error": article.get("body_rendering_error", False),
            "origin_by": article.get("origin_by"),
            "origin_by_id": fk_user(article.get("origin_by_id")),
            "detected_language": article.get("detected_language"),
            "time_unit": num(article.get("time_unit")),
            "accounted_time": num(article.get("accounted_time")) or 0,
            "created_at": ts(article["created_at"]),
            "updated_at": ts(article["updated_at"]),
            "created_by_id": fk_user(article.get("created_by_id")),
            "updated_by_id": fk_user(article.get("updated_by_id")),
        },
        ["id"],
    )
