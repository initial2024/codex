# Orbit IME v0.13 Keyboard Pet Module

This file documents the accepted v0.13 pet feature scope.

## Goal

Make the pet module actually usable inside Orbit IME while keeping it low-distraction and local-only.

## What changed in v0.13

- The keyboard top Hub has a `宠物` / `Pet` entry again.
- The in-keyboard pet panel shows current pet, species, stage, level, EXP, mood, today typed characters, total typed characters, and outfit.
- The panel supports direct actions: check-in, hatch egg, switch owned pet, rotate outfit, show pet catalog, show outfit catalog, hide/show pet, and close.
- Check-in messages are now Chinese and immediately visible in the panel.
- Hatching is usable: the first hatch each day is free; later hatches cost 30 Stars.
- Owned pets are stored locally and can be cycled.
- Outfit rotation is implemented as a data-layer placeholder until Gemini art assets are supplied.
- Simple local chat is available as status text at all stages; Adult stage produces more mature lines.
- Today typed characters are tracked locally and shown in the panel.

## Boundary

The pet system is local and keyboard-only.

It does not:

- Request overlay / floating-window permission.
- Draw outside the IME surface.
- Send notifications.
- Play sounds.
- Add ad tasks.
- Add paid gacha.
- Upload pet data.
- Sync pet data.
- Persist full typed key streams.
- Learn or grow in password / sensitive fields.
- Add `INTERNET` permission.

## Display modes

Supported display modes:

- `keyboard_only`: Pet is accessible from the Orbit Hub and opens a small in-keyboard panel.
- `hidden`: Pet is hidden and remains quiet.

System-wide floating mode is intentionally not included.

## Pet pool

Free pool:

- Orbi: 星轨核心。
- Kuro: 数据墨滴。
- Pica: 棱镜晶体。
- Voxel: 终端像素猫。
- Rune: 悬浮符碑。

Pro pool placeholder:

- Noctua: 机械夜鸮。
- Nami: 极光水母。
- Aegis: 晶格护卫狐。

## Outfit pool

Free outfit placeholders:

- 磁悬浮光环。
- 单目数据镜。
- 学术帽。
- 信号披风。
- 全息领结。

Pro outfit placeholders:

- 发光轨道环。
- 矩阵微光。
- 极光尾迹。

These are data-layer placeholders. Real pet art, icons, and outfit assets should be added later after Gemini supplies original designs.

## Local growth inputs

The pet grows only from local counters:

- Typing visible characters.
- Committing Pinyin candidates.
- Saving clips after explicit `保存当前剪贴板` taps.
- Inserting local translation output or Translate Preview prompts.
- Daily check-in.

No source sentences, app names, target fields, or full input streams are stored for the pet system.

## Growth model

Stages:

```text
Seed: 0-49 EXP
Junior: 50-199 EXP
Teen: 200-499 EXP
Adult: 500+ EXP
```

Typing grants 1 EXP per 20 visible characters. Candidate commit grants 1 EXP. Clip save grants 1 EXP. Translate insertion grants 2 EXP. Daily check-in grants Stars and a small EXP bonus.

## Local storage

The pet profile is stored using app-private `SharedPreferences`:

```text
prefs: orbit_pet
```

Stored fields include active pet id, owned pet ids, EXP, Stars, streak, local typed character count, today typed count, display mode, and equipped outfit id.

## Chat boundary

Simple chat is local template output only.

The pet does not call a model, cloud service, or external API.

## Next art step

When Gemini provides original pet SVG/vector concepts, v0.14 should only add lightweight vector assets and mapping IDs. Do not add Lottie, WebView, heavy animation, audio, notifications, or floating-window behavior.
