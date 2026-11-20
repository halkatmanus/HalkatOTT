# HalkatOTT for Android TV (armv7 / Android 9+)

A thin Android wrapper around the HalkatOTT terminal app:

- `app/src/main/java/com/halkatott/tv/MainActivity.java` - full-screen Termux `TerminalView` that runs the native
  binary (`libhalkatott.so`, built from the Rust crate for `armv7-linux-androideabi`) on a pseudo-terminal.
  Remote: D-pad = arrows, OK = Enter, BACK = Esc, MENU / SEARCH / yellow = toggle the on-screen keyboard.
  Quitting the app from inside HalkatOTT (`q`) closes the activity. Ctrl +/- (USB keyboard) changes the font size.
- `app/src/main/java/com/halkatott/tv/PlayerBridge.java` - video playback. The Rust app launches external
  players through a `termux-am` command; we install a tiny shim by that name and turn its requests into Android
  intents. You need a video player app installed on the TV (VLC, MX Player, Kodi, Just Player, ...).
- `app/src/main/java/com/termux/**`, `app/src/main/cpp/termux.c` - Termux terminal libraries (Apache-2.0), see THIRD_PARTY/.

## Build

CI: run the `android-apk` workflow (`.github/workflows/android-apk.yml`); the APK is the build artifact.

Locally (Linux/macOS, Android SDK + NDK r26+ and Rust installed):

    rustup target add armv7-linux-androideabi && cargo install cargo-ndk
    cargo ndk -t armeabi-v7a --platform 24 build --release --bin halkatott
    mkdir -p android/app/src/main/jniLibs/armeabi-v7a
    cp target/armv7-linux-androideabi/release/halkatott android/app/src/main/jniLibs/armeabi-v7a/libhalkatott.so
    cd android && gradle assembleDebug        # or open ./android in Android Studio

Install: `adb connect <tv-ip>` then `adb install -r app/build/outputs/apk/debug/app-debug.apk`
(or copy the APK to the TV and open it with a file manager; enable "unknown sources" for that app).
