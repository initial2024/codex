# Orbit IME v0.12 Local Phrase Translation

This file documents the accepted v0.12 local translation scope.

## Goal

Make the Translate feature feel real for common short phrases while keeping Orbit IME offline and privacy-first.

## Behavior

Translate now uses this order:

1. Exact local phrase match in `OfflineTranslationPack.kt`.
2. Exact local phrase match in `TranslationBoostData.kt`.
3. Conservative rough local token assembly.
4. Translate Preview prompt-generation fallback.

If a phrase exists in the local table, the translated text can be inserted directly.

If no local translation exists, Orbit does not pretend to translate. It falls back to a prompt the user can insert into another AI/chat app.

## Boundary

The feature is not cloud translation and not a full machine-translation model.

It does not:

- Add `INTERNET` permission.
- Call external translation APIs.
- Upload source text.
- Persist Translate Preview history.
- Persist source text.
- Persist translated output.
- Claim high-quality long-form translation.

## Supported directions

- Chinese to English.
- English to Chinese.

## Example supported phrases

- `你好` -> `Hello.`
- `你是谁` -> `Who are you?`
- `这是什么` -> `What is this?`
- `怎么办` -> `What should I do?`
- `还是有问题` -> `There is still a problem.`
- `请给出可执行步骤` -> `Please provide actionable steps.`
- `不要添加 INTERNET 权限` -> `Do not add the INTERNET permission.`
- `I will handle it later` -> `我晚点处理。`

## Future professional path

A more professional offline translation pack should be generated from a licensed bilingual dictionary or phrase corpus. The generated data should be stored as compact assets rather than very large Kotlin maps.

A real arbitrary-sentence translator would require an offline model pack or an explicit online translation service. Neither is included in v0.12.
