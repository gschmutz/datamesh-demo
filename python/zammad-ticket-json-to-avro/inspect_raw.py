#!/usr/bin/env python3
"""Dump the structure of raw messages on a topic, to see what the real Zammad
payload looks like (shapes often differ from a hand-built sample).

    python inspect_raw.py --topic priv.zammad.ticket.v1 --max 1 --dump raw.json
"""
from __future__ import annotations

import argparse
import json
import os
import sys
import time


def shape(value, depth=0, max_depth=2):
    pad = "  " * depth
    if isinstance(value, dict):
        lines = []
        for k in sorted(value):
            v = value[k]
            if isinstance(v, dict):
                lines.append(f"{pad}{k}: dict({len(v)} keys)")
                if depth < max_depth:
                    lines.append(shape(v, depth + 1, max_depth))
            elif isinstance(v, list):
                kind = type(v[0]).__name__ if v else "empty"
                lines.append(f"{pad}{k}: list[{kind}] (len {len(v)})")
            else:
                lines.append(f"{pad}{k}: {type(v).__name__}")
        return "\n".join(l for l in lines if l)
    return f"{pad}{type(value).__name__}"


def main(argv):
    p = argparse.ArgumentParser()
    p.add_argument("--bootstrap-servers", default=os.environ.get("BOOTSTRAP_SERVERS", "localhost:9092"))
    p.add_argument("--topic", default="priv.zammad.ticket.v1")
    p.add_argument("--max", type=int, default=1)
    p.add_argument("--seconds", type=int, default=30)
    p.add_argument("--dump", help="Write the full raw JSON of the first message here.")
    args = p.parse_args(argv)

    from confluent_kafka import Consumer

    c = Consumer(
        {
            "bootstrap.servers": args.bootstrap_servers,
            "group.id": f"inspect-{int(time.time())}",
            "auto.offset.reset": "earliest",
            "enable.auto.commit": False,
        }
    )
    c.subscribe([args.topic])
    seen = 0
    deadline = time.time() + args.seconds
    try:
        while seen < args.max and time.time() < deadline:
            m = c.poll(1.0)
            if m is None:
                continue
            if m.error():
                print(f"consumer error: {m.error()}", file=sys.stderr)
                continue
            raw = m.value()
            d = json.loads(raw)
            print(f"\n=== {m.topic()}[{m.partition()}]@{m.offset()} — top-level keys: {list(d.keys())}")
            print(shape(d))
            if args.dump and seen == 0:
                with open(args.dump, "wb") as f:
                    f.write(raw if isinstance(raw, bytes) else raw.encode())
                print(f"\n(full message written to {args.dump})")
            seen += 1
    finally:
        c.close()
    if seen == 0:
        print("No message received.", file=sys.stderr)
    return 0


if __name__ == "__main__":
    raise SystemExit(main(sys.argv[1:]))
