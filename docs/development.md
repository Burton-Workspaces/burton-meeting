# Development

## Tooling

- JDK **17**
- Android SDK compile/target **35**, min **26**
- Android Gradle Plugin 8.7.3, Kotlin 2.0.21, Compose BOM 2024.12.01
- Hilt 2.53.1 (KSP)
- WebRTC `io.github.webrtc-sdk:android:144.7559.14`

Point Gradle at the SDK with `local.properties` (`sdk.dir=…`). That file is gitignored.

## Commands

```bash
./gradlew :app:assembleDebug
./gradlew :app:installDebug
./gradlew testDebugUnitTest
```

Release assemble is blocked unless `keystore.properties` exists and `storeFile` points at a real keystore. Copy [`keystore.properties.example`](../keystore.properties.example) and keep `keystore.properties`, `*.jks`, and `*.keystore` out of git (see `.gitignore`). GitHub Actions signing is [build automation](build-automation.md).

Debug application id is `com.burton.meeting.debug` so it can sit next to a signed install.

Two phones on one Wi-Fi is the real test: start a meeting on one, join from the other (list or code). The emulator can run the UI; WebRTC camera and LAN discovery are unreliable there.

## Layout

```
app/src/main/java/com/burton/meeting/
  MainActivity.kt              permissions, nav, keep-screen-on
  data/discovery/              NSD
  data/signaling/              WebSocket + TinyJson codec
  data/webrtc/                 PeerEngine
  data/repository/             MeetingRepository, DataStore
  domain/                      models, room codes
  ui/home, call, settings, components, theme
  service/MeetingCallService.kt
app/src/test/java/…            room codes, signal codec, TinyJson
```

Parser tests cover the signaling JSON. Run those before changing `SignalCodec`.

## Network while debugging

Cleartext WebSocket to the host is required (`usesCleartextTraffic`). Discovery needs multicast; some guest Wi-Fi APs isolate clients and will hide rooms.

## Versioning while developing

Do not hand-edit `CHANGELOG.md` or `version.txt` on feature branches. Those are owned by [release-please](releases.md) from Conventional Commits on `master`.

Commit subjects must follow Conventional Commits. Install the hook once:

```bash
./scripts/install-git-hooks.sh
```

See [CONTRIBUTING.md](../CONTRIBUTING.md).

After a tagged SemVer release, publish the APK into the shared Burton Workspaces catalog:

```bash
./scripts/publish-fdroid-pages.sh
```

That reads `version.txt`. Setup: [fdroid.md](fdroid.md).
