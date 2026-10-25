"""Schema-driven projection of an arbitrary JSON dict onto an Avro schema.

The raw Zammad JSON carries many more fields than the target Avro schema
(TicketAction) defines, and a few fields have different types than the
schema expects (e.g. ``role_ids`` is ``array<long>`` in the JSON but
``array<string>`` in Avro).  ``conform`` walks the Avro schema and, for every
field the schema declares, picks the matching value out of the JSON, coercing
primitives and recursing into records/arrays/unions.  Anything the schema does
not declare is dropped; anything missing falls back to the field default.
"""

from __future__ import annotations

from typing import Any

PRIMITIVES = {"null", "boolean", "int", "long", "float", "double", "bytes", "string"}


class ConformError(ValueError):
    """Raised when a required field is missing or a value cannot be coerced."""


def _coerce_primitive(value: Any, avro_type: str, path: str) -> Any:
    if value is None:
        return None
    try:
        if avro_type == "string":
            return value if isinstance(value, str) else str(value)
        if avro_type in ("long", "int"):
            return int(value)
        if avro_type in ("double", "float"):
            return float(value)
        if avro_type == "boolean":
            return bool(value)
        if avro_type == "bytes":
            return value if isinstance(value, bytes) else str(value).encode("utf-8")
        if avro_type == "null":
            return None
    except (TypeError, ValueError) as exc:
        raise ConformError(f"{path}: cannot coerce {value!r} to {avro_type}") from exc
    raise ConformError(f"{path}: unsupported primitive type {avro_type!r}")


def _conform_union(value: Any, branches: list, named: dict, path: str) -> Any:
    # Null first: unions in this schema are all [null, X] optionals.
    if value is None:
        if any(b == "null" for b in branches):
            return None
        raise ConformError(f"{path}: null value for non-nullable union {branches}")
    non_null = [b for b in branches if b != "null"]
    if len(non_null) == 1:
        return conform(value, non_null[0], named, path)
    # Multiple non-null branches: try each, keep the first that conforms.
    last_err: Exception | None = None
    for branch in non_null:
        try:
            return conform(value, branch, named, path)
        except ConformError as exc:
            last_err = exc
    raise ConformError(f"{path}: {value!r} matched no branch of {branches}") from last_err


def conform(value: Any, schema: Any, named: dict | None = None, path: str = "$") -> Any:
    """Return ``value`` reshaped to match the Avro ``schema``.

    ``named`` is a mutable registry of named types (records/enums/fixed) used to
    resolve by-name references such as the reused ``User`` record.
    """
    if named is None:
        named = {}

    # Named-type reference or primitive given as a bare string.
    if isinstance(schema, str):
        if schema in PRIMITIVES:
            return _coerce_primitive(value, schema, path)
        if schema in named:
            return conform(value, named[schema], named, path)
        raise ConformError(f"{path}: unknown named type {schema!r}")

    # Union.
    if isinstance(schema, list):
        return _conform_union(value, schema, named, path)

    if not isinstance(schema, dict):
        raise ConformError(f"{path}: unexpected schema node {schema!r}")

    avro_type = schema["type"]

    if avro_type == "record":
        name = schema.get("name")
        if name:
            named.setdefault(name, schema)
            ns = schema.get("namespace")
            if ns:
                named.setdefault(f"{ns}.{name}", schema)
        if not isinstance(value, dict):
            raise ConformError(f"{path}: expected object for record {name}, got {type(value).__name__}")
        result: dict[str, Any] = {}
        for field in schema["fields"]:
            fname = field["name"]
            ftype = field["type"]
            fpath = f"{path}.{fname}"
            if fname in value:
                result[fname] = conform(value[fname], ftype, named, fpath)
            elif "default" in field:
                result[fname] = field["default"]
            else:
                # Required field absent from the JSON: let a nullable union
                # become null, otherwise fail loudly.
                if isinstance(ftype, list) and "null" in ftype:
                    result[fname] = None
                else:
                    raise ConformError(f"{fpath}: required field missing from input")
        return result

    if avro_type == "enum":
        name = schema.get("name")
        if name:
            named.setdefault(name, schema)
        if value is None:
            raise ConformError(f"{path}: null value for enum {name}")
        sym = str(value)
        if sym not in schema["symbols"]:
            raise ConformError(f"{path}: {sym!r} not a valid {name} symbol {schema['symbols']}")
        return sym

    if avro_type == "array":
        items = schema["items"]
        return [conform(v, items, named, f"{path}[{i}]") for i, v in enumerate(value or [])]

    if avro_type == "map":
        values = schema["values"]
        return {k: conform(v, values, named, f"{path}.{k}") for k, v in (value or {}).items()}

    if avro_type == "fixed":
        name = schema.get("name")
        if name:
            named.setdefault(name, schema)
        return value

    # Primitive wrapped in a dict, e.g. {"type": "long"}.
    if avro_type in PRIMITIVES:
        return _coerce_primitive(value, avro_type, path)

    raise ConformError(f"{path}: unsupported schema type {avro_type!r}")
