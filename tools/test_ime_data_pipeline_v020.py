#!/usr/bin/env python3
"""Offline v0.20 data tests layered on the existing mature pipeline tests."""
from __future__ import annotations

import tempfile
from pathlib import Path

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
            "画蛇添足 畫蛇添足 [hua4 she2 tian1 zu2] /to ruin the effect by adding something superfluous/\n"
            "数据库 数据库 [shu4 ju4 ku4] /database/\n"
        ).encode("utf-8")
        target = root / "idiom.tsv"
        count = v020.build_idiom_tsv(raw, target, 180000)
        text = target.read_text(encoding="utf-8")
        assert count == 2
        assert "yixinyiyi\t一心一意\t180000" in text
        assert "huashetianzu\t畫蛇添足" in text or "huashetianzu\t画蛇添足" in text
        assert "数据库" not in text

    software = Path(__file__).resolve().parents[1] / "data/ime_sources/seed_software.tsv"
    assert v020.count_project_software(software) >= 100
    print("Orbit IME v0.20 augmentation tests: PASS")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
