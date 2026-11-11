# Pocket Box architecture

This page explains how Pocket Box is put together. You don't need it to build or use the app; it's here for anyone reading the code.

## The big picture

Pocket Box follows the layered structure Google recommends for Android apps, with data flowing in one direction:

```
 ┌──────────────────────────── ui ────────────────────────────┐
 │  Compose screens  ──events──▶  ViewModels  ──StateFlow──▶  │
 │        ▲                                          │         │
 │        └───────────────── UI state ◀──────────────┘         │
 └──────────────────────────────┬─────────────────────────────┘
                                │ calls / Flows
 ┌──────────────────────────── domain ────────────────────────┐
 │  Pure Kotlin rules: sorting, release type, audio quality,  │
 │  queue/shuffle/repeat, search ranking, fuzzy matching,     │
 │  LRC parsing, M3U safety, donation-reminder timing         │
 └──────────────────────────────┬─────────────────────────────┘
                                │
 ┌──────────────────────────── data ──────────────────────────┐
 │  Room (library, FTS4 search, playlists, history, queue)    │
 │  DataStore (settings, onboarding, reminder state)          │
 │  Scanner (SAF folders → integrity check → tags → Room)     │
 │  Artwork cache (disk + memory LRU), lyrics loader          │
 └──────────────────────────────┬─────────────────────────────┘
                                │
 ┌────────────────────────── playback ────────────────────────┐
 │  PlaybackService (Media3 MediaLibraryService + ExoPlayer)  │
 │  Queue engine, audio effects, output-device monitor        │
 └────────────────────────────────────────────────────────────┘
```

- **Screens** never touch the database or the player directly. They show a single immutable UI-state object from their ViewModel and send user events back.
- **ViewModels** combine repository `Flow`s into a `StateFlow`, using `stateIn(viewModelScope, WhileSubscribed(5_000), …)` so work stops when nobody is watching.
- **Domain** code is plain Kotlin with no Android imports wherever possible, so it can be unit-tested on a normal JVM.
- **Playback** lives in a foreground `MediaLibraryService`. The UI talks to it through a Media3 `MediaController` (wrapped in `PlayerConnection`), exactly like the lock screen, notification, Quick Settings and Bluetooth devices do. There is one source of truth for "what's playing".
- **Settings are the source of truth for playback options** (speed, pitch, crossfade, ReplayGain, EQ, bit-perfect). The UI writes them to DataStore; the service observes them and applies them. Pocket Box runs in a single process, so a few small pieces of state (sleep-timer state, output info, playback error messages) are shared in-process through the `AppContainer`; commands that change playback always go through the media session.

## Dependency injection: manual, not Hilt

Pocket Box uses a small hand-written `AppContainer` created once in `PocketBoxApp`.

Why:
- **Fewer moving parts.** No annotation processor or generated code for DI; every object is created in one readable file.
- **Smaller, faster app.** No Dagger runtime or generated factories in the APK; nothing extra at startup.
- **GrapheneOS-friendly.** Plain constructor calls are trivially safe for ahead-of-time compilation and R8.
- The object graph is small (one database, a handful of repositories, one service), so Hilt's benefits don't outweigh its cost here.

ViewModels get their dependencies through `viewModelFactory { initializer { … } }` from AndroidX Lifecycle.

## Threads and coroutines

- Database and file work runs on `Dispatchers.IO`; CPU-heavy work (search ranking, fuzzy matching, colour maths, palette extraction) on `Dispatchers.Default`.
- No `GlobalScope`. Long-lived work uses scopes owned by the `Application` container or the service, cancelled when they end.
- Search uses `debounce(60)` + `flatMapLatest` so stale queries are cancelled.
- Player position for the seek bar is read once per frame (`withFrameNanos`) **only while the Now Playing or mini player is on screen**. There are no polling loops.

## Package tree

