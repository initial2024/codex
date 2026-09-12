#!/usr/bin/env python3
"""Offline tests for Orbit IME data preparation and packing.

No Android SDK and no network are required.
"""
from __future__ import annotations

import argparse
import json
import tempfile
from pathlib import Path

import ime_importer
import prepare_mature_ime_data as mature


def alpha_word(value: int) -> str:
    chars: list[str] = []
    value += 1
    while value:
        value, rem = divmod(value - 1, 26)
        chars.append(chr(ord("a") + rem))
    return "word" + "".join(reversed(chars))


def test_git_blob_hash() -> None:
    assert mature.git_blob_sha1(b"hello\n") == "ce013625030ba8dba906f756967f9e9ca394464a"


def test_aosp_parser(root: Path) -> None:
    raw = (
        "\ufeff你好 20728.6414301 0 ni hao\n"
        "你好吗 182.423584067 0 ni hao ma\n"
        "妳好 20.0 1 ni hao\n"
        "A 999 0 a\n"
    ).encode("utf-8")
    lexicon = root / "aosp.tsv"
    ngrams = root / "aosp_ngram.tsv"
    stats = mature.parse_aosp_pinyin(
        raw,
        lexicon,
        ngrams,
        frequency_scale=100,
        max_phrase_chars=12,
        ngram_limits={"1": 100, "2": 100, "3": 100},
    )
    text = lexicon.read_text(encoding="utf-8")
    assert "nihao\t你好\t2072864" in text
    assert "nihaoma\t你好吗\t18242" in text
    assert "妳好" not in text
    assert "A\t" not in text
    assert stats["aosp_lexicon_entries"] == 2
    ngram_text = ngrams.read_text(encoding="utf-8")
    assert "你\t好\t" in ngram_text
    assert "你\t好\t吗\t" in ngram_text


def test_esdb_parser(root: Path) -> None:
    raw = "hello\ndon't\nco-op\nProper\na\nverylongword\n".encode("utf-8")
    target = root / "english.tsv"
    stats = mature.parse_esdb_english(raw, target, 2, 28, 40_000)
    text = target.read_text(encoding="utf-8")
    assert "hello\t" in text
    assert "dont\t" in text and "don't" in text
    assert "coop\t" in text and "co-op" in text
    assert "Proper" not in text
    assert "\na\t" not in text
    assert stats["esdb_english_entries"] == 4


def test_license_gate(root: Path) -> None:
    source = root / "source.tsv"
    source.write_text("ni\t你\t10\n", encoding="utf-8")
    denied = ime_importer.SourceSpec("denied", source, "orbit-tsv", "PROJECT", "", False, "project")
    try:
        ime_importer.validate_source(denied, strict=True)
        raise AssertionError("redistribution gate did not fail")
    except ValueError as exc:
        assert "not marked redistributable" in str(exc)
    unknown = ime_importer.SourceSpec("unknown", source, "orbit-tsv", "UNKNOWN", "https://example.invalid", True, "test")
    try:
        ime_importer.validate_source(unknown, strict=True)
        raise AssertionError("unknown license gate did not fail")
    except ValueError as exc:
        assert "strict allow-list" in str(exc)


def test_importer_and_english_sharding(root: Path) -> None:
    lexicon = root / "lexicon.tsv"
    lexicon.write_text("ni\t你\t100\nnihao\t你好\t90\n", encoding="utf-8")
    ngram = root / "ngram.tsv"
    ngram.write_text("你\t100\n你\t好\t90\n你\t好\t吗\t80\n", encoding="utf-8")
    english = root / "english.tsv"
    with english.open("w", encoding="utf-8", newline="\n") as handle:
        for index in range(ime_importer.ENGLISH_SHARD_THRESHOLD + 5):
            handle.write(f"{alpha_word(index)}\t100\n")
    manifest = root / "manifest.json"
    manifest.write_text(
        json.dumps(
            {
                "sources": [
                    {
                        "name": "lexicon",
                        "path": "lexicon.tsv",
                        "format": "orbit-tsv",
                        "license": "PROJECT",
                        "redistribution_allowed": True,
                        "attribution": "test",
                    },
                    {
                        "name": "english",
                        "path": "english.tsv",
                        "format": "english-tsv",
                        "license": "PROJECT",
                        "redistribution_allowed": True,
                        "attribution": "test",
                    },
                    {
                        "name": "ngram",
                        "path": "ngram.tsv",
                        "format": "ngram-tsv",
                        "license": "PROJECT",
                        "redistribution_allowed": True,
                        "attribution": "test",
                    },
                ]
            }
        ),
        encoding="utf-8",
    )
    output = root / "out"
    args = argparse.Namespace(
        manifest=str(manifest),
        output=str(output),
        allow_unknown_license=False,
    )
    assert ime_importer.build(args) == 0
    runtime = json.loads((output / "manifest.json").read_text(encoding="utf-8"))
    assert runtime["counts"]["lexicon"] == 2
    assert runtime["counts"]["english"] == ime_importer.ENGLISH_SHARD_THRESHOLD + 5
    assert (output / "lexicon/n.odict").is_file()
    assert not (output / "english.odict").exists()
    assert (output / "english/w.odict").is_file()
    assert (output / "ngram1.odict").is_file()
    assert (output / "ngram2.odict").is_file()
    assert (output / "ngram3.odict").is_file()


def main() -> int:
    with tempfile.TemporaryDirectory(prefix="orbit-ime-data-test-") as temp:
        root = Path(temp)
        test_git_blob_hash()
        test_aosp_parser(root)
        test_esdb_parser(root)
        test_license_gate(root)
        test_importer_and_english_sharding(root)
    print("Orbit IME data pipeline tests: PASS")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
