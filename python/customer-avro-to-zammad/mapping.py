"""Map deserialized ``CustomerState`` / ``OrganizationState`` Avro records onto
Zammad users / organizations and push them through the Zammad REST API.

The Zammad user is upserted on ``login``: it is looked up first and updated
(``PUT``) if found, created (``POST``) otherwise. So re-consuming the same (or
an updated) state message is idempotent.

The Zammad organization is looked up by the id stored in ``organization_id_mapping``
(falling back to its name) and updated if found, created otherwise.

New users get the ``Customer`` role. The role list is not sent on updates, so a
user that already exists keeps whatever roles it has.
"""

from __future__ import annotations

from typing import Any

import requests
from requests.adapters import HTTPAdapter
from urllib3.util.retry import Retry

CUSTOMER_ROLE = "Customer"


def _clean(value: Any) -> Any:
    """Treat empty strings like missing values so Zammad doesn't get blanked fields."""
    return None if value in (None, "") else value


def to_zammad_user(customer: dict[str, Any]) -> dict[str, Any]:
    """Build the Zammad user payload (without ``roles``) from the Avro ``Customer``."""
    payload: dict[str, Any] = {
        "login": customer["login"],
        "email": customer["email_address"],
        "firstname": customer.get("first_name"),
        "lastname": customer["last_name"],
        "phone": customer.get("phone_number"),
        "mobile": customer.get("mobile"),
        "fax": customer.get("fax"),
        "web": customer.get("web"),
        "note": customer.get("note"),
        "department": customer.get("department"),
        "address": customer.get("address"),
        "active": customer["active"],
        "verified": customer["verified"],
        "vip": customer["vip"],
    }

    # Zammad stores a single address on the user, so the first one wins.
    addresses = customer.get("addresses") or []
    if addresses:
        first = addresses[0]
        payload["street"] = first.get("street")
        payload["zip"] = first.get("zip_code")
        payload["city"] = first.get("city")

    return {k: v for k, v in ((k, _clean(v)) for k, v in payload.items()) if v is not None}


def to_zammad_organization(organization: dict[str, Any]) -> dict[str, Any]:
    """Build the Zammad organization payload from the Avro ``Organization``."""
    payload: dict[str, Any] = {
        "name": organization["name"],
        "shared": organization["shared"],
        "domain": organization.get("domain") or "",
        "domain_assignment": organization["domain_assignment"],
        "active": organization["active"],
        "vip": organization["vip"],
    }
    note = _clean(organization.get("note"))
    if note is not None:
        payload["note"] = note
    return payload


# Remember which Zammad user belongs to which Customer id (UUID). A customer that
# is re-created in Zammad (new login -> new user id) replaces its stale mapping.
DELETE_STALE_MAPPING_SQL = """
DELETE FROM customer_id_mapping
WHERE customer_id = %(customer_id)s AND zammad_user_id <> %(zammad_user_id)s
"""

UPSERT_MAPPING_SQL = """
INSERT INTO customer_id_mapping (zammad_user_id, customer_id)
VALUES (%(zammad_user_id)s, %(customer_id)s)
ON CONFLICT (zammad_user_id) DO UPDATE SET customer_id = EXCLUDED.customer_id
"""


def save_customer_mapping(cur, zammad_user_id: int, customer_id: str) -> None:
    """Upsert ``zammad_user_id -> customer_id`` into ``customer_id_mapping`` (idempotent)."""
    params = {"zammad_user_id": zammad_user_id, "customer_id": customer_id}
    cur.execute(DELETE_STALE_MAPPING_SQL, params)
    cur.execute(UPSERT_MAPPING_SQL, params)


DELETE_STALE_ORG_MAPPING_SQL = """
DELETE FROM organization_id_mapping
WHERE organization_id = %(organization_id)s AND zammad_organization_id <> %(zammad_organization_id)s
"""

UPSERT_ORG_MAPPING_SQL = """
INSERT INTO organization_id_mapping (zammad_organization_id, organization_id)
VALUES (%(zammad_organization_id)s, %(organization_id)s)
ON CONFLICT (zammad_organization_id) DO UPDATE SET organization_id = EXCLUDED.organization_id
"""

