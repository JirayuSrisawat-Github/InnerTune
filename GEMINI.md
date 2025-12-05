# InnerTune Project Context

## Overview

**InnerTune** is a Material 3 YouTube Music client for Android. It features a modern UI built with Jetpack Compose and supports background playback, downloading, synchronized lyrics, and dynamic theming.

*   **Package Name:** `com.zionhuang.music`
*   **Minimum SDK:** 24 (Android 7.0)
*   **Target SDK:** 35 (Android 15)
*   **Language:** Kotlin (JVM Target 17)

## Architecture & Tech Stack

The project follows a modern Android architecture (MVVM) using Jetpack Compose for the UI.

*   **UI:** Jetpack Compose (Material 3), Coil (Image Loading), Shimmer (Loading states).
*   **Dependency Injection:** Dagger Hilt.
*   **Data & Storage:** Room (Database), DataStore (Preferences).
*   **Media:** AndroidX Media3 (ExoPlayer) for playback and session management.
*   **Networking:** Ktor (likely for API interactions), OkHttp (via Media3).
*   **Multi-module Structure:**
    *   `:app` - Main application module.
    *   `:innertube` - YouTube Music API interaction.
    *   `:kugou`, `:lrclib`, `:dochord` - Lyrics and other metadata sources.
    *   `:kizzy` - Likely a utility or experimental module.
    *   `:material-color-utilities` - Dynamic color generation.

## Build System

The project uses Gradle with Kotlin DSL (`.gradle.kts`) and a Version Catalog (`libs.versions.toml`).

### Build Variants
*   **FOSS:** Free and Open Source flavor (excludes proprietary Google/Firebase libraries).
*   **Full:** Includes Google Services, Firebase (Crashlytics, Analytics, Perf), and ML Kit.

### Key Commands
*   **Build Debug APK:**
    ```bash
    ./gradlew assembleDebug
    # Specific flavors:
    ./gradlew assembleFossDebug
    ./gradlew assembleFullDebug
    ```
*   **Install Debug APK:**
    ```bash
    ./gradlew installDebug
    ```
*   **Run Unit Tests:**
    ```bash
    ./gradlew test
    ```
*   **Clean Project:**
    ```bash
    ./gradlew clean
    ```

## Code Structure (`app/src/main/java/com/zionhuang/music`)

*   `App.kt`: Application entry point (Hilt setup).
*   `MainActivity.kt`: Single Activity architecture, hosts the Compose navigation graph.
*   `di/`: Dependency Injection modules.
*   `ui/`: Compose UI components, screens, and theme.
*   `viewmodels/`: ViewModel classes managing UI state.
*   `db/`: Room database entities and DAOs.
*   `playback/`: Media3 service (`MusicService`, `ExoDownloadService`) and playback logic.
*   `models/`: Data classes.
*   `lyrics/`, `chords/`: Logic for parsing and displaying song lyrics/chords.

## Development Conventions

*   **Dependency Management:** Always use `gradle/libs.versions.toml` for adding or updating dependencies.
*   **Compiler Options:** Context receivers (`-Xcontext-receivers`) are enabled.
*   **Database:** Room schemas are exported to `app/schemas`.
*   **Permissions:** Defined in `AndroidManifest.xml`, including Foreground Service permissions for media playback.

## Environment Variables
For signing and specific build configurations (mostly for CI/CD), the following environment variables are checked:
*   `MUSIC_DEBUG_KEYSTORE_FILE`
*   `MUSIC_DEBUG_SIGNING_STORE_PASSWORD`
*   `MUSIC_DEBUG_SIGNING_KEY_PASSWORD`
*   `MAVEN_USERNAME` / `MAVEN_PASSWORD` (for publishing)
