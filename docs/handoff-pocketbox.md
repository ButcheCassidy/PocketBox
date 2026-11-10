# active-memory handoff: Pocket Box (offline Material 3 Expressive music player for GrapheneOS / Pixel 8+)

**Handoff #1** · 2026-11-10 · Lineage: #1 (2026-11-10): planned the app; built tasks 1–14 of 24 (scaffold, data, scanner, playback, effects, theme, full UI); everything compiles, 42 unit tests pass, pushed.

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
- **Done looks like:** a repository the user can push to GitHub and build into a signed APK by following `BUILDING.md`, with no code changes; plus the final report described in prompt §11 step 9.
- **Why it matters / context:** The user doesn't code; output must be complete, buildable without edits, and explained in plain English. The spec calls it "a one-shot request".

## 2. About the user (as relevant to this work)
- Not a programmer ("I don't code"); judges results by the spec, the README and whether it builds.
- Author credit everywhere must be exactly **ButcheCassidy - The Nomadic Rebel**.
- GitHub: repository `ButcheCassidy/PocketBox` (the session's GitHub scope shows it as `butchecassidy/pocketbox`).
- Uses Claude Code cloud sessions; has plugins installed (ui-consistency, active-memory, design, playwright, agent-army).
- Personal preference: "When we are developing apps together Especially Mac Apps Please in the later stages focus on security."
- Target devices: Google Pixel 8 and later (no Folds), GrapheneOS (Android 16 based) or stock Android 14+.

## 3. Style & communication
- **Language:** English.
- **Tone:** direct, plain English, no corporate fluff; README gets "a subtle, light hint of the Wild West" (tasteful, never cheesy).
- **Reply length:** "Eliminate all conversational filler be concise and short in your response. Only add detail to execution steps and the end reply only."
- **Formatting:** Markdown; short status reports per batch of tasks; tables are fine.
- **Working style:** Work through the task list in order; stop and report after the agreed batch; wait for "Go". User asked to "slow down … just take things easy" — work steadily, don't rush, don't use the extra time to over-reason.
- **Avoid:** conversational filler; research tangents; using agents/sub-agents; bug hunting mid-build (save for the end).

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
10. From the prompt: "Credit the app everywhere as **ButcheCassidy - The Nomadic Rebel** … Never use any other name for the author."
11. From the prompt: no `TODO`, no "implement here", no placeholders, no "rest of code unchanged" in delivered code; don't describe the project as "open source" anywhere — use "source-available".
12. From the session setup (user's own setup script `CLAUDE.md` for the macOS workspace): "Never create, read or print keystores or `keystore.properties`. The owner handles signing." (Followed: no keystore was ever created.)
13. Git: develop and push only on branch `dimitri/exciting-faraday-oh69ke`; do not open a pull request unless asked; commit messages end with the two attribution lines in section 8.

## 5. Corrections log
| # | Claude did | The user wanted |
|---|---|---|
| 1 | Was doing careful checks/tests while building UI tasks | "For now, just build everything. Don't do any bug checks while building unless it is really essential … big bug fix at the very end of the task list." |
| 2 | Worked fast and wrote some rushed shortcuts (helper hacks, leftover unused lines) | "Please slow down. Don't use the slow down time to reason more. Just take things easy." (Shortcuts were then cleaned up.) |
| 3 | Was in the middle of tasks 4–6 when the Maven question came up | "Do this later on, just after you finished with 6 as a quick tryout try entering Maven with a new worker and reduce requests even further. Maybe try through a proxy?" (Done after task 6; result in section 8.) |
| 4 | Had planned to report every 3 tasks | For tasks 7–14: "Go extend do tasks 7, 8, 9, 10, 11, 12, 13, 14 then stop get back to me" |

## 6. Decisions
| Decision | Why |
|---|---|
| Manual dependency injection (`di/AppContainer.kt`), not Hilt | Fewer moving parts, smaller APK, no codegen, AOT/R8-safe; documented in `docs/ARCHITECTURE.md` |
| compileSdk **37**, targetSdk **36**, minSdk **34** | Compose 1.12 + Material 3 1.5.0-rc01 refuse to build below API 37; runtime behaviour still Android 16 (documented in `VERSIONS.md`) |
| Material 3 **1.5.0-rc01** (pre-release) | Prompt §6A's one allowed exception; stable 1.4.0 lacks ButtonGroup, wavy progress, LoadingIndicator, MaterialShapes, ToggleButton |
| Own size-capped tag/header readers; dropped `media3-inspector` | Faster scanning, exact fields, hostile-file safety; Android `MediaMetadataRetriever` is the fallback |
| Built-in `AiffExtractor` | Media3 has no AIFF extractor |
| Declare `MODIFY_AUDIO_SETTINGS` (normal, install-time, no prompt) | Required by `AudioManager.setPreferredMixerAttributes()` for the bit-perfect USB mode the prompt asks for; flagged as a deviation from the prompt's "allowed install-time permissions" list |
| ReplayGain applied through player volume (never above 1.0) | Exact timing at track changes, never clips; limitation: quiet tracks aren't boosted |
| Crossfade via a short-lived helper ExoPlayer sharing the audio session | ExoPlayer has no native crossfade; gapless untouched when crossfade = 0 |
| Bit-perfect = float output, no processing, mixer attributes matched per track, output reopened on format change | Only public API route; status shown honestly (Active / Unsupported / no USB) |
| Untrusted media controllers get transport only (no queue, no browsing) | Privacy; system UI, notification, Bluetooth and own app are trusted |
| Now Playing is an overlay over the app (not a nav route) | Shared-element art from the mini player; library keeps its place |
| Bottom bar = Library · Search · Settings; library tabs are a scrollable tab row | 6 library tabs don't fit a bottom bar |
| Adaptive nav chosen by `screenWidthDp >= 600` | Simple, no extra API |
| Strings in `res/values/strings*.xml` (5 files) | Production practice |
| Icons: Material Symbols Rounded vector drawables downloaded from fonts.gstatic.com (Apache 2.0), not `material-icons-extended` | Prompt wants Material Symbols Rounded; smaller APK |
| `applicationId` / package `io.github.butchecassidy.pocketbox` | Matches the GitHub owner |
| Sort keys = `java.text.Collator` (Locale.ROOT, PRIMARY) collation-key BLOBs stored in Room, digits zero-padded to 12 | DB can sort 20k tracks by byte compare exactly like Collator |
| Album grouping: album + album-artist tag, else album + folder (disc folders "CD1/Disc 2" merge up); multi-artist untagged albums → "Various Artists" | Prompt §6C: compilations don't scatter |
| `SupportConfig.DONATION_URL` left as the placeholder | Prompt requires placeholder until owner fills it; dialog/About row hidden while it contains `OWNER/REPO` |
| No keystore created; signing config reads `keystore.properties` or env vars | User's rule #12; release currently builds unsigned |

## 7. Changed / rejected
- compileSdk 36 → 37 (library requirement; targetSdk stays 36)
- Material 3 1.4.0 → 1.5.0-rc01 (Expressive components)
- `media3-inspector` dependency → removed (own readers)
- Hilt → manual DI
- `material-icons-extended` → Material Symbols Rounded drawables
- Now Playing route `nowplaying` → overlay (`NowPlayingState` in `PocketBoxRoot`)
- Custom ReplayGain AudioProcessor → player volume (VolumeMixer)
- LRC hours format `[hh:mm:ss]` → dropped; `[mm:ss:xx]` means centiseconds
- ❌ Routing Maven traffic through another proxy (session rules forbid routing around the mandatory proxy)
- ❌ `MediaButtonReceiver` in manifest (would be an extra exported component; resumption works via the browser service)
- ⚠️ `androidx.compose.material3.adaptive` is still declared in `app/build.gradle.kts` and `libs.versions.toml` but **unused** (nav uses `screenWidthDp`) → remove in the review (rule #5)

## 8. Data & facts (exact)

**Repo / git**
- Working dir: `/home/user/PocketBox`; branch `dimitri/exciting-faraday-oh69ke` (pushed to origin).
- Commits so far: `c736974` plan+versions · `7314441` scaffold · `ddc6882` data/scanner/domain · `bc57477` playback/effects/output · `4ed2c06` theme · `f869d01` full UI · (+ architecture update commit).
- Commit trailer lines (must end every commit):
  `Co-Authored-By: Claude Opus 5.5 <noreply@anthropic.com>`
  `Claude-Session: https://claude.ai/code/session_01JPHpnf1DxsdZEPT1yozkoQ`
- PR description ending (only if a PR is ever asked for): `🤖 Generated with [Claude Code](https://claude.com/claude-code)` + blank line + session URL.

**Build**
| Item | Value |
|---|---|
| Gradle wrapper | 9.9.1, `distributionSha256Sum=d8a18a7a841dd5df825d35a685fad6f2d7f12ac0c280e57b67c27b4a520ba228` |
| AGP | 9.5.1 (built-in Kotlin) |
| Kotlin / Compose compiler plugin | 2.4.21 |
| KSP | 2.3.13 (plugin ID mapped to `com.google.devtools.ksp:symbol-processing-gradle-plugin` in `settings.gradle.kts`) |
| Compose BOM | 2026.09.00 |
| material3 | 1.5.0-rc01 |
| graphics-shapes | 1.1.0 |
| activity-compose | 1.13.0 |
| navigation-compose | 2.10.2 |
| lifecycle | 2.11.0 |
| core-ktx | 1.19.1 · core-splashscreen 1.2.0 |
| media3 (exoplayer, session, common) | 1.11.1 |
| room | 2.8.5 |
| datastore-preferences | 1.3.0 |
| documentfile / palette | 1.1.0 / 1.1.0 |
| glance appwidget / material3 | 1.2.0 |
| profileinstaller | 1.4.1 · baselineprofile plugin 1.5.0 · benchmark 1.5.0 |
| adaptive (unused) | 1.3.0 |
| coroutines | 1.11.0 · collections-immutable 0.5.2 · junit 4.13.2 |
| SDK | compileSdk 37, targetSdk 36, minSdk 34, build-tools 36.0.0, ABI `arm64-v8a` only |
| versionCode / versionName | 1 / 1.0.0 (debug suffix `-debug`, appId suffix `.debug`) |
| Release APK (unsigned, R8 + shrink) | 5,365,739 bytes (~5.4 MB) |
| Debug APK | 50,260,795 bytes |
| Unit tests | 42 passing, 0 failing |

**Permissions in merged release manifest (app's own):** `FOREGROUND_SERVICE`, `FOREGROUND_SERVICE_MEDIA_PLAYBACK`, `WAKE_LOCK`, `MODIFY_AUDIO_SETTINGS`; plus `io.github.butchecassidy.pocketbox.DYNAMIC_RECEIVER_NOT_EXPORTED_PERMISSION` (signature permission merged from androidx.core). Explicitly stripped with `tools:node="remove"`: INTERNET, ACCESS_NETWORK_STATE, ACCESS_WIFI_STATE, READ/WRITE_EXTERNAL_STORAGE, READ_MEDIA_AUDIO, MANAGE_EXTERNAL_STORAGE, POST_NOTIFICATIONS, BLUETOOTH_CONNECT, RECEIVE_BOOT_COMPLETED.

**Exported components to resolve in review round 2/4** (found in merged release manifest at task 3): `MainActivity` (launcher, required), `PlaybackService` (required), `androidx.media3.session.BluetoothValidationActivity` (exported — inspect/remove), `androidx.glance.appwidget.GlanceRemoteViewsService` (BIND_REMOTEVIEWS), WorkManager `SystemJobService` (BIND_JOB_SERVICE, needed by Glance), WorkManager `DiagnosticsReceiver` (DUMP — remove). `ProfileInstallReceiver` already removed in `src/release/AndroidManifest.xml`.

**Maven tryout result (after task 6):** fresh Gradle home, `--no-daemon --max-workers=1 -Dorg.gradle.parallel=false`, no mirror: ~55 requests OK then HTTP 429 from Maven Central (Cloudflare) on this sandbox's egress IP; session proxy showed no failures/queueing. `repo1.maven.org` works. Sandbox-only workaround kept: `~/.gradle/init.d/central-mirror.gradle.kts` (redirects `repo.maven.apache.org` → `https://repo1.maven.org/maven2/`). Not in the repo.

**Sandbox environment:** Android SDK installed by hand at `/opt/android-sdk` (cmdline-tools `commandlinetools-linux-13114758_latest.zip`, `platform-tools`, `platforms;android-36`, `platforms;android-37.0`, `build-tools;36.0.0`); `local.properties` = `sdk.dir=/opt/android-sdk` (git-ignored). The cloud environment "PocketBox" setup script was replaced with a Linux version (env vars `ANDROID_HOME`/`ANDROID_SDK_ROOT=/opt/android-sdk`) — unproven until a fresh session; it installs platform 36 only, so a new session must also install `platforms;android-37.0`. Java 21 present. ffmpeg/ffprobe available (used to verify tag readers on 9 real files; all matched ffprobe).

**Donation reminder (prompt §6E):** show if ≥30 days since first launch AND ≥7 distinct play days in the last 30; "Maybe later" → once more after 60 days then never; "No thanks"/"Donate" → never; hidden while URL contains `OWNER/REPO`; URL must start with `https://github.com/`. Placeholder: `const val DONATION_URL = "https://github.com/OWNER/REPO/blob/main/DONATE.md"` in `app/src/main/java/io/github/butchecassidy/pocketbox/SupportConfig.kt`. Tests required: Day 29 → no; Day 30 with 6 play days → no; Day 34 with 7 play days in last 30 → yes; "Maybe later" + 60 days → shows once more; "No thanks" → never; placeholder URL → never.

**Other exact rules implemented:** play counts after 30 s or 50 % (whichever first); search debounce 60 ms; fuzzy distance ≤1 for 4–7 letters, ≤2 for 8+, none for 1–3; art cache disk limit 128 MB (trim to 96 MB), memory LRU min(maxHeap/8, 64 MB); art size buckets 96/192/384/768/1280 px; max embedded art/picture read 16 MB; max `.lrc` 1 MB; max playlist file 4 MB; scanner parallel reads 4, batch 64; crossfade 0–12 s; sleep fade 10 s; paused art scale 0.88; swipe commit 30 % or fling 1500 px/s; Now Playing art = min(84 % width, 45 % height) portrait.

## 9. People, terms & names
- **People:** ButcheCassidy - The Nomadic Rebel = owner/author (the user).
- **Terms & nicknames:** "Go" = proceed with the next batch of tasks; "the prompt" / "Pocketbox Prompt.md" = `PocketBox_Prompt.md` spec; "big bug fix" = review tasks 20–23.
- **Names in use:** app "Pocket Box"; package `io.github.butchecassidy.pocketbox`; DB file `pocketbox.db`; DataStore file `settings`; test tags `np_art`, `np_progress`, `np_play`; branch `dimitri/exciting-faraday-oh69ke`; UI checklist kept outside the repo (ui-consistency plugin rule).

## 10. Work state
| Item | Status | Location | Notes |
|---|---|---|---|
| 1 Plan | done | `docs/ARCHITECTURE.md` | updated after task 14 |
| 2 Dependencies | done | `VERSIONS.md`, `gradle/libs.versions.toml` | remove unused `adaptive` later |
| 3 Scaffold | done | Gradle files, manifests (main/debug/release), `proguard-rules.pro`, icons, `.gitignore`, `keystore.properties.example` | |
| 4 Data layer | done | `data/db/*`, `data/prefs/SettingsRepository.kt` | Room v1, schema in `app/schemas/` |
| 5 Scanner | done | `data/scan/*`, `data/scan/tags/*`, `data/art/*`, `data/lyrics/*`, `data/repo/*` | |
| 6 Domain rules | done | `domain/sort`, `domain/release`, `domain/model`, `domain/lyrics`, `domain/playlist` | tests in `app/src/test/.../domain` |
| 7 Playback service | done | `playback/*` | |
| 8 Effects & output | done | `playback/AudioEffectsController.kt`, `playback/OutputMonitor.kt`, `domain/audio/*`, `data/repo/EqPresetRepository.kt` | |
| 9 Theme | done | `ui/theme/*`, `ui/theme/color/*` | WCAG tests pass |
| 10 UI shell | done | `ui/navigation/*`, `ui/components/*`, `MainActivity.kt`, `MainViewModel.kt` | |
| 11 Library screens | done | `ui/library/*`, `ui/detail/DetailScreens.kt` | |
| 12 Search | done | `domain/search/SearchText.kt`, `data/repo/SearchRepository.kt`, `ui/search/*` | no search tests yet |
| 13 Now Playing | done | `ui/nowplaying/*` | |
| 14 Onboarding/Settings/About/Donation | done | `ui/onboarding/*`, `ui/settings/*`, `ui/support/*`, `SupportConfig.kt`, `domain/support/*` | donation tests not written yet |
| 15 Glance widget | not started | — | exported receiver + `appwidget-provider` XML; theme-following |
| 16 Baseline profile module | not started | — | re-add `include(":baselineprofile")` in settings and `baselineProfile(project(":baselineprofile"))` in app deps |
| 17 Tests | partial | `app/src/test/...` (42) | still need: donation timing (6 cases), search ranking/fuzzy, tag readers with tiny committed fixtures, Compose centering test (art/progress/play centre X within 1 dp) |
| 18 Repo docs & GitHub files | not started | — | README (Wild-West touch, all required sections), LICENSE (PolyForm Strict 1.0.0 full text + `Required Notice: Copyright (c) 2026 ButcheCassidy - The Nomadic Rebel`), NOTICE.md, SECURITY.md, BUILDING.md, CHANGELOG.md, CONTRIBUTING.md, THIRD_PARTY_NOTICES.md, DONATE.md, `.github/FUNDING.yml`, issue templates, `docs/screenshots/README.md`, `.github/workflows/release.yml` (actions pinned to full SHAs), `.editorconfig` |
| 19 Full build + lint + tests | not started | — | lint never run yet |
| 20–23 Review rounds | not started | — | see section 11 for known suspects |
| 24 Manual checklist + final report | not started | — | `docs/TESTING.md` |

Every source file starts with: `// Copyright (c) 2026 ButcheCassidy - The Nomadic Rebel. Licensed under PolyForm Strict 1.0.0. See LICENSE.` (XML files use the same text in `<!-- -->`.)

## 11. Next steps
1. **Next action:** On the user's "Go", start task 15: the Jetpack Glance home-screen widget (follows the chosen theme; play/pause/next/previous; exported `GlanceAppWidgetReceiver` with `APPWIDGET_UPDATE` filter; `FLAG_IMMUTABLE` PendingIntents), then tasks 16 and 17 (report after each batch of three unless told otherwise).
2. Task 18 docs, task 19 full build + lint + tests.
3. Tasks 20–23 big bug fix and reviews (security focus per user preference). Known suspects to check: Crossfader `ownTransition` flag and scheduling after seeks; `PlayerConnection.controller()` reconnect race; `AiffExtractor` header/seek; `PlaybackService.rebuildPlayer` listener/session handover; queue `SwipeToDismissBox` state reuse and keys; LibraryScreen early `return` inside composables; `DonationReminderHost` placement; "Run setup again" navigation; merged-manifest exported components (section 8); remove unused `material3-adaptive`; run Android lint; verify R8 keeps nothing reflective; signing config with a test env (without creating a keystore in the repo).
4. Task 24: `docs/TESTING.md` manual checklist (Bluetooth headphones, USB DAC, light/dark switching, network off, revoking folder access) and the final report (all permissions incl. the `MODIFY_AUDIO_SETTINGS` deviation; no `// VERIFY VERSION` items because every version was checked; known limitations; APK size; reminder to fill `DONATION_URL` and `.github/FUNDING.yml`; exact commands to build the signed APK and push/tag the repo).

## 12. Open questions ⚠️
- None blocking. Owner actions to remind about at the end: fill in `DONATION_URL` and `.github/FUNDING.yml`; create and back up a keystore (BUILDING.md).

## 13. Re-attach checklist
- [ ] `PocketBox_Prompt.md`: the full spec every task follows (it is not in the repo; it is git-ignored by design).
- [ ] The repository itself (branch `dimitri/exciting-faraday-oh69ke` on GitHub, or the zip `PocketBox-tasks-1-14.zip`).

---
<sub>Audit: 13/13 sections · 13 rules · 4 corrections · 70+ data points · secrets removed: none found · generated by active-memory</sub>