```
io.github.butchecassidy.pocketbox
├── PocketBoxApp.kt                 Application; owns AppContainer
├── MainActivity.kt                 Single activity; edge-to-edge; hosts Compose
├── SupportConfig.kt                The one and only DONATION_URL
├── di/AppContainer.kt              Manual dependency graph
├── data/
│   ├── db/                         Room database, entities, DAOs, FTS4 table
│   ├── prefs/                      DataStore: settings, onboarding, reminder state
│   ├── scan/                       SAF folder access, tag & header readers, scanner
│   │   └── integrity/              First-add integrity check (what else a file holds)
│   ├── art/                        Artwork extraction, disk thumbnail cache, memory LRU
│   ├── lyrics/                     Embedded + sidecar .lrc loading
│   └── repo/                       Library, playlist, history, search, queue repositories
├── domain/
│   ├── model/                      Track, Album, Artist, AudioQuality, OutputInfo…
│   ├── sort/                       Collator sort keys, article stripping, natural numbers
│   ├── quality/                    Lossy / Lossless / Hi-Res classification
│   ├── release/                    Album / Single / EP classification
│   ├── lyrics/                     LRC parser
│   ├── playlist/                   M3U/M3U8 read/write + path validation
│   ├── queue/                      ShuffleSequence: play-once shuffle, insert/remove/move rules
│   ├── audio/                      ReplayGain maths, equalizer presets and bass-boost curve
│   ├── search/                     Normaliser, ranking tiers, highlighting, Damerau-Levenshtein
│   └── support/                    Donation reminder policy
├── playback/
│   ├── PlaybackService.kt          MediaLibraryService, session callback, trusted-controller rules
│   ├── PlayerFactory.kt            ExoPlayer setup (content:// only), VolumeMixer
│   ├── QueueShuffleOrder.kt        Media3 ShuffleOrder backed by ShuffleSequence
│   ├── QueueStore.kt               Saves/restores the queue in Room
│   ├── MediaItems.kt               Track ↔ MediaItem, private art URIs
│   ├── ArtworkBitmapLoader.kt      Notification/lock-screen art from the local cache
│   ├── AiffExtractor.kt            AIFF/AIFF-C playback (Media3 has none)
│   ├── LibraryBrowseTree.kt        Browsing for system UI / connected devices
│   ├── PlayCountTracker.kt         30 s / 50 % listening rule
│   ├── SleepTimer.kt, Crossfader.kt
│   ├── AudioEffectsController.kt   10-band DynamicsProcessing EQ + bass boost (Equalizer fallback)
│   ├── OutputMonitor.kt            Output device, formats, resampling, bit-perfect (AudioMixerAttributes)
│   ├── PlayerConnection.kt         UI side: MediaController → StateFlows and commands
│   └── QueueController.kt          UI side: "tap a song" rules, confirm dialog, undo
├── widget/                         Jetpack Glance home-screen widget and its state
└── ui/
    ├── theme/                      MaterialExpressiveTheme, HCT colour schemes, tokens
    ├── components/                 TrackRow, AlbumCard, AlbumRow, ArtistRow, ArtImage, QualityBadge,
    │                               PocketDialog, PocketSheet, action menus, drag-to-reorder
    ├── navigation/                 Routes, NavHost, PocketBoxRoot (adaptive bar/rail, mini player)
    ├── onboarding/                 Welcome → Folders → Look → Scan
    ├── library/                    Albums, Singles & EPs, Songs, Artists, Playlists, Folders
    ├── detail/                     Album, artist, playlist, folder pages
    ├── search/                     Expressive search bar + results
    ├── nowplaying/                 Now Playing, mini player, lyrics, queue, quality sheets
    ├── settings/                   Settings sections, colour picker, equalizer, about, licences,
    │                               held-back songs
    └── support/                    Donation reminder dialog
```

Other modules:
- `:baselineprofile` generates startup and scrolling Baseline Profiles for `:app` on a connected phone, and benchmarks cold start. It is test-only and never ships in the APK.

## Screens and navigation

