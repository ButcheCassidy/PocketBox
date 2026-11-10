# Pocket Box: verified dependency versions

Checked on 2026-11-10 against the official repositories' `maven-metadata.xml`
(Google Maven: `dl.google.com/android/maven2`, Maven Central: `repo1.maven.org/maven2`).

Pocket Box uses **Latest stable** everywhere, with **one** approved exception:
`androidx.compose.material3:material3`. The stable 1.4.0 does not contain the
Material 3 Expressive components the design needs (`ButtonGroup`,
`LinearWavyProgressIndicator`, `LoadingIndicator`, `MaterialShapes`,
`ToggleButton`), so Pocket Box uses **1.5.0-rc01**, the newest release that has
them (prompt section 6A). Everything else in Compose comes from the stable BOM.

## Libraries

| Library | Source | Latest stable | Latest (any) | Used |
|---|---|---|---|---|
| `com.android.tools.build:gradle` (AGP) | Google Maven | 9.5.1 | 9.6.0-alpha03 | 9.5.1 |
| `androidx.compose:compose-bom` | Google Maven | 2026.09.00 | 2026.09.00 | 2026.09.00 |
| `androidx.compose.material3:material3` | Google Maven | 1.4.0 | 1.5.0-rc01 | **1.5.0-rc01** (Expressive exception) |
| `androidx.graphics:graphics-shapes` | Google Maven | 1.1.0 | 1.1.0-rc01 | 1.1.0 |
| `androidx.activity:activity-compose` | Google Maven | 1.13.0 | 1.14.0-alpha03 | 1.13.0 |
| `androidx.navigation:navigation-compose` | Google Maven | 2.10.2 | 2.10.2 | 2.10.2 |
| `androidx.lifecycle:lifecycle-runtime-compose` | Google Maven | 2.11.0 | 2.12.0-alpha05 | 2.11.0 |
| `androidx.lifecycle:lifecycle-viewmodel-compose` | Google Maven | 2.11.0 | 2.12.0-alpha05 | 2.11.0 |
| `androidx.lifecycle:lifecycle-process` | Google Maven | 2.11.0 | 2.12.0-alpha05 | 2.11.0 |
| `androidx.core:core-ktx` | Google Maven | 1.19.1 | 1.19.1 | 1.19.1 |
| `androidx.core:core-splashscreen` | Google Maven | 1.2.0 | 1.2.0-rc01 | 1.2.0 |
| `androidx.media3:media3-exoplayer` | Google Maven | 1.11.1 | 1.12.0-alpha02 | 1.11.1 |
| `androidx.media3:media3-session` | Google Maven | 1.11.1 | 1.12.0-alpha02 | 1.11.1 |
| `androidx.media3:media3-common` | Google Maven | 1.11.1 | 1.12.0-alpha02 | 1.11.1 |
| `androidx.room:room-runtime` / `room-ktx` / `room-compiler` | Google Maven | 2.8.5 | 2.8.5 | 2.8.5 |
| `androidx.datastore:datastore-preferences` | Google Maven | 1.3.0 | 1.3.0-rc01 | 1.3.0 |
| `androidx.documentfile:documentfile` | Google Maven | 1.1.0 | 1.1.0-rc01 | 1.1.0 |
| `androidx.palette:palette-ktx` | Google Maven | 1.1.0 | 1.1.0-rc01 | 1.1.0 |
| `androidx.glance:glance-appwidget` | Google Maven | 1.2.0 | 1.3.0-alpha02 | 1.2.0 |
| `androidx.glance:glance-material3` | Google Maven | 1.2.0 | 1.3.0-alpha02 | 1.2.0 |
| `androidx.profileinstaller:profileinstaller` | Google Maven | 1.4.1 | 1.4.1 | 1.4.1 |
| `androidx.baselineprofile` (Gradle plugin) | Google Maven | 1.5.0 | 1.6.0-alpha01 | 1.5.0 |
| `androidx.benchmark:benchmark-macro-junit4` | Google Maven | 1.5.0 | 1.6.0-alpha01 | 1.5.0 |
| `androidx.compose.material3.adaptive:adaptive` | Google Maven | 1.3.0 | 1.4.0-alpha03 | 1.3.0 |
| `androidx.test.ext:junit` | Google Maven | 1.3.0 | 1.4.0-alpha03 | 1.3.0 |
| `androidx.test:runner` | Google Maven | 1.7.0 | 1.8.0-alpha03 | 1.7.0 |
| `androidx.test.uiautomator:uiautomator` | Google Maven | 2.4.1 | 2.4.1 | 2.4.1 |
| `androidx.compose.ui:ui-test-junit4` / `ui-test-manifest` | Google Maven | 1.12.1 (via BOM) | 1.13.0-rc01 | BOM |
| `org.jetbrains.kotlin` (Kotlin + Compose compiler plugin) | Maven Central | 2.4.21 | 2.5.0-Beta2 | 2.4.21 |
| `org.jetbrains.kotlinx:kotlinx-coroutines-android` / `-test` | Maven Central | 1.11.0 | 1.11.0-rc02 | 1.11.0 |
| `org.jetbrains.kotlinx:kotlinx-collections-immutable` | Maven Central | 0.5.2 | 0.5.2 | 0.5.2 |
| `com.google.devtools.ksp` | Maven Central | 2.3.13 | 2.3.13 | 2.3.13 |
| `junit:junit` | Maven Central | 4.13.2 | 4.13.2 | 4.13.2 (tests only) |

