"""In-memory organization lookup cache, populated from a Kafka topic.

Reads the whole organization topic from the beginning (no consumer group, so it
always rebuilds the full table), keeps the latest record per id, and keeps
following the topic in a background thread for live updates.
"""

from __future__ import annotations

import json
import threading
import uuid

from confluent_kafka import OFFSET_BEGINNING, Consumer, TopicPartition

from mapping import conform_organization


class OrganizationCache:
    def __init__(self, bootstrap_servers: str, topic: str):
        self._topic = topic
        self._orgs: dict[int, dict] = {}
        self._lock = threading.Lock()
        self._stop = threading.Event()
        self._caught_up = threading.Event()
        self._thread: threading.Thread | None = None
        self._consumer = Consumer(
            {
                "bootstrap.servers": bootstrap_servers,
                # Unique group + manual assignment => full replay every run.
                "group.id": f"org-cache-{uuid.uuid4()}",
                "enable.auto.commit": False,
                "auto.offset.reset": "earliest",
            }
        )

    def start(self) -> None:
        md = self._consumer.list_topics(self._topic, timeout=10)
        topic_md = md.topics.get(self._topic)
        if topic_md is None or topic_md.error is not None:
            # Topic doesn't exist yet (upstream hasn't produced). Don't crash:
            # run with an empty cache so customers are still produced (with
            # organization = null) until the organization topic appears.
            print(
                f"WARNING: organization topic '{self._topic}' not available yet — "
                f"starting with an empty cache (no organization enrichment). "
                f"Restart once it has data.",
                flush=True,
            )
            self._caught_up.set()
            return
        partitions = list(topic_md.partitions)

        # Assign all partitions at the beginning; compute target high watermarks
        # so we can tell when the initial load has caught up.
        self._consumer.assign([TopicPartition(self._topic, p, OFFSET_BEGINNING) for p in partitions])
        targets: dict[int, int] = {}
        for p in partitions:
            lo, hi = self._consumer.get_watermark_offsets(TopicPartition(self._topic, p), timeout=10)
            targets[p] = hi  # next offset to be produced; caught up when consumed offset >= hi-1
        self._thread = threading.Thread(target=self._run, args=(targets,), daemon=True)
        self._thread.start()

    def _run(self, targets: dict[int, int]) -> None:
        # Partitions that are already empty are caught up immediately.
        caught = {p: (targets[p] <= 0) for p in targets}
        if all(caught.values()):
            self._caught_up.set()

        while not self._stop.is_set():
            msg = self._consumer.poll(1.0)
            if msg is None:
                continue
            if msg.error():
                continue
            try:
                raw = json.loads(msg.value())
                org = conform_organization(raw)
            except (json.JSONDecodeError, KeyError, ValueError):
                continue
            with self._lock:
                self._orgs[org["id"]] = org

            if not self._caught_up.is_set():
                if msg.offset() >= targets.get(msg.partition(), 0) - 1:
                    caught[msg.partition()] = True
                if all(caught.values()):
                    self._caught_up.set()

    def wait_until_caught_up(self, timeout: float = 30.0) -> bool:
        return self._caught_up.wait(timeout)

    def get(self, org_id) -> dict | None:
        if org_id is None:
            return None
        with self._lock:
            return self._orgs.get(int(org_id))

    def size(self) -> int:
        with self._lock:
            return len(self._orgs)

    def close(self) -> None:
        self._stop.set()
        if self._thread:
            self._thread.join(timeout=5)
        self._consumer.close()
