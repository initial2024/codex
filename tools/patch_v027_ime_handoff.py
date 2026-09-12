#!/usr/bin/env python3
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]

# 1) Participate in Android's standard IME switching protocol.
p = ROOT / "app/src/main/res/xml/method.xml"
s = p.read_text(encoding="utf-8")
if 'android:supportsSwitchingToNextInputMethod="true"' not in s:
    needle = '    android:isDefault="false"\n'
    if needle not in s:
        raise SystemExit("method.xml isDefault marker not found")
    s = s.replace(needle, needle + '    android:supportsSwitchingToNextInputMethod="true"\n', 1)
p.write_text(s, encoding="utf-8")

# 2) Give the independent test IME a distinct picker label.
p = ROOT / "app/build.gradle.kts"
s = p.read_text(encoding="utf-8")
if 'resValue("string", "ime_name", "Orbit IME Test")' not in s:
    needle = '            versionNameSuffix = "-bundled-models-test"\n'
    repl = needle + '            resValue("string", "app_name", "Orbit IME Test")\n            resValue("string", "ime_name", "Orbit IME Test")\n'
    if needle not in s:
        raise SystemExit("bundledModelsDebug marker not found")
    s = s.replace(needle, repl, 1)
p.write_text(s, encoding="utf-8")

# 3) Cleanly hand the editor session to another IME and remove dead picker code.
p = ROOT / "app/src/main/java/com/ccwu/orbitime/OrbitInputMethodService.kt"
s = p.read_text(encoding="utf-8")
s = s.replace("import android.view.inputmethod.InputMethodManager\n", "")

if "override fun onFinishInputView(finishingInput: Boolean)" not in s:
    needle = '''    override fun onWindowHidden() {
        detachClipboardListener()
        if (this::speechController.isInitialized) speechController.cancelCapture()
        super.onWindowHidden()
    }
'''
    repl = needle + '''
    override fun onFinishInputView(finishingInput: Boolean) {
        // Let the framework finish any active composing span first, then drop our
        // mirrored buffers/UI state so a different IME receives a clean editor.
        super.onFinishInputView(finishingInput)
        resetSessionUiForImeHandoff()
    }

    override fun onFinishInput() {
        super.onFinishInput()
        resetSessionUiForImeHandoff()
    }

    override fun onUnbindInput() {
        // Do not keep capture/listener/session state attached after Android hands
        // the editor to another IME or client.
        resetSessionUiForImeHandoff()
        super.onUnbindInput()
    }
'''
    if needle not in s:
        raise SystemExit("onWindowHidden block not found")
    s = s.replace(needle, repl, 1)

old_picker = '''    private fun showInputMethodPickerSafely() {
        try { (getSystemService(INPUT_METHOD_SERVICE) as InputMethodManager).showInputMethodPicker() }
        catch (_: Exception) { toast("请从系统输入法按钮切换") }
    }

'''
s = s.replace(old_picker, "")

if "private fun resetSessionUiForImeHandoff()" not in s:
    needle = '''    private fun resetInternalCompositionState() {
        pinyinBuffer = ""
        englishBuffer = ""
        invalidatePinyinUiCache()
    }
'''
    repl = needle + '''
    private fun resetSessionUiForImeHandoff() {
        detachClipboardListener()
        if (this::speechController.isInitialized) speechController.cancelCapture()
        resetInternalCompositionState()
        symbols = false
        symbolPage = 0
        shiftState = ShiftState.OFF
        lastShiftTapAt = 0L
        showClips = false
        showPet = false
        showPetCatalog = false
        showMoreTools = false
        showExpressions = false
        petPanelMessage = null
        speechStatusMessage = null
        clearTranslateState()
    }
'''
    if needle not in s:
        raise SystemExit("resetInternalCompositionState block not found")
    s = s.replace(needle, repl, 1)
p.write_text(s, encoding="utf-8")

# 4) Permanently guard the switch/handoff contract in the mature validator.
p = ROOT / "tools/validate_mature_ime_assets.py"
s = p.read_text(encoding="utf-8")
if "IME switching protocol declaration missing" not in s:
    needle = '''    require("onWindowHidden" in service and "sensitiveMode" in service, "recording cancellation/privacy mode missing")
'''
    repl = needle + '''    for token in ("onFinishInputView", "onFinishInput", "onUnbindInput", "resetSessionUiForImeHandoff"):
        require(token in service, f"IME handoff lifecycle cleanup missing: {token}")
    require("showInputMethodPickerSafely" not in service, "obsolete in-keyboard IME picker hook must remain removed")
    method_xml = read(ROOT / "app/src/main/res/xml/method.xml")
    require('android:supportsSwitchingToNextInputMethod="true"' in method_xml, "IME switching protocol declaration missing")
'''
    if needle not in s:
        raise SystemExit("service validator marker not found")
    s = s.replace(needle, repl, 1)

if "bundled-model test IME picker label missing" not in s:
    needle = '''    require("orbit-ime-v0.27-bundled-models-test-apk" in workflow, "v0.27 bundled test artifact name missing")
'''
    repl = needle + '''    gradle = read(ROOT / "app/build.gradle.kts")
    require('resValue("string", "ime_name", "Orbit IME Test")' in gradle, "bundled-model test IME picker label missing")
'''
    if needle not in s:
        raise SystemExit("workflow validator marker not found")
    s = s.replace(needle, repl, 1)
p.write_text(s, encoding="utf-8")

print("v0.27 IME handoff patch applied")
