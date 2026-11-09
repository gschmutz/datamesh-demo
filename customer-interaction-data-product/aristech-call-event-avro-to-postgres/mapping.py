"""Map a ``CallEvent`` Avro record onto the ``aristech.call_event`` table.

The nested ``userData`` record is flattened into columns (see
``scripts/postgresql/cr_aristech.sql``). The row is upserted on ``call_id`` so
re-consuming the same or an updated event is idempotent.
"""

from __future__ import annotations

from typing import Any

UPSERT_SQL = """
INSERT INTO call_event (
    call_id, is_authenticated, is_identified,
    anliegen, bedrohung, kundennummer, plz, prio, soc_l
) VALUES (
    %(call_id)s, %(is_authenticated)s, %(is_identified)s,
    %(anliegen)s, %(bedrohung)s, %(kundennummer)s, %(plz)s, %(prio)s, %(soc_l)s
)
ON CONFLICT (call_id) DO UPDATE SET
    is_authenticated = EXCLUDED.is_authenticated,
    is_identified    = EXCLUDED.is_identified,
    anliegen         = EXCLUDED.anliegen,
    bedrohung        = EXCLUDED.bedrohung,
    kundennummer     = EXCLUDED.kundennummer,
    plz              = EXCLUDED.plz,
    prio             = EXCLUDED.prio,
    soc_l            = EXCLUDED.soc_l
"""


def to_row(event: dict[str, Any]) -> dict[str, Any]:
    """Flatten a CallEvent record into the column values of ``call_event``."""
    user = event["userData"]
    return {
        "call_id": event["callID"],
        "is_authenticated": event["isAuthenticated"],
        "is_identified": event["isIdentified"],
        "anliegen": user["anliegen"],
        "bedrohung": user["bedrohung"],
        "kundennummer": user.get("kundennummer"),
        "plz": user.get("plz"),
        "prio": user["prio"],
        "soc_l": user["socL"],
    }


def persist_call_event(cur, event: dict[str, Any]) -> None:
    """Upsert one CallEvent using the given psycopg cursor (caller commits)."""
    cur.execute(UPSERT_SQL, to_row(event))
