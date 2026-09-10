# Orbit IME v0.4 Translate Preview

Translate Preview is the accepted v0.4 feature scope.

## Goal

Let the user prepare a translation request before confirming input, without adding network access or a cloud translation provider.

## Boundary

Translate Preview is prompt generation only.

It does not:

- Produce a final translated sentence.
- Call a translation API.
- Add `INTERNET` permission.
- Upload source text.
- Persist source text.
- Persist generated prompt history.
- Run in password fields.

## Sources

Source text is read only after explicit user action:

| Source | Button | Behavior |
|---|---|---|
| Previous sentence | `前一句` | Calls `getTextBeforeCursor(240, 0)` after tap, extracts the last sentence locally. |
| Selected text | `选中文本` | Calls `getSelectedText(0)` after tap. |
| Clipboard | `剪贴板` | Reads the current clipboard after tap. |
| In-IME draft | `草稿` | Lets the user type into an IME-owned draft buffer before committing anything to the target editor. |

## Directions

Supported directions:

- `中→英`
- `英→中`

The `换方向` button regenerates the preview prompt when a source is already selected.

## Prompt templates

Chinese to English:

```text
Translate the following Chinese into natural English. Keep the meaning accurate and avoid stiff literal wording.

Text:
<source>
```

English to Chinese:

```text
Translate the following English into natural Chinese. Keep the meaning accurate and avoid machine-translation wording.

Text:
<source>
```

## Safety filter

Before prompt generation, Orbit IME rejects source text that looks like:

- OTP-only numeric codes.
- Password-like text.
- API keys.
- Bearer tokens.
- Authorization headers.
- Cookie/session strings.
- Very long dense secret-like tokens.

The maximum Translate Preview source length is 1200 characters.

## Privacy mode

When `PrivacyGuard.isSensitiveInput()` returns true:

- Hub actions are hidden.
- Translate Preview is cleared.
- Pinyin composition is cleared.
- Clipboard Save/Clips are hidden.
- The keyboard uses warning/border skin colors.

## Future note

The default roadmap does not include cloud translation. A future external translation service would require a separate product, privacy, permission, and cost decision.