SELECT_ORG_MAPPING_SQL = """
SELECT zammad_organization_id FROM organization_id_mapping WHERE organization_id = %(organization_id)s
"""


def find_zammad_organization_id(cur, organization_id: str) -> int | None:
    """Zammad organization id already mapped to this Organization id (UUID), if any."""
    cur.execute(SELECT_ORG_MAPPING_SQL, {"organization_id": organization_id})
    row = cur.fetchone()
    return row[0] if row else None


def save_organization_mapping(cur, zammad_organization_id: int, organization_id: str) -> None:
    """Upsert ``zammad_organization_id -> organization_id`` into ``organization_id_mapping`` (idempotent)."""
    params = {"zammad_organization_id": zammad_organization_id, "organization_id": organization_id}
    cur.execute(DELETE_STALE_ORG_MAPPING_SQL, params)
    cur.execute(UPSERT_ORG_MAPPING_SQL, params)


class ZammadClient:
    def __init__(self, base_url: str, token: str | None = None, user: str | None = None,
                 password: str | None = None, timeout: float = 30.0) -> None:
        self.base_url = base_url.rstrip("/")
        self.timeout = timeout
        self.session = requests.Session()
        if token:
            self.session.headers["Authorization"] = f"Token token={token}"
        elif user:
            self.session.auth = (user, password or "")
        retry = Retry(total=3, backoff_factor=1, status_forcelist=(429, 502, 503, 504), allowed_methods=None)
        self.session.mount("http://", HTTPAdapter(max_retries=retry))
        self.session.mount("https://", HTTPAdapter(max_retries=retry))

    def _url(self, path: str) -> str:
        return f"{self.base_url}/api/v1/{path}"

    def find_user_by_login(self, login: str) -> dict[str, Any] | None:
        # The search is fuzzy, so filter for the exact login on the result.
        resp = self.session.get(
            self._url("users/search"), params={"query": f'login:"{login}"', "limit": 10}, timeout=self.timeout
        )
        resp.raise_for_status()
        return next((u for u in resp.json() if u.get("login") == login), None)

    def find_organization_by_name(self, name: str) -> dict[str, Any] | None:
        # The search is fuzzy, so filter for the exact name on the result.
        resp = self.session.get(
            self._url("organizations/search"), params={"query": f'name:"{name}"', "limit": 10}, timeout=self.timeout
        )
        resp.raise_for_status()
        return next((o for o in resp.json() if o.get("name") == name), None)

    def upsert_organization(self, payload: dict[str, Any], known_id: int | None = None) -> tuple[str, int]:
        """Create or update the organization; returns ``("created"|"updated", zammad_id)``.

        ``known_id`` is the Zammad id from the mapping table. Without it the organization is matched by name.
        """
        zammad_id = known_id
        if zammad_id is None:
            existing = self.find_organization_by_name(payload["name"])
            zammad_id = existing["id"] if existing else None
        if zammad_id is not None:
            resp = self.session.put(self._url(f"organizations/{zammad_id}"), json=payload, timeout=self.timeout)
            action = "updated"
        else:
            resp = self.session.post(self._url("organizations"), json=payload, timeout=self.timeout)
            action = "created"
        if not resp.ok:
            raise RuntimeError(f"Zammad {resp.request.method} {resp.url} -> {resp.status_code}: {resp.text[:300]}")
        return action, resp.json()["id"]

    def upsert_customer(self, payload: dict[str, Any]) -> tuple[str, int]:
        """Create or update the user; returns ``("created"|"updated", zammad_id)``."""
        existing = self.find_user_by_login(payload["login"])
        if existing:
            resp = self.session.put(self._url(f"users/{existing['id']}"), json=payload, timeout=self.timeout)
            action = "updated"
        else:
            resp = self.session.post(
                self._url("users"), json={**payload, "roles": [CUSTOMER_ROLE]}, timeout=self.timeout
            )
            action = "created"
        if not resp.ok:
            raise RuntimeError(f"Zammad {resp.request.method} {resp.url} -> {resp.status_code}: {resp.text[:300]}")
        return action, resp.json()["id"]
