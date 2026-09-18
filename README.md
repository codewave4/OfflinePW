# 🔐 OfflinePW — Zero-Knowledge Offline Password & 2FA Manager

<p align="center">
  <b>An Air-Gapped, Zero-Knowledge Offline Password & TOTP 2FA Vault for Android</b>
</p>

<p align="center">
  <img src="https://img.shields.io/badge/Security-Air--Gapped%20(No%20Internet)-success?style=flat-square" alt="Air-Gapped" />
  <img src="https://img.shields.io/badge/Encryption-AES--256--GCM-blue?style=flat-square" alt="AES-256-GCM" />
  <img src="https://img.shields.io/badge/Key%20Protection-DEK%20%2F%20KEK%20%28PBKDF2--SHA256%29-orange?style=flat-square" alt="DEK / KEK (PBKDF2-SHA256)" />
  <img src="https://img.shields.io/badge/Master%20Password-PBKDF2--SHA256%20(600k)-purple?style=flat-square" alt="PBKDF2" />
  <img src="https://img.shields.io/badge/2FA-Built--in%20TOTP-yellow?style=flat-square" alt="TOTP" />
  <img src="https://img.shields.io/badge/License-MIT-green?style=flat-square" alt="License" />
</p>

---

## Overview

**OfflinePW** is an open-source, ultra-secure, completely offline password manager and TOTP 2FA authenticator for Android. Designed around a strict **Zero-Knowledge** architecture, it keeps your credentials, keys, and personal notes entirely on your device, encrypted at rest with AES-256-GCM and SQLCipher.

The application declares **zero internet permissions**, ensuring an air-gapped environment with no network communication, no background analytics, and no remote dependencies.

---

## Security Architecture

1. **Zero-Knowledge Local Encryption (AES-256-GCM)**
   - All sensitive payload fields (passwords, usernames, 2FA secret keys, secure notes) are individually encrypted using standard **AES-256-GCM** (Galois/Counter Mode).
   - Authenticated encryption ensures both confidentiality and cryptographic integrity against tampering. A fresh random 12-byte IV is generated for every encryption operation.

2. **Key Hierarchy (DEK / KEK)**
   - A random 256-bit Data Encryption Key (DEK) protects all payloads; it is itself encrypted (wrapped) by a Key Encryption Key (KEK) derived from your master password.
   - Only the wrapped DEK is persisted on disk — the raw DEK exists solely in memory during an unlocked session and is wiped when the vault locks.

3. **Brute-Force Resistant Master Password (PBKDF2)**
   - Authentication is guarded by your master password (min 10 characters).
   - The KEK is derived using **PBKDF2WithHmacSHA256** with **600,000 iterations** and a unique cryptographically secure 16-byte random salt per installation.
   - The plain text master password is never stored anywhere.

4. **Self-Destruct Anti-Brute-Force Protection**
   - After **3 consecutive incorrect master password attempts**, the app **permanently erases all data** (vault database, 2FA keys, wrapped keys and settings) to guarantee that no attacker can ever brute-force their way into your secrets.
   - The attempt counter is stored persistently and is never reset by simply restarting the app; only a successful unlock resets it.

5. **Integrated 2FA TOTP Engine (RFC 6238)**
   - Native RFC 6238 time-based one-time password generator with 30-second interval rotation.
   - **Shoulder-Surfing Defense**: TOTP codes are masked by default (`••••••`) alongside passwords. Tapping the code reveals it for 5 seconds and automatically copies it to the clipboard.

6. **True Air-Gapped Operation (No Internet Permission)**
   - The application does not request `android.permission.INTERNET` in its manifest.
   - Operating in a complete sandbox, no data can be exfiltrated, uploaded, or transmitted over any network interface.

7. **Anti-Screen Scraping & OS Protection (`FLAG_SECURE`)**
   - Hardened with Android's window-level `FLAG_SECURE` attribute across all application surfaces.
   - Blocks screenshots, screen recordings, screen casting, and visual memory caching in the Android Recent Apps / Task Switcher overview.

---

## Key Features

- **Modern BottomSheet Interface**: Clean, accessible BottomSheet dialog for item inspection, quick-copy triggers (`📋`), and toggleable password visibility (`ic_visibility` / `ic_visibility_off`).
- **Responsive Dialog Design**: Full `<ScrollView>` wrapping ensures smooth input and accessibility across all screen sizes and virtual keyboards.
- **Cryptographic Password Generator**: Built-in high-entropy 16-character password generator using `SecureRandom`.
- **Smart Categorization**: Flexible categorization for entries (`LOGIN`, `CARD`, `WIFI`, `NOTE`).
- **Instant Offline Search**: Fast real-time filtering across titles, account names, categories, and secure notes.
- **Pin / Unpin Items**: Swipe an item **left to pin** it (it jumps to the top with a 📌 marker) and **right to unpin** it. The pin state is persisted in the encrypted database.
- **List Ordering**: Sort the vault by **creation order, title (A-Z, Persian-collation aware), category, or recently-updated-first** from the ⋮ menu — pinned items always stay on top in every order. Each card shows its **last-updated date** (Jalali calendar for Persian).
- **Password Health Report**: One tap in the ⋮ menu scans the vault locally (nothing leaves the device) for **reused passwords**, **weak passwords** (length + Shannon-entropy heuristics), and **stale passwords** unchanged for 180+ days, and shows which items need attention.
- **Encrypted Backup & Restore**: The backup button (above the `+` button) offers **Export** — the whole vault is re-serialized and encrypted with AES-256-GCM using a key derived from a *separate backup password* (PBKDF2-SHA256, 600k iterations, random salt) and shared as a file you can store anywhere — and **Restore** — pick a backup file, enter the backup password, and the items are merged back into the vault (same-ID items are updated, new ones added). A backup is useless without its backup password, so the zero-knowledge property is preserved.
- **Bilingual Support**: Instant toggle between English (EN) and Persian (FA).
- **Dark & Light Themes**: Minimalist, high-contrast Material Design 3 interface with battery-saving dark mode.

---

## Building & Installation

### Prerequisites
- **Android SDK**: API level 26 (Android 8.0) minimum, targeting API 34+
- **JDK**: Java 17 or Java 21
- **Gradle**: the bundled wrapper uses Gradle 8.13 (no manual install needed)

### Commands

```bash
git clone https://github.com/codewave4/OfflinePW.git
cd OfflinePW

./gradlew assembleDebug      # -> app/build/outputs/apk/debug/app-debug.apk
./gradlew assembleRelease    # -> app/build/outputs/apk/release/app-release.apk
```

**About release signing:** a local `assembleRelease` without a `app/release.keystore`
is automatically signed with the **debug** key so the APK stays installable on your
device. On CI (GitHub Actions), if the `KEYSTORE_BASE64` / `KEYSTORE_PASSWORD` secrets
are set, the release APK is signed with your real key; otherwise CI falls back to a
debug build.

---

## License

This project is licensed under the MIT License — free and open source for personal and community use.

See the full LICENSE file for details.
