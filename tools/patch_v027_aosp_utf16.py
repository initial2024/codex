from pathlib import Path

path = Path("tools/prepare_mature_ime_data.py")
text = path.read_text(encoding="utf-8")

old = '''def iter_aosp_rows(raw: bytes):
    for line in raw.decode("utf-8-sig").splitlines():
'''
new = '''def decode_aosp_dictionary(raw: bytes) -> str:
    """Decode the pinned AOSP PinyinIME dictionary without changing source verification.

    The current rawdict_utf16_65105_freq.txt is UTF-16 with a BOM, while older
    compatible source snapshots may be UTF-8/UTF-8-SIG. Hash verification is
    intentionally performed on the original bytes before this decoding step.
    """
    if raw.startswith((b"\\xff\\xfe", b"\\xfe\\xff")):
        return raw.decode("utf-16")
    return raw.decode("utf-8-sig")


def iter_aosp_rows(raw: bytes):
    for line in decode_aosp_dictionary(raw).splitlines():
'''

if old not in text:
    raise RuntimeError("AOSP decoder anchor not found")
text = text.replace(old, new, 1)
path.write_text(text, encoding="utf-8")
print("patched AOSP dictionary decoder for UTF-16 BOM compatibility")
