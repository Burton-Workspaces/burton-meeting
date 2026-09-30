# Architecture

The app is a single Gradle module (`:app`), Kotlin, Jetpack Compose, Hilt, OkHttp (unused by the call path), Java-WebSocket, and `io.github.webrtc-sdk`. UI collects `MeetingRepository` state.

```
ui/          Compose screens and ViewModels (Hilt)
domain/      CallSession, NearbyMeeting, RoomCodes
data/
  discovery  NSD browse + advertise (`_burton-meeting._tcp`)
  signaling  WebSocket host + client, TinyJson protocol
  webrtc     PeerEngine (mesh: existing peers offer to newcomers)
  repository MeetingRepository, LocalPrefs (DataStore)
service/     Foreground meeting notification
di/          (none required beyond Hilt application)
```

## Hosting

The host starts `MeetingSignalServer` on port **9472**, advertises the room over NSD, then connects to `ws://127.0.0.1:9472` like any guest. Hello carries the room code. The server welcomes the new peer with everyone already present, then broadcasts `peer-joined`. Existing peers create WebRTC offers; the newcomer only answers. That avoids glare.

ICE servers are empty: same-Wi-Fi host candidates. There is no TURN server in this app.

## Protocol

JSON text frames, encoded with `TinyJson`:

| type | Role |
| --- | --- |
| `hello` | Join (peer id, name, mode, code, mute/camera) |
| `welcome` | Room + existing peers |
| `peer-joined` / `peer-left` | Membership |
| `offer` / `answer` / `ice` | Relayed SDP and candidates |
| `state` | Mute, camera, display name |
| `error` | Wrong code or disconnect |

## Media

`PeerEngine` builds a `PeerConnectionFactory` once per call. Voice rooms never create a video track. Video rooms create a track and enable the capturer when the camera is on. Mute is `AudioTrack.setEnabled(false)`.

The mesh is suitable for a handful of household devices. It is not an SFU.

## UI shell

`MainActivity` hosts a `NavHost`. **Meetings** is `home`. A non-null `CallSession` navigates to `call`. Settings, start, and join-by-code are full-screen modals. Joining a nearby row uses a bottom sheet.

Theme tokens match Burton Sonos: black surfaces, ivory text, sand accent, danger `#C45C4A`.
