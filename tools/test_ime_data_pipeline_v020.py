#!/usr/bin/env python3
"""Offline v0.20 data tests layered on the existing mature pipeline tests."""
from __future__ import annotations

import tempfile
from pathlib import Path

import augment_v018_data as v018
import augment_v020_data as v020
import test_ime_data_pipeline as base


def main() -> int:
    if base.main() != 0:
        return 1
    with tempfile.TemporaryDirectory(prefix="orbit-v020-data-test-") as temp:
        root = Path(temp)
        raw = (
            "# CC-CEDICT\n"
            "一心一意 一心一意 [yi1 xin1 yi1 yi4] /wholeheartedly/\n"
            "畫蛇添足 画蛇添足 [hua4 she2 tian1 zu2] /to ruin the effect by adding something superfluous/\n"
            "数据库 数据库 [shu4 ju4 ku4] /database/\n"
        ).encode("utf-8")
        target = root / "idiom.tsv"
        count = v020.build_idiom_tsv(raw, target, 180000)
        text = target.read_text(encoding="utf-8")
        assert count == 2
        assert "yixinyiyi\t一心一意\t180000" in text
        assert "huashetianzu\t画蛇添足\t180000" in text
        assert "数据库" not in text

        english_raw = (
            "hello\n"
            "OpenAI\n"
            "GitHub\n"
            "NASA\n"
            "don't\n"
            "co-op\n"
            "A\n"
        ).encode("utf-8")
        english_target = root / "english.tsv"
        english_count = v020.build_expanded_english_tsv(english_raw, english_target, 2, 32, 35000)
        english_text = english_target.read_text(encoding="utf-8")
        assert english_count == 6
        assert "hello\t" in english_text
        assert "openai\t" in english_text and "OpenAI" in english_text
        assert "github\t" in english_text and "GitHub" in english_text
        assert "nasa\t" in english_text and "NASA" in english_text
        assert "dont\t" in english_text and "don't" in english_text
        assert "coop\t" in english_text and "co-op" in english_text
        assert "\na\t" not in english_text

        source_manifest = root / "mature_import_manifest_v019.json"
        source_manifest.write_text('{"sources":[{"name":"base","path":"seed.tsv","format":"orbit-tsv"}]}\n', encoding="utf-8")
        manifest = v020.build_manifest(
            root,
            {"sources": {"cedict": {"url": "https://example.invalid/cedict", "attribution": "test attribution"}}},
            180000,
        )
        assert manifest.name == "mature_import_manifest_v020.json"
        manifest_text = manifest.read_text(encoding="utf-8")
        assert '"name": "base"' in manifest_text
        assert '"name": "orbit-project-software-vocabulary"' in manifest_text
        assert '"name": "cc-cedict-four-char-boost"' in manifest_text

        # Runtime CEDICT coverage and translation coverage are deliberately separate.
        # The second row is structurally valid and importable, but its "see ..." gloss
        # is rejected by the translation-quality filter. Both must still count toward
        # the runtime CEDICT source gate.
        cedict_raw = (
            "数据库 数据库 [shu4 ju4 ku4] /database/\n"
            "資料庫 数据库 [zi1 liao4 ku4] /see 数据库/\n"
        ).encode("utf-8")
        assert v018.count_cedict_runtime_entries(cedict_raw) == 2
        assert sum(1 for _ in v018.parse_cedict(cedict_raw)) == 1

    software = Path(__file__).resolve().parents[1] / "data/ime_sources/seed_software.tsv"
    assert v020.count_project_software(software) >= 100
    print("Orbit IME v0.20 augmentation tests: PASS")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