| Route | Screen | Reached from |
|---|---|---|
| `onboarding` | Welcome → Pick folders → Pick a look → Scan | First launch; Settings → "Run setup again" |
| `library` | Tabs: Albums · Singles & EPs · Songs · Artists · Playlists · Folders | Start destination after onboarding |
| `album/{id}` | Album page | Album grid, search, long-press "Go to album" |
| `artist/{id}` | Artist page (Albums, Singles & EPs, All songs) | Artists tab, search, "Go to artist" |
| `playlist/{id}` | Playlist page (reorder, rename, export) | Playlists tab |
| `folder/{id}` | Folder contents | Folders tab |
| `shelf/{kind}` | Recently played / Most played / Recently added | Albums tab shelves |
| `favorites` | Favourite songs | Playlists tab, library menu |
| `facets/{type}`, `facet/{type}/{value}` | Genres, years, composers and their songs | Library menu |
| `search` | Expressive search bar expanding to full-screen search | Search icon on the library, bottom bar |
| `settings` | Settings home | Library top bar |
| `settings/{section}` | Library · Appearance · Playback · Audio · Privacy · About | Settings home |
| `equalizer` | 10-band equalizer + bass boost + presets | Settings → Audio, Now Playing menu |
| `licenses` | Third-party licences | About |
| `held` | Songs the integrity check held back, with what was found and "Add anyway" | Settings → Library; the notice after a scan |

**Now Playing is an overlay, not a route.** It expands from the mini player over everything (shared-element art transition) and collapses with back, so the library keeps its place underneath.

Bottom sheets (not routes): queue, lyrics, audio-quality details, track info, sleep timer, speed & pitch, song and release actions.

Adaptive navigation: Library · Search · Settings in an expressive `ShortNavigationBar` on narrow screens and a `WideNavigationRail` at 600 dp and wider (landscape), chosen from the screen width. The mini player sits above the bar.

## Data model (Room)

| Table | Purpose |
|---|---|
| `folders` | SAF tree URIs the user granted, with exclude flags |
| `tracks` | One row per audio file: tags, sort keys, quality, art key, file mtime |
| `tracks_fts` | FTS4 (unicode61, `remove_diacritics=2`) over title, artist, album artist, album, genre, composer |
| `release_overrides` | User's per-release Album / Single / EP choice |
| `playlists`, `playlist_tracks` | Playlists and ordered entries |
| `favorites` | Favourite tracks |
| `play_stats` | Play count + last played per track (history; never backed up) |
| `recent_searches` | Recent search terms (clearable) |
| `queue_items`, `queue_state` | The saved queue, shuffle order and position |
| `eq_presets` | Saved custom equalizer presets |
| `excluded_folders` | Subfolders the user left out of the library |
| `held_files` | New files the integrity check held back, their findings and the user's "Add anyway" |

Albums and artists are derived from `tracks` with indexed `GROUP BY` queries, so there is only one copy of the truth.

## The integrity check

The first time the scanner meets a file, `IntegrityInspector` reads its structure before anything is added: ID3v2 frames, APE/Lyrics3/ID3v1 footers, FLAC metadata blocks and frame CRCs, Ogg pages and streams, MP4 boxes and track types, WAV/AIFF chunks. Every embedded picture and text tag goes through the same report, through a `TagObserver` hooked into the normal tag readers. It reports what doesn't belong in a music file (embedded files, program code or archives, data after the album art or after the audio, filled-in padding, other programs' blocks, extra streams, script in text tags) and ignores well-known harmless extras.

A file with findings becomes a `held_files` row instead of a track. It is not checked again while it stays the same; the user can review it and choose "Add anyway", after which that exact version (same size and modification time) is added without another check. Files already in the library are never re-checked, even when they change. Nothing found is ever opened or decoded.

## The widget

`NowPlayingWidget` (Jetpack Glance) shows the art, title, artist and controls. The playback service publishes a small `WidgetSnapshot` into the `AppContainer`; `WidgetRefresher` redraws placed widgets only when the song, play state or look changes (debounced). Colours come from the same scheme builder as the app. Its buttons go through the app's own `MediaController`, like every other control.

## Security model in one paragraph

Pocket Box has no network permission, reads only the folders the user picks through the system file picker, treats every file as hostile (size caps, defensive parsing, no crash on bad data), checks each new file for hidden contents and holds suspicious ones back, exports only the media service and the widget receiver, uses immutable PendingIntents, strips logs from release builds, excludes all data from backups, and contains no WebView, no reflection tricks and no dynamically loaded code. See [SECURITY.md](../SECURITY.md).
