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
| A booby-trapped music file (a crafted tag, a lying length field, a huge picture) aimed at crashing the app or running code | Every file is treated as hostile. Pocket Box's own tag readers check every length against the file size and hard caps (16 MB per picture or block, 1 MB per `.lrc` file, 4 MB per playlist, capped text and tag counts), never trust offsets, and turn any damage into "skip this file", never a crash. No native code of its own. |
| A music file that carries something else: an app, an archive, a script, data hidden after the picture or the music | The **integrity check** runs on every file the first time it's added and holds back anything that holds more than music, tags, album art and lyrics, until you decide. Nothing found is ever opened, extracted or run; only the audio is ever decoded. |
| A playlist that tries to reach files outside your music folders (`../../`) | `.m3u` imports resolve only inside the folders you granted, with path traversal rejected. |
| Another app on the phone trying to read your library or control playback | Only the playback service and the home-screen widget are exported. Apps Android doesn't trust may only play, pause and skip, and can't see the queue or browse the library. Every `PendingIntent` is immutable. |
| Data leaking off the phone | No `INTERNET` permission, so Pocket Box can't send anything anywhere. No analytics, crash reporting or update checks. All app data is excluded from cloud backups and device-to-device transfer. |
| Leaking through logs | Release builds strip all `Log` calls; file paths and library contents are never logged. |
| A tampered or impostor APK | Releases are signed with the owner's key (v2 and v3 signatures) and published with a SHA-256 checksum. Android refuses updates signed with a different key. |
| Supply-chain attacks through dependencies | Libraries only from Google Maven and Maven Central (official AndroidX, Media3, Kotlin and JetBrains artifacts), at pinned versions; the Gradle wrapper download is checksum-verified; GitHub Actions are pinned to full commit SHAs. |

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
- 64-bit only, no custom native code; debug builds turn on synchronous memory tagging (MTE) so memory bugs show up during testing.
- No sensors, contacts, location or camera access.

## What the integrity check can and can't do

It knows how audio files are put together and flags whatever doesn't belong. It's a strong filter for files that carry something extra, but it isn't an antivirus: it can't prove that a file is harmless, and it only runs the first time a song is added. Pocket Box's real protection is that it never does anything with a music file except read its tags and decode its audio.

## Signing keys

The signing key never goes into this repository. `keystore.properties`, `*.jks`, `*.keystore`, `*.p12` and `*.pem` are in `.gitignore`, and the build reads the key from that local file or from encrypted GitHub secrets.
