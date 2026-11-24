# Sound馆 subtitles status · 2026-11-24

## Realtime subtitles

**Implemented:** ScreeningSound exposes a subtitle toggle and reuses the existing realtime Volcengine ASR session (`VideoRealtimeSubtitleSession` plus `StreamingSubtitleOverlayPresenter`). The session reads the active Media3 media URL, falls back to the selected sound track URL, and probes the service-owned player's play state and position. Subtitle overlays detach on track change and when the track screen is disposed.

**Verification:** `ScreeningSoundSubtitleTest`, full `:feature-video:testDebugUnitTest`, and `:app:assembleChatDebug` passed. This is build/unit-test evidence; no device playback was performed.

## Static lyrics

**BLOCKED:** No static lyric field exists in the current `SoundItemsVo` schema (`core/src/main/java/com/openapi/core/model/SoundItemsVo.java:7-10` contains only `name`, `file`, and `fileDate`). The legacy Sound馆 subtitle behavior is realtime ASR, wired in `feature-content/src/main/java/com/openapi/feature/content/sound/MusicSubtitleBindings.java` to `speech/src/main/java/com/openapi/speech/subtitle/realtime/VideoRealtimeSubtitleSession.java`; it does not establish that server-provided lyric text exists.

**Owner:** Sound API/backend owner.

**Next action:** If static synchronized lyrics are still required, expose an explicit lyrics payload and timing contract in the sound item response, then implement and verify it. Do not fabricate lyric text from track metadata.
