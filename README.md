# 🔒 OfflinePW

> **Air-Gapped, Hardware-Backed, Zero-Knowledge Offline Password & Vault Manager for Android.**

[![License: MIT](https://img.shields.io/badge/License-MIT-yellow.svg)](https://opensource.org/licenses/MIT)
[![Platform](https://img.shields.io/badge/Platform-Android-3DDC84.svg?logo=android&logoColor=white)](https://android.com)
[![Encryption](https://img.shields.io/badge/Encryption-AES--256--GCM-blue.svg)](#security-architecture)
[![Security](https://img.shields.io/badge/Security-Android%20Keystore%20%2F%20StrongBox-red.svg)](#hardware-backed-keystore)
[![Internet Permission](https://img.shields.io/badge/Internet%20Permission-None%20(Air--Gapped)-success.svg)](#privacy-guarantee)

---

## 🌟 Overview

**OfflinePW** is a minimalist, privacy-first, fully offline password and credential manager built for individuals who prioritize extreme security and sovereign data ownership. 

Unlike traditional password managers that sync data to third-party cloud servers, **OfflinePW is strictly air-gapped**: it does **not** declare or possess internet permissions, making remote telemetry, tracking, or network leaks mathematically impossible.

---

## 🛡️ Security Architecture

OfflinePW enforces a multi-layered defense architecture:

### 1. Full Zero-Knowledge Local Encryption (AES-256-GCM)
Every sensitive field—including **Title**, **Username / Card Number**, **Password**, and **Secure Notes**—is encrypted individually using authenticated **AES-256-GCM (Galois/Counter Mode)** with unique 12-byte initialization vectors (IV) before write operations to the local SQLite database.

### 2. Hardware-Backed Master Key (Android Keystore & StrongBox)
Master encryption keys are generated and securely stored inside the device's hardware security module (**TEE / StrongBox Keymaster HSM**). The key material is isolated from the main Android OS and cannot be exported or extracted by malware.

### 3. Master PIN Authentication (Zero-Knowledge)
- Access is guarded by a mandatory **6-digit Master PIN**.
- The PIN is hashed locally using **SHA-256 with cryptographic salt**.
- **No Backdoors / No Recovery:** The plaintext PIN is never saved. If forgotten, recovery is mathematically impossible.
- **Immediate Auto-Lock:** The vault automatically locks and terminates memory state whenever the app is minimized, switched, or backgrounded (`onStop`).

### 4. Anti-Screen Scraping & Memory Isolation (`FLAG_SECURE`)
Enforces Android's `FLAG_SECURE` window policy:
- Blocks screen recording and screenshots by other background apps.
- Hides confidential vault contents from the Android Recent Apps / Multitasking switcher.

### 5. Zero Network Access (Air-Gapped)
The `AndroidManifest.xml` deliberately **omits `android.permission.INTERNET`**. The app cannot send or receive data over Wi-Fi, cellular, or local networks.

---

## ✨ Features

- 🔐 **Military-Grade Vault:** Store logins, credit/debit card numbers, Wi-Fi keys, and private encrypted notes.
- 🎲 **Cryptographic Password Generator:** Built-in `SecureRandom` password generator for creating high-entropy passwords (16+ chars).
- 👁️ **Shoulder-Surfing Protection:** Masked card numbers and credentials (`•••• •••• •••• 7898`) on the primary overview.
- 🌓 **Dual Nordic Themes:** Seamless switching between deep OLED Dark mode and minimal Light mode.
- 🌐 **Bilingual Interface:** Instant real-time language toggling between **English** and **Persian (فارسی)**.
- ⚡ **Zero Bloat & Super Fast:** Built purely with native Android components (no heavy webviews or external bloat).

---

## 📥 Download & Installation

You can download the ready-to-install Android APK directly from GitHub Releases:

1. Go to the [**Releases**](https://github.com/codewave4/OfflinePW/releases) section.
2. Download the latest **`OfflinePW_1.0.apk`**.
3. Open the APK on your Android device and tap **Install**.
   *(Note: If prompted by Google Play Protect, choose "More details" -> "Install anyway", as the APK is built directly from source via GitHub Actions without a Play Store developer certificate).*

---

## 🏗️ Building from Source

To compile and build the APK yourself:

```bash
# Clone the repository
git clone https://github.com/codewave4/OfflinePW.git
cd OfflinePW

# Build debug APK with Gradle
./gradlew assembleDebug

---

## 📜 License

This project is licensed under the **MIT License** - see the [LICENSE](LICENSE) file for details.

---

<div align="center">
  <sub>Built with security, simplicity, and complete digital sovereignty in mind.</sub>
</div>
