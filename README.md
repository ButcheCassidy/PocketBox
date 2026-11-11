# Pocket Box

*Your music, your saddlebag. No strings, no signal.*

Pocket Box is an offline music player for Android, built with Material 3 Expressive. It plays the music you already keep on your phone, shows you exactly what quality you're hearing, and stays out of your business.

**Who it's for:** people who keep MP3s or lossless music (FLAC, ALAC, WAV, AIFF and more) stored locally on their device.

**Runs on:** Built for Google Pixel 8 and newer (Fold models excluded), on GrapheneOS or stock Android 14+.

---

## What it doesn't do

Let's get this out of the way first, because it matters:

- **No streaming.** Pocket Box plays files on your phone. That's the whole job.
- **No internet access at all.** The app doesn't even ask for the network permission, so it couldn't go online if it wanted to. Album art and lyrics come only from your own files.
- **No accounts, no ads, no tracking, no analytics, no crash reports.** Nothing about you or your music ever leaves the phone.
- **No permission prompts.** Pocket Box asks for nothing at runtime. You pick your music folders with Android's own folder picker, and Pocket Box can see those folders and nothing else.

**Bluetooth just works, without a Bluetooth permission.** Sending audio to headphones and speakers is handled by Android itself, and play/pause/skip buttons on your headphones reach the app through the standard media session. Reading the name of the connected device needs no permission either. So Pocket Box never asks for `BLUETOOTH_CONNECT`.

## Features

### Playback
- Gapless playback, with optional crossfade from 0 to 12 seconds
- A real queue: drag to reorder, swipe to remove, play next, add to queue, clear, save as a playlist, and undo the last change
- Shuffle that plays every song once before repeating, and puts the original order back when you turn it off
- Repeat off, repeat all and repeat one
- Playback speed and pitch, and a sleep timer (minutes or end of the song, with an optional fade-out)
- ReplayGain from your tags (track or album mode, with a pre-amp)
- A 10-band equalizer and bass boost, with built-in presets and your own saved ones
- Picks up exactly where you left off, even after a restart
- Ducks or pauses for calls, pauses when headphones come out, and can resume when Bluetooth reconnects (off by default)
- Lock screen, notification, Quick Settings and headphone controls; connected devices can browse your library

### Audio quality, shown honestly
- **Source quality** for every file: codec, sample rate, bit depth, bitrate (constant, variable or average), channels, size and length, with a **Lossy**, **Lossless** or **Hi-Res Lossless** badge
- **Output quality**: what's actually reaching your speaker, wired headphones, USB-C DAC or Bluetooth device, with the output sample rate and format where Android shares them
- A **Resampled** indicator when the file's rate differs from the output's, with a short explanation of why that happens
- An optional **bit-perfect mode for USB DACs** on Android 14 and later, with a clear status and a graceful fallback when a DAC or phone can't do it
- It never invents numbers. If Android doesn't tell it something (like the Bluetooth codec), it says so.

### Material 3 Expressive design
- A big play button that **morphs its shape**, a circle when paused and a rounded square when playing
- A **wavy progress bar** that ripples while music plays and flattens when you pause
- Previous, play and next in an **expressive button group** that squishes and nudges as you press
- A **perfectly centred Now Playing screen**: art, title, quality chips, progress bar and controls all share one centre line, on every Pixel from the 8 to the 10 Pro XL
- A softly **blurred copy of the album art** behind everything, tinted so text always stays readable
- **Swipe the art to skip**: it follows your finger, the next cover slides in, and the art shrinks gently while you drag, just like it does when paused
- **Tap the art to flip it** and read the lyrics in the same square
- Expressive switches, sliders, chips, toggles, loading shapes and navigation everywhere else
- Respects Android's "Remove animations" setting

### Your colours, your way
- Light, dark or follow the system, with a pure-black AMOLED option
- Colours from your wallpaper (Material You), from the album art that's playing, or a colour you pick yourself
- Light and dark are generated as true matching colour schemes, not a cheap inversion, and every combination meets WCAG AA contrast
- You choose all this on first launch, with a live preview of Now Playing

