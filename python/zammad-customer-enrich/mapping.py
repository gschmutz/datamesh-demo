"""Shape raw Zammad JSON into the Customer Avro schema.

The Customer record (ch.admin.customer.avro.Customer) embeds full Organization
records: ``organization`` (the primary org) and ``secondary_organizations``
(the rest). This module provides the projections; the organization lookups are
supplied by the caller from the organization cache.
"""

from __future__ import annotations

from typing import Any, Optional


def conform_organization(o: dict) -> dict:
    """Project a raw Zammad organization onto the Organization Avro schema."""
    return {
        "id": int(o["id"]),
        "name": str(o["name"]),
        "shared": bool(o.get("shared", False)),
        "domain": o.get("domain") or "",
        "domain_assignment": bool(o.get("domain_assignment", False)),
        "active": bool(o.get("active", True)),
        "vip": bool(o.get("vip", False)),
        "note": o.get("note"),  # ["null","string"]
        "member_ids": [int(x) for x in (o.get("member_ids") or [])],
        "secondary_member_ids": [int(x) for x in (o.get("secondary_member_ids") or [])],
        "created_at": str(o["created_at"]),
        "updated_at": str(o["updated_at"]),
    }


def conform_customer(
    u: dict,
    organization: Optional[dict],
    secondary_organizations: list[dict],
) -> dict:
    """Project a raw Zammad user + resolved orgs onto the Customer Avro schema."""
    return {
        "id": int(u["id"]),
        "login": str(u["login"]),
        "email": str(u.get("email") or ""),
        "firstname": str(u.get("firstname") or ""),
        "lastname": str(u.get("lastname") or ""),
        "image": u.get("image"),
        "image_source": u.get("image_source"),
        "department": u.get("department"),
        "organization": organization,  # dict (Organization) or None
        "secondary_organizations": secondary_organizations,
        "created_at": str(u["created_at"]),
        "updated_at": str(u["updated_at"]),
        "active": bool(u.get("active", True)),
        "verified": bool(u.get("verified", False)),
        "vip": bool(u.get("vip", False)),
        "phone": u.get("phone") or "",
        "mobile": u.get("mobile") or "",
        "fax": u.get("fax") or "",
        "web": u.get("web") or "",
        "street": u.get("street") or "",
        "city": u.get("city") or "",
        "zip": u.get("zip") or "",
        "country": u.get("country") or "",
        "note": u.get("note") or "",
        "address": u.get("address"),
    }


def is_customer(user: dict, customer_role_id: int = 3) -> bool:
    """True if the user holds the customer role (role_ids contains the id)."""
    role_ids = user.get("role_ids") or []
    return customer_role_id in [int(r) for r in role_ids]
