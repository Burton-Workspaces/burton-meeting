# Using Burton Meeting

Burton Meeting is a household LAN app. Put every phone on the same Wi-Fi. One device starts a room; the others join it. Media never leaves the LAN unless a peer path requires it (this build uses host ICE candidates only).

## Permissions

| Android | Permission | Why |
| --- | --- | --- |
| 13+ | Nearby Wi-Fi devices | Find meetings advertised with mDNS |
| 12 and older | Location | Same discovery path on older platform APIs |
| All | Microphone | Voice in every meeting |
| Video meetings | Camera | Local video; optional if you join as voice |
| 13+ | Notifications | Ongoing “in a meeting” notification |
| 12+ | Bluetooth connect | Headsets (optional) |

The first screen asks for discovery. Microphone and camera are requested when you start or join.

## Screens

### Meetings

Lists rooms found on this Wi-Fi. Each row shows the name, **Video** or **Voice**, and the 6-character code.

- **Start a meeting** — name, then Video or Voice only
- **Join with a code** — type the code; join as video or voice
- Tap a nearby row to join that room

Settings (gear) holds your display name and the app version.

### In a meeting

Top line is the room name. The line under it is mode, code, and participant count. Tap the code to copy it.

Tiles are you plus everyone else. Video tiles show the camera; voice (or camera off) shows an initial.

Controls, left to right:

- Mute / unmute
- Camera (video rooms only) — turning it off is voice-only for you
- Speaker
- Leave (red)

The host leaving ends signaling for everyone else.

## Video vs voice

| | Video room | Voice room |
| --- | --- | --- |
| Host | Camera on unless you switch it off | No camera control |
| Guest | Join as video or voice | Voice only |
| Mid-call | Camera toggle | Always audio |

## Discovery

Rooms are advertised as `_burton-meeting._tcp` with the code in the name. If a phone does not appear in the list, join with the code while both devices are on the same network.

Debug builds use application id `com.burton.meeting.debug` and can sit next to a signed install.
