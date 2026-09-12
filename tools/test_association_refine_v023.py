#!/usr/bin/env python3
from __future__ import annotations

import importlib.util
import tempfile
import unittest
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
SPEC = importlib.util.spec_from_file_location("refine_association_v023", ROOT / "tools/refine_association_v023.py")
assert SPEC and SPEC.loader
refine = importlib.util.module_from_spec(SPEC)
SPEC.loader.exec_module(refine)


class AssociationRefineTests(unittest.TestCase):
    def test_builds_six_character_contexts(self) -> None:
        phrases = [
            ("数据库需要继续扩充", 100000),
            ("数据库需要继续优化", 90000),
            ("输入法需要继续优化", 85000),
        ]
        buckets = refine.build_associations(phrases)
        self.assertIn("据需要继续", buckets)
        self.assertIn("库需要继续", buckets)
        self.assertTrue(any(len(key) == 6 for key in buckets))

    def test_final_lexicon_base36_reader(self) -> None:
        with tempfile.TemporaryDirectory() as tmp:
            root = Path(tmp)
            lexicon = root / "lexicon"
            lexicon.mkdir()
            (lexicon / "s.odict").write_text(
                "# test\nshujuku\t数据库\tzz\nshurufa\t输入法\t100\n",
                encoding="utf-8",
            )
            rows = dict(refine.load_top_phrases(lexicon))
            self.assertEqual(rows["数据库"], int("zz", 36))
            self.assertEqual(rows["输入法"], int("100", 36))

    def test_shards_are_deterministic(self) -> None:
        phrases = [("数据库需要扩充", 10000), ("数据库需要优化", 9000), ("输入法需要优化", 8000)]
        buckets = refine.build_associations(phrases)
        with tempfile.TemporaryDirectory() as first_tmp, tempfile.TemporaryDirectory() as second_tmp:
            first = Path(first_tmp)
            second = Path(second_tmp)
            first_rows, first_context = refine.write_shards(first, buckets)
            second_rows, second_context = refine.write_shards(second, buckets)
            self.assertEqual((first_rows, first_context), (second_rows, second_context))
            first_bytes = {p.name: p.read_bytes() for p in sorted((first / "association").glob("*.odict"))}
            second_bytes = {p.name: p.read_bytes() for p in sorted((second / "association").glob("*.odict"))}
            self.assertEqual(first_bytes, second_bytes)
            self.assertEqual(len(first_bytes), 32)


if __name__ == "__main__":
    unittest.main()
