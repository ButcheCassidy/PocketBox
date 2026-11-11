# Changelog

All notable changes to Pocket Box are listed here. Versions follow [Semantic Versioning](https://semver.org/).

## [1.1.0] - 2026-11-11

Installs over 1.0.0 and keeps your library, playlists, history and settings.

### Added
- Audiobooks: mark a folder as an audiobook folder and its books move into their own Audiobooks tab, apart from your music. Progress is saved automatically, bookmarks save an exact spot, and on return the app offers to continue or jump to a bookmark. Back/forward 30 seconds; chapters play in order without shuffle or crossfade.
- Word-by-word synced lyrics for "enhanced" `.lrc` files, and a per-song lyrics timing adjustment.
- Support for the Pixel 6, 6 Pro, 6a, 7, 7 Pro and 7a.

### Fixed
- Seeking while paused now moves the time, progress bar and lyrics straight away instead of when playback resumes.

## [1.0.0] - 2026-11-11

The first stable release. It installs over 1.0.0-beta.1 and keeps your library, playlists and settings.

### Fixed since 1.0.0-beta.1
- The app no longer keeps the player connected while it's off screen, so a paused player can shut down and stop using battery and memory.
- The sleep timer keeps time while the phone sleeps, and "end of song" follows you when you skip.
- Removing a folder during a scan, or a song disappearing while it plays, can no longer crash the app.
- Damaged AIFF files and files the integrity check can't follow are handled safely (the latter are held back for you to decide, instead of being skipped silently).
- Drag-to-reorder in playlists, actions from the song and album menus, the queue's swipe-to-remove, "Run setup again", rotation with Now Playing open, and the held-back songs notice all behave correctly.
- Counts read naturally ("1 song", not "1 songs"), and the drag handle is easier to grab.
- A WorkManager developer receiver that Pocket Box doesn't need was removed, and unused code and resources were cleaned out.

### Added
- Offline playback of MP3, AAC, ALAC, FLAC, Ogg Vorbis, Opus, WAV, AIFF and Matroska audio from folders you pick, with gapless playback, crossfade, a full queue with undo, play-once shuffle, repeat one/all, speed and pitch, a sleep timer, ReplayGain, a 10-band equalizer and bass boost.
- Source and output audio quality display, Hi-Res badge, Resampled indicator and bit-perfect USB DAC mode.
- Material 3 Expressive design: shape-morphing play button, wavy progress bar, expressive button group, a centred Now Playing screen with blurred art, swipe-to-skip and tap-to-flip lyrics.
- Light, dark and AMOLED themes from Material You, album art or a colour you pick.
- Library tabs for albums, singles & EPs, songs, artists, playlists and folders; instant typo-tolerant search; listening history that stays on the phone.
- A one-time integrity check for every new song that holds back files containing anything besides music, tags, album art and lyrics.
- Synced and plain lyrics from `.lrc` files or tags, and a home-screen widget.
