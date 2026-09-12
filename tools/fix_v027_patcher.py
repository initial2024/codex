from pathlib import Path

# One-shot repair for the generated patcher; safe to run repeatedly.
path = Path(__file__).with_name("apply_v027_usability_patch.py")
text = path.read_text(encoding="utf-8")
marker = 'if "caps" in text:\n'
insert = '''text = text.replace(\n    "        val text = if (caps) letter.uppercase() else letter.lowercase()\\n",\n    "        val text = letter.lowercase()\\n",\n    1,\n)\n'''
if insert not in text:
    if marker not in text:
        raise RuntimeError("could not locate caps guard in patcher")
    text = text.replace(marker, insert + marker, 1)
path.write_text(text, encoding="utf-8")
print("patched v0.27 patcher for appendEnglish caps state")
