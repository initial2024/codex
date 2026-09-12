from pathlib import Path

path = Path("tools/validate_mature_ime_assets.py")
text = path.read_text(encoding="utf-8")
replacements = {
    '"""Fail the build when Orbit v0.26 mature assets, privacy gates or local runtimes regress."""': '"""Fail the build when Orbit v0.27 mature assets, privacy gates or local runtimes regress."""',
    'description="Validate Orbit v0.26 mature IME assets/runtime gates"': 'description="Validate Orbit v0.27 mature IME assets/runtime gates"',
    'require(\'versionCode = 26\' in gradle and \'versionName = "0.26.0"\' in gradle, "Gradle is not v0.26.0")': 'require(\'versionCode = 27\' in gradle and \'versionName = "0.27.0"\' in gradle, "Gradle is not v0.27.0")',
}
for old, new in replacements.items():
    if text.count(old) != 1:
        raise RuntimeError(f"validator version anchor not found exactly once: {old}")
    text = text.replace(old, new, 1)
path.write_text(text, encoding="utf-8")
print("updated mature validator release gate to v0.27.0")
