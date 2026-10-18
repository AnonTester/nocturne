# Nocturne

A native Android bedside music player and home-screen (launcher) app. Plays music from a Samba (SMB2/3) share or from the device, shows the last-played Audiobookshelf book, and can be set as the default launcher. Designed for an AMOLED phone on a nightstand: landscape first, portrait supported, four colour themes (OLED Black, Midnight Slate, Warm Amber, Night Red), sleep timer with fade-out, dim screen.

Kotlin, Jetpack Compose, Media3 ExoPlayer, smbj, Room. `minSdk 26` (Android 8.0), `targetSdk 34`, application id `app.nocturne.bedside`.

## Requirements

### To build (Linux/macOS/Windows host)
- **JDK 17** (a JRE alone is not enough, Gradle needs the compiler). Point Gradle at it in `~/.gradle/gradle.properties`:
  ```
  org.gradle.java.home=/path/to/jdk-17
  ```
- **Android SDK** with platform 34, build-tools 34 and platform-tools. Create `local.properties` in this folder (it is git-ignored):
  ```
  sdk.dir=/path/to/Android/Sdk
  ```
- Internet access for the first Gradle run (dependencies are downloaded and cached).

### To install on a phone
- `adb` (Android platform-tools) on the host.
- A USB data cable (not a charge-only cable).
- On the phone: **Developer options > USB debugging** enabled (see below).

## Build

```
./gradlew assembleDebug      # app/build/outputs/apk/debug/app-debug.apk
./gradlew assembleRelease    # signed, needs the signing properties below
./gradlew testDebugUnitTest  # JVM unit tests
./gradlew lintDebug          # must stay at 0 errors
```

Release signing reads these from `~/.gradle/gradle.properties` (never put them or the keystore in the project):
```
NOCTURNE_KEYSTORE=/path/to/release.jks
NOCTURNE_KEYSTORE_PASSWORD=...
NOCTURNE_KEY_ALIAS=...
NOCTURNE_KEY_PASSWORD=...
```
Create a keystore once with `keytool -genkeypair -v -keystore release.jks -alias nocturne -keyalg RSA -keysize 2048 -validity 10000` and **back it up**: an update only installs over an existing install if it is signed with the same key.

## Connecting the phone over USB

1. **Enable developer options**: Settings > About phone > tap *Build number* seven times.
2. **Enable USB debugging**: Settings > System > Developer options > *USB debugging*.
3. **Set the USB mode to File transfer (MTP)**: pull down the notification shade, tap the *USB charging this device* / *Android System* notification and choose *File transfer*. "Charging only" is the wrong mode on some devices because it can leave ADB off the USB interface list; MTP (or PTP) is the safe choice. Changing this mode is not needed on every phone, but it is the first thing to try when the device does not show up.
4. Plug in the cable (use a USB 2.0/3.0 port directly, not a charge-only hub).
5. Accept the **"Allow USB debugging?"** prompt on the phone (tick *Always allow from this computer*).
6. Check on the host:
   ```
   adb devices -l
   ```
   The phone must be listed as `device`. `unauthorized` means the prompt on the phone was not accepted; empty means the phone is not exposing the ADB interface (check steps 2-4 and the cable).
7. Linux only: `adb` needs permission to the USB device. Install the udev rules (`android-sdk-platform-tools-common` or `android-udev-rules`) and make sure your user is in the `plugdev` group, then replug.

## Install

```
adb install -r dist/nocturne-<version>-release.apk
```
Use `-r` to update in place and keep data. If the signing key differs from the installed one, uninstall first (this removes the library index and settings).

Then, on the phone:
1. Open Nocturne. Allow notifications and, when asked, access to music/audio.
2. **Settings > Library**: enter the Samba host, share, user and password (and optionally a subfolder), run *Test connection*, then *Save & scan share*. For device music, enable the device library and/or pick a folder.
3. **Settings > Launcher > Set as default launcher**, choose Nocturne and *Always*. The Home button then returns to Nocturne; use the app list in the app to start other apps. To undo: Android Settings > Apps > Default apps > Home app.
4. Optional, **Settings > Audiobooks**: server URL, user, password. Only a login and read requests are made. The screen shows the last-played book (cover, progress, time left) and an *Open Audiobookshelf* button for the installed app (web page without it). The app cannot be told to start playback from outside, so there is no resume button.

## Volume
Nocturne's volume slider is its own gain on top of Android's media volume, which Nocturne never changes (Audiobookshelf has no volume control of its own and uses the same Android setting). Set the loudness ceiling with the hardware keys; the status line warns when Android's media volume is 0.

## Network notes
- Samba: SMB2/3 only (SMB1-only servers are not supported). The phone must be able to reach the server on port 445 (same Wi-Fi/LAN or VPN). Guest access works with an empty user name.
- Cleartext HTTP is allowed so a LAN Audiobookshelf server without TLS works.
- The first library scan builds the index from file names; tags and cover art are read in the background afterwards and are much slower on large libraries over Wi-Fi.

## Tests that need extra infrastructure
- `NOCTURNE_SMB_TEST=host:port ./gradlew testDebugUnitTest` also runs the SMB integration test against a throwaway server (share `music`, user `test`, password `secret`, containing a folder `Loose`).
- `./gradlew connectedDebugAndroidTest` runs the Room/reconcile tests on an emulator or device. **It uninstalls the app afterwards** (wipes data and the default-launcher role), so do not run it on a phone you use.

See `CLAUDE.md` for architecture notes and the rules learned while building this.
