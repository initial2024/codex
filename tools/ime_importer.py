#!/usr/bin/env python3
"""Orbit IME build-time dictionary importer.

The importer is intentionally offline. It converts explicitly licensed source
files into compact .odict assets consumed by the Android runtime.

Supported source formats:
- orbit-tsv: pinyin<TAB>text<TAB>frequency
- cedict: standard CC-CEDICT lines (trad simp [pin1 yin1] /gloss/)
- english-tsv: word<TAB>frequency[<TAB>candidate phrase ...]
- ngram-tsv: token1<TAB>token2<TAB>count OR token1<TAB>token2<TAB>token3<TAB>count

Unknown or non-redistributable sources are rejected in strict mode.
"""
from __future__ import annotations

import argparse
import hashlib
import json
import math
import re
import sys
from collections import defaultdict
from dataclasses import dataclass
from pathlib import Path
from typing import Iterable

ALLOWED_LICENSES = {
    "PROJECT",
    "Apache-2.0",
    "MIT",
    "BSD-2-Clause",
    "BSD-3-Clause",
    "CC-BY-4.0",
    "CC-BY-SA-4.0",
}

CEDICT_RE = re.compile(r"^(\S+)\s+(\S+)\s+\[([^\]]+)\]\s+/(.*)/$")
TONE_RE = re.compile(r"[1-5]")
NON_PINYIN_RE = re.compile(r"[^a-zv]+")


def base36(value: int) -> str:
    value = max(0, int(value))
    chars = "0123456789abcdefghijklmnopqrstuvwxyz"
    if value == 0:
        return "0"
    out = []
    while value:
        value, rem = divmod(value, 36)
        out.append(chars[rem])
    return "".join(reversed(out))


def normalize_pinyin(value: str) -> str:
    value = value.lower().replace("ü", "v").replace("u:", "v")
    value = TONE_RE.sub("", value)
    return NON_PINYIN_RE.sub("", value)


def clamp_frequency(value: float | int, minimum: int = 1, maximum: int = 2_000_000_000) -> int:
    if isinstance(value, float) and not math.isfinite(value):
        return minimum
    return max(minimum, min(maximum, int(round(value))))


@dataclass(frozen=True)
class SourceSpec:
    name: str
    path: Path
    format: str
    license: str
    url: str
    redistribution_allowed: bool
    attribution: str
    default_frequency: int = 100


def load_manifest(path: Path) -> list[SourceSpec]:
    raw = json.loads(path.read_text(encoding="utf-8"))
    base = path.parent
    specs: list[SourceSpec] = []
    for item in raw.get("sources", []):
        specs.append(
            SourceSpec(
                name=str(item["name"]),
                path=(base / str(item["path"])).resolve(),
                format=str(item["format"]),
                license=str(item["license"]),
                url=str(item.get("url", "")),
                redistribution_allowed=bool(item.get("redistribution_allowed", False)),
                attribution=str(item.get("attribution", "")),
                default_frequency=clamp_frequency(item.get("default_frequency", 100)),
            )
        )
    return specs


def validate_source(spec: SourceSpec, strict: bool) -> None:
    if not spec.path.is_file():
        raise ValueError(f"source missing: {spec.name}: {spec.path}")
    if not spec.redistribution_allowed:
        raise ValueError(f"source is not marked redistributable: {spec.name}")
    if strict and spec.license not in ALLOWED_LICENSES:
        raise ValueError(f"license is not in strict allow-list: {spec.name}: {spec.license}")
    if spec.license != "PROJECT" and not spec.url:
        raise ValueError(f"third-party source needs a source URL: {spec.name}")
    if spec.license.startswith("CC-BY") and not spec.attribution:
        raise ValueError(f"CC licensed source needs attribution text: {spec.name}")


def iter_orbit_tsv(spec: SourceSpec) -> Iterable[tuple[str, str, int]]:
    with spec.path.open("r", encoding="utf-8") as handle:
        for line_no, raw in enumerate(handle, 1):
            line = raw.strip("\n\r")
            if not line or line.startswith("#"):
                continue
            parts = line.split("\t")
            if len(parts) < 2:
                raise ValueError(f"{spec.name}:{line_no}: expected pinyin<TAB>text[<TAB>frequency]")
            pinyin = normalize_pinyin(parts[0])
            text = parts[1].strip()
            freq = clamp_frequency(parts[2]) if len(parts) >= 3 and parts[2].strip().isdigit() else spec.default_frequency
            if pinyin and text:
                yield pinyin, text, freq


