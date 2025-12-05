# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Project Overview

InnerTune is a Material 3 YouTube Music client for Android built with Jetpack Compose. It provides ad-free music streaming from YouTube Music with features like background playback, synchronized lyrics, guitar chords support, offline caching, and Android Auto integration.

## Build Commands

### Building the App

```bash
# Build debug APK (Full variant with Google Play Services)
./gradlew assembleFullDebug

# Build debug APK (FOSS variant without proprietary libraries)
./gradlew assembleFossDebug

# Build release APK
./gradlew assembleFullRelease
./gradlew assembleFossRelease

# Build all variants
./gradlew build

# Clean build artifacts
./gradlew clean
```

### Testing & Verification

```bash
# Run all unit tests
./gradlew test

# Run unit tests for specific variant
./gradlew testFullDebugUnitTest
./gradlew testFossDebugUnitTest

# Run lint checks
./gradlew lint

# Apply lint fixes automatically
./gradlew lintFix

# Run connected tests on device/emulator
./gradlew connectedAndroidTest
```

### Installing on Device

```bash
# Install debug build on connected device
./gradlew installFullDebug
./gradlew installFossDebug
```

## Architecture Overview

### MVVM + Jetpack Compose Pattern

InnerTune follows a modern Android architecture with clear separation of concerns:

**UI Layer** (Jetpack Compose + Material 3)
- Single Activity architecture (`MainActivity.kt`)
- Compose Navigation with type-safe routing
- Screens: Home, Library, Search, Player, Album/Artist details, Settings
- Reusable components in `ui/component/` and `ui/player/`

**ViewModel Layer**
- Hilt-injected ViewModels with coroutines and Flow
- Key ViewModels: `HomeViewModel`, `LibraryViewModels`, `OnlineSearchViewModel`, `AlbumViewModel`, `ArtistViewModel`
- Reactive state management via `StateFlow`/`Flow`

**Data Layer**
- Room database for local persistence (`MusicDatabase.kt`, `DatabaseDao.kt`)
- 14 entities including SongEntity, AlbumEntity, ArtistEntity, PlaylistEntity, LyricsEntity, ChordsEntity
- Junction tables for many-to-many relationships (SongArtistMap, SongAlbumMap, etc.)
- DataStore for user preferences

**Service Layer**
- `MusicService.kt`: MediaLibraryService using Media3/ExoPlayer
- Handles playback, queue management, media controls, Android Auto
- Custom audio processors for silence skipping and tempo/pitch adjustment
- Dual caching system (streaming cache + download cache)

### Multi-Module Structure

The project is organized into multiple Gradle modules:

**`:app`** - Main application module
- UI, ViewModels, database, playback service
- Hilt dependency injection setup in `di/AppModule.kt`

**`:innertube`** - YouTube Music API wrapper
- Unofficial YouTube InnerTube API client using Ktor
- Handles search, browse, player endpoints, home/explore pages
- Multiple YouTube client emulation (WEB, ANDROID_MUSIC, IOS, TVHTML5)
- Cookie-based authentication with SAPISID hash authorization
- Core: `InnerTube.kt`, `YouTube.kt`

**`:kugou`** - Chinese lyrics provider
- Fetches synced lyrics from KuGou API
- Duration-based matching, Chinese character normalization

**`:lrclib`** - Synced lyrics provider
- Lightweight wrapper for lrclib.net API
- LRC format parsing with timestamps

**`:dochord`** - Guitar chords provider
- Google CSE-based chord sheet search
- Parses HTML chord notation from dochord.com and other sites
- Recently integrated (see `feat/guitar-chords` branch)

**`:kizzy`** - Discord Rich Presence integration
- Shows currently playing music on Discord

**`:material-color-utilities`** - Google's Material You color extraction
- Dynamic theme generation from album artwork

### Playback System Architecture

```
User Action
    ↓
UI (Compose) → ViewModel/PlayerConnection
    ↓
MusicService (MediaLibraryService)
    ↓
ExoPlayer with Custom DataSource Pipeline:
  ├─ ResolvingDataSource (fetch stream URLs from YouTube)
  ├─ CacheDataSource (LRU cache for streaming)
  └─ OkHttpDataSource (HTTP requests)
    ↓
Audio Processors:
  ├─ SilenceSkippingAudioProcessor
  ├─ SonicAudioProcessor (tempo/pitch)
  └─ DefaultAudioSink
```

**Queue System** (`playback/queues/`):
- `Queue` interface with multiple implementations
- `ListQueue`: Local playlist playback
- `YouTubeQueue`: Streaming from YouTube Music
- `YouTubeAlbumRadio`, `LocalAlbumRadio`: Context-aware radio playback

**PlayerConnection.kt**:
- Bridge between UI and MusicService
- Exposes playback state via `StateFlow`
- Handles lyrics/chords synchronization

### Lyrics & Chords System

**Multi-Provider Strategy** (`lyrics/LyricsHelper.kt`):
Providers are tried in priority order:
1. `YouTubeSubtitleLyricsProvider` - YouTube subtitles
2. `LrcLibLyricsProvider` - LrcLib API
3. `KuGouLyricsProvider` - KuGou API
4. `YouTubeLyricsProvider` - YouTube native lyrics

**Chords** (`chords/ChordsHelper.kt`):
- Currently uses `DochordChordProvider`
- Pluggable architecture for future providers
- Database persistence with LRU caching (3 entries)

