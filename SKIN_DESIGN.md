# Orbit IME v0.3 Skin Design

This file records the accepted Gemini design subset for Orbit IME `0.3.0`.

## Accepted scope

Version `0.3.0` implements a small local skin system only.

Accepted:

- `OrbitSkin` token model.
- `OrbitSkins` built-in presets.
- `SkinManager` local skin selection via `SharedPreferences`.
- Settings screen skin selector.
- IME surface uses the selected skin.
- Privacy mode uses the current skin's `warning` and `border` tokens.

Rejected for `0.3.0`:

- Canvas keyboard rewrite.
- Compose migration.
- Room, Realm, encrypted database migration.
- Animated Orbit dynamic bar.
- Clips two-row card drawer.
- Prompt waterfall grid.
- Pinyin 9-key.
- Wubi.
- Handwriting recognition.
- Ad SDK or theme marketplace.
- Billing implementation.

## Skin tokens

```kotlin
data class OrbitSkin(
    val id: String,
    val name: String,
    val isPro: Boolean = false,
    val background: String,
    val panel: String,
    val panelAlt: String,
    val key: String,
    val keyPressed: String,
    val controlKey: String,
    val text: String,
    val mutedText: String,
    val accent: String,
    val warning: String,
    val border: String,
)
```

## Built-in skins

| ID | Name | Pro | Purpose |
|---|---|---:|---|
| `orbit_dark` | Orbit Dark | No | Default dark cyber style. |
| `orbit_light` | Orbit Light | No | Bright office style. |
| `amoled_black` | AMOLED Black | No | OLED-friendly pure black style. |
| `study_blue` | Study Blue | No | Long study and academic writing style. |
| `pro_aurora` | Pro Aurora | Yes | Paid-skin placeholder; locked until ProGate is enabled. |

## Color table

| Token | Orbit Dark | Orbit Light | AMOLED Black | Study Blue | Pro Aurora |
|---|---|---|---|---|---|
| background | `#101216` | `#F4F6F9` | `#000000` | `#0F172A` | `#130E24` |
| panel | `#171A21` | `#FFFFFF` | `#000000` | `#1E293B` | `#1C1536` |
| panelAlt | `#212631` | `#E9EDF3` | `#0F0F0F` | `#253347` | `#261D4A` |
| key | `#222733` | `#FFFFFF` | `#0D0D0D` | `#1E293B` | `#221A42` |
| keyPressed | `#333B4D` | `#E1E6EE` | `#242424` | `#334155` | `#362A66` |
| controlKey | `#1B202A` | `#E9EDF3` | `#050505` | `#172033` | `#181230` |
| text | `#F0F3F8` | `#181C24` | `#E0E0E0` | `#F8FAFC` | `#FDF4FF` |
| mutedText | `#76839B` | `#78849E` | `#555555` | `#94A3B8` | `#A78BFA` |
| accent | `#00E5FF` | `#0A66C2` | `#00FF88` | `#38BDF8` | `#E040FB` |
| warning | `#FFAB00` | `#E65100` | `#FF3B30` | `#F59E0B` | `#FF5252` |
| border | `#2A3140` | `#DDE2EB` | `#1F1F1F` | `#2A3950` | `#3B2D6E` |

## Privacy mode visual rule

When `PrivacyGuard.isSensitiveInput()` is true:

- Hub actions remain hidden.
- Pinyin composition is cleared.
- The keyboard skin is derived from the selected skin.
- `accent` and `border` are replaced by `warning`.
- Panel/key colors are darkened by reusing `background` and `controlKey` tokens.

## Future scope

`0.4` can add a user-local dictionary. It should learn only from explicit candidate selection and explicit user-added entries. It must not persist raw typed key streams, password fields, OTP fields, or app fields marked as no personalized learning.
