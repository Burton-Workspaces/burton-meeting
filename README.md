# Burton Meeting

LAN video and voice meetings for the household. One phone hosts the room, others join from the same Wi-Fi. There is no cloud account and no extra server.

Signed APKs are published on [GitHub Releases](https://github.com/Burton-Workspaces/burton-meeting/releases). Droidify / F-Droid: [burton-sonos-fdroid](https://github.com/Burton-Workspaces/burton-sonos-fdroid) (`https://burton-workspaces.github.io/burton-sonos-fdroid/fdroid/repo`).

## What it does

- **Meetings** — nearby rooms advertised on the LAN, or join with a 6-character code
- **Start a meeting** — video or voice-only; you are the host
- **Join** — video (camera) or voice only, including voice in a video room
- **In call** — mute, camera on/off, speaker, leave; tap the code to copy it

Everyone must be on the same Wi-Fi. Media is WebRTC between the phones; signaling stays on the host.

## Requirements

- Android 8.0+ (API 26)
- Same Wi-Fi as the host
- Nearby devices (Android 13+) or location (older) for discovery
- Microphone; camera only for video

## Docs

| Doc | Contents |
| --- | --- |
| [Using the app](docs/using.md) | Screens, permissions, video vs voice |
| [Architecture](docs/architecture.md) | Packages, discovery, signaling, WebRTC mesh |
| [Development](docs/development.md) | Build, run, test, project layout |
| [Build automation](docs/build-automation.md) | GitHub Actions, workflow permissions, signing secrets |
| [Releases](docs/releases.md) | SemVer 2.0, local build + publish walkthrough, GitHub Releases |
| [F-Droid / Droidify](docs/fdroid.md) | Same catalog as Burton Sonos, Fingerprint, one-command Pages publish |
| [Contributing](CONTRIBUTING.md) | Conventional Commits (required) |

## Quick start (debug)

```bash
./gradlew :app:installDebug
```

Debug builds use application id `com.burton.meeting.debug`. Release builds need a keystore; see [docs/releases.md](docs/releases.md).

```bash
./gradlew testDebugUnitTest
```
