# active-memory handoff: Pocket Box (offline Material 3 Expressive music player for GrapheneOS / Pixel 8+)

**Handoff #2** · 2026-11-11 · Lineage: #1 (2026-11-10): planned the app; built tasks 1–14 of 24 (scaffold, data, scanner, playback, effects, theme, full UI); everything compiles, 42 unit tests pass, pushed; #2 (2026-11-11): fixed the cloud setup script; added a new task (integrity check) so the list is now 25; built tasks 15–20 (widget, integrity check, baseline profile, tests, repo docs, full build + lint); 68 tests pass; created the release signing key; signed beta APK sent to the user (GitHub Release not created: tag push blocked).

## 0. Instructions for Claude (read first)

You are continuing work from a previous chat. That chat is gone; this file is the complete context and the source of truth.

1. Read this whole file before replying.
2. Follow sections 3 (Style), 4 (Hard rules) and 5 (Corrections) in every reply, for the rest of this chat. They override your defaults.
3. Use the values in section 8 exactly. Never round, re-estimate, or "correct" them.
4. Do not suggest anything listed in section 7 (Changed / rejected) again unless the user brings it up.
5. Code word: None active. If the user types /amcodeword, start every reply with the phrase they choose (default "Yes Boss!").
6. Your first reply: at most 5 lines covering the goal, the current state, and the next step (section 11). Mention any files from section 13 that were not attached. Ask the questions in section 12 if there are any. End with "Ready to continue with <next step>?" Then wait for the user's go.

## 1. Mission
- **Goal:** Build **Pocket Box**, a complete, production-quality, offline music player for Android (Kotlin, Jetpack Compose, Material 3 Expressive, Media3), published publicly on GitHub, following every section of `PocketBox_Prompt.md`.
- **Done looks like:** a repository the user can push to GitHub and build into a signed APK by following `BUILDING.md`, with no code changes; plus the final report described in spec §11 step 9; plus a clean repo ("I don't want unnecessary files left inside").
- **What's left:** tasks 21–25 only: four review rounds (bugs, GrapheneOS/devices/permissions, performance, security/signing), then manual test checklist + repo cleanup + final report.
- **Why it matters / context:** The user doesn't code; output must be complete, buildable without edits, and explained in plain English. The spec calls it "a one-shot request".

## 2. About the user (as relevant to this work)
- Not a programmer ("I don't code"); judges results by the spec, the README and whether it builds.
- Author credit everywhere must be exactly **ButcheCassidy - The Nomadic Rebel**.
- GitHub repository `ButcheCassidy/PocketBox` (session scope shows `butchecassidy/pocketbox`).
- Uses Claude Code cloud sessions (follows from the Claude app, often away mid-task); plugins installed: ui-consistency, active-memory, design, playwright, agent-army.
- Personal preference: "When we are developing apps together Especially Mac Apps Please in the later stages focus on security." → reviews 21–24 are "the later stages": put security first.
- Target devices: Google Pixel 8 and later (no Folds), GrapheneOS (Android 16 based) or stock Android 14+.

## 3. Style & communication
- **Language:** English.
- **Tone:** direct, plain English, no corporate fluff. README has "a subtle, light hint of the Wild West" (tagline *"Your music, your saddlebag. No strings, no signal."*, header "Saddle up"); keep it tasteful, never cheesy.
- **Reply length:** "Eliminate all conversational filler be concise and short in your response. Only add detail to execution steps and the end reply only."
- **Formatting:** Markdown; short status report per batch of tasks; tables fine.
- **Working style:** work through the task list in order; stop and report after the agreed batch; wait for "Go". Work steadily, don't rush. When the user is away and has given a batch, keep going without asking. Ask questions with the question tool when a decision is genuinely theirs.
- **Avoid:** conversational filler; research tangents; agents/sub-agents; giving up on something without trying the reasonable options first.

