from pathlib import Path

path = Path("app/src/main/java/com/ccwu/orbitime/OrbitInputMethodService.kt")
text = path.read_text(encoding="utf-8")
old = '''    private fun handlePrintableKey(rawKey: String) {
        if (rawKey.length == 1 && rawKey[0].isLetter() && !symbols && shiftState != ShiftState.OFF) {
            commitPendingForControl()
            val upper = rawKey.uppercase()
            if (showTranslate && translateLiveMode) {
                longFormTranslationPreview = null
                translateComposeText += upper
                updateLiveTranslationPreview()
            } else {
                resetInternalCompositionState()
                currentInputConnection?.commitText(upper, 1)
                if (!sensitiveMode) petRepository.recordTypedChars(upper.length)
            }
            if (shiftState == ShiftState.ONCE) shiftState = ShiftState.OFF
            root?.let { rebuild(it) }
            return
        }
        if (rawKey.length == 1 && rawKey[0].isLetter() && inputMode == InputMode.PINYIN && !symbols) { appendPinyin(rawKey); return }
        if (rawKey.length == 1 && rawKey[0].isLetter() && inputMode == InputMode.ENGLISH && !symbols) { appendEnglish(rawKey); return }
'''
new = '''    private fun handlePrintableKey(rawKey: String) {
        if (rawKey.length == 1 && rawKey[0].isLetter() && !symbols && inputMode == InputMode.PINYIN && shiftState != ShiftState.OFF) {
            commitPendingForControl()
            val upper = rawKey.uppercase()
            if (showTranslate && translateLiveMode) {
                longFormTranslationPreview = null
                translateComposeText += upper
                updateLiveTranslationPreview()
            } else {
                resetInternalCompositionState()
                currentInputConnection?.commitText(upper, 1)
                if (!sensitiveMode) petRepository.recordTypedChars(upper.length)
            }
            if (shiftState == ShiftState.ONCE) shiftState = ShiftState.OFF
            root?.let { rebuild(it) }
            return
        }
        if (rawKey.length == 1 && rawKey[0].isLetter() && inputMode == InputMode.PINYIN && !symbols) { appendPinyin(rawKey); return }
        if (rawKey.length == 1 && rawKey[0].isLetter() && inputMode == InputMode.ENGLISH && !symbols) { appendEnglish(rawKey); return }
'''
if old not in text:
    raise RuntimeError("handlePrintableKey v0.27 block not found")
text = text.replace(old, new, 1)
old2 = '''    private fun appendEnglish(letter: String) {
        val text = letter.lowercase()
        if (englishBuffer.length + text.length > MAX_ENGLISH_BUFFER) { toast("word too long"); return }
        englishBuffer += text
        currentInputConnection?.setComposingText(englishBuffer, 1)
        showClips = false
        showPet = false
        showExpressions = false
        longFormTranslationPreview = null
        if (!sensitiveMode) petRepository.recordTypedChars(1)
        refreshDynamicHost()
    }
'''
new2 = '''    private fun appendEnglish(letter: String) {
        val shifted = shiftState != ShiftState.OFF
        val consumeOneShot = shiftState == ShiftState.ONCE
        val text = if (shifted) letter.uppercase() else letter.lowercase()
        if (englishBuffer.length + text.length > MAX_ENGLISH_BUFFER) { toast("word too long"); return }
        englishBuffer += text
        currentInputConnection?.setComposingText(englishBuffer, 1)
        if (consumeOneShot) shiftState = ShiftState.OFF
        showClips = false
        showPet = false
        showPetCatalog = false
        showExpressions = false
        longFormTranslationPreview = null
        if (!sensitiveMode) petRepository.recordTypedChars(1)
        if (consumeOneShot) root?.let { rebuild(it) } else refreshDynamicHost()
    }
'''
if old2 not in text:
    raise RuntimeError("appendEnglish v0.27 block not found")
text = text.replace(old2, new2, 1)
path.write_text(text, encoding="utf-8")
print("English shift composition corrected")
