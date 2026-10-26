"""Map a deserialized ``AgentUser`` Avro record onto the ``agent_user`` table
defined in ``cr_ticket.sql``.

The row is upserted on primary key, so re-consuming the same (or an updated)
state message is idempotent.

``out_of_office_replacement_id`` is a self-referencing FK. It is resolved with a
sub-select, so it is written as NULL when the replacement user has not been
loaded yet (instead of violating the constraint).
"""

from __future__ import annotations

from datetime import datetime
from typing import Any

UPSERT_SQL = """
INSERT INTO agent_user (
    id, login, email, firstname, lastname, image, image_source,
    active, verified, out_of_office, out_of_office_start_at, out_of_office_end_at,
    out_of_office_replacement_id, created_at, updated_at
) VALUES (
    %(id)s, %(login)s, %(email)s, %(firstname)s, %(lastname)s, %(image)s, %(image_source)s,
    %(active)s, %(verified)s, %(out_of_office)s, %(out_of_office_start_at)s, %(out_of_office_end_at)s,
    (SELECT id FROM agent_user WHERE id = %(out_of_office_replacement_id)s),
    %(created_at)s, %(updated_at)s
)
ON CONFLICT (id) DO UPDATE SET
    login = EXCLUDED.login,
    email = EXCLUDED.email,
    firstname = EXCLUDED.firstname,
    lastname = EXCLUDED.lastname,
    image = EXCLUDED.image,
    image_source = EXCLUDED.image_source,
    active = EXCLUDED.active,
    verified = EXCLUDED.verified,
    out_of_office = EXCLUDED.out_of_office,
    out_of_office_start_at = EXCLUDED.out_of_office_start_at,
    out_of_office_end_at = EXCLUDED.out_of_office_end_at,
    out_of_office_replacement_id = EXCLUDED.out_of_office_replacement_id,
    created_at = EXCLUDED.created_at,
    updated_at = EXCLUDED.updated_at
"""


def ts(value: Any):
    """Parse an ISO-8601 string into a timezone-aware datetime, or None."""
    if value is None or value == "":
        return None
    if isinstance(value, datetime):
        return value
    return datetime.fromisoformat(str(value).replace("Z", "+00:00"))


def persist_agent_user(cur, u: dict) -> None:
    cur.execute(
        UPSERT_SQL,
        {
            "id": u["id"],
            "login": u["login"],
            "email": u.get("email") or "",
            "firstname": u.get("firstname"),
            "lastname": u.get("lastname"),
            "image": u.get("image"),
            "image_source": u.get("image_source"),
            "active": u.get("active", True),
            "verified": u.get("verified", False),
            "out_of_office": u.get("out_of_office", False),
            "out_of_office_start_at": ts(u.get("out_of_office_start_at")),
            "out_of_office_end_at": ts(u.get("out_of_office_end_at")),
            "out_of_office_replacement_id": u.get("out_of_office_replacement_id"),
            "created_at": ts(u["created_at"]),
            "updated_at": ts(u["updated_at"]),
        },
    )
