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
 │  Scanner (SAF folders → tags/quality → Room)               │
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
- **Playback** lives in a foreground `MediaLibraryService`. The UI talks to it through a Media3 `MediaController`, exactly like the lock screen, notification, Quick Settings and Bluetooth devices do. There is one source of truth for "what's playing".

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
│   ├── queue/                      Queue engine: shuffle order, repeat, undo
│   ├── search/                     Normaliser, ranking, highlighting, Damerau-Levenshtein
│   └── support/                    Donation reminder policy
├── playback/
│   ├── PlaybackService.kt          MediaLibraryService + session callbacks
│   ├── QueueController.kt          Applies the queue engine to ExoPlayer
│   ├── LibraryBrowseTree.kt        Browsing for system UI / connected devices
│   ├── PlayCountTracker.kt         30 s / 50 % listening rule
│   ├── SleepTimer.kt, Crossfader.kt, ReplayGainProcessor.kt
│   ├── effects/                    Equalizer (DynamicsProcessing), bass boost, presets
│   └── output/                     Output-device monitor, bit-perfect (AudioMixerAttributes)
├── widget/                         Jetpack Glance home-screen widget
└── ui/
    ├── theme/                      MaterialExpressiveTheme, HCT colour schemes, tokens
    ├── components/                 TrackRow, AlbumCard, ArtImage, dialogs, sheets, chips…
    ├── navigation/                 Routes, NavHost, adaptive bar/rail
    ├── onboarding/                 Welcome → Folders → Look → Scan
    ├── library/                    Albums, Singles & EPs, Songs, Artists, Playlists, Folders
    ├── detail/                     Album, artist, playlist, folder pages
    ├── search/                     Expressive search bar + results
    ├── nowplaying/                 Now Playing, mini player, lyrics, queue, quality sheets
    ├── settings/                   Settings, equalizer, about, licences
    └── support/                    Donation dialog
```

Other modules:
- `:baselineprofile` generates startup and scrolling Baseline Profiles for `:app`.

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
| `search` | Full-screen search | Search bar / search icon on any tab |
| `nowplaying` | Now Playing (expands from the mini player) | Mini player, notification, widget |
| `settings` | Settings home | Library top bar |
| `settings/{section}` | Library · Appearance · Playback · Audio · Privacy · About | Settings home |
| `equalizer` | 10-band equalizer + bass boost + presets | Settings → Audio, Now Playing menu |
| `licenses` | Third-party licences | About |

Bottom sheets (not routes): queue, lyrics, audio-quality details, track info, sleep timer, speed & pitch, song actions, colour picker.

Adaptive navigation: a bottom navigation bar in portrait and a navigation rail in landscape, chosen from the window size class. The mini player sits above the bar and expands into Now Playing with a shared-element transition.

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

Albums and artists are derived from `tracks` with indexed `GROUP BY` queries, so there is only one copy of the truth.

## Security model in one paragraph

Pocket Box has no network permission, reads only the folders the user picks through the system file picker, treats every file as hostile (size caps, defensive parsing, no crash on bad data), exports only the media service and the widget receiver, uses immutable PendingIntents, strips logs from release builds, excludes all data from backups, and contains no WebView, no reflection tricks and no dynamically loaded code. See [SECURITY.md](../SECURITY.md).