### A tidy library
- **Albums**, **Singles & EPs**, **Songs**, **Artists**, **Playlists** and **Folders**, in tabs you can reorder or hide; the app reopens on the tab you left
- Albums sorted by album artist, then year, then title; each album lists its songs disc by disc, track by track
- Singles and EPs get their own shelf, from your tags or worked out from the track count and length, and you can override it
- Every song in strict A–Z order, with letter headers and a fast-scroll letter rail
- Artist pages with their albums, singles & EPs and all their songs
- Sorting that ignores case, accents and a leading "The", "A" or "An", and puts "Track 2" before "Track 10"
- Compilations stay together under "Various Artists"
- Playlists you can create, rename, reorder and delete, plus `.m3u`/`.m3u8` import and export
- Favourites, genres, years and composers; hide very short tracks; leave out subfolders
- Quick rescans that only read new or changed files

### Instant search
- Results update with every letter you type
- The best matches come first: titles that start with what you typed, then titles with a word that does, and so on
- Forgives small typos ("Beatels" still finds The Beatles)
- Grouped into a top result, songs, albums, singles & EPs and artists, with filter chips
- Your recent searches stay on the phone, and you can clear them

### Listening history that stays on your phone
- Recently played, Most played and Recently added shelves on the Albums tab
- A play counts after 30 seconds or half the song, whichever comes first
- Pause history any time, or wipe it with one tap. It's never backed up or sent anywhere.

### A queue that respects you
- Tapping a song plays that list from that song, the way you'd expect
- If you've queued songs by hand, Pocket Box asks before clearing them
- Every queue replacement comes with an **Undo**

### File integrity check
Every song gets a one-time check the first time it's added to your library. It doesn't matter where the file came from. Pocket Box looks through the file's structure and reports anything inside besides the music itself, its tags, album art and lyrics, such as:

- an embedded file, an app, an archive or other program code
- data tucked in after the album-art image, or after the end of the music
- data hidden in space that should be empty
- another program's private data, or an extra stream such as video
- web or script code inside a text tag

Common harmless extras (store IDs, encoder notes, ReplayGain values, cue sheets, empty padding) don't trigger a warning, so normal ripped and purchased music passes quietly.

When something is found, Pocket Box **holds that song back** and tells you. Under **Settings → Library → Held-back songs** you can see exactly what was found and where, and add the song anyway if you trust it. Pocket Box never opens or runs anything it finds. It only ever plays the audio.

### Lyrics and widget
- Lyrics from your own `.lrc` files or embedded tags. Synced lyrics scroll smoothly with the current line highlighted, and you can tap a line to jump there.
- A home-screen widget with the art, the song and the controls, in your chosen colours

## Supported formats

| Format | Files |
|---|---|
| MP3, MP2 | `.mp3`, `.mp2` |
| AAC and ALAC | `.m4a`, `.m4b`, `.mp4`, `.aac` |
| FLAC | `.flac` (also inside Ogg) |
| Ogg Vorbis, Opus | `.ogg`, `.oga`, `.opus` |
| WAV | `.wav` (PCM and float, including RF64) |
| AIFF | `.aif`, `.aiff`, `.aifc` |
| Matroska audio | `.mka` |

Album art comes from the file itself, or from a `cover.jpg`, `folder.jpg` or `front.png` (and similar) in the same folder.

## Permissions

Pocket Box asks for nothing when you run it. These are the only permissions it declares, all granted automatically at install:

| Permission | Why it's there |
|---|---|
| `FOREGROUND_SERVICE` | Keeps music playing with the screen off or the app in the background |
| `FOREGROUND_SERVICE_MEDIA_PLAYBACK` | Android 14+ requires every background service to say what it does; Pocket Box's only one plays media |
| `WAKE_LOCK` | Stops the phone's processor from sleeping mid-song while music is playing; released as soon as you pause |
| `MODIFY_AUDIO_SETTINGS` | Needed only by the optional bit-perfect USB DAC mode, to choose the output format for Pocket Box's own audio. It can't record, read files or reach the network. |

