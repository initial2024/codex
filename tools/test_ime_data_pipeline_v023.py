#!/usr/bin/env python3
from __future__ import annotations

import importlib.util
import tempfile
import unittest
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
SPEC = importlib.util.spec_from_file_location("augment_v023_data", ROOT / "tools/augment_v023_data.py")
assert SPEC and SPEC.loader
v023 = importlib.util.module_from_spec(SPEC)
SPEC.loader.exec_module(v023)


class V023DataTests(unittest.TestCase):
    def test_thuocl_parser_and_conservative_pinyin(self) -> None:
        raw = "虚拟地址\t120\n火锅\t90\nabc\t99\n".encode()
        rows = v023.parse_thuocl(raw)
        self.assertIn(("虚拟地址", 120), rows)
        exact = {"火锅": "huoguo"}
        chars = {"虚": "xu", "拟": "ni", "地": "di", "址": "zhi"}
        self.assertEqual(v023.derive_pinyin("火锅", exact, chars), "huoguo")
        self.assertEqual(v023.derive_pinyin("虚拟地址", exact, chars), "xunidizhi")
        self.assertIsNone(v023.derive_pinyin("未知", exact, chars))

    def test_frequency_parser_and_overlay(self) -> None:
        rows = v023.parse_frequency_words(b"hello 200000\nworld 120000\nbad line\n")
        self.assertEqual(rows[:2], [("hello", 200000), ("world", 120000)])
        with tempfile.TemporaryDirectory() as tmp:
            target = Path(tmp) / "en.tsv"
            count = v023.build_english_frequency_overlay(rows, target)
            self.assertEqual(count, 2)
            text = target.read_text(encoding="utf-8")
            self.assertIn("hello\t", text)
            self.assertIn("world\t", text)

    def test_association_asset_is_deterministic(self) -> None:
        phrases = [("数据库不够", 10000), ("数据库需要扩充", 9000), ("输入法优化", 8000)]
        with tempfile.TemporaryDirectory() as tmp:
            root = Path(tmp)
            first = v023.build_association_assets(root, phrases)
            snapshot = {p.name: p.read_bytes() for p in sorted((root / "association").glob("*.odict"))}
            second = v023.build_association_assets(root, phrases)
            snapshot2 = {p.name: p.read_bytes() for p in sorted((root / "association").glob("*.odict"))}
            self.assertEqual(first, second)
            self.assertEqual(snapshot, snapshot2)
            self.assertGreater(first, 0)


if __name__ == "__main__":
    unittest.main()