Checked but **not used**: Hilt (`com.google.dagger:hilt-android` 2.60.1; Pocket Box uses manual DI, see
`docs/ARCHITECTURE.md`), `material-icons-extended` (replaced by Material Symbols Rounded vector drawables),
`espresso-core`, `navigation-suite`, `media3-inspector` (Pocket Box has its own small, size-capped tag and
header readers, which are faster for scanning and read exactly the fields it shows).

## Tools

| Tool | Version |
|---|---|
| Gradle wrapper | 9.9.1 |
| JDK | 21 |
| compileSdk | 37 (see note) |
| targetSdk | 36 |
| build-tools | 36.0.0 |
| minSdk | 34 |

**Note on compileSdk.** The prompt asks for compileSdk 36, but every current Compose 1.12 artifact and
Material 3 1.5.0-rc01 (needed for Expressive) refuse to build against anything below API 37. Pocket Box
therefore *compiles* against API 37 while *targeting* API 36. compileSdk only decides which APIs the
compiler knows about; it doesn't change how the app behaves or which phones it installs on. Runtime
behaviour follows targetSdk 36 (Android 16, what GrapheneOS is based on), and minSdk stays 34.

## Why each library is here

| Library | Why Pocket Box needs it |
|---|---|
| AGP, Kotlin, Compose compiler plugin | Build the app; Kotlin is the language, Compose the UI toolkit |
| KSP + Room compiler | Generates Room database code at build time (nothing at runtime) |
| Compose BOM (ui, foundation, animation) | The UI toolkit itself, kept at matching versions |
| Material 3 (Expressive) | Every control: theme, buttons, button groups, wavy progress, loading indicator, sheets, dialogs |
| Material 3 Adaptive | Window size class to switch between bottom bar and navigation rail |
| Graphics Shapes | Shape morphing for the play button and the selectable album-art shapes |
| Activity Compose | Hosts Compose in the single activity; edge-to-edge; predictive back; folder picker contract |
| Navigation Compose | Moves between screens with back-stack and transitions |
| Lifecycle (runtime/viewmodel compose, process) | ViewModels, lifecycle-aware state collection, app foreground detection |
| Core KTX + Splashscreen | Kotlin helpers for Android APIs; the Android 12+ splash screen |
| Media3 ExoPlayer | The audio engine: gapless playback, decoding, audio sink |
| Media3 Session | `MediaLibraryService`, notification, lock screen, Bluetooth buttons, system browsing |
| Room (+ KTX) | Local library database with FTS4 search |
| DataStore Preferences | Settings and small state, written safely off the main thread |
| DocumentFile | Walks the folders the user granted through the Storage Access Framework |
| Palette | Picks a seed colour from album art, on the device |
| Glance (appwidget + material3) | The home-screen widget |
| ProfileInstaller + Baseline Profile plugin | Ships pre-compiled hot code for fast startup and smooth scrolling |
| kotlinx.coroutines | Background work and Flows |
| kotlinx.collections.immutable | Stable list types so Compose skips unnecessary recomposition |
| JUnit, AndroidX Test, UiAutomator, Macrobenchmark, Compose UI test | Tests and Baseline Profile generation only; never in the APK |