def iter_cedict(spec: SourceSpec) -> Iterable[tuple[str, str, int]]:
    with spec.path.open("r", encoding="utf-8") as handle:
        for raw in handle:
            line = raw.strip()
            if not line or line.startswith("#"):
                continue
            match = CEDICT_RE.match(line)
            if not match:
                continue
            _trad, simp, pinyin, _gloss = match.groups()
            normalized = normalize_pinyin(pinyin)
            if normalized and simp:
                yield normalized, simp, spec.default_frequency


def iter_english_tsv(spec: SourceSpec) -> Iterable[tuple[str, int, list[str]]]:
    with spec.path.open("r", encoding="utf-8") as handle:
        for line_no, raw in enumerate(handle, 1):
            line = raw.strip("\n\r")
            if not line or line.startswith("#"):
                continue
            parts = line.split("\t")
            if len(parts) < 2:
                raise ValueError(f"{spec.name}:{line_no}: expected word<TAB>frequency[<TAB>candidate...]")
            key = parts[0].strip().lower()
            try:
                freq = clamp_frequency(float(parts[1]))
            except ValueError as exc:
                raise ValueError(f"{spec.name}:{line_no}: invalid frequency") from exc
            candidates = [value.strip() for value in parts[2:] if value.strip()]
            yield key, freq, candidates


def iter_ngram_tsv(spec: SourceSpec) -> Iterable[tuple[tuple[str, ...], int]]:
    with spec.path.open("r", encoding="utf-8") as handle:
        for line_no, raw in enumerate(handle, 1):
            line = raw.strip("\n\r")
            if not line or line.startswith("#"):
                continue
            parts = line.split("\t")
            if len(parts) not in (3, 4):
                raise ValueError(f"{spec.name}:{line_no}: expected 2-gram or 3-gram TSV")
            try:
                count = clamp_frequency(float(parts[-1]))
            except ValueError as exc:
                raise ValueError(f"{spec.name}:{line_no}: invalid count") from exc
            tokens = tuple(value.strip() for value in parts[:-1])
            if all(tokens):
                yield tokens, count


def write_lexicon_assets(records: dict[tuple[str, str], int], out_dir: Path) -> dict[str, int]:
    lexicon_dir = out_dir / "lexicon"
    lexicon_dir.mkdir(parents=True, exist_ok=True)
    shards: dict[str, list[tuple[str, str, int]]] = defaultdict(list)
    for (pinyin, text), freq in records.items():
        shard = pinyin[0] if pinyin and pinyin[0].isalpha() else "_"
        shards[shard].append((pinyin, text, freq))
    counts: dict[str, int] = {}
    for shard, rows in shards.items():
        rows.sort(key=lambda row: (row[0], -row[2], row[1]))
        target = lexicon_dir / f"{shard}.odict"
        with target.open("w", encoding="utf-8", newline="\n") as handle:
            handle.write("#ORBIT_ODICT\t1\tLEXICON\n")
            for pinyin, text, freq in rows:
                handle.write(f"{pinyin}\t{text}\t{base36(freq)}\n")
        counts[shard] = len(rows)
    return counts


def write_english_asset(records: dict[str, tuple[int, set[str]]], out_dir: Path) -> int:
    target = out_dir / "english.odict"
    rows = sorted(records.items(), key=lambda item: (-item[1][0], item[0]))
    with target.open("w", encoding="utf-8", newline="\n") as handle:
        handle.write("#ORBIT_ODICT\t1\tENGLISH\n")
        for key, (freq, candidates) in rows:
            suffix = "\t".join(sorted(candidates))
            line = f"{key}\t{base36(freq)}"
            if suffix:
                line += "\t" + suffix
            handle.write(line + "\n")
    return len(rows)