Android also adds one internal signature-level permission that only Pocket Box itself can hold (it protects the app's own internal messages). There is **no** internet, storage, media, notification, Bluetooth, location, contacts or sensor permission.

## Supported devices

| Device | Series | Status |
|---|---|---|
| Pixel 8 | Pixel 8 | Supported |
| Pixel 8 Pro | Pixel 8 | Supported |
| Pixel 8a | Pixel 8 | Supported |
| Pixel 9 | Pixel 9 | Supported |
| Pixel 9 Pro | Pixel 9 | Supported |
| Pixel 9 Pro XL | Pixel 9 | Supported |
| Pixel 9a | Pixel 9 | Supported |
| Pixel 10 | Pixel 10 | Supported |
| Pixel 10 Pro | Pixel 10 | Supported |
| Pixel 10 Pro XL | Pixel 10 | Supported |


**Pixel 9 Pro Fold, Pixel 10 Pro Fold and all other Fold models are not supported.** Pixel 7 and older are not supported either.

Why Pixel 8 is the minimum: it ships with Android 14, which the USB DAC bit-perfect mode needs; it's 64-bit only, so the app carries only 64-bit code and stays small; and on GrapheneOS it has hardware memory tagging, which catches memory bugs before they become security problems.

## Saddle up

1. Go to this repository's **Releases** page and download the latest `PocketBox-x.y.z.apk` and its `.sha256` file.
2. Check the download wasn't damaged or swapped. On a computer:
   ```bash
   sha256sum -c PocketBox-x.y.z.apk.sha256
   ```
   It should print `OK`.
3. Copy the APK to your phone and open it. On GrapheneOS, Android asks you to allow installs from the app you opened it with; allow it for that one app.
4. Open Pocket Box, pick your music folders, choose a look, and you're done.

Updates install over the old version as long as they're signed with the same key.

## Building from source

Everything you need, step by step and written for people who've never built an app, is in [BUILDING.md](BUILDING.md). One command produces a signed APK.

## Privacy and security

Pocket Box can't reach the internet, reads only the folders you give it, treats every music file as untrusted, checks each new file for hidden contents, keeps everything it knows on your phone (and out of backups), and contains no web views or downloadable code. The full threat model, and how to report a vulnerability, are in [SECURITY.md](SECURITY.md).

## Known limitations

- **Bluetooth codec details:** Android doesn't share which Bluetooth codec is in use with ordinary apps, so Pocket Box shows "Bluetooth: codec not exposed by Android" instead of guessing.
- **Resampling:** Android's audio mixer usually runs at 48 kHz. A 44.1 kHz or hi-res file is resampled on the way out unless bit-perfect USB mode is active, and Pocket Box shows when that happens.
- **Bit-perfect mode** works only with USB DACs (and phones) that accept the file's exact format. While it's on, the equalizer, speed, pitch, ReplayGain and crossfade are switched off, because any of them would change the bits.
- **ReplayGain** only turns songs down, never up, so nothing ever clips. Very quiet tracks aren't boosted.
- **The integrity check** looks for things that shouldn't be in a music file. It can't prove a file is harmless, and it runs only the first time a song is added, not every time it plays.
- **Formats Android reads for Pocket Box** (Matroska audio, raw AAC) show fewer quality details than the formats it reads itself.
- **Nothing online, by design:** no streaming, no downloaded artwork or lyrics, no update checks.

## Screenshots

Screenshots of the library, Now Playing, lyrics, the queue, settings and the widget, in light and dark, will be added here.

## License

Copyright (c) 2026 ButcheCassidy - The Nomadic Rebel.

Pocket Box is source-available under the [PolyForm Strict License 1.0.0](LICENSE). You're welcome to read the code, build it and use the app for any noncommercial purpose. Redistributing it or publishing modified versions isn't allowed. Third-party components and their licences are listed in [THIRD_PARTY_NOTICES.md](THIRD_PARTY_NOTICES.md).

---

Made by **ButcheCassidy - The Nomadic Rebel** · [Donate](DONATE.md)
