# Building Pocket Box

This guide takes you from nothing to a signed Pocket Box APK on your phone. You don't need to know how to code. Commands go in a terminal (Terminal on macOS, a shell on Linux, PowerShell on Windows). Where Windows is different, it says so.

You only do steps 1 and 2 once.

## 1. Install the tools

You need two things: a **Java Development Kit (JDK) 21** and the **Android SDK command-line tools**.

**JDK 21.** Install a free OpenJDK 21 build, for example from [Adoptium](https://adoptium.net/temurin/releases/?version=21). Check it worked:

```bash
java -version
```

It should mention version 21.

**Android SDK.** The easiest way is to install [Android Studio](https://developer.android.com/studio), open it once, and let it set up the SDK; you can close it afterwards. Or, without Android Studio:

1. Download the **command-line tools only** package for your system from the bottom of the [Android Studio download page](https://developer.android.com/studio#command-line-tools-only).
2. Make a folder for the SDK, for example `~/android-sdk`, and unzip the download so you end up with `~/android-sdk/cmdline-tools/latest/bin/sdkmanager`.
3. Tell your terminal where it is:
   ```bash
   export ANDROID_HOME=~/android-sdk
   ```
   (Windows: `setx ANDROID_HOME "%USERPROFILE%\android-sdk"`, then open a new terminal.)

Either way, install the exact SDK parts Pocket Box builds with:

```bash
"$ANDROID_HOME/cmdline-tools/latest/bin/sdkmanager" "platform-tools" "platforms;android-37.0" "build-tools;36.0.0"
```

Accept the licences when asked.

Finally, download the source code (the green **Code** button on GitHub, then **Download ZIP**, or `git clone`), and open a terminal in the `PocketBox` folder. If you didn't set `ANDROID_HOME`, create a file called `local.properties` there containing one line:

```
sdk.dir=/full/path/to/your/android-sdk
```

## 2. Create your signing key (once, then back it up)

Android only installs apps that are signed. Your signing key proves that an update really comes from you.

```bash
keytool -genkeypair -v -keystore pocketbox-release.jks -alias pocketbox -keyalg RSA -keysize 4096 -validity 10000
```

It asks for a password and a few details (your name can be anything). Then:

1. Move `pocketbox-release.jks` somewhere safe **outside** the project folder.
2. Copy `keystore.properties.example` to `keystore.properties` (in the project folder) and fill in the path to your `.jks` file, the alias (`pocketbox`) and your passwords.

**Back up the `.jks` file and its password**, for example on a USB stick and in your password manager. If you lose them, you can never publish an update that installs over the existing app: everyone would have to uninstall Pocket Box (losing their playlists and history) and install it again.

`keystore.properties` and `*.jks` files are listed in `.gitignore`, so they can't be uploaded to GitHub by accident.

## 3. Build

```bash
./gradlew assembleRelease
```

(Windows: `gradlew.bat assembleRelease`.)

The first build downloads Gradle and the libraries, so it takes a few minutes. Later builds are much quicker. If you see `no keystore found, so the release APK will be UNSIGNED`, check step 2.

## 4. Find the APK

```
app/build/outputs/apk/release/app-release.apk
```

That's your finished, signed, optimised app (about 6 MB).

## 5. Install it on GrapheneOS (or stock Android)

**Without a computer:** copy `app-release.apk` to the phone (USB cable, or any file-sharing method you trust), open it in the Files app, and allow installs from that app when Android asks. On GrapheneOS that permission applies to just that one app, and you can switch it off again afterwards.

**With a computer (adb):** turn on Developer options and USB debugging on the phone, connect it, then:

```bash
"$ANDROID_HOME/platform-tools/adb" install app/build/outputs/apk/release/app-release.apk
```

## 6. Check the signature

To confirm the APK is signed with your key:

```bash
"$ANDROID_HOME/build-tools/36.0.0/apksigner" verify --print-certs --verbose app/build/outputs/apk/release/app-release.apk
```

Look for `Verifies` and `Verified using v3 scheme (APK Signature Scheme v3): true`. (v2 signing is switched on too, but because Pocket Box needs Android 14, which always checks the newer v3 signature, the build tools leave the v2 block out and apksigner reports it as `false`.) The certificate's SHA-256 digest must match your key's:

```bash
keytool -list -v -keystore /path/to/pocketbox-release.jks -alias pocketbox
```

## Optional: publish releases from GitHub

`.github/workflows/release.yml` builds a signed APK, with its SHA-256 checksum, whenever you push a version tag, and attaches both to a GitHub Release. It uses only official GitHub and Gradle actions, pinned to exact versions.

It needs four repository secrets (**Settings → Secrets and variables → Actions → New repository secret**):

| Secret | Value |
|---|---|
| `POCKETBOX_KEYSTORE_BASE64` | Your `.jks` file as Base64: `base64 -w0 pocketbox-release.jks` (macOS: `base64 -i pocketbox-release.jks`) |
| `POCKETBOX_KEYSTORE_PASSWORD` | The keystore password |
| `POCKETBOX_KEY_ALIAS` | `pocketbox` |
| `POCKETBOX_KEY_PASSWORD` | The key password |

Then tag a version and push the tag:

```bash
git tag v1.0.0
```

```bash
git push origin v1.0.0
```

Or, without a computer: open the repository's **Actions** tab → **Release** → **Run workflow**, type the version without the `v` (for example `1.1.0`), and press **Run workflow**. It builds from the branch you pick, then creates the tag and the release itself.

A version with a dash in it (like `1.1.0-beta.1`) is published as a pre-release.

## Optional: record a Baseline Profile

Pocket Box ships a starter Baseline Profile. For the best start-up and scrolling speed, record an exact one on your own Pixel: connect it with USB debugging on, then run:

```bash
./gradlew :app:generateBaselineProfile
```

The new profile is saved under `app/src/release/generated/baselineProfiles/` and used by the next release build. To measure cold start and scrolling with and without it:

```bash
./gradlew :baselineprofile:connectedBenchmarkReleaseAndroidTest
```

## Running the tests

```bash
./gradlew testDebugUnitTest
```

The on-device layout test (art, progress bar and play button share one centre line) needs a connected phone or emulator:

```bash
./gradlew connectedDebugAndroidTest
```

## Versions

Every dependency comes from Google Maven or Maven Central, at its latest stable version (checked 2026-11-10), with one approved exception: **Material 3 1.5.0-rc01**, because the stable 1.4.0 doesn't include the Expressive components Pocket Box is built on (button groups, wavy progress, the morphing loading indicator, Material shapes and toggle buttons).

| Library | Version | Why |
|---|---|---|
| Android Gradle Plugin | 9.5.1 | Builds the app |
| Kotlin and Compose compiler plugin | 2.4.21 | The language and the UI compiler |
| KSP and Room compiler | 2.3.13 / 2.8.5 | Generates database code at build time |
| Compose BOM (UI, foundation, animation) | 2026.09.00 | The UI toolkit |
| Material 3 (Expressive) | 1.5.0-rc01 | Every control and the theme |
| Graphics Shapes | 1.1.0 | Shape morphing and album-art shapes |
| Activity Compose | 1.13.0 | Hosts the UI; edge-to-edge; predictive back; folder picker |
| Navigation Compose | 2.10.2 | Moving between screens |
| Lifecycle | 2.11.0 | ViewModels and lifecycle-aware state |
| Core KTX / Splashscreen | 1.19.1 / 1.2.0 | Android helpers; the launch screen |
| Media3 (ExoPlayer, Session, Common) | 1.11.1 | Audio engine, media session, notification, system browsing |
| Room | 2.8.5 | The library database and its search index |
| DataStore Preferences | 1.3.0 | Settings |
| DocumentFile / Palette | 1.1.0 / 1.1.0 | Reading the folders you picked; colours from album art |
| Glance (appwidget, material3) | 1.2.0 | The home-screen widget |
| ProfileInstaller / Baseline Profile plugin / Macrobenchmark | 1.4.1 / 1.5.0 / 1.5.0 | Faster start-up and scrolling |
| kotlinx.coroutines / collections-immutable | 1.11.0 / 0.5.2 | Background work; stable lists for the UI |
| JUnit / AndroidX Test / UiAutomator | 4.13.2 / 1.3.0, 1.7.0 / 2.4.1 | Tests only, never in the APK |

| Tool | Version |
|---|---|
| Gradle wrapper | 9.9.1 (checksum-verified download) |
| JDK | 21 |
| compileSdk / targetSdk / minSdk | 37 / 36 / 34 |
| Build tools | 36.0.0 |

**Why compileSdk is 37 but targetSdk 36.** The current Compose and Material 3 Expressive libraries refuse to build against anything older than API 37. compileSdk only decides which Android APIs the compiler knows about. How the app behaves is set by targetSdk 36 (Android 16, which GrapheneOS is based on), and the oldest supported Android is still 14 (minSdk 34).
