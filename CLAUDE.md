# Nocturne (native Android)

Native Kotlin/Compose port of an AI Studio web app. The original export lives locally in `../baseline/` (git-ignored, untouched; it is the design/functionality reference).
Bedside music player + Audiobookshelf "last played" widget + home-screen (launcher) app. Target: OnePlus 3, landscape first, portrait supported. `minSdk 26`, `targetSdk 34`, application id `app.nocturne.bedside`.

## Build / test
- JDK 17 is required (the system only has a JRE). Gradle picks it up from `org.gradle.java.home` in `~/.gradle/gradle.properties`; the Android SDK is at `~/Android/Sdk` (see `local.properties`).
- `./gradlew assembleDebug` -> `app/build/outputs/apk/debug/app-debug.apk`
- `./gradlew testDebugUnitTest` -> JVM unit tests. `NOCTURNE_SMB_TEST=host:port` also enables `SmbIntegrationTest` (needs an SMB server with share `music`, user `test`, password `secret` containing a folder `Loose`).
- `./gradlew connectedDebugAndroidTest` -> Room/reconcile tests on an emulator or device. **This uninstalls the app afterwards (wipes data and the home-app role).**
- `./gradlew lintDebug` must stay at 0 errors.
- Release: `./gradlew assembleRelease` signs with the keystore named by `NOCTURNE_KEYSTORE`, `NOCTURNE_KEYSTORE_PASSWORD`, `NOCTURNE_KEY_ALIAS`, `NOCTURNE_KEY_PASSWORD` in `~/.gradle/gradle.properties`. Never put these or the keystore in the project.

## Rules on volume
- Never change Android's media volume (shared with the ABS app). The in-app volume is a gain only; the status strip warns if the device volume is 0.

## Layout (`app/src/main/java/app/nocturne`)
- `playback/` `PlaybackEngine` (app-scoped ExoPlayer owner: queue, shuffle, sleep timer, volume, retry), `PlaybackService` (Media3 session/notification), pure logic: `ShuffleEngine`, `VolumeCurve`, `SleepTimer`.
- `library/` `LibraryManager` (scans, rescan loop, tag reading), `SmbShareProvider` (smbj connection), `SmbDataSource` (ExoPlayer ranged reads), `SmbReadAhead` (block buffer, essential), `SmbScanner`, `LocalScanner` (MediaStore + SAF), `MetadataEnricher`, `CoverLoader`, `Reconciler`.
- `data/` Room (`Track`, `TrackDao`), DataStore settings, `SecretStore` (encrypted SMB/ABS passwords).
- `abs/` Audiobookshelf REST client + repository. `launcher/` app list, default-launcher settings, ABS launch.
- `ui/` Compose screens. **All colours come from `ui/theme/NocturneColors` tokens; never use literal colours in components.**

## Sources
- `tracks.source` is a source id: `local` (device), `samba` (the main share, original ids/URIs, always merged) or `smb-xxxxxxxx` (an added share). Anything that is not `local` is Samba (`Source.isSmb`).
- Track ids: main `smb:<path>`, added `smb:<shareId>:<path>`. Added shares' URIs carry `?src=<shareId>` (missing = main) so playback, tags and covers use the right connection (`SmbUri`, `RoutingDataSource`).
- Settings: main share in its own keys, added shares as JSON (`smb_extra_sources`), passwords per share in `SecretStore`. `AppSettings.mergedSources` = shares with `includeInMain` + device if `deviceInMain`; `QueueScope.All`, Randomize and the default Library/Albums lists use only those. A share that is not merged is played via `QueueScope.Source`.
- `SmbProviders` gives one `SmbShareProvider` per share, in three sets (scan, tag reading, playback).
- Folder queries must always filter by source too (two shares can share a folder path).

## Hard-won rules
- Three separate SMB connections on purpose: scanning, tag reading, playback. Sharing one made playback stall during scans.
- Never wrap SMB reads for ExoPlayer/MediaMetadataRetriever without `SmbReadAhead`: parsers do ~260-byte reads and each was a network round trip.
- A scan with unreadable folders must not delete tracks (`Reconciler(deleteMissing=false)`).
- Scans run in `LibraryManager`'s scope, never in a composable/ViewModel scope (closing a dialog cancelled them once).
- smbj needs BouncyCastle: `NocturneApp.installBouncyCastle()` replaces Android's trimmed "BC" provider.
- Audiobookshelf cannot be told to play from outside (tested on app 0.14.2-beta: its media browser service rejects third-party callers, no play intent or deep link, media keys ignored until a book is loaded). The ABS screen only shows the last book and an Open button; no resume button.

## Test infrastructure used during development (not part of the repo)
- Emulator AVDs `nocturne_op3` (API 34) and `nocturne_api28` (Android 9, closest to the OnePlus 3), both 1080x1920 at 420 dpi. Rotate headless with `adb emu rotate`.
- Local SMB test server: impacket `smbserver.py -smb2support -port 4455 -username test -password secret music <dir>` (reachable from the emulator via the emulator's host-loopback alias).
- The test phone runs a force-rotation app that pins landscape, so portrait cannot be reached by rotating. Emulate it with `wm density 270` + `wm overscan 656,0,656,0` on a `wm size 1920x1080` display (a 608x1080 px = 360x640 dp strip), then reset with `wm overscan reset`, `wm size reset` and `wm density <original override>`.
- Mock Audiobookshelf server implementing `/ping`, `/login`, `/api/me`, `/api/items/{id}`, `/cover`.

## Privacy
Public-repo rule: no internal IPs/hostnames, paths, credentials in anything committed. Credentials live only in the app's encrypted store and in `../creds` (never commit it).
