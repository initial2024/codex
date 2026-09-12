#!/usr/bin/env python3
"""Build a deterministic .orbitpack for Orbit IME v0.22+.

The builder does not download models. It packages user-provided local files with
explicit source/license metadata and SHA-256 coverage for every regular file.
"""
from __future__ import annotations

import argparse
import hashlib
import json
import re
import tempfile
import zipfile
from pathlib import Path

PACK_ID_RE = re.compile(r"^[a-z0-9][a-z0-9._-]{2,79}$")
ALLOWED_TYPES = {"translation", "asr", "tts", "voice_clone"}
ALLOWED_RUNTIMES = {"onnx", "gguf", "sherpa_onnx"}
FIXED_TIME = (2026, 1, 1, 0, 0, 0)


def sha256(path: Path) -> str:
    digest = hashlib.sha256()
    with path.open("rb") as stream:
        for block in iter(lambda: stream.read(1024 * 1024), b""):
            digest.update(block)
    return digest.hexdigest()


def validate_manifest(data: dict) -> None:
    required = {
        "pack_format", "pack_id", "display_name", "version", "type", "runtime",
        "model_name", "source_url", "license", "commercial_use", "redistribution",
        "languages", "privacy", "disclaimer_required",
    }
    missing = sorted(required - data.keys())
    if missing:
        raise ValueError(f"manifest missing: {', '.join(missing)}")
    if data["pack_format"] != 1:
        raise ValueError("pack_format must be 1")
    if not PACK_ID_RE.fullmatch(str(data["pack_id"])):
        raise ValueError("invalid pack_id")
    if data["type"] not in ALLOWED_TYPES:
        raise ValueError(f"unsupported type: {data['type']}")
    if data["runtime"] not in ALLOWED_RUNTIMES:
        raise ValueError(f"unsupported runtime: {data['runtime']}")
    if not str(data["source_url"]).startswith("https://"):
        raise ValueError("source_url must use https")
    if data["privacy"] != "offline_only":
        raise ValueError("privacy must be offline_only")
    if data["disclaimer_required"] is not True:
        raise ValueError("disclaimer_required must be true")
    permissions = list(data.get("requires_permissions", []))
    if "android.permission.INTERNET" in permissions:
        raise ValueError("Orbit local packs may not require INTERNET")
    if not data.get("languages"):
        raise ValueError("languages must not be empty")


def safe_payload_files(root: Path) -> list[tuple[Path, str]]:
    if not root.is_dir():
        raise ValueError(f"payload dir not found: {root}")
    result: list[tuple[Path, str]] = []
    for path in sorted(root.rglob("*")):
        if path.is_symlink():
            raise ValueError(f"symlinks are not allowed: {path}")
        if not path.is_file():
            continue
        rel = path.relative_to(root).as_posix()
        if not rel or ".." in Path(rel).parts:
            raise ValueError(f"unsafe payload path: {rel}")
        result.append((path, f"model/{rel}"))
    if not result:
        raise ValueError("payload directory contains no model files")
    return result


def zip_write_file(zf: zipfile.ZipFile, source: Path, arcname: str) -> None:
    info = zipfile.ZipInfo(arcname, FIXED_TIME)
    info.compress_type = zipfile.ZIP_STORED if source.suffix.lower() in {".onnx", ".gguf", ".bin", ".safetensors"} else zipfile.ZIP_DEFLATED
    info.external_attr = 0o644 << 16
    with source.open("rb") as stream, zf.open(info, "w") as target:
        for block in iter(lambda: stream.read(1024 * 1024), b""):
            target.write(block)


def zip_write_bytes(zf: zipfile.ZipFile, data: bytes, arcname: str) -> None:
    info = zipfile.ZipInfo(arcname, FIXED_TIME)
    info.compress_type = zipfile.ZIP_DEFLATED
    info.external_attr = 0o644 << 16
    zf.writestr(info, data)


def main() -> int:
    parser = argparse.ArgumentParser(description="Build Orbit .orbitpack")
    parser.add_argument("--manifest", required=True, type=Path)
    parser.add_argument("--payload-dir", required=True, type=Path)
    parser.add_argument("--license", required=True, dest="license_file", type=Path)
    parser.add_argument("--notice", required=True, type=Path)
    parser.add_argument("--output", required=True, type=Path)
    args = parser.parse_args()

    manifest = json.loads(args.manifest.read_text(encoding="utf-8"))
    validate_manifest(manifest)
    if not args.license_file.is_file() or args.license_file.stat().st_size == 0:
        raise ValueError("LICENSE file missing/empty")
    if not args.notice.is_file() or args.notice.stat().st_size == 0:
        raise ValueError("NOTICE file missing/empty")

    payload = safe_payload_files(args.payload_dir)
    args.output.parent.mkdir(parents=True, exist_ok=True)
    if args.output.suffix != ".orbitpack":
        raise ValueError("output file must use .orbitpack extension")

    with tempfile.TemporaryDirectory(prefix="orbitpack-") as temp_raw:
        temp = Path(temp_raw)
        normalized_manifest = temp / "manifest.json"
        normalized_manifest.write_text(json.dumps(manifest, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")
        metadata = [
            (normalized_manifest, "manifest.json"),
            (args.license_file, "LICENSE.txt"),
            (args.notice, "NOTICE.txt"),
        ]
        all_files = metadata + payload
        checksum_lines = [f"{sha256(source)}  {arcname}" for source, arcname in all_files]
        checksums = ("\n".join(checksum_lines) + "\n").encode("utf-8")

        with zipfile.ZipFile(args.output, "w", allowZip64=True) as zf:
            for source, arcname in all_files:
                zip_write_file(zf, source, arcname)
            zip_write_bytes(zf, checksums, "checksums.sha256")

    print(json.dumps({
        "status": "PASS",
        "output": str(args.output),
        "pack_id": manifest["pack_id"],
        "type": manifest["type"],
        "runtime": manifest["runtime"],
        "payload_files": len(payload),
        "packed_bytes": args.output.stat().st_size,
    }, ensure_ascii=False))
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
