
# 📺 NoTube+ (Cobalt Edition)

<p align="center">
  <img src="https://img.shields.io/github/v/release/yotmykj/NoTubeTV-Bug-Fix?style=for-the-badge&color=blue" alt="Latest Release">
  <img src="https://img.shields.io/github/actions/workflow/status/yotmykj/NoTubeTV-Bug-Fix/build.yml?style=for-the-badge&label=Build" alt="Build Status">
  <img src="https://img.shields.io/badge/Platform-Android%20TV%20%7C%20Smart%20TV-red?style=for-the-badge" alt="Platform">
</p>

**NoTube+** is a fork of NoTubeTV inspired by **TizenTube Cobalt**. It turns the standard YouTube TV web client into a fast, clean, and ad-free client optimized for Android TV and TV boxes.

---

## ✨ Features

- 🛡️ **Built-in AdBlock & SponsorBlock:** Automatically skips ads, sponsored segments, intros, outros, and subscription reminders.
- ✂️ **Shorts Removal:** Hides YouTube Shorts shelves from the home screen and search results.
- 🎮 **Remote Engine (D-pad):** Smooth navigation optimized for TV remotes, responsive D-pad controls, and clear focus highlights.
- ⚡ **OTA Userscript Engine:** All fixes and patches load dynamically from `cobalt_patch.js`. Updates are applied on the fly without needing to reinstall the APK!
- 🔐 **Google Auth Fix:** Uses modern Cobalt User-Agents to bypass `403 disallowed_useragent` login errors.
- 🔄 **In-App Auto-Updater:** Automatically checks for new APK releases on GitHub upon launch.
- 🎨 **Dark Cobalt UI:** Customized dark background theme and clean startup experience.

---

## 🏗️ Architecture

The project is split into two independent parts:

