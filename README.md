<div align="center">
  <h1>📸 SeCam (Secure Camera)</h1>
  <p>A military-grade, end-to-end encrypted Android camera and media vault.</p>

[![Kotlin](https://img.shields.io/badge/Kotlin-1.9.0-blue.svg?logo=kotlin)](https://kotlinlang.org)
[![Android Minimum SDK](https://img.shields.io/badge/Min%20SDK-28-orange.svg)](https://developer.android.com/about/versions/pie)
[![Security](https://img.shields.io/badge/Encryption-AES--256--GCM-red.svg)](#)
[![CameraX](https://img.shields.io/badge/Camera-CameraX_1.3.1-green.svg)](#)
</div>

---

## 🛡️ Overview

**SeCam** is an ultra-secure, self-contained camera application built natively for Android. Designed with privacy as the absolute highest priority, SeCam does not use the device's default Gallery or external storage.

Instead, every photo and video captured is encrypted on-the-fly using **AES-256-GCM** encryption and stored in an isolated, hidden internal vault. Media can only be decrypted and viewed directly inside the app's custom-built memory sandbox.

## ✨ Features

* **Zero-Knowledge Architecture:** Media is encrypted before it ever touches long-term storage.
* **Screenshot Prevention:** Enforces `FLAG_SECURE` across the entire application to block screenshots and screen recordings by the OS or malicious background apps.
* **High-Fidelity Capture:** Supports `1080p 60fps` HEVC video recording and max-quality HEIC photo capture using Google's CameraX pipeline.
* **Encrypted ExoPlayer Engine:** Videos are rapidly decrypted into a secure sandbox for fluid playback using AndroidX Media3 (ExoPlayer).
* **Native In-Memory Gallery:** An iOS-inspired grid gallery that decrypts thumbnails dynamically in the background without leaking data to the disk.
* **Pinch-to-Zoom Image Viewer:** Built-in programmatic `ViewPager2` and `PhotoView` integration for smooth, full-screen image inspection.
* **EXIF Manipulation:** Automatically handles hardware sensor rotation offsets to ensure HEIC files are displayed upright.

## 🛠️ Tech Stack

* **Language:** Kotlin
* **UI Framework:** XML (ConstraintLayout, Material Design Components)
* **Camera Engine:** AndroidX CameraX (Core, Camera2, Video, Lifecycle)
* **Cryptography:** AndroidX Security Crypto (Google Tink)
* **Media Playback:** AndroidX Media3 (ExoPlayer)
* **Image Loading & Zoom:** Coil, PhotoView

## 🔒 How the Encryption Works

SeCam utilizes the Android Keystore system to securely generate and store cryptographic keys.
1. Media is captured and written to a volatile `cacheDir`.
2. `EncryptedFile` creates a cipher stream using an AES-256-GCM Master Key.
3. The raw media is piped through the cipher into the protected `filesDir`.
4. A military-style wipe (`delete()`) is called on the unencrypted cache file immediately upon completion or failure.

## 🚀 Getting Started

### Prerequisites
* Android Studio (Iguana or newer)
* Android Device running SDK 28 (Android 9.0 Pie) or higher. *(Note: Camera features are best tested on physical hardware, not emulators).*

### Installation
1. Clone the repository:
   ```bash
   git clone [https://github.com/yourusername/secam.git](https://github.com/midxv/secam.git)