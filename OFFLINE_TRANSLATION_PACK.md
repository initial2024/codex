# Orbit IME Local Phrase Translation

## Version

This document applies to Orbit IME `0.11.0`.

## Goal

Translate should not feel fake. Version `0.11.0` first tries to produce a real local phrase translation from packaged phrase tables.

If no local phrase translation exists, Orbit falls back to Translate Preview prompt generation.

## Boundary

This is not cloud translation and not a full offline model.

It does not use:

- INTERNET permission
- External translation API
- Cloud model endpoint
- Background translation
- Translation history storage

## Supported behavior

Known phrases can produce direct translations, for example:

```text
你好 -> Hello.
你是谁 -> Who are you?
还是有问题 -> There is still a problem.
我晚点处理 -> I will handle it later.
I will handle it later -> 我晚点处理。
```

Unsupported long or complex text falls back to prompt generation instead of pretending to translate.

## Acceptance examples

- `你好` should show a directly insertable English translation.
- `你是谁` should show a directly insertable English translation.
- `还是有问题` should show a directly insertable English translation.
- `I will handle it later` should show a directly insertable Chinese translation.
- Unsupported text should show the prompt-generation fallback.
