# Security

Pocket Box is a music player, so it shouldn't need much trust. It's built to need as little as possible, and to stay safe when the files it plays come from strangers.

## Reporting a vulnerability

Please report security problems **privately** through GitHub Security Advisories: open this repository's **Security** tab and choose **Report a vulnerability**. Don't open a public issue for a security problem.

Include what you found, the steps or a sample file to reproduce it, and the Pocket Box version and phone/OS you used. You'll get an answer as soon as possible. Fixes are released as a new version, and reporters are credited in the changelog unless they'd rather not be.

## Supported versions

Only the latest release gets security fixes.

## Threat model

**What Pocket Box protects:** your privacy (what you own and listen to), your phone (from hostile files) and the integrity of the app itself.

**What it assumes:** Android and GrapheneOS work as designed; the phone isn't rooted or compromised; you got the APK from this repository's Releases (or built it yourself) and checked its SHA-256.

**Who might attack it, and how:**

| Threat | How Pocket Box handles it |
|---|---|
| A booby-trapped music file (a crafted tag, a lying length field, a huge picture) aimed at crashing the app or running code | Every file is treated as hostile. Pocket Box's own tag readers check every length against the file size and hard caps (16 MB per picture or block, 1 MB per `.lrc` file, 4 MB per playlist, capped text and tag counts), never trust offsets, and turn any damage into "skip this file" (or, the first time a file is added, "hold it back for you to decide"), never a crash. Thousands of deliberately damaged files are part of the automated tests. No native code of its own. |
| A music file that carries something else: an app, an archive, a script, data hidden after the picture or the music | The **integrity check** runs on every file the first time it's added and holds back anything that holds more than music, tags, album art and lyrics, until you decide. Nothing found is ever opened, extracted or run; only the audio and the album-art pictures are ever decoded. |
| A playlist that tries to reach files outside your music folders (`../../`) | `.m3u` imports resolve only inside the folders you granted, with path traversal rejected, and only ever match songs already in your library. |
| Another app on the phone trying to read your library or control playback | Besides the launcher screen, only the playback service and the home-screen widget receiver are exported (the libraries' remaining exported parts accept only Android itself, through system-only permissions; WorkManager's diagnostics receiver is removed). Apps Android doesn't trust may only play, pause, skip and see the current song; they can't see the queue or browse the library. The widget receiver only redraws the widget, so a message from another app can at most cause a redraw. Every `PendingIntent` Pocket Box creates is immutable. |
| Data leaking off the phone | No `INTERNET` permission, so Pocket Box can't send anything anywhere. No analytics, crash reporting or update checks. All app data (including audiobook progress, bookmarks and lyrics timing) stays in the app's private storage and is excluded from cloud backups and device-to-device transfer. |
| Leaking through logs | Pocket Box's own code never logs file paths or library contents, and release builds strip every log call from the app and the libraries inside it. (Android's own system components keep their usual logs, which only apps with special system access can read.) |
| A tampered or impostor APK | Releases are signed with the owner's key (APK Signature Scheme v3, which Android 14 and later check) and published with a SHA-256 checksum. Android refuses updates signed with a different key. You can check who signed an APK yourself: its certificate SHA-256 must be `8E:0E:20:BD:52:8D:62:BF:D6:0A:D7:29:7B:24:F6:6B:B2:62:F2:7D:EE:FF:F5:85:03:A1:14:03:A8:F5:24:60` (see BUILDING.md, step 6, or use GrapheneOS's AppVerifier with the package name `io.github.butchecassidy.pocketbox`). |
| Supply-chain attacks through dependencies | Libraries come only from Google Maven and Maven Central, and Pocket Box asks for nothing but official AndroidX, Media3, Kotlin and JetBrains artifacts, at pinned versions. Build plugins come from the same two places, plus the Gradle Plugin Portal for Gradle's own plugins only. The Gradle download is checksum-verified, and the release workflow uses only official GitHub and Gradle actions, pinned to full commit SHAs. |
| Someone impersonating the project to collect donations | The only real donation addresses are the ones in [DONATE.md](DONATE.md) in this repository. Pocket Box itself never handles money or payment details. |

## Permissions, and why

Nothing is asked for at runtime. Declared, all granted at install:

- `FOREGROUND_SERVICE` and `FOREGROUND_SERVICE_MEDIA_PLAYBACK`: keep music playing in the background.
- `WAKE_LOCK`: keep the processor awake only while audio is playing.
- `MODIFY_AUDIO_SETTINGS`: only for the optional bit-perfect USB DAC mode, to set the output format of Pocket Box's own audio.

Explicitly removed, even if a library tries to add them: `INTERNET`, `ACCESS_NETWORK_STATE`, `ACCESS_WIFI_STATE`, storage and media permissions, `POST_NOTIFICATIONS`, `BLUETOOTH_CONNECT` and `RECEIVE_BOOT_COMPLETED`.

Music is reached only through Android's folder picker (the Storage Access Framework), which gives access to the folders you pick and nothing else. If you revoke a folder, Pocket Box asks you to pick it again; it never asks for broad storage access.

## GrapheneOS hardening

- Works with the Network permission switched off (it never asks for it).
- No dynamically loaded code, no WebView, no `JavascriptInterface`, no reflection tricks: safe under GrapheneOS's dynamic-code-loading restrictions and ahead-of-time compilation.
- 64-bit only. No native code of its own: the only native parts are two small official AndroidX libraries (graphics paths and DataStore). Debug builds turn on synchronous memory tagging (MTE) so memory bugs show up during testing, on phones whose hardware has it (Pixel 8 and newer); on the Pixel 6 and 7 the setting is simply ignored.
- No sensors, contacts, location or camera access.

## What the integrity check can and can't do

It knows how audio files are put together and flags whatever doesn't belong. It's a strong filter for files that carry something extra, but it isn't an antivirus: it can't prove that a file is harmless, and it only runs the first time a song is added. Pocket Box's real protection is that it never does anything with a music file except read its tags and decode its audio.

## Signing keys

The signing key never goes into this repository. `keystore.properties`, `*.jks`, `*.keystore`, `*.p12` and `*.pem` are in `.gitignore`, and the build reads the key from that local file or from encrypted GitHub secrets.

---

I maintain Pocket Box and take responsibility for what this page promises. If anything here turns out to be wrong or unclear, please report it as above and I'll put it right.

**ButcheCassidy - The Nomadic Rebel** · last reviewed 2026-11-11
