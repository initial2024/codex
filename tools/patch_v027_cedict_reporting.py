from pathlib import Path

root = Path(__file__).resolve().parents[1]
augment_path = root / "tools/augment_v018_data.py"
text = augment_path.read_text(encoding="utf-8")

old_import = '''import urllib.request\nfrom collections import defaultdict\nfrom pathlib import Path\n'''
new_import = '''import urllib.request\nfrom collections import defaultdict\nfrom pathlib import Path\n\nimport ime_importer\n'''
if old_import not in text:
    raise RuntimeError("augment import anchor not found")
text = text.replace(old_import, new_import, 1)

anchor = '''def parse_cedict(raw: bytes):\n    for line in raw.decode("utf-8-sig").splitlines():\n        if not line or line.startswith("#"):\n            continue\n        match = CEDICT_RE.match(line.strip())\n        if not match:\n            continue\n        traditional, simplified, pinyin, gloss_blob = match.groups()\n        glosses = [normalize_english_gloss(value) for value in gloss_blob.split("/")]\n        glosses = [value for value in glosses if value]\n        if simplified and pinyin and glosses:\n            yield traditional, simplified, pinyin, glosses\n\n\n'''
addition = anchor + '''def count_cedict_runtime_entries(raw: bytes) -> int:\n    """Count rows accepted by the runtime CEDICT importer, independent of translation gloss filtering."""\n    count = 0\n    for line in raw.decode("utf-8-sig").splitlines():\n        if not line or line.startswith("#"):\n            continue\n        match = ime_importer.CEDICT_RE.match(line.strip())\n        if not match:\n            continue\n        _traditional, simplified, pinyin, _gloss = match.groups()\n        if simplified and ime_importer.normalize_pinyin(pinyin):\n            count += 1\n    return count\n\n\n'''
if anchor not in text:
    raise RuntimeError("parse_cedict anchor not found")
text = text.replace(anchor, addition, 1)

old_stats = '''def write_translation_assets(raw: bytes, output: Path) -> dict[str, int]:\n    zh_records: dict[str, str] = {}\n    en_records: dict[str, str] = {}\n    cedict_entries = 0\n    for _traditional, simplified, _pinyin, glosses in parse_cedict(raw):\n        cedict_entries += 1\n'''
new_stats = '''def write_translation_assets(raw: bytes, output: Path) -> dict[str, int]:\n    zh_records: dict[str, str] = {}\n    en_records: dict[str, str] = {}\n    # This gate measures CEDICT rows accepted into the runtime lexicon. Translation\n    # coverage is intentionally measured separately by translation_zh/en_entries.\n    cedict_entries = count_cedict_runtime_entries(raw)\n    for _traditional, simplified, _pinyin, glosses in parse_cedict(raw):\n'''
if old_stats not in text:
    raise RuntimeError("write_translation_assets stats anchor not found")
text = text.replace(old_stats, new_stats, 1)
augment_path.write_text(text, encoding="utf-8")

test_path = root / "tools/test_ime_data_pipeline_v020.py"
test = test_path.read_text(encoding="utf-8")
old_test_import = '''import augment_v020_data as v020\nimport test_ime_data_pipeline as base\n'''
new_test_import = '''import augment_v018_data as v018\nimport augment_v020_data as v020\nimport test_ime_data_pipeline as base\n'''
if old_test_import not in test:
    raise RuntimeError("test import anchor not found")
test = test.replace(old_test_import, new_test_import, 1)

insert_anchor = '''        assert '\"name\": \"cc-cedict-four-char-boost\"' in manifest_text\n\n    software = Path(__file__).resolve().parents[1] / "data/ime_sources/seed_software.tsv"\n'''
insert = '''        assert '\"name\": \"cc-cedict-four-char-boost\"' in manifest_text\n\n        # Runtime CEDICT coverage and translation coverage are deliberately separate.\n        # The second row is structurally valid and importable, but its \"see ...\" gloss\n        # is rejected by the translation-quality filter. Both must still count toward\n        # the runtime CEDICT source gate.\n        cedict_raw = (\n            "数据库 数据库 [shu4 ju4 ku4] /database/\\n"\n            "資料庫 数据库 [zi1 liao4 ku4] /see 数据库/\\n"\n        ).encode("utf-8")\n        assert v018.count_cedict_runtime_entries(cedict_raw) == 2\n        assert sum(1 for _ in v018.parse_cedict(cedict_raw)) == 1\n\n    software = Path(__file__).resolve().parents[1] / "data/ime_sources/seed_software.tsv"\n'''
if insert_anchor not in test:
    raise RuntimeError("test insertion anchor not found")
test = test.replace(insert_anchor, insert, 1)
test_path.write_text(test, encoding="utf-8")

print("patched CEDICT runtime-reporting semantics and regression test")
