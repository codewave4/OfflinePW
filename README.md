# 🔒 OfflinePW

> **Air-Gapped, Hardware-Backed, Zero-Knowledge Offline Password Vault for Android.**

[![License: MIT](https://img.shields.io/badge/License-MIT-yellow.svg)](LICENSE)
[![Platform](https://img.shields.io/badge/Platform-Android-3DDC84.svg?logo=android&logoColor=white)](https://android.com)
[![Encryption](https://img.shields.io/badge/Encryption-AES--256--GCM-blue.svg)](https://en.wikipedia.org/wiki/Galois/Counter_Mode)
[![Network](https://img.shields.io/badge/Internet%20Permission-None-success.svg)](#-zero-internet-guarantee)

---

## 🌟 What is OfflinePW?

**OfflinePW** is a sovereign, 100% offline password and credential manager. It is designed to keep your sensitive accounts, card numbers, and secret notes strictly inside your own device with military-grade encryption and zero cloud dependencies.

---

## 🛡️ Core Security Features

- 🔐 **AES-256-GCM Encryption:** Every credential (Title, Username/Card, Password, Notes) is encrypted before being stored.
- 🔑 **Hardware Keystore:** Master keys are securely generated in the device's hardware security chip (**StrongBox / TEE**).
- 🔢 **6-Digit Master PIN:** Guarded by local SHA-256 hashed PIN authentication.
- 🚫 **No Backdoors:** No cloud accounts, no servers, and zero data recovery if the PIN is forgotten.
- 🔒 **Instant Auto-Lock:** Automatically locks as soon as the app is closed or switched.
- 🛡️ **Anti-Screenshot (`FLAG_SECURE`):** Prevents malware screen recording and hides app content in Recent Apps.
- 🌐 **Zero Internet Guarantee:** The application has **no internet permissions**, ensuring data never leaves your device.

---

## ✨ Features

- **Store Any Record:** Logins, Credit Cards, Wi-Fi Keys, and Encrypted Notes.
- **Strong Password Generator:** Built-in cryptographic 16-character password generator.
- **Privacy Masking:** Concealed values on the home screen to prevent shoulder surfing.
- **Dual Themes:** Clean OLED Dark mode and minimal Light mode.
- **Bilingual:** Instant language toggle between English and Persian (فارسی).

---

## 📥 Download APK

Get the ready-to-install Android APK directly from GitHub:

1. Open the [**Releases**](https://github.com/codewave4/OfflinePW/releases) tab.
2. Download **`OfflinePW_1.0.apk`**.
3. Install and set your 6-digit Master PIN.

---

## 📜 License

This project is licensed under the **MIT License**.
