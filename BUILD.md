# Building SeenVideo

SeenVideo is a 100% Free and Open Source (FOSS) Android application built using Kotlin and Jetpack Compose. It has no proprietary dependencies (no Google Play Services / GMS or Firebase).

## Prerequisites
* JDK 17
* Android SDK (Compile SDK 35, Build Tools 35.0.0)

## Configuration
Configure your YouTube Data API Key in `local.properties` (or as an environment variable):

```properties
YOUTUBE_API_KEY=your_youtube_api_key_here
```

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
