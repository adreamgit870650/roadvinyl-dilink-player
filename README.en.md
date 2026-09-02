# RoadVinyl

[简体中文](README.md) | English

RoadVinyl is an Android music player optimized for BYD DiLink in-car displays. It combines a
low-glare vinyl-inspired player with local library scanning, word-timed karaoke lyrics,
desktop/instrument-cluster lyric overlays, steering-wheel media controls, boot resume, online
music discovery, radio, podcasts, and integrity-checked in-app updates.

Current version: **4.1.0**

## Highlights

- Scan local audio from shared storage, SD cards, and user-selected folders.
- Cache the library for fast startup; search and sort by title, artist, or duration.
- Parse adjacent LRC files and embedded ID3 USLT/SYLT lyrics.
- Fetch and save lyrics from multiple providers when no local lyrics are available.
- Show synchronized lyrics in the player, a movable overlay, or a secondary display.
- Handle previous, next, and play/pause commands through Media3 `MediaSessionService`.
- Resume the last queue after boot and recover the media-button route during long sessions.
- Browse configurable online music, Radio Browser stations, and RSS podcasts.
- Check, download, SHA-256 verify, and hand off APK updates to Android's installer.

## Build

Requirements: JDK 17, Android SDK 34, and Gradle 8.2 or newer.

```bash
gradle testDebugUnitTest lintDebug assembleRelease --no-daemon
```

The unsigned release APK is written to
`app/build/outputs/apk/release/app-release-unsigned.apk`.

## Optional update endpoint

The public source tree contains no private CloudBase environment, domain, APK object ID, or
signing credential. In-app updates are disabled until an HTTPS metadata endpoint is supplied at
build time:

```bash
gradle assembleRelease \
  -PROADVINYL_UPDATE_VERSION_URL=https://updates.example.com/version.json
```

For GitHub Actions, define the repository variable `ROADVINYL_UPDATE_VERSION_URL`. A sanitized
CloudBase template is provided under [`cloudbase`](cloudbase/README.md).

## Safety and third-party services

RoadVinyl is an experimental in-car application, not an official BYD product. Validate it on a
test device while parked before regular use. Online music, lyric, radio, and podcast providers
have their own terms and availability. See [SECURITY.md](SECURITY.md),
[THIRD_PARTY_NOTICES.md](THIRD_PARTY_NOTICES.md), and [NOTICE.md](NOTICE.md).

BYD and DiLink are trademarks of their respective owner. This project is not affiliated with or
endorsed by BYD.
