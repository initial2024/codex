# Orbit IME v0.7 Pro Offline Translation Pack

This file documents the accepted v0.7 offline translation feature scope.

## Goal

Add a Pro-only local translation pack that can provide short exact or rough phrase translations without network access.

## Boundary

The Offline Translation Pack is not cloud translation and not a full machine translation model.

It does not:

- Add `INTERNET` permission.
- Call external translation APIs.
- Upload source text.
- Persist Translate Preview history.
- Persist source text.
- Replace the free Translate Preview prompt-generation flow.
- Claim high-quality long-form translation.

## Availability

The pack is unlocked only when:

```kotlin
ProGate.isOfflineTranslationPackUnlocked(context) == true
```

For free users, Translate Preview still generates local prompts only.

## Supported directions

- Chinese to English.
- English to Chinese.

## Translation behavior

The pack first tries exact local phrase matches.

If exact matching fails, it can try a conservative rough local phrase assembly for very short inputs.

If no local match is safe or useful, Orbit IME falls back to the existing Translate Preview prompt output.

## Safety filter

Before local translation, source text must pass `PrivacyGuard.isSafeToUseForPrompt()`.

This rejects OTP-like codes, password-like text, API keys, bearer tokens, authorization headers, cookies, sessions, dense secrets, and overly long text.

## UI behavior

When Pro is unlocked and a local offline translation is found:

- Translate Preview shows an `offline:` preview line.
- The user can tap `插入译文` to commit the local offline result.
- The user can still insert or copy the generated prompt.

When Pro is not unlocked:

- The UI shows `离线包Pro`.
- Tapping it shows a local lock message.
- No network call is made.

## Future expansion

A real offline model pack would require a separate size, latency, memory, and quality evaluation. It should remain optional and local-only.