def write_ngram_assets(ngrams: dict[tuple[str, ...], int], out_dir: Path) -> dict[str, int]:
    by_n: dict[int, list[tuple[tuple[str, ...], int]]] = defaultdict(list)
    for tokens, count in ngrams.items():
        by_n[len(tokens)].append((tokens, count))
    counts: dict[str, int] = {}
    for n, rows in by_n.items():
        rows.sort(key=lambda item: (item[0], -item[1]))
        target = out_dir / f"ngram{n}.odict"
        with target.open("w", encoding="utf-8", newline="\n") as handle:
            handle.write(f"#ORBIT_ODICT\t1\tNGRAM{n}\n")
            for tokens, count in rows:
                handle.write("\t".join(tokens) + "\t" + base36(count) + "\n")
        counts[str(n)] = len(rows)
    return counts


def sha256(path: Path) -> str:
    digest = hashlib.sha256()
    with path.open("rb") as handle:
        for chunk in iter(lambda: handle.read(1024 * 1024), b""):
            digest.update(chunk)
    return digest.hexdigest()


def write_runtime_manifest(out_dir: Path, source_specs: list[SourceSpec], counts: dict[str, object]) -> None:
    files = []
    for path in sorted(out_dir.rglob("*.odict")):
        files.append({
            "path": path.relative_to(out_dir).as_posix(),
            "bytes": path.stat().st_size,
            "sha256": sha256(path),
        })
    payload = {
        "format": "ORBIT_ODICT",
        "version": 1,
        "counts": counts,
        "sources": [
            {
                "name": spec.name,
                "format": spec.format,
                "license": spec.license,
                "url": spec.url,
                "attribution": spec.attribution,
            }
            for spec in source_specs
        ],
        "files": files,
    }
    (out_dir / "manifest.json").write_text(json.dumps(payload, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")


def build(args: argparse.Namespace) -> int:
    manifest_path = Path(args.manifest).resolve()
    out_dir = Path(args.output).resolve()
    specs = load_manifest(manifest_path)
    if not specs:
        raise ValueError("manifest has no sources")
    for spec in specs:
        validate_source(spec, strict=not args.allow_unknown_license)

    lexicon: dict[tuple[str, str], int] = {}
    english: dict[str, tuple[int, set[str]]] = {}
    ngrams: dict[tuple[str, ...], int] = {}

    for spec in specs:
        if spec.format == "orbit-tsv":
            for pinyin, text, freq in iter_orbit_tsv(spec):
                key = (pinyin, text)
                lexicon[key] = max(freq, lexicon.get(key, 0))
        elif spec.format == "cedict":
            for pinyin, text, freq in iter_cedict(spec):
                key = (pinyin, text)
                lexicon[key] = max(freq, lexicon.get(key, 0))
        elif spec.format == "english-tsv":
            for key, freq, candidates in iter_english_tsv(spec):
                old_freq, old_candidates = english.get(key, (0, set()))
                english[key] = (max(old_freq, freq), old_candidates | set(candidates))
        elif spec.format == "ngram-tsv":
            for tokens, count in iter_ngram_tsv(spec):
                ngrams[tokens] = max(count, ngrams.get(tokens, 0))
        else:
            raise ValueError(f"unsupported source format: {spec.format}")

    out_dir.mkdir(parents=True, exist_ok=True)
    lexicon_counts = write_lexicon_assets(lexicon, out_dir)
    english_count = write_english_asset(english, out_dir) if english else 0
    ngram_counts = write_ngram_assets(ngrams, out_dir)
    write_runtime_manifest(
        out_dir,
        specs,
        {
            "lexicon": sum(lexicon_counts.values()),
            "lexicon_shards": lexicon_counts,
            "english": english_count,
            "ngrams": ngram_counts,
        },
    )
    print(f"Wrote Orbit IME assets to {out_dir}")
    print(f"Lexicon entries: {sum(lexicon_counts.values())}")
    print(f"English entries: {english_count}")
    print(f"N-grams: {ngram_counts}")
    return 0


def main() -> int:
    parser = argparse.ArgumentParser(description="Build compact Orbit IME dictionary assets")
    parser.add_argument("--manifest", required=True, help="source manifest JSON")
    parser.add_argument("--output", default="app/src/main/assets/ime", help="output asset directory")
    parser.add_argument("--allow-unknown-license", action="store_true", help="development only; bypass strict license allow-list")
    args = parser.parse_args()
    try:
        return build(args)
    except Exception as exc:  # fail closed: importer errors must stop asset publication
        print(f"IME importer failed: {exc}", file=sys.stderr)
        return 2


if __name__ == "__main__":
    raise SystemExit(main())
