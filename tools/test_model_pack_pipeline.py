#!/usr/bin/env python3
"""Offline tests for Orbit .orbitpack creation and integrity metadata."""
from __future__ import annotations

import hashlib
import importlib.util
import json
import subprocess
import sys
import tempfile
import zipfile
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
BUILDER = ROOT / "tools/build_orbitpack.py"


def load_builder():
    spec = importlib.util.spec_from_file_location("orbitpack_builder", BUILDER)
    if spec is None or spec.loader is None:
        raise RuntimeError("cannot import build_orbitpack.py")
    module = importlib.util.module_from_spec(spec)
    spec.loader.exec_module(module)
    return module


def sha256_bytes(data: bytes) -> str:
    return hashlib.sha256(data).hexdigest()


def parse_checksums(raw: str) -> dict[str, str]:
    result: dict[str, str] = {}
    for line in raw.splitlines():
        line = line.strip()
        if not line:
            continue
        digest, path = line.split(maxsplit=1)
        result[path.strip()] = digest.lower()
    return result


def build_once(root: Path, name: str) -> Path:
    manifest = {
        "pack_format": 1,
        "pack_id": "test.translation.zh-en",
        "display_name": "Test Translation Pack",
        "version": "1.0.0",
        "type": "translation",
        "runtime": "onnx",
        "model_name": "test/checkpoint",
        "source_url": "https://example.com/test-model",
        "license": "Apache-2.0",
        "commercial_use": True,
        "redistribution": "allowed",
        "languages": ["zh", "en"],
        "size_mb": 1,
        "min_ram_mb": 256,
        "recommended_ram_mb": 512,
        "requires_permissions": [],
        "privacy": "offline_only",
        "disclaimer_required": True,
        "experimental": True,
        "description": "pipeline test",
    }
    manifest_path = root / "manifest.json"
    manifest_path.write_text(json.dumps(manifest, ensure_ascii=False), encoding="utf-8")
    payload = root / "payload"
    payload.mkdir(exist_ok=True)
    (payload / "model.onnx").write_bytes(b"ORBIT-TEST-ONNX\x00\x01")
    (payload / "tokenizer.json").write_text('{"test":true}\n', encoding="utf-8")
    license_path = root / "LICENSE.txt"
    notice_path = root / "NOTICE.txt"
    license_path.write_text("Apache License 2.0 test fixture\n", encoding="utf-8")
    notice_path.write_text("Test fixture only; no third-party model bundled.\n", encoding="utf-8")
    output = root / name
    subprocess.run(
        [
            sys.executable,
            str(BUILDER),
            "--manifest", str(manifest_path),
            "--payload-dir", str(payload),
            "--license", str(license_path),
            "--notice", str(notice_path),
            "--output", str(output),
        ],
        check=True,
        cwd=ROOT,
        stdout=subprocess.PIPE,
        stderr=subprocess.PIPE,
        text=True,
    )
    return output


def test_pack_contents_and_checksums() -> None:
    with tempfile.TemporaryDirectory(prefix="orbitpack-test-") as raw:
        root = Path(raw)
        pack = build_once(root, "a.orbitpack")
        with zipfile.ZipFile(pack, "r") as zf:
            names = set(zf.namelist())
            required = {"manifest.json", "LICENSE.txt", "NOTICE.txt", "checksums.sha256", "model/model.onnx", "model/tokenizer.json"}
            assert required <= names, (required - names)
            checksums = parse_checksums(zf.read("checksums.sha256").decode("utf-8"))
            assert "checksums.sha256" not in checksums
            for name in names:
                if name.endswith("/") or name == "checksums.sha256":
                    continue
                assert name in checksums, name
                assert sha256_bytes(zf.read(name)) == checksums[name], name


def test_deterministic_output() -> None:
    with tempfile.TemporaryDirectory(prefix="orbitpack-test-") as raw:
        root = Path(raw)
        first = build_once(root, "first.orbitpack")
        first_hash = hashlib.sha256(first.read_bytes()).hexdigest()
        second = build_once(root, "second.orbitpack")
        second_hash = hashlib.sha256(second.read_bytes()).hexdigest()
        assert first_hash == second_hash, (first_hash, second_hash)


def test_manifest_rejects_internet() -> None:
    builder = load_builder()
    manifest = {
        "pack_format": 1,
        "pack_id": "test.asr.local",
        "display_name": "bad",
        "version": "1",
        "type": "asr",
        "runtime": "sherpa_onnx",
        "model_name": "bad",
        "source_url": "https://example.com",
        "license": "Apache-2.0",
        "commercial_use": True,
        "redistribution": "allowed",
        "languages": ["zh"],
        "privacy": "offline_only",
        "disclaimer_required": True,
        "requires_permissions": ["android.permission.INTERNET"],
    }
    try:
        builder.validate_manifest(manifest)
    except ValueError:
        return
    raise AssertionError("INTERNET-requiring model pack was not rejected")


def main() -> int:
    test_pack_contents_and_checksums()
    test_deterministic_output()
    test_manifest_rejects_internet()
    print(json.dumps({"status": "PASS", "tests": 3}, ensure_ascii=False))
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
