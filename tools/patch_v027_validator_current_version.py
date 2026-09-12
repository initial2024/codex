from pathlib import Path

root = Path(__file__).resolve().parents[1]
validator = root / "tools/validate_mature_ime_assets.py"
text = validator.read_text(encoding="utf-8")

old = '''    main_activity = read(SRC / "MainActivity.kt")\n    for token in ("requestMicrophonePermission", "previewTts", "confirmAndChooseVoiceReference", "previewVoiceClone", "v0.26.0"):\n        require(token in main_activity, f"v0.26 settings/runtime action missing: {token}")\n'''
new = '''    main_activity = read(SRC / "MainActivity.kt")\n    # Preserve the v0.24-v0.26 settings/runtime capabilities by checking their\n    # concrete actions. Do not couple feature-regression checks to an obsolete\n    # release-label string in the UI.\n    for token in ("requestMicrophonePermission", "previewTts", "confirmAndChooseVoiceReference", "previewVoiceClone"):\n        require(token in main_activity, f"historical settings/runtime action missing: {token}")\n'''
if text.count(old) != 1:
    raise RuntimeError("MainActivity historical capability sentinel anchor not found exactly once")
text = text.replace(old, new, 1)

old_artifact = 'require("orbit-ime-v0.26-debug-apk" in workflow, "v0.26 Actions artifact name missing")'
new_artifact = 'require("orbit-ime-v0.27-debug-apk" in workflow, "v0.27 Actions artifact name missing")'
if text.count(old_artifact) != 1:
    raise RuntimeError("validator artifact sentinel not found exactly once")
text = text.replace(old_artifact, new_artifact, 1)

if text.count('"version": "0.26.0"') != 1:
    raise RuntimeError("validator summary version anchor not found exactly once")
text = text.replace('"version": "0.26.0"', '"version": "0.27.0"', 1)
validator.write_text(text, encoding="utf-8")

# build-apk.yml is updated separately through the GitHub contents API because
# the Actions token is intentionally not allowed to push workflow-file changes.
workflow = root / ".github/workflows/build-apk.yml"
wf = workflow.read_text(encoding="utf-8")
if "orbit-ime-v0.27-debug-apk" not in wf:
    raise RuntimeError("build workflow is not pinned to the v0.27 debug artifact")

print("patched validator historical sentinels and verified current v0.27 release metadata")
