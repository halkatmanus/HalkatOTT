# HalkatOTT - Android TV (armv7a / Android 9) status

## Done and checked (Linux x86_64 sandbox)
- Rebrand: app name, crate/binary (`halkatott`), config/data/cache dirs, env vars (`HALKATOTT_*`), provider label,
  UI strings, docs. Upstream API hostnames (`*.aoneroom.com`, `api.inmoviebox.com`), upstream GitHub URLs and the
  LICENSE files are intentionally unchanged. Auto-update defaults to off.
- `cargo check --all-targets` and the full `cargo test` suite pass on the host.
- `cargo check --target armv7-linux-androideabi` passes (the Rust code type-checks for the TV's architecture).
- Android wrapper project in `android/` (see `android/README.md`): all Java sources compile against the Android 34 API,
  the JNI C file compiles, XML resources/manifest are well-formed.
- Logo set in `branding/` (clapperboard + "HalkatOTT"): icon, logo lockups, TV banner, launcher mipmaps.

## NOT verified (could not be done in the authoring sandbox: no NDK / Android SDK / Maven access, no device)
- The Gradle build, the NDK cross-compile and the CI workflow `.github/workflows/android-apk.yml` have not been run.
- Nothing has been run on a real Android TV: terminal rendering, remote-control handling, the on-screen keyboard
  and the video-player hand-off are untested. Expect to iterate after the first install.
- A video player app (VLC, MX Player, Kodi, ...) must be installed on the TV; HalkatOTT hands playback to it.
