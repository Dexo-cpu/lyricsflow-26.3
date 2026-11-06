# LyricsFlow 26.3

Live, synchronized Spotify lyrics for Minecraft Java **26.3** (Fabric), fully client-side.
Two display modes: a classic **HUD overlay** and **floating world lyrics** (glowing text that rises in front of you and fades out).

> **Status: source project, not yet compiled.** This was written without access to the 26.3 jars, so it has never
> been built or run in-game. See "Known uncertain points" below: they are the first things to check if the build fails.

## Features
- Detects the Spotify track and playback position (Spotify Web API, PKCE login, no client secret).
- Synced lyrics from **LRCLIB** (free, no key). Disk + memory cache. All network calls run on background threads.
- Line sync always; **word-by-word highlight/reveal only when the provider returns real word timestamps** (enhanced LRC).
  Songs without synced lyrics show nothing (plus a short hint). Lyrics are never faked or estimated.
- Handles pause, resume, skip, seek and song changes. Local clock interpolation between 1 s polls with drift blending.
- HUD: stacked lines, SLIDE/FADE/NONE transitions, per-line dimming, anchors, offsets, optional title/artist.
- Floating world lyrics: spawn per line in sync, float UP / DOWN / DRIFT, fade in/out, auto-removed, camera-facing,
  first and third person, optional word reveal, crosshair avoidance, never blocks gameplay.
- Styling: HEX or RGB colors, glow (color/intensity/radius), shadow, outline, bold/italic, thickness, scale, opacity, background.
- Settings screen with tabs and a **live preview** (HUD or floating, whichever mode is selected).

## Controls (rebindable under Controls > LyricsFlow)
| Key | Action |
|---|---|
| `L` | Toggle lyrics overlay |
| `K` | Switch HUD <-> floating world lyrics |
| `O` | Open settings |

## Spotify setup
1. Go to <https://developer.spotify.com/dashboard> and create an app (any name).
2. Add this **Redirect URI** exactly: `http://127.0.0.1:8888/callback` (change the port in the config if 8888 is busy).
3. Tick **Web API**, save, and copy the **Client ID**.
4. In game: `O` > **Spotify** tab > paste the Client ID > **Connect Spotify**. Approve in the browser tab that opens
   (or use **Copy login link**).
5. Play a song on any Spotify device. Lyrics appear within a second or two.

Your refresh token is stored in `config/lyricsflow26/spotify-token.json` (owner-only permissions where supported).
Press **Disconnect** to delete it. Spotify's developer-mode rules change over time; check the dashboard if login is refused.

## Install
Requires Minecraft 26.3, Fabric Loader >= 0.19.5, Fabric API (26.3 build), Java 25.
Drop the built `lyricsflow26-*.jar` into `.minecraft/mods`.

## Build
```
# one-time: copy gradlew, gradlew.bat and the gradle/ folder from Fabric's example mod for 26.x
./gradlew build
# jar: build/libs/lyricsflow26-1.0.0+26.3.jar
```
Versions live in `gradle.properties`. Confirm them at <https://fabricmc.net/develop/> before building.

### Build without installing anything
Push this folder to a GitHub repo. `.github/workflows/build.yml` builds the jar in the cloud (Java 25, no wrapper needed);
download it from the run's **Artifacts**. Any compile error shows in the log.

## Floating world lyrics: how it works (and its limit)
Each lyric line becomes a particle with a real 3D position. It is projected through the live game camera and drawn as
a camera-facing billboard in the HUD pass. That makes it readable from every angle, correct in first and third person,
and unable to block, push or collide with anything.
**Limit:** because it is drawn in the HUD pass, it is **not depth-tested**: blocks and entities do not hide it.
A true in-world mesh would need Fabric's world-render hook for 26.3, which I could not verify offline.
All text drawing already goes through `render/Gfx.java`, so a world-pass renderer can replace `WorldLyricsRenderer`'s draw step.

Anchors: `FOLLOW_VIEW` (stays in front of you), `WORLD_AT_SPAWN` (hangs where it appeared), `FIXED_POSITION` (set coordinates).

## Configuration
`config/lyricsflow26.json` (all values editable in-game). Use **Lyrics offset ms** to compensate Spotify/Bluetooth latency
(positive = lyrics earlier).

## Known uncertain points (check here first if compilation fails)
| Where | What may differ in 26.3 |
|---|---|
| `build.gradle` | Loom plugin id `net.fabricmc.fabric-loom`, version `1.17-SNAPSHOT`, `implementation` instead of `modImplementation` for unobfuscated MC |
| `LyricsFlowMod` | `KeyMappingHelper.registerKeyMapping` (older: `KeyBindingHelper.registerKeyBinding`), `KeyMapping.Category.register` |
| `render/Gfx.java` | `GuiGraphicsExtractor.text(...)`, `fill(...)`, `pose()` method names (older: `drawString`, `GuiGraphics`) |
| `render/CameraCompat.java` | `Camera.position()`, `xRot()`, `yRot()` (older: `getPosition()`, `getXRot()`, `getYRot()`) |
| `screen/ConfigScreen.java` | `extractRenderState` override (older: `render`), `EditBox.setHint/setTextColor`, `keyboardHandler.setClipboard` |

## Layout
`config/` settings | `core/` playback state and service | `spotify/` PKCE auth and poller | `lyrics/` LRC parser, LRCLIB, cache |
`render/` HUD, floating world, text effects, projector | `screen/` settings UI

## Not supported
Custom font files, true depth-tested 3D text (see above), word timing for songs whose provider only has line timing.
