"""Persist a deserialized Avro Customer into the normalized PostgreSQL schema
(scripts/postgresql/cr_customer.sql).

The Avro Customer embeds a primary ``organization`` and a list of
``secondary_organizations``. These are normalized into:
  * organization                      (one row per org, upserted by id)
  * customer.organization_id          (FK to the primary org)
  * customer_secondary_organization   (M:N join for the secondary orgs)

Upserts are idempotent (ON CONFLICT by primary key), so re-consuming the same
or an updated message is safe.
"""

from __future__ import annotations

from datetime import datetime
from typing import Any


def ts(value: Any):
    """Parse an ISO-8601 string into a tz-aware datetime (or None)."""
    if value is None or value == "":
        return None
    if isinstance(value, datetime):
        return value
    return datetime.fromisoformat(str(value).replace("Z", "+00:00"))


def upsert(cur, table: str, row: dict, pk_cols: list[str]) -> None:
    cols = list(row.keys())
    placeholders = ", ".join(["%s"] * len(cols))
    conflict = ", ".join(pk_cols)
    update_cols = [c for c in cols if c not in pk_cols]
    if update_cols:
        action = "DO UPDATE SET " + ", ".join(f"{c} = EXCLUDED.{c}" for c in update_cols)
    else:
        action = "DO NOTHING"
    cur.execute(
        f"INSERT INTO {table} ({', '.join(cols)}) VALUES ({placeholders}) "
        f"ON CONFLICT ({conflict}) {action}",
        [row[c] for c in cols],
    )


def _upsert_organization(cur, o: dict) -> int:
    upsert(
        cur,
        "organization",
        {
            "id": int(o["id"]),
            "name": o["name"],
            "shared": bool(o.get("shared", False)),
            "domain": o.get("domain") or "",
            "domain_assignment": bool(o.get("domain_assignment", False)),
            "active": bool(o.get("active", True)),
            "vip": bool(o.get("vip", False)),
            "note": o.get("note"),
            "member_ids": [int(x) for x in (o.get("member_ids") or [])],
            "secondary_member_ids": [int(x) for x in (o.get("secondary_member_ids") or [])],
            "created_at": ts(o["created_at"]),
            "updated_at": ts(o["updated_at"]),
        },
        ["id"],
    )
    return int(o["id"])


def persist_customer(cur, customer: dict) -> None:
    cid = int(customer["id"])

    # 1) Organizations first (FK targets): primary + all secondary, de-duped.
    primary = customer.get("organization")
    primary_id = _upsert_organization(cur, primary) if primary else None

    secondary_ids: list[int] = []
    seen: set[int] = set()
    for so in customer.get("secondary_organizations") or []:
        oid = _upsert_organization(cur, so)
        if oid not in seen:
            seen.add(oid)
            secondary_ids.append(oid)

    # 2) The customer row (primary org via FK).
    upsert(
        cur,
        "customer",
        {
            "id": cid,
            "login": customer["login"],
            "email": customer.get("email") or "",
            "firstname": customer.get("firstname") or "",
            "lastname": customer.get("lastname") or "",
            "image": customer.get("image"),
            "image_source": customer.get("image_source"),
            "department": customer.get("department"),
            "organization_id": primary_id,
            "created_at": ts(customer["created_at"]),
            "updated_at": ts(customer["updated_at"]),
            "active": bool(customer.get("active", True)),
            "verified": bool(customer.get("verified", False)),
            "vip": bool(customer.get("vip", False)),
            "phone": customer.get("phone") or "",
            "mobile": customer.get("mobile") or "",
            "fax": customer.get("fax") or "",
            "web": customer.get("web") or "",
            "street": customer.get("street") or "",
            "city": customer.get("city") or "",
            "zip": customer.get("zip") or "",
            "country": customer.get("country") or "",
            "note": customer.get("note") or "",
            "address": customer.get("address"),
        },
        ["id"],
    )

    # 3) Rebuild the secondary-org join rows for this customer.
    cur.execute("DELETE FROM customer_secondary_organization WHERE customer_id = %s", (cid,))
    for oid in secondary_ids:
        upsert(
            cur,
            "customer_secondary_organization",
            {"customer_id": cid, "organization_id": oid},
            ["customer_id", "organization_id"],
        )
