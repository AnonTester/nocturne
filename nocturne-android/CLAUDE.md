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

## Layout (`app/src/main/java/app/nocturne`)
- `playback/` `PlaybackEngine` (app-scoped ExoPlayer owner: queue, shuffle, sleep timer, volume, retry), `PlaybackService` (Media3 session/notification), pure logic: `ShuffleEngine`, `VolumeCurve`, `SleepTimer`.
- `library/` `LibraryManager` (scans, rescan loop, tag reading), `SmbShareProvider` (smbj connection), `SmbDataSource` (ExoPlayer ranged reads), `SmbReadAhead` (block buffer, essential), `SmbScanner`, `LocalScanner` (MediaStore + SAF), `MetadataEnricher`, `CoverLoader`, `Reconciler`.
- `data/` Room (`Track`, `TrackDao`), DataStore settings, `SecretStore` (encrypted SMB/ABS passwords).
- `abs/` Audiobookshelf REST client + repository. `launcher/` app list, default-launcher settings, ABS launch.
- `ui/` Compose screens. **All colours come from `ui/theme/NocturneColors` tokens; never use literal colours in components.**

## Hard-won rules
- Three separate SMB connections on purpose: scanning, tag reading, playback. Sharing one made playback stall during scans.
- Never wrap SMB reads for ExoPlayer/MediaMetadataRetriever without `SmbReadAhead`: parsers do ~260-byte reads and each was a network round trip.
- A scan with unreadable folders must not delete tracks (`Reconciler(deleteMissing=false)`).
- Scans run in `LibraryManager`'s scope, never in a composable/ViewModel scope (closing a dialog cancelled them once).
- smbj needs BouncyCastle: `NocturneApp.installBouncyCastle()` replaces Android's trimmed "BC" provider.
- Audiobookshelf has no documented "play this item" intent; Resume = media-button play + launch the ABS app, web fallback otherwise.

## Test infrastructure used during development (not part of the repo)
- Emulator AVDs `nocturne_op3` (API 34) and `nocturne_api28` (Android 9, closest to the OnePlus 3), both 1080x1920 at 420 dpi. Rotate headless with `adb emu rotate`.
- Local SMB test server: impacket `smbserver.py -smb2support -port 4455 -username test -password secret music <dir>` (reachable from the emulator via the emulator's host-loopback alias).
- Mock Audiobookshelf server implementing `/ping`, `/login`, `/api/me`, `/api/items/{id}`, `/cover`.

## Privacy
Public-repo rule: no internal IPs/hostnames, paths, credentials in anything committed. Credentials live only in the app's encrypted store and in `../creds` (never commit it).
