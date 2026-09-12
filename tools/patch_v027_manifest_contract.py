from pathlib import Path

root = Path(__file__).resolve().parents[1]

v020_path = root / "tools/augment_v020_data.py"
text = v020_path.read_text(encoding="utf-8")
old = '''def build_manifest(staging: Path, config: dict, idiom_frequency: int) -> Path:\n    source_manifest = staging / "mature_import_manifest_v018.json"\n    if not source_manifest.is_file():\n        raise RuntimeError("mature_import_manifest_v018.json missing; run augment_v018_data.py first")\n'''
new = '''def build_manifest(staging: Path, config: dict, idiom_frequency: int) -> Path:\n    # augment_v018_data.py is the retained v0.19 augmentation stage and writes\n    # mature_import_manifest_v019.json. Keep the stage contract aligned with\n    # the actual producer instead of referring to the obsolete v018 filename.\n    source_manifest = staging / "mature_import_manifest_v019.json"\n    if not source_manifest.is_file():\n        raise RuntimeError("mature_import_manifest_v019.json missing; run augment_v018_data.py first")\n'''
if old not in text:
    raise RuntimeError("v0.20 manifest consumer anchor not found")
text = text.replace(old, new, 1)
v020_path.write_text(text, encoding="utf-8")

# Add an offline regression test for the v0.18/v0.19 -> v0.20 staging contract.
test_path = root / "tools/test_ime_data_pipeline_v020.py"
test = test_path.read_text(encoding="utf-8")
anchor = '''        assert "\\na\\t" not in english_text\n\n    software = Path(__file__).resolve().parents[1] / "data/ime_sources/seed_software.tsv"\n'''
insert = '''        assert "\\na\\t" not in english_text\n\n        source_manifest = root / "mature_import_manifest_v019.json"\n        source_manifest.write_text('{"sources":[{"name":"base","path":"seed.tsv","format":"orbit-tsv"}]}\\n', encoding="utf-8")\n        manifest = v020.build_manifest(\n            root,\n            {"sources": {"cedict": {"url": "https://example.invalid/cedict", "attribution": "test attribution"}}},\n            180000,\n        )\n        assert manifest.name == "mature_import_manifest_v020.json"\n        manifest_text = manifest.read_text(encoding="utf-8")\n        assert '"name": "base"' in manifest_text\n        assert '"name": "orbit-project-software-vocabulary"' in manifest_text\n        assert '"name": "cc-cedict-four-char-boost"' in manifest_text\n\n    software = Path(__file__).resolve().parents[1] / "data/ime_sources/seed_software.tsv"\n'''
if anchor not in test:
    raise RuntimeError("v0.20 test insertion anchor not found")
test = test.replace(anchor, insert, 1)
test_path.write_text(test, encoding="utf-8")

print("patched v0.20 manifest producer/consumer contract and regression test")
