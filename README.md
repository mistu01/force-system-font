<div align="center">

# 🔤 Force System Font

**An advanced LSPosed / Xposed module that forces applications using custom bundled fonts to render with your native Android system font.**

[![Build APK](https://github.com/mistu01/force-system-font/actions/workflows/build.yml/badge.svg)](https://github.com/mistu01/force-system-font/actions/workflows/build.yml)
[![Platform](https://img.shields.io/badge/Platform-Android%208.0%20--%2017-brightgreen.svg?logo=android)](https://developer.android.com)
[![Framework](https://img.shields.io/badge/Framework-LSPosed%20%7C%20Xposed-blue.svg)](https://github.com/LSPosed/LSPosed)
[![Min SDK](https://img.shields.io/badge/Min%20SDK-26%20(Android%208.0)-orange.svg)](https://developer.android.com)
[![Target SDK](https://img.shields.io/badge/Target%20SDK-35%2B%20(Android%2015%2B)-yellow.svg)](https://developer.android.com)
[![License](https://img.shields.io/badge/License-Apache%202.0-blue.svg)](LICENSE)

<p align="center">
  <a href="#overview">Overview</a> •
  <a href="#key-features">Key Features</a> •
  <a href="#how-it-works">How It Works</a> •
  <a href="#compatibility">Compatibility</a> •
  <a href="#installation">Installation</a> •
  <a href="#building-from-source">Building</a> •
  <a href="#faq">FAQ</a>
</p>

</div>

---

## 📖 Overview

Many modern Android applications (such as Twitter/X, Discord, Telegram, Instagram, Reddit, and Duolingo) bundle proprietary custom fonts (like *Chirp*, *gg sans*, *DIN Next*, or *Reddit Sans*). 

If you use custom system fonts—via **Magisk**, **KernelSU**, **APatch**, or OEM theme engines (such as *Google Sans*, *Apple San Francisco*, *Inter*, or *Helvetica*)—these apps ignore your preferences and create an inconsistent visual experience.

**Force System Font** seamlessly intercepts font resolution across every layer of the Android runtime. It replaces custom bundled fonts with your configured system font while strictly preserving typographic hierarchy, continuous variable font weights, slant, and monospace code alignment.

---

## ✨ Key Features

### 🚀 Modern Android 15, 16 & 17 Ready
- **Variable Font Engine Alignment**: Fully aligned with Android 15+ variable typography (`font_fallback.xml`, Roboto Flex, Google Sans Flex) using dynamic OS-level `wght` axis interpolation.
- **Strict Boundary Protection**: Conforms with Android 14+ / 15+ strict parameter checking (`Preconditions.checkArgumentInRange(weight, 1, 1000)`), preventing crashes from out-of-range font weights.

### 🎨 Deep UI Framework Interception
- **AndroidX & Jetpack Compose**: Intercepts `Typeface.CustomFallbackBuilder` (API 29+), modern `Typeface.Builder` (API 26+), and Compose file fonts (`Typeface.createFromFile(File)`). Fonts loaded via `@font/...` XML and Google Fonts are redirected to the system font before caching.
- **Resource & Layout Inflation**: Intercepts `Resources.getFont(int)` and `TypedArray.getFont(int)` so views receive the system font directly during XML layout inflation.
- **Canvas & Low-Level Drawing**: Catches dynamic drawing paths via `Paint.setTypeface(Typeface)` and `TextView.setTypeface(...)`.

### 📐 Typographic Hierarchy & Monospace Preservation
- **Comprehensive Weight Mapping**: Accurately infers numeric weights (`100` to `950`, including filenames like `Inter_600.ttf`, `font-w700.otf`) and all standard typography keywords:
  - `100` (Thin / Hairline)
  - `200` (ExtraLight / UltraLight)
  - `300` (Light)
  - `400` (Regular / Normal / Book)
  - `500` (Medium)
  - `600` (SemiBold / DemiBold)
  - `700` (Bold)
  - `800` (ExtraBold / UltraBold)
  - `900` / `950` (Black / Heavy / ExtraBlack)
- **Monospace Protection**: Intelligently identifies monospaced fonts (`mono`, `code`, `courier`, `consolas`) and maps them to **`Typeface.MONOSPACE`**, preserving code blocks, terminal emulators, OTP fields, and ASCII tables without breaking alignment.

### ⚡ Silky-Smooth Performance
- **Thread-Safe Memory Cache**: Backed by high-concurrency `ConcurrentHashMap` caching to eliminate race conditions during background font parsing.
- **120Hz Fast-Path Filter**: Employs an $O(1)$ set identity check inside `Paint.setTypeface`. Once a typeface is converted, subsequent draw frames bypass extraction entirely, guaranteeing 120 FPS scrolling without micro-stutter.
- **Fault-Tolerant Hooking**: Each hook registration is safely isolated, ensuring non-standard OEM ROMs degrade gracefully without disabling other hooks.

---

## 🛠️ How It Works

```
┌─────────────────────────────────────────────────────────────┐
│                    Application / UI Layers                  │
│       (Jetpack Compose, AndroidX, XML Layouts, Views)       │
└──────────────────────────────┬──────────────────────────────┘
                               │
               Font Creation / Retrieval Requests
                               │
       ┌───────────────────────┼───────────────────────┐
       ▼                       ▼                       ▼
Typeface.create*     CustomFallbackBuilder      Resources.getFont
Typeface.Builder     createFromFile(File)       TypedArray.getFont
       └───────────────────────┬───────────────────────┘
                               │
                 [ Force System Font Hook Matrix ]
                               │
            ┌──────────────────┴──────────────────┐
            ▼                                     ▼
   Is Monospaced Font?                   Standard / Custom Font?
            │                                     │
   Preserve Monospace Hierarchy         Force System Default Font
  (Typeface.create(MONOSPACE))             (Typeface.DEFAULT)
            │                                     │
            └──────────────────┬──────────────────┘
                               │
             Matching Weight (100–950) & Slant Applied
                               ▼
        Silky-Smooth Canvas Rendering (Paint / TextView)
```

---

## 📱 Compatibility

| Category | Supported Versions / Environments |
| :--- | :--- |
| **Android Versions** | Android 8.0 (Oreo) through Android 17 (API 26 – 36+) |
| **Xposed Frameworks** | [LSPosed](https://github.com/LSPosed/LSPosed), LSPosed_mod, LSPosed (Irena) |
| **Root Solutions** | Magisk (Zygisk), KernelSU, APatch |
| **UI Toolkits** | Android Views, Material Components, AndroidX, Jetpack Compose |

> [!NOTE]
> Apps that render text through their own standalone native rendering engines (e.g. Flutter or raw internal WebViews) bypass Android Java-level `Typeface` and `Paint` APIs, and may not be affected by Java-level Xposed hooks.

---

## 📥 Installation

1. Download the latest release APK from the [Releases](https://github.com/mistu01/force-system-font/releases) page or Actions artifacts.
2. Install the APK on your rooted device with LSPosed active.
3. Open **LSPosed Manager** and enable the **Force System Font** module.
4. Check and select the target apps you wish to force the system font upon.
5. Force-close or restart the selected apps.

---

## 🔨 Building from Source

### Prerequisites
- JDK 17
- Android SDK (API 34+)

### Build Commands
```bash
# Clone repository
git clone https://github.com/mistu01/force-system-font.git
cd force-system-font

# Generate Gradle wrapper (if not present)
gradle wrapper --gradle-version 8.2

# Build unsigned release APK
./gradlew assembleRelease

# The output APK is generated at:
# app/build/outputs/apk/release/app-release-unsigned.apk
```

---

## ❓ FAQ

<details>
<summary><b>Why did my monospace code snippets remain monospaced?</b></summary>
Force System Font intentionally preserves monospace fonts using the system's monospace font (<code>Typeface.MONOSPACE</code>). Converting code blocks or OTP inputs into proportional sans-serif breaks column indentation and tabular alignments.
</details>

<details>
<summary><b>Does this module consume battery or CPU?</b></summary>
No. Once a system typeface for a given weight and style is created, it is cached in memory. An $O(1)$ fast-path identity filter in <code>Paint.setTypeface</code> ensures zero redundant overhead during rapid screen redraws.
</details>

<details>
<summary><b>Why are some Flutter or game fonts not changed?</b></summary>
Flutter and native game engines bundle their own custom HarfBuzz / FreeType / Skia binaries that directly read raw TTF files without calling Android's Java framework font APIs.
</details>

---

## 📄 License

```
Copyright (c) 2026 mistu01

Licensed under the Apache License, Version 2.0 (the "License");
you may not use this file except in compliance with the License.
You may obtain a copy of the License at

    http://www.apache.org/licenses/LICENSE-2.0

Unless required by applicable law or agreed to in writing, software
distributed under the License is distributed on an "AS IS" BASIS,
WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
See the License for the specific language governing permissions and
limitations under the License.
```
