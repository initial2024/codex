from pathlib import Path
import hashlib
import json
import urllib.request

CONFIG = Path("data/ime_sources/mature_sources.json")
EXPECTED_URL = "https://www.unicode.org/Public/17.0.0/emoji/emoji-test.txt"
EXPECTED_SHA256 = "1d8a944f88d7952f7ef7c5167fef3c67995bcae24543949710231b03a201acda"
OLD_URL = "https://www.unicode.org/Public/emoji/17.0/emoji-test.txt"
OLD_SHA256 = "07ee0565612af5d8cf36ea7d2cd7d255429441059133c60f863e97e648ebeb29"

request = urllib.request.Request(EXPECTED_URL, headers={"User-Agent": "Orbit-IME-build-data/0.27-source-audit"})
with urllib.request.urlopen(request, timeout=120) as response:
    data = response.read()
actual = hashlib.sha256(data).hexdigest()
if actual != EXPECTED_SHA256:
    raise RuntimeError(f"Unicode 17 emoji-test SHA-256 mismatch: expected {EXPECTED_SHA256}, got {actual}")

obj = json.loads(CONFIG.read_text(encoding="utf-8"))
spec = obj["sources"]["unicode_emoji"]
if spec.get("url") not in {OLD_URL, EXPECTED_URL}:
    raise RuntimeError(f"unexpected Unicode Emoji URL: {spec.get('url')}")
if spec.get("sha256") not in {OLD_SHA256, EXPECTED_SHA256}:
    raise RuntimeError(f"unexpected Unicode Emoji SHA-256: {spec.get('sha256')}")

spec["url"] = EXPECTED_URL
spec["sha256"] = EXPECTED_SHA256
CONFIG.write_text(json.dumps(obj, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")
print(f"verified Unicode 17 emoji-test: {len(data)} bytes, sha256={actual}")
print("updated mature_sources.json Unicode Emoji fixed URL and checksum")
