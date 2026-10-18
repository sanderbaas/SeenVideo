# SeenVideo

SeenVideo is an Android application designed for a focused, local-first YouTube experience. It allows users to follow specific channels, organize them into groups, manage local "Watch Later" and "Watched" statuses, filter out YouTube Shorts, and enjoy content without distractions or account login requirements.

![Logo](app/src/main/res/drawable/ic_seen_video_logo.xml)

## Previews

<p align="center">
  <img src="screenshots/screenshot1.png" width="25%" />
  <img src="screenshots/screenshot2.png" width="25%" />
  <img src="screenshots/screenshot3.png" width="25%" />
  <img src="screenshots/screenshot4.png" width="25%" />
</p>

## Features

*   **Channel Management**: Follow specific YouTube channels by their handle (e.g., `@VPROTegenlicht`) or using the built-in search.
*   **Channel Groups**: Categorize your channels into custom groups (e.g., "News", "Entertainment") and filter your home feed instantly using interactive group pills.
*   **Focused Home Feed**: Only see videos from the channels you've explicitly added.
*   **Shorts Filter**: Option to filter out YouTube Shorts for a distraction-free long-form video experience.
*   **Local Watch Later & Watched Tracking**: 
    *   Manage your "Watch Later" queue and track watched videos locally on your device.
    *   Completely local-first with no account login or cloud sync required.
*   **Privacy & Focus Tools**:
    *   **Blur Titles & Thumbnails**: Option to blur video thumbnails and titles for specific channels to prevent spoilers or clickbait (tap to reveal).
    *   **Safe Keywords**: Highlight or filter specific keywords in video titles.
*   **Daily Notifications**: 
    *   Configure a preferred daily notification time to receive a single, bundled summary of new videos from your followed channels.
    *   Toggle notifications per individual channel.
*   **Share Extension**: Share videos from the YouTube app directly into SeenVideo to queue them to Watch Later or mark them as watched.
*   **Material 3 Design**: A clean, modern interface with full support for Dark Mode.
*   **Local-First & Private**: No login required; all data stays on your device.

## Technical Stack

*   **Language**: [Kotlin](https://kotlinlang.org/)
*   **UI Framework**: [Jetpack Compose](https://developer.android.com/test-compose) (Jetpack Compose)
*   **Architecture**: MVVM with Clean Architecture principles.
*   **Dependency Injection**: [Hilt](https://dagger.dev/hilt/)
*   **Local Database**: [Room](https://developer.android.com/training/data-storage/room)
*   **Networking**: [Retrofit](https://square.github.io/retrofit/) & [Gson](https://github.com/google/gson)
*   **APIs**: [YouTube Data API v3](https://developers.google.com/youtube/v3) (for public channel feed fetching)
*   **Image Loading**: [Coil](https://coil-kt.github.io/coil/)

## Setup

To build the project locally, you need to provide your own API key in `local.properties`:

1.  Create a project in the [Google Cloud Console](https://console.cloud.google.com/).
2.  Enable the **YouTube Data API v3**.
3.  Generate an **API Key**.
4.  Add the following line to your `local.properties` file in the project root:

```properties
YOUTUBE_API_KEY=YOUR_API_KEY_HERE
```

## License

This project is licensed under the MIT License - see the [LICENSE](LICENSE) file for details.

## Privacy Policy & Terms

*   [Privacy Policy](PRIVACY_POLICY.md)
*   [Terms of Service](TERMS_OF_SERVICE.md)
