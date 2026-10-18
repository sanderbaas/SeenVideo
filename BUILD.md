# Building SeenVideo

SeenVideo is a 100% Free and Open Source (FOSS) Android application built using Kotlin and Jetpack Compose. It has no proprietary dependencies (no Google Play Services / GMS or Firebase).

## Prerequisites
* JDK 17
* Android SDK (Compile SDK 36, Build Tools 35.0.0)

## Configuration
SeenVideo works 100% out-of-the-box using built-in Web & RSS Fallback Scrapers—no API keys or configuration files required to build or run!

Optionally, users or developers can enter their own YouTube Data API v3 Key directly in the app under **Settings (Gear Icon) -> YouTube API Key**.

## Building from Command Line

To build an unsigned release APK:
```bash
./gradlew assembleRelease
```

The compiled APK will be located at:
`app/build/outputs/apk/release/SeenVideo-1.4.apk`

To build a debug APK:
```bash
./gradlew assembleDebug
```

SeenVideo supports **Reproducible Builds**. Every build produces a 100% byte-for-byte identical APK binary regardless of the build environment.
