#!/usr/bin/env python3
"""Orbit IME v0.20 local vocabulary augmentation.

Runs after augment_v018_data.py and adds:
- a higher-priority CC-CEDICT-derived four-character idiom layer;
- project-authored common software/platform/product vocabulary.

No new runtime network capability is introduced.
"""
from __future__ import annotations

import argparse
import json
import re
import shutil
import subprocess
import sys
from pathlib import Path

import augment_v018_data as v018

ROOT = Path(__file__).resolve().parents[1]
DEFAULT_CONFIG = ROOT / "data/ime_sources/mature_sources.json"
DEFAULT_OUTPUT = ROOT / "app/src/main/assets/ime"
DEFAULT_STAGING = ROOT / "build/ime-mature/staging"
DEFAULT_CACHE = ROOT / "build/ime-mature/vendor"
NON_PINYIN_RE = re.compile(r"[^a-zv]+")
TONE_RE = re.compile(r"[1-5]")


def is_cjk_text(text: str) -> bool:
    if not text:
        return False
    return all(0x3400 <= ord(ch) <= 0x4DBF or 0x4E00 <= ord(ch) <= 0x9FFF or 0xF900 <= ord(ch) <= 0xFAFF for ch in text)


def normalize_pinyin(raw: str) -> str:
    value = raw.lower().replace("ü", "v").replace("u:", "v")
    value = TONE_RE.sub("", value)
    return NON_PINYIN_RE.sub("", value)


def build_idiom_tsv(cedict_raw: bytes, target: Path, frequency: int) -> int:
    records: dict[tuple[str, str], int] = {}
    for _traditional, simplified, pinyin, _glosses in v018.parse_cedict(cedict_raw):
        if len(simplified) != 4 or not is_cjk_text(simplified):
            continue
        key = normalize_pinyin(pinyin)
        if not key:
            continue
        records[(key, simplified)] = max(frequency, records.get((key, simplified), 0))
    target.parent.mkdir(parents=True, exist_ok=True)
    with target.open("w", encoding="utf-8", newline="\n") as handle:
        handle.write("# CC-CEDICT-derived four-character phrase/idiom boost layer. CC BY-SA 4.0.\n")
        handle.write("# pinyin<TAB>text<TAB>frequency\n")
        for (pinyin, text), freq in sorted(records.items(), key=lambda item: (item[0][0], item[0][1])):
            handle.write(f"{pinyin}\t{text}\t{freq}\n")
    return len(records)


def build_manifest(staging: Path, config: dict, idiom_frequency: int) -> Path:
    source_manifest = staging / "mature_import_manifest_v018.json"
    if not source_manifest.is_file():
        raise RuntimeError("mature_import_manifest_v018.json missing; run augment_v018_data.py first")
    payload = json.loads(source_manifest.read_text(encoding="utf-8"))
    sources = [item for item in payload.get("sources", []) if item.get("name") not in {"orbit-project-software-vocabulary", "cc-cedict-four-char-boost"}]
    sources.append({
        "name": "orbit-project-software-vocabulary",
        "path": "seed_software.tsv",
        "format": "orbit-tsv",
        "license": "PROJECT",
        "url": "",
        "redistribution_allowed": True,
        "attribution": "Orbit IME project-authored common software/platform/product vocabulary",
        "default_frequency": 1000,
    })
    cedict = config["sources"]["cedict"]
    sources.append({
        "name": "cc-cedict-four-char-boost",
        "path": "cedict_idiom_boost.tsv",
        "format": "orbit-tsv",
        "license": "CC-BY-SA-4.0",
        "url": cedict["url"],
        "redistribution_allowed": True,
        "attribution": cedict["attribution"],
        "default_frequency": idiom_frequency,
    })
    payload["sources"] = sources
    target = staging / "mature_import_manifest_v020.json"
    target.write_text(json.dumps(payload, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")
    return target


def count_project_software(path: Path) -> int:
    count = 0
    with path.open("r", encoding="utf-8") as handle:
        for line in handle:
            value = line.strip()
            if value and not value.startswith("#"):
                count += 1
    return count


def update_report(output: Path, idiom_entries: int, software_entries: int) -> None:
    report_path = output / "mature-report.json"
    manifest_path = output / "manifest.json"
    report = json.loads(report_path.read_text(encoding="utf-8"))
    runtime = json.loads(manifest_path.read_text(encoding="utf-8"))
    report["version"] = 4
    report.setdefault("stats", {})["cedict_four_char_entries"] = idiom_entries
    report.setdefault("stats", {})["project_software_entries"] = software_entries
    report["runtime_counts"] = runtime.get("counts", {})
    report_path.write_text(json.dumps(report, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")


def main() -> int:
    parser = argparse.ArgumentParser(description="Augment Orbit IME v0.20 idiom/software data")
    parser.add_argument("--config", default=str(DEFAULT_CONFIG))
    parser.add_argument("--output", default=str(DEFAULT_OUTPUT))
    parser.add_argument("--staging-dir", default=str(DEFAULT_STAGING))
    parser.add_argument("--cache-dir", default=str(DEFAULT_CACHE))
    args = parser.parse_args()

    config = json.loads(Path(args.config).resolve().read_text(encoding="utf-8"))
    policy = config["policy"]
    output = Path(args.output).resolve()
    staging = Path(args.staging_dir).resolve()
    cache = Path(args.cache_dir).resolve()
    staging.mkdir(parents=True, exist_ok=True)

    cedict_path = cache / "cc_cedict.txt"
    if not cedict_path.is_file():
        raise RuntimeError("verified CC-CEDICT cache missing; run augment_v018_data.py first")
    cedict_raw = cedict_path.read_bytes()

    software_source = ROOT / "data/ime_sources/seed_software.tsv"
    if not software_source.is_file():
        raise RuntimeError("seed_software.tsv missing")
    shutil.copy2(software_source, staging / "seed_software.tsv")

    idiom_entries = build_idiom_tsv(
        cedict_raw,
        staging / "cedict_idiom_boost.tsv",
        int(policy.get("cedict_idiom_frequency", 180000)),
    )
    software_entries = count_project_software(software_source)
    manifest = build_manifest(staging, config, int(policy.get("cedict_idiom_frequency", 180000)))

    importer = ROOT / "tools/ime_importer.py"
    result = subprocess.run(
        [sys.executable, str(importer), "--manifest", str(manifest), "--output", str(output)],
        cwd=str(ROOT),
        check=False,
    )
    if result.returncode != 0:
        raise SystemExit(result.returncode)

    update_report(output, idiom_entries, software_entries)
    print(json.dumps({
        "status": "PASS",
        "cedict_four_char_entries": idiom_entries,
        "project_software_entries": software_entries,
    }, ensure_ascii=False))
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
