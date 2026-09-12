#!/usr/bin/env python3
"""Rebuild v0.23 association shards from the final mature Chinese lexicon.

This stage runs after all licensed dictionary overlays are imported. It does not add a
new corpus: it derives bounded 1..6-character continuation statistics from the final
packaged lexicon so the runtime can use longer local context without loading the whole
lexicon into memory.
"""
from __future__ import annotations

import argparse
import heapq
import json
import shutil
from collections import defaultdict
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
DEFAULT_ASSETS = ROOT / "app/src/main/assets/ime"
SHARD_COUNT = 32
MAX_CONTEXT_CHARS = 6
MAX_SUFFIX_CHARS = 16
MAX_ROWS_PER_CONTEXT = 72
PRUNE_AT = 160
PRUNE_TO = 96
MAX_SOURCE_PHRASES = 140_000


def is_cjk_text(text: str) -> bool:
    return bool(text) and all(
        0x3400 <= ord(ch) <= 0x4DBF or 0x4E00 <= ord(ch) <= 0x9FFF or 0xF900 <= ord(ch) <= 0xFAFF
        for ch in text
    )


def parse_base36(raw: str) -> int:
    try:
        return max(1, int(raw.strip().lower(), 36))
    except ValueError:
        return 1


def load_top_phrases(lexicon_dir: Path) -> list[tuple[str, int]]:
    best: dict[str, int] = {}
    for path in sorted(lexicon_dir.glob("*.odict")):
        with path.open("r", encoding="utf-8") as handle:
            for raw in handle:
                line = raw.rstrip("\n")
                if not line or line.startswith("#"):
                    continue
                parts = line.split("\t")
                if len(parts) < 3:
                    continue
                text = parts[1].strip()
                if not (2 <= len(text) <= 24) or not is_cjk_text(text):
                    continue
                frequency = parse_base36(parts[2])
                if frequency > best.get(text, 0):
                    best[text] = frequency
    if len(best) <= MAX_SOURCE_PHRASES:
        return list(best.items())
    return heapq.nlargest(MAX_SOURCE_PHRASES, best.items(), key=lambda item: item[1])


def prune(bucket: dict[str, int], keep: int = PRUNE_TO) -> None:
    if len(bucket) <= keep:
        return
    selected = heapq.nlargest(keep, bucket.items(), key=lambda item: (item[1], item[0]))
    bucket.clear()
    bucket.update(selected)


def shard_for(context: str) -> int:
    return sum(ord(ch) for ch in context) % SHARD_COUNT


def build_associations(phrases: list[tuple[str, int]]) -> dict[str, dict[str, int]]:
    buckets: dict[str, dict[str, int]] = defaultdict(dict)
    for text, frequency in phrases:
        for split in range(1, len(text)):
            prefix = text[:split]
            suffix = text[split:]
            if not suffix or len(suffix) > MAX_SUFFIX_CHARS:
                continue
            max_width = min(MAX_CONTEXT_CHARS, len(prefix))
            for width in range(1, max_width + 1):
                context = prefix[-width:]
                bucket = buckets[context]
                old = bucket.get(suffix, 0)
                if frequency > old:
                    bucket[suffix] = frequency
                if len(bucket) >= PRUNE_AT:
                    prune(bucket)
    return buckets


def write_shards(output: Path, buckets: dict[str, dict[str, int]]) -> tuple[int, int]:
    association_dir = output / "association"
    if association_dir.exists():
        shutil.rmtree(association_dir)
    association_dir.mkdir(parents=True, exist_ok=True)

    rows_by_shard: dict[int, list[tuple[str, str, int]]] = defaultdict(list)
    total = 0
    max_context_seen = 0
    for context, candidates in buckets.items():
        max_context_seen = max(max_context_seen, len(context))
        ranked = heapq.nlargest(
            MAX_ROWS_PER_CONTEXT,
            candidates.items(),
            key=lambda item: (item[1], item[0]),
        )
        for candidate, frequency in ranked:
            rows_by_shard[shard_for(context)].append((context, candidate, frequency))
            total += 1

    for shard in range(SHARD_COUNT):
        rows = sorted(rows_by_shard.get(shard, []), key=lambda item: (item[0], -item[2], item[1]))
        path = association_dir / f"{shard:02x}.odict"
        with path.open("w", encoding="utf-8", newline="\n") as handle:
            handle.write("# Orbit v0.23 final-lexicon association data; contexts 1..6 chars.\n")
            for context, candidate, frequency in rows:
                handle.write(f"{context}\t{candidate}\t{frequency:x}\n")
    return total, max_context_seen


def update_report(output: Path, source_phrases: int, rows: int, max_context: int) -> None:
    path = output / "mature-report.json"
    payload = json.loads(path.read_text(encoding="utf-8"))
    stats = payload.setdefault("stats", {})
    stats["association_refine_source_phrases"] = source_phrases
    stats["association_entries"] = rows
    stats["association_max_context_chars"] = max_context
    path.write_text(json.dumps(payload, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")


def main() -> int:
    parser = argparse.ArgumentParser(description="Refine Orbit v0.23 association assets from final lexicon")
    parser.add_argument("--assets", default=str(DEFAULT_ASSETS))
    args = parser.parse_args()
    assets = Path(args.assets).resolve()
    lexicon_dir = assets / "lexicon"
    if not lexicon_dir.is_dir():
        raise RuntimeError("final lexicon shards are missing")
    report = assets / "mature-report.json"
    if not report.is_file():
        raise RuntimeError("mature-report.json is missing")

    phrases = load_top_phrases(lexicon_dir)
    if len(phrases) < 20_000:
        raise RuntimeError(f"too few final phrases for association refinement: {len(phrases)}")
    buckets = build_associations(phrases)
    rows, max_context = write_shards(assets, buckets)
    if rows < 10_000 or max_context < MAX_CONTEXT_CHARS:
        raise RuntimeError(f"refined association pack is incomplete: rows={rows}, max_context={max_context}")
    update_report(assets, len(phrases), rows, max_context)
    print(json.dumps({
        "status": "PASS",
        "source_phrases": len(phrases),
        "association_rows": rows,
        "max_context_chars": max_context,
        "shards": SHARD_COUNT,
        "rows_per_context": MAX_ROWS_PER_CONTEXT,
    }, ensure_ascii=False))
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