## 4. Hard rules (word for word)
1. "-Execute every task in order in Pocketbox Prompt.md This contains all information you need to build the app, create a FULL task list and work things through task by task stop and report completion every three tasks you complete. When I say "Go", you will proceed with the next three tasks on the task list you created. Do bug fixes and bug hunts at the end."
2. "Eliminate all conversational filler be concise and short in your response. Only add detail to execution steps and the end reply only."
3. "Automatically apply appropriate plugins available to you at the beginning of every new chat session."
4. "NEVER Use agents or sub-agents unless I ask for it. Don't go on research tangents only search for files you need or topics you need, nothing else."
5. "Every line of code must have a purpose."
6. "When making the readme make sure that it is compatible with GitHub and that it has good formatting subtitles etc explaining the features in a natural way refer to Pocketbox Prompt.md"
7. "For now, just build everything. Don't do any bug checks while building unless it is really essential. And we are going to do the big bug fix at the very end of the task list."
8. "Please slow down. Don't use the slow down time to reason more. Just take things easy."
9. User preference: "When we are developing apps together Especially Mac Apps Please in the later stages focus on security."
10. From the spec: "Credit the app everywhere as **ButcheCassidy - The Nomadic Rebel** … Never use any other name for the author."
11. No `TODO`, no "implement here", no placeholders, no "rest of code unchanged" in delivered code; never describe the project as "open source"; it is "source-available" (third-party libraries may be called open-source; the in-app licences screen is labelled "Third-party licences").
12. Session setup (user's `CLAUDE.md`): "Never create, read or print keystores or `keystore.properties`. The owner handles signing." **One-time exception granted 2026-11-11:** user said "Create a key store outside the repo, sign with it and send me the JKS plus passwords to back up. Remember keep requests at a bare minimum if going outside." (Done; see section 8. The rule applies again from now on; never put any key in the repo.)
13. Git: develop and push only on branch `dimitri/exciting-faraday-oh69ke`; no pull request unless asked; every commit message ends with the two attribution lines in section 8.
14. "Be sure to reduce attack surfaces as much as possible but at no sacrifice of features."
15. "Could you clean up the GitHub repo after you are done with building the app, making the read me, etc. I don't want unnecessary files left inside." (Do this in task 25.)
16. "Remember keep requests at a bare minimum if going outside." (network use)
17. Never use the GitHub CLI `gh` except for search; GitHub actions go through the GitHub MCP tools.

## 5. Corrections log
| # | Claude did | The user wanted |
|---|---|---|
| 1 | Was doing careful checks/tests while building UI tasks | "For now, just build everything. Don't do any bug checks while building unless it is really essential … big bug fix at the very end of the task list." |
| 2 | Worked fast and wrote some rushed shortcuts (helper hacks, leftover unused lines) | "Please slow down. Don't use the slow down time to reason more. Just take things easy." (Shortcuts were then cleaned up.) |
| 3 | Was in the middle of tasks 4–6 when the Maven question came up | "Do this later on, just after you finished with 6 as a quick tryout try entering Maven with a new worker and reduce requests even further. Maybe try through a proxy?" (Done after task 6.) |
| 4 | Had planned to report every 3 tasks | For tasks 7–14: "Go extend do tasks 7, 8, 9, 10, 11, 12, 13, 14 then stop get back to me". For tasks 15–20: chose "Go, do more", then "once you are done with step 20 stop. Add the full APK under releases as a beta. We'll stop there for now." |
| 5 | Release APK was unsigned (no key, per rule 12) | User asked to "try Google Maven Central again for signing APK and try all other Maven Central alternative proxies". Answer: Maven was never the cause (all repos HTTP 200); the key was. User then chose a one-time key creation (rule 12 exception). |

## 6. Decisions
| Decision | Why |
|---|---|
| Manual DI (`di/AppContainer.kt`), not Hilt | Fewer moving parts, smaller APK, no codegen, AOT/R8-safe; in `docs/ARCHITECTURE.md` |
| compileSdk **37**, targetSdk **36**, minSdk **34** | Compose 1.12 + Material 3 1.5.0-rc01 refuse to build below API 37; behaviour is Android 16; explained in BUILDING.md "Versions" |
| Material 3 **1.5.0-rc01** (pre-release) | Spec §6A's one allowed exception |
| **License: PolyForm Strict 1.0.0** ("source-available"), not the spec's MIT | User chose "Keep PolyForm + donation" (the attached spec is an older version; the newer spec used in chat #1 had donation §6E and source-available wording) |
| Integrity check (new task 16): runs once, the first time a file is added; **warn and hold back** until user taps "Add anyway"; **balanced** strictness | User's answers: "Warn and hold back", "Balanced (Recommended)" |
| Repo cleanup scope: keep user + required docs | User's answer "User + required docs": keep README, LICENSE, BUILDING, SECURITY, CHANGELOG, NOTICE, CONTRIBUTING, DONATE, THIRD_PARTY_NOTICES, docs/ARCHITECTURE + docs/TESTING, .github files; remove handoff, VERSIONS.md (done: merged into BUILDING), build leftovers |
| VERSIONS.md merged into BUILDING.md "Versions" section and deleted | Cleanup decision |
| `material3-adaptive` dependency removed | Unused (nav uses `screenWidthDp`), rule 5 |
| Widget: Glance, colours via shared `colorSchemeFor()`; service publishes `WidgetSnapshot` into `AppContainer.widgetState`; `WidgetRefresher` debounced 250 ms; buttons via `PlayerConnection` (`command()` now returns `Job`, widget `join()`s) | Theme-following, no polling |
| `NowPlayingLayout(art, details)` extracted into `NowPlayingParts.kt` | So the centring UI test checks the real layout |
| `app/lint.xml` declares `UnsafeOptInUsageError` opt-in for `androidx.media3.common.util.UnstableApi` | Media3-documented; 219 false lint errors |
| Release signing key created outside the repo (rule 12 exception) | User's choice |
| Beta delivery: user picked "Tag it; workflow publishes" | But tag push is blocked in this session (section 8); signed APK + checksum were sent to the user instead |
| Untrusted media controllers get transport only; Now Playing is an overlay; Material Symbols Rounded drawables; package `io.github.butchecassidy.pocketbox`; sort keys = Collator collation-key BLOBs; ReplayGain via player volume (≤1.0); crossfade via helper ExoPlayer; bit-perfect = float output + mixer attributes | From chat #1, unchanged |
| `SupportConfig.DONATION_URL` stays the placeholder | Spec (newer version) requires placeholder until the owner fills it; reminder/About row hidden while it contains `OWNER/REPO` |

## 7. Changed / rejected
- compileSdk 36 → 37; Material 3 1.4.0 → 1.5.0-rc01; `media3-inspector` → own readers; Hilt → manual DI; `material-icons-extended` → Material Symbols drawables; Now Playing route → overlay; custom ReplayGain AudioProcessor → player volume; LRC `[hh:mm:ss]` dropped (`[mm:ss:xx]` = centiseconds)
- MIT (attached spec §10) → PolyForm Strict 1.0.0 (user decision)
- `VERSIONS.md` → section "Versions" in `BUILDING.md` (file deleted)
- `material3-adaptive` dependency → removed
- Licences screen label "Open-source licences" → "Third-party licences"
- Rule 12 "never create keystores" → one-time exception used 2026-11-11 (back in force)
- ❌ Routing Maven traffic through another proxy (session rules forbid routing around the mandatory proxy; also unnecessary: Google Maven, `repo.maven.apache.org`, `repo1.maven.org`, `maven-central.storage-download.googleapis.com` all return HTTP 200)
- ❌ `MediaButtonReceiver` in manifest (extra exported component)
- ❌ Running the ui-consistency `verifying` phase (it spawns an agent; rule 4). Self-checked against the checklist instead.

## 8. Data & facts (exact)

**Repo / git**
- Working dir `/home/user/PocketBox`; branch `dimitri/exciting-faraday-oh69ke`, pushed, HEAD `845a9f9`.
- Commits this chat: `38bd616` widget · `47a49a0` integrity check · `e36e5f2` baseline profile · `3896e01` tests · `54fc66f` docs/GitHub files · `845a9f9` lint pass. Earlier: `c736974` `7314441` `ddc6882` `bc57477` `4ed2c06` `f869d01` `e42e369` `c019f84` (handoff #1).
- Commit trailer (must end every commit):
  `Co-Authored-By: Claude Opus 5.5 <noreply@anthropic.com>`
  `Claude-Session: https://claude.ai/code/session_01JPHpnf1DxsdZEPT1yozkoQ`
- PR description ending (only if asked): `🤖 Generated with [Claude Code](https://claude.com/claude-code)` + blank line + `https://claude.ai/code/session_01JPHpnf1DxsdZEPT1yozkoQ`
- **Tag push is blocked from the cloud session** (`git push origin v1.0.0-beta.1` → "send-pack: unexpected disconnect … remote end hung up", 5 tries); only the branch can be pushed. GitHub MCP has no create-release/create-tag tool. No tag exists on GitHub. Local tag was deleted.

**Build (verified 2026-11-11)**
| Item | Value |
|---|---|
| Gradle / AGP / Kotlin / KSP | 9.9.1 (sha256 `d8a18a7a841dd5df825d35a685fad6f2d7f12ac0c280e57b67c27b4a520ba228`) / 9.5.1 / 2.4.21 / 2.3.13 |
| Compose BOM / material3 / glance / media3 / room | 2026.09.00 / 1.5.0-rc01 / 1.2.0 / 1.11.1 / 2.8.5 |
| baselineprofile plugin / benchmark / uiautomator / test runner / ext-junit | 1.5.0 / 1.5.0 / 2.4.1 / 1.7.0 / 1.3.0 |
| SDK | compileSdk 37, targetSdk 36, minSdk 34, build-tools 36.0.0, ABI `arm64-v8a` only |
| versionCode / versionName | 1 / 1.0.0 |
| Signed release APK | 6,012,090 bytes (unsigned build 6,003,898) |
| Debug APK | 50,410,764 bytes |
| Unit tests | **68 passing, 0 failing** (11 classes) |
| `lintRelease` | **0 errors, 60 warnings** (`abortOnError = true`) |
| Instrumented test | `NowPlayingCenteringTest` compiles; NOT run (no device/emulator in the cloud) |
| Memory | Full combined Gradle run got OOM-killed (exit 137, worker restart); run tasks separately with `--max-workers=2` |

**Lint warnings left (fix or justify in reviews)**
| Warning | Count / where |
|---|---|
| UnusedResources | 43 |
| UseKtx (`String.toUri`) | 9: ArtworkRepository.kt:83, DonationReminder.kt:82, LibraryScanner.kt:117/131/140/166/294, LyricsRepository.kt:24/28 |
| PluralsCandidate | 8: strings.xml:84; strings_library.xml:16,17,21,22,23,26; strings_settings.xml:20 |
| LocalContextResourcesRead | 3: DetailScreens.kt:273, LibraryScreen.kt:496, SettingsScreens.kt:518 |
| ConfigurationScreenWidthHeight | PocketBoxRoot.kt:118 |
| ModifierParameter | NowPlayingParts.kt:149 |
| OldTargetApi | build.gradle.kts:40 (targetSdk 36 is deliberate per spec) |
| ChromeOsAbiSupport | build.gradle.kts:45 (arm64 only is deliberate per spec) |
| ExportedService | AndroidManifest.xml:71 (PlaybackService; required export, callers filtered in `onConnect`) |

**Release signing key (created 2026-11-11, outside the repo, sent to the user)**
- File `pocketbox-release.jks`, PKCS12, RSA 4096, validity 10000 days, alias `pocketbox`, DN `CN=ButcheCassidy - The Nomadic Rebel, O=Pocket Box`, store password = key password = [secret removed: re-enter it] (in the user's `pocketbox-signing-secrets.txt`).
- Certificate SHA-256: `8E:0E:20:BD:52:8D:62:BF:D6:0A:D7:29:7B:24:F6:6B:B2:62:F2:7D:EE:FF:F5:85:03:A1:14:03:A8:F5:24:60`
- Verified: signed build via env vars `POCKETBOX_KEYSTORE_FILE/_PASSWORD`, `POCKETBOX_KEY_ALIAS/_PASSWORD`; apksigner "Verifies", v3 true, v2 false (apksig drops v2 for minSdk ≥ 28; v2 still enabled in config; BUILDING.md step 6 explains).
- The cloud container is temporary: the key in the scratchpad will be gone; the user holds the only copies.
- GitHub secrets the workflow needs: `POCKETBOX_KEYSTORE_BASE64`, `POCKETBOX_KEYSTORE_PASSWORD`, `POCKETBOX_KEY_ALIAS`, `POCKETBOX_KEY_PASSWORD` (values in the secrets file).

**Beta 1 sent to the user**
- `PocketBox-1.0.0-beta.1.apk`, SHA-256 `79b3f4970cfdf179fc94732c79436b42448b73b18899b3b64e25e75db97fa0bf`, built from `845a9f9` code, versionName shows 1.0.0, not debuggable.
- Permissions in that APK: `FOREGROUND_SERVICE`, `FOREGROUND_SERVICE_MEDIA_PLAYBACK`, `WAKE_LOCK`, `MODIFY_AUDIO_SETTINGS`, `io.github.butchecassidy.pocketbox.DYNAMIC_RECEIVER_NOT_EXPORTED_PERMISSION` (signature, from androidx.core).

**Release workflow `.github/workflows/release.yml`** (never run yet): on `push: tags: ["v*"]`; `actions/checkout@3d3c42e5aac5ba805825da76410c181273ba90b1 # v7.0.1`, `gradle/actions/wrapper-validation@8da46b539cd8fe6ab27ca75128919a18d757604c # v6.4.1`, `actions/setup-java@77dd5b2ce225ea11469928ac317850245a74ae78 # v6.2.0` (temurin 21), `gradle/actions/setup-gradle@8da46b5… # v6.4.1`; installs `platforms;android-37.0` + `build-tools;36.0.0`; runs `testDebugUnitTest`, `assembleRelease`; apksigner verify; `sha256sum`; `gh release create … --verify-tag`, `--prerelease` when the tag contains `-`; asset `PocketBox-<tag without v>.apk` + `.sha256`.

**Integrity check (task 16)**
- Code: `data/scan/integrity/IntegrityFindings.kt` (`IntegrityIssue`, `IntegrityFinding`, `Report`, `CodeSignatures`, `ImageEnds`), `IntegrityInspector.kt` (dispatch, ID3v2 frames, MPEG head/tail, `Footers`: ID3v1/APEv2/Lyrics3/ID3 footer), `ContainerChecks.kt` (FLAC blocks + CRC-8/16 tail, Ogg BOS streams + page-chain tail, MP4 boxes + track handlers, WAV/RF64/AIFF chunks + trailing data, `Crc`).
- Issues: EMBEDDED_FILE, PROGRAM_CODE, SCRIPT_IN_TAGS, NOT_A_PICTURE, DATA_AFTER_PICTURE, DATA_BEFORE_AUDIO, DATA_AFTER_AUDIO, HIDDEN_IN_PADDING, APPLICATION_DATA, UNKNOWN_BLOCK, EXTRA_STREAM, OVERSIZED, DAMAGED.
- Limits: LARGE_BLOCK 64 KB; scan cap 64 MB per region; 1 MB chunks; text > 1,048,576 chars = OVERSIZED; max 20 findings; MP3 head window 128 KB, head tolerance 4 KB, tail window 64 KB, chain ≥ 3 frames; FLAC head 64 KB, tail 256 KB, 16 CRC candidates; Ogg tail 128 KB; ≤ 16 Ogg BOS streams.
- Hooks: `ReadOptions.INSPECT` (`inspectPictures`), `TagObserver` in `RawTags`, `AudioFileReader.read(source, options, observer)`, `Mp3Reader.frameLength()`, `Id3Reader.removeUnsync` internal.
- Storage: Room entity `HeldFileEntity` table `held_files` (PK `documentUri`, FK folders CASCADE; findings one per line `ISSUE|where`; `approved`). DB still **version 1** (unreleased; schema JSON in `app/schemas/`).
- Scanner: new file (no index row) + not approved-same-version → `audioFiles.inspect()`; findings → held row, not added; unchanged waiting files skipped; approved + same size/mtime → added without check; held rows of deleted files removed; `ScanState.Finished(trackCount, added, removed, heldBack)`; `acknowledgeHeldBack()`; `addHeldFileAnyway(uri)`.
- UI: `ui/settings/HeldFilesScreen.kt` (`HeldFilesScreen`, route `Routes.HELD_FILES = "held"`; `HeldFilesNotice` dialog in `PocketBoxRoot` when not on Now Playing); Settings → Library row "Held-back songs" (`ic_shield`); strings in `strings_settings.xml` (plural `held_notice_title`).
- Tests: no false positives on all 7 ffmpeg fixtures; 11 tests in `IntegrityInspectorTest`.

**Other exact rules implemented (from #1)**: play counts after 30 s or 50 %; search debounce 60 ms; fuzzy ≤1 for 4–7 letters, ≤2 for 8+, none 1–3; art cache 128 MB (trim to 96 MB), memory LRU min(maxHeap/8, 64 MB); art buckets 96/192/384/768/1280 px; max embedded picture 16 MB; max `.lrc` 1 MB; max playlist 4 MB; scanner parallel 4, batch 64; crossfade 0–12 s; sleep fade 10 s; paused art 0.88; swipe commit 30 % or fling 1500 px/s; Now Playing art = min(84 % width, 45 % height) portrait. Donation reminder rules + the 6 required test cases are implemented and tested (`DonationReminderPolicyTest`, 7 tests).

**Sandbox**: Android SDK at `/opt/android-sdk` (platforms 36 + 37.0, build-tools 36.0.0, cmdline-tools 19.0); `local.properties` = `sdk.dir=/opt/android-sdk` (git-ignored); `~/.gradle/init.d/central-mirror.gradle.kts` (sandbox only). Cloud environment "PocketBox" setup script replaced 2026-11-11 with a Linux script (installs cmdline-tools `commandlinetools-linux-13114758_latest.zip` if missing, accepts licences, installs `platform-tools`, `platforms;android-36`, `platforms;android-37.0`, `build-tools;36.0.0`, writes `PocketBox/local.properties`); unproven until a fresh container.

**Spec text the remaining tasks audit against (from `PocketBox_Prompt.md`, word for word where quoted)**

§11 process steps 4–9:
- 4 "**Self-review, round 1 (bugs):** Re-read every file. Check for compile errors, missing imports, null-safety problems, threading and coroutine bugs, lifecycle leaks, MediaSession state bugs, and queue/shuffle/repeat edge cases: empty queue, one-song queue, removing the playing track, repeat-one combined with skip next, shuffle toggled mid-queue, a file deleted during playback, folder access revoked. Fix everything and list what you fixed."
- 5 "**Self-review, round 2 (GrapheneOS, devices and permissions):** Check every item in sections 2 and 3 against the code and the **merged** manifest. Check layouts at the screen sizes and densities of Pixel 8, 8a, 9 Pro XL and 10 Pro XL."
- 6 "**Self-review, round 3 (performance):** Check against section 7. Look for recomposition hot spots, main-thread input/output and image decoding costs."
- 7 "**Self-review, round 4 (security):** Audit against section 8 and the signing setup in section 9. Confirm no secrets are in the repo and that `.gitignore` covers them."
- 8 "**Tests:** Unit tests for queue, shuffle and repeat logic; quality classification; LRC parsing; and M3U path validation. Plus a manual test checklist I can follow on my Pixel (including Bluetooth headphones, a USB DAC, switching between light and dark, network off, and revoking folder access)."
- 9 "**Final report:** All permissions; every `// VERIFY VERSION` item; known limitations; estimated APK size; and the exact commands to build the signed APK and push the repo to GitHub for the first time (`git init`, commit, add remote, push, create a tag for the release)."

§2 Permissions: no runtime permissions; SAF only (`ACTION_OPEN_DOCUMENT_TREE` + `takePersistableUriPermission`), never `READ_MEDIA_AUDIO`/`READ_EXTERNAL_STORAGE`/`MANAGE_EXTERNAL_STORAGE`; Bluetooth via system, device name via `AudioManager.getDevices()` + `getProductName()`, no `BLUETOOTH_CONNECT` by default; no `POST_NOTIFICATIONS` unless justified; allowed install-time: `FOREGROUND_SERVICE`, `FOREGROUND_SERVICE_MEDIA_PLAYBACK`, `WAKE_LOCK` only if Media3 truly needs it; "Add a comment above every permission in the manifest explaining exactly why it exists. In the final report, list every permission and confirm nothing else is declared, including permissions merged in from libraries. Check the merged manifest and strip unwanted ones with `tools:node="remove"`." (Deviation to report: `MODIFY_AUDIO_SETTINGS` for bit-perfect.)

§3 GrapheneOS: never assume broad storage; revoked/moved folders → friendly re-pick prompt, never a crash; no dynamic code loading (DEX/plugins/native from storage); avoid custom native code; `android:memtagMode="sync"` in the **debug** manifest; no reflection-heavy tricks, R8 rules correct (AOT); no sensors or contacts; network off must break nothing.

§6A centring check: art, title, chips, progress bar (same width as art, edges aligned), control group (play/pause at exact horizontal centre), secondary row: one vertical centre line; "Verify the centering at Pixel 8, 8a, 9 Pro XL and 10 Pro XL widths. Write a Compose screenshot or UI test that checks the art, progress bar and play button share the same center X position (within 1 dp)." (Test exists: `app/src/androidTest/.../ui/NowPlayingCenteringTest.kt`, sizes 412×820, 412×830, 448×900 dp.) Accessibility: TalkBack labels, 48 dp targets, WCAG AA in every theme and custom colour, largest font size.

§7 Performance: Baseline Profiles (startup + scrolling), startup profile, R8 full mode, resource shrinking, only `arm64-v8a`; cold start < 500 ms on Pixel 8; 120 Hz scrolling on 20,000 tracks; scan on `Dispatchers.IO` in batches with Room transactions and indexes; lazy lists with stable keys; avoid recomposition (stable models, `derivedStateOf`, `remember`); decode images at display size; LRU cap; no polling loops; seek bar tied to frame timing only while visible; "Release the player and wake locks when idle. Use minimal battery when paused."; animations don't allocate in hot paths; no `GlobalScope`, no leaked contexts/bitmaps; lifecycle-correct service; APK under about 10 MB (report the estimate). Search < 50 ms on 20,000 tracks (§6D).

§8 Security: `android:allowBackup="false"` or strict `dataExtractionRules`; "Export only the media service and the widget receiver, each with correct permissions and intent filters. Check every `exported` flag."; treat all files as untrusted, cap embedded art and `.lrc`, handle malformed tags/corrupt/truncated/huge files without crashing; `.m3u` imports only inside granted folders, no path traversal; strip `Log.*` in release with R8, never log paths or library contents; no WebView, no `JavascriptInterface`, `FLAG_IMMUTABLE` on every PendingIntent; `SECURITY.md` (threat model, permission rationale, reporting via GitHub Security Advisories).

§9 Signing: one command → signed release APK; signing config reads `keystore.properties` **or** env vars, never hard-coded; `keystore.properties`, `*.jks`/`*.keystore` in `.gitignore`; `keystore.properties.example` with placeholders; v2 and v3 enabled; release minified, shrunk, signed, versioned (1.0.0); optional workflow from encrypted secrets on version tag, attaches APK + SHA-256 to a Release, only official `actions/*` and `gradle/*` pinned to full SHAs; `BUILDING.md` steps 1–6 (JDK + cmdline tools; keytool + why back up; `./gradlew assembleRelease`; where the APK is; installing on GrapheneOS; `apksigner verify --print-certs`).

§10 README must include (all present now): name + tagline; who it's for; no streaming/no internet/no accounts/ads/tracking/analytics/minimal permissions and why Bluetooth needs none; features of §5, 6, 6A, 6B, 6C, 6D grouped; "Runs on" line; Supported devices table (Device | Series | Status) incl. later non-Fold row, Fold note, why Pixel 8 minimum, Pixel 7 and older unsupported; permissions table; formats; "Saddle up" install with SHA-256; building (BUILDING.md); privacy/security (SECURITY.md); Known limitations; Screenshots placeholders; License; "Made by **ButcheCassidy - The Nomadic Rebel**". User's addition: the integrity check is described in the README (done) and must stay.

## 9. People, terms & names
- **People:** ButcheCassidy - The Nomadic Rebel = owner/author (the user).
- **Terms & nicknames:** "Go" = proceed with the next batch; "the prompt" / "Pocketbox Prompt.md" = `PocketBox_Prompt.md`; "big bug fix" = review tasks 21–24; "held back" = file kept out by the integrity check; "Add anyway" = user's override.
- **Names in use:** app "Pocket Box"; package `io.github.butchecassidy.pocketbox`; DB `pocketbox.db`; DataStore `settings`; test tags `np_art`, `np_progress`, `np_play`; route `held`; widget classes `NowPlayingWidget`, `NowPlayingWidgetReceiver`, `TogglePlayAction`, `NextAction`, `PreviousAction`; widget XML `res/xml/now_playing_widget_info.xml`; module `:baselineprofile` (`BaselineProfileGenerator`, `StartupBenchmark`, `Journeys.kt`, `TARGET_PACKAGE`); seed profile `app/src/main/baselineProfiles/baseline-prof.txt`; fixtures `app/src/test/resources/fixtures/tone.{mp3,flac,ogg,opus,m4a,wav,aiff}` + `cover.png`; beta name `PocketBox-1.0.0-beta.1.apk`.

## 10. Work state
| Item | Status | Location | Notes |
|---|---|---|---|
| 1–14 | done | see `docs/ARCHITECTURE.md` | from #1 |
| 15 Glance widget | done | `widget/*`, manifest receiver, `res/xml/now_playing_widget_info.xml` | exported receiver has no `android:permission` → decide in review 4 |
| 16 Integrity check | done | `data/scan/integrity/*`, scanner, `HeldFilesScreen.kt` | new task added mid-list by the user |
| 17 Baseline profile | done | `baselineprofile/`, seed profile | real profile needs a phone (`./gradlew :app:generateBaselineProfile`) |
| 18 Tests | done | `app/src/test/...` (68), `app/src/androidTest/.../NowPlayingCenteringTest.kt` | centring test not run (no device) |
| 19 Docs & GitHub files | done | README, LICENSE, BUILDING, SECURITY, CHANGELOG ("1.0.0 - Unreleased"), CONTRIBUTING, NOTICE, THIRD_PARTY_NOTICES, DONATE, `.github/FUNDING.yml` (all commented), issue forms + `config.yml`, `.github/workflows/release.yml`, `docs/screenshots/README.md`, `.editorconfig` | |
| 20 Build + lint + tests | done | | 0 lint errors, 60 warnings |
| Beta release | APK sent, Release NOT created | user's files | tag push blocked; see section 11 step 6 |
| 21 Review 1: bugs | not started | | |
| 22 Review 2: GrapheneOS/devices/manifest | not started | | |
| 23 Review 3: performance | not started | | |
| 24 Review 4: security & signing | not started | | security focus (user preference) |
| 25 Manual checklist + cleanup + final report | not started | `docs/TESTING.md` (to write) | |

Every source file starts with: `// Copyright (c) 2026 ButcheCassidy - The Nomadic Rebel. Licensed under PolyForm Strict 1.0.0. See LICENSE.` (XML/YAML: same text as a comment.)

## 11. Next steps
1. **Next action:** On the user's "Go", start task 21 (review round 1: bugs): re-read every file against spec §11 step 4 (section 8 above) and fix. Known suspects: `Crossfader` `ownTransition` flag and scheduling after seeks; `PlayerConnection.controller()` reconnect race; `AiffExtractor` header/seek; `PlaybackService.rebuildPlayer` listener/session handover (also `widgetPublisher` re-attached); queue `SwipeToDismissBox` state reuse and keys; LibraryScreen early `return` inside composables; `DonationReminderHost` placement; "Run setup again" navigation; unused import `ShuffleSequence` in `PlaybackService.kt`; `HeldFilesScreen` `EmptyState` uses `fillMaxSize` inside a scrolling column; integrity check flags free-format MP3 or truncated downloads as DAMAGED/DATA_AFTER_AUDIO (decide if acceptable); `NowPlayingSheets.kt` import order.
2. Task 22 (GrapheneOS/devices/permissions): merged release manifest exported components: `MainActivity`, `PlaybackService`, `NowPlayingWidgetReceiver`, `androidx.media3.session.BluetoothValidationActivity` (inspect/remove), `androidx.glance.appwidget.GlanceRemoteViewsService` (BIND_REMOTEVIEWS), WorkManager `SystemJobService` (BIND_JOB_SERVICE, needed by Glance), WorkManager `DiagnosticsReceiver` (DUMP, remove); `ProfileInstallReceiver` already removed in `src/release/AndroidManifest.xml` (keep it in the `nonMinifiedRelease`/`benchmarkRelease` variants for profile generation). Check §2/§3 items and layouts at Pixel 8, 8a, 9 Pro XL, 10 Pro XL.
3. Task 23 (performance): `PlayerConnection` keeps a `MediaController` bound forever (also from widget taps) → service may never stop when idle: release when no UI/widget needs it; integrity inspector loads every embedded picture (≤16 MB each) once per new file; widget `produceState` art loading; the lint warnings in section 8; verify search < 50 ms claim reasoning; seek bar frame loop only while visible.
4. Task 24 (security & signing): §8 + §9 audit; decide `android:permission` on the widget receiver (e.g. `android.permission.BIND_APPWIDGET`, test-safe?) and document; confirm every PendingIntent immutable (Glance-created too); R8 keep rules (Glance keeps `ActionCallback` subclasses); no secrets in repo (`git log -p` scan for passwords, `.jks`); `.gitignore` coverage; workflow review.
5. Task 25: write `docs/TESTING.md` (manual checklist incl. Bluetooth headphones, USB DAC, light/dark switching, network off, revoking folder access, widget, integrity hold-back + "Add anyway", beta upgrade path); **repo cleanup**: delete tracked `docs/handoff-pocketbox.md`; remove `.gitignore` lines that no longer apply (`ARCHITECTURE.md`, `LICENSES.md`, `reference-repos`, possibly `PocketBox_Prompt.md`/`handoff-pocketbox.md` kept so they can't be committed); ensure no build leftovers tracked; update CHANGELOG/ARCHITECTURE if reviews changed things; final report per spec §11 step 9 (permissions incl. `MODIFY_AUDIO_SETTINGS` deviation and the signature permission; "no `// VERIFY VERSION` items: every version was checked"; known limitations; APK ~6.0 MB; exact commands: build signed APK, `git init`/commit/add remote/push/tag for a first push; note PolyForm vs spec's MIT/GPL-3.0 note; reminders: fill `DONATION_URL` in `SupportConfig.kt` and `.github/FUNDING.yml`, back up the keystore, add the 4 GitHub secrets).
6. Beta on GitHub Releases (owner action, cloud session can't push tags): either upload `PocketBox-1.0.0-beta.1.apk` + `.sha256` via Releases → Draft a new release → tag `v1.0.0-beta.1` (target branch `dimitri/exciting-faraday-oh69ke`) → "Set as a pre-release" → Publish; or add the 4 secrets and push the tag from a computer (`git tag v1.0.0-beta.1` then `git push origin v1.0.0-beta.1`) so the workflow publishes it. If the workflow and a manual release both run for the same tag, `gh release create` fails (release exists).

## 12. Open questions ⚠️
- Did the user upload beta 1 to GitHub Releases, and did they add the 4 signing secrets to the repository? (Affects whether the release workflow has been exercised.)
- Should the cloud session's push restriction be checked again in the new chat (a fresh session may allow tag pushes)? Candidate: try once `git push origin v1.0.0-beta.1`; if refused, leave it to the owner.
- After the reviews, should a beta 2 be built? The key isn't in the new container; the user would have to provide secrets in the repo (workflow) or build locally per BUILDING.md.

## 13. Re-attach checklist
- [ ] `PocketBox_Prompt.md`: the full spec (git-ignored, not in the repo). Note: the attached copy is an older version (MIT license, no donation §6E); the PolyForm + donation decisions in section 6 override it.
- [ ] This file `handoff-pocketbox.md`.
- [ ] (Optional, only if a newly signed build is needed) the user's `pocketbox-release.jks` + `pocketbox-signing-secrets.txt`. Prefer GitHub secrets over attaching them to a chat.

---
<sub>Audit: 13/13 sections · 17 rules · 5 corrections · 150+ data points · secrets removed: yes (keystore password) · generated by active-memory</sub>
