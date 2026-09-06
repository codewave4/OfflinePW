# 🔐 OfflinePW — Zero-Knowledge Offline Password & 2FA Manager

<p align="center">
  <b>An Air-Gapped, Hardware-Backed, Zero-Knowledge Offline Password & TOTP 2FA Vault for Android</b>
</p>

<p align="center">
  <img src="https://img.shields.io/badge/Security-Air--Gapped%20(No%20Internet)-success?style=flat-square" alt="Air-Gapped" />
  <img src="https://img.shields.io/badge/Encryption-AES--256--GCM-blue?style=flat-square" alt="AES-256-GCM" />
  <img src="https://img.shields.io/badge/Key%20Protection-Hardware%20Keystore%20%2F%20StrongBox-orange?style=flat-square" alt="StrongBox" />
  <img src="https://img.shields.io/badge/Master%20PIN-PBKDF2--SHA256%20(120k)-purple?style=flat-square" alt="PBKDF2" />
  <img src="https://img.shields.io/badge/2FA-Built--in%20TOTP-yellow?style=flat-square" alt="TOTP" />
  <img src="https://img.shields.io/badge/License-MIT-green?style=flat-square" alt="License" />
</p>

---

## Overview

**OfflinePW** is an open-source, ultra-secure, completely offline password manager and TOTP 2FA authenticator for Android. Designed around a strict **Zero-Knowledge** architecture and hardware isolation, it guarantees that your credentials, keys, and personal notes remain entirely on your device and are mathematically protected against extraction or unauthorized access.

The application declares **zero internet permissions**, ensuring an air-gapped environment with no network communication, no background analytics, and no remote dependencies.

---

## Security Architecture

1. **Zero-Knowledge Local Encryption (AES-256-GCM)**
   - All sensitive payload fields (passwords, usernames, 2FA secret keys, secure notes) are individually encrypted using standard **AES-256-GCM** (Galois/Counter Mode).
   - Authenticated encryption ensures both confidentiality and cryptographic integrity against tampering. A fresh random 12-byte IV is generated for every encryption operation.

2. **Hardware-Isolated Master Key (StrongBox & TEE Keystore)**
   - The master AES encryption key is generated inside and managed exclusively by Android's hardware security module (**StrongBox Keymaster** where supported, falling back gracefully to the hardware **Trusted Execution Environment (TEE)**).
   - Master keys are non-exportable and never exist in plain text in memory, storage, or application databases.

3. **Brute-Force Resistant Master PIN (PBKDF2)**
   - Authentication is guarded by an 8-digit Master PIN.
   - The PIN is derived using **PBKDF2WithHmacSHA256** with **120,000 iterations** and a unique cryptographically secure 16-byte random salt per installation.
   - The plain text PIN is never stored anywhere.

4. **Automated Lockout & Anti-Brute-Force Protection**
   - After **5 consecutive failed PIN attempts**, the app automatically locks down for **5 minutes**.
   - An active countdown timer is displayed on the authentication screen, completely halting automated or robotic dictionary attacks.

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
- **Bilingual Support**: Instant toggle between English (EN) and Persian (FA).
- **Dark & Light Themes**: Minimalist, high-contrast Material Design 3 interface with battery-saving dark mode.

---

## Building & Installation

### Prerequisites
- **Android SDK**: API level 24 (Android 7.0) minimum, targeting API 34+
- **JDK**: Java 17 or Java 21
- **Gradle**: 8.0+

License

This project is licensed under the MIT License — free and open for personal and community use.