**Features**:
- Lyrics translation via `TranslationHelper` (Firebase ML Kit in full build)
- Synced lyrics parsing from LRC format
- Chord rendering integrated into player UI

### Database Schema

**Room Database** with 13 schema versions (migrations in `db/`):
- **Entities**: Song, Artist, Album, Playlist, Format, Lyrics, Chords, Event, SearchHistory, RelatedSongMap
- **Junction Tables**: SongArtistMap, SongAlbumMap, AlbumArtistMap, PlaylistSongMap
- **Views**: SortedSongArtistMap (artist ordering), PlaylistSongMapPreview (playlist thumbnails)

**Key Patterns**:
- Flow-based reactive queries for real-time UI updates
- Sorting by create date, name, play time, artist
- Filtering for liked songs, library songs, archived items
- Atomic batch operations via transactions

### Dependency Injection (Hilt)

**AppModule.kt** provides singletons:
- `MusicDatabase`: Room database instance
- `@PlayerCache`: ExoPlayer streaming cache (LRU eviction)
- `@DownloadCache`: Offline download cache (no eviction)

All ViewModels use `@HiltViewModel` with constructor injection. Services are Hilt-aware via `@HiltAndroidApp` on the Application class.

### Navigation Structure

**Compose Navigation** with 20+ destinations:
- Main tabs: Home, Search, Library (Songs/Albums/Artists/Playlists)
- Details: Album, Artist, OnlinePlaylist, LocalPlaylist
- Settings: Appearance, Content, Player, Storage, Privacy, Backup/Restore, Discord, About
- Other: History, Stats, Mood & Genres, New Releases, Account/Login

Navigation uses slide/fade transitions with type-safe arguments (albumId, artistId, browseId).

## Build Flavors

**`full`** - Full variant (default)
- Includes Google Play Services and Firebase (Crashlytics, Performance, Remote Config)
- ML Kit for lyrics translation (language detection, translation)
- OpenCC4J for Chinese character conversion

**`foss`** - F-Droid compatible variant
- No proprietary Google libraries
- No Firebase or ML Kit dependencies

The build system automatically excludes Firebase plugins in FOSS builds and when `PULL_REQUEST` environment variable is set.

## Development Notes

### Code Generation
- **KSP** (Kotlin Symbol Processing) for Room and Hilt
- Room schema location: `app/schemas/` (exported for migration testing)

### Kotlin Features
- **Context Receivers**: Enabled via `-Xcontext-receivers` compiler flag
- **JVM Target**: Java 17 with core library desugaring for API 24+ compatibility
- **Coroutines**: Extensively used for async operations with Flow for reactive streams

### Compose Compiler Reports
Enable detailed Compose compiler metrics:
```bash
./gradlew assembleFullDebug -PenableComposeCompilerReports=true
```
Reports will be generated in `build/compose_metrics/`.

### Custom Maven Repository
This fork uses a custom Maven repository at `maven.jirayu.net` instead of standard Maven Central/Google repositories (see `settings.gradle.kts`).

### Signing Configuration
Debug builds use custom signing if environment variables are set:
- `MUSIC_DEBUG_KEYSTORE_FILE`
- `MUSIC_DEBUG_SIGNING_STORE_PASSWORD`
- `MUSIC_DEBUG_SIGNING_KEY_PASSWORD`

## Key Files & Locations

- **MainActivity**: `app/src/main/java/com/zionhuang/music/MainActivity.kt`
- **MusicService**: `app/src/main/java/com/zionhuang/music/playback/MusicService.kt`
- **Database**: `app/src/main/java/com/zionhuang/music/db/MusicDatabase.kt`
- **YouTube API**: `innertube/src/main/java/com/zionhuang/innertube/YouTube.kt`
- **Lyrics Helper**: `app/src/main/java/com/zionhuang/music/lyrics/LyricsHelper.kt`
- **Chords Helper**: `app/src/main/java/com/zionhuang/music/chords/ChordsHelper.kt`
- **Hilt Module**: `app/src/main/java/com/zionhuang/music/di/AppModule.kt`
- **Navigation**: `app/src/main/java/com/zionhuang/music/ui/navigation/NavigationBuilder.kt`

## Important Patterns

### Adding a New Screen
1. Create Composable in `ui/screens/`
2. Create ViewModel with `@HiltViewModel` in `viewmodels/`
3. Add route to `NavigationBuilder.kt`
4. Use Navigation with type-safe arguments

### Adding a New Lyrics/Chords Provider
1. Implement `LyricsProvider` or `ChordProvider` interface
2. Add to priority list in `LyricsHelper.kt` or `ChordsHelper.kt`
3. Handle caching in database (LyricsEntity/ChordsEntity)

### Database Migrations
1. Update entity classes and increment version in `MusicDatabase.kt`
2. Add migration logic in `Migration.kt`
3. Export schema to `app/schemas/` via KSP
4. Test migration with Room schema validation

### Working with Player
Access player state via `PlayerConnection`:
- Inject `PlayerConnection` via Hilt
- Observe state with `playbackState.collectAsState()`
- Control playback: `play()`, `pause()`, `seekTo()`, etc.

## Testing Notes

- Unit tests configuration in `app/build.gradle.kts`: `isIncludeAndroidResources = true`, `isReturnDefaultValues = true`
- No instrumented tests currently in the codebase
- Lint configuration in `app/lint.xml`

## Branch Information

Current branch: `feat/guitar-chords` - Implements guitar chords support via Dochord integration
Main development branch: `dev`
