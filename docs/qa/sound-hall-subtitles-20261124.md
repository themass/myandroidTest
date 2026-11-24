# Sound馆 release evidence · 2026-11-24

## Playback speed (slice ②)

**Code-side PASS:** Independent verification reported that speed semantics match the legacy 0.25×–3.0× range, persist across tracks, and the test matrix finished 11 passed / 0 failed. This is independently reported evidence; no device speed check is recorded here.

**Device check:** Change speed, move to another track, background the app, return, and confirm the selected rate and pitch behavior. **BLOCKED** until a device playback session is run.

## Foreground service and notification (slice ③)

**Code-side ownership contract:** `ScreeningAudioService` creates and owns the ExoPlayer and releases it from `onDestroy`; the Compose owner builds a `MediaController` and releases that controller when its effect is disposed. Disposing the page therefore disconnects the UI controller and does not directly release the service-owned player. The service declares `foregroundServiceType="mediaPlayback"` in `feature-video/src/main/AndroidManifest.xml`. Notification metadata is populated by `screeningAudioMediaItem`; transport controls are provided through the MediaSession/MediaController path.

**Inherited Media3 behavior (source inspected: `androidx.media3:media3-session:1.10.1`):** `MediaSessionService.onTaskRemoved` calls `pauseAllPlayersAndStopSelf()` when playback is not ongoing or no session is playing; when a session is actively playing, the default implementation does not stop it. `MediaNotificationManager` records notification dismissal to suppress the notification; that event does not itself issue a player stop. These are dependency-source observations, not app-level tests.

**Still unverified:** The app has no explicit override or tests for these inherited behaviors. Player release is explicit in app code only when Android destroys the service (`onDestroy`). Audio-focus loss and system process reclamation/recreation also have no app-level verification here. Do not infer runtime PASS from dependency source or the MediaItem metadata test.

| Path | Current code evidence | Verification / status |
| --- | --- | --- |
| Leave track list for channel picker | `SoundTrackHub` and its `RealtimeSubtitleRunner` leave composition; the controller belongs to the enclosing `ScreeningSound` route | Code-reviewed contract; subtitle detaches on track hub disposal; **device check BLOCKED**: verify audio continues and captions stop |
| Dispose the enclosing `ScreeningSound` route while service is alive | Release the `MediaController` client; service player release is only in `onDestroy` | **device check BLOCKED**: start playback, exit the route, confirm notification audio continues, reopen and reconnect |
| User dismisses the notification | Media3 records dismissal to suppress that notification; no app callback stops playback | **BLOCKED**: verify playback continues, notification stays dismissed, and later notification state follows expected policy |
| User swipes app task away | Inherited Media3 `onTaskRemoved` stops the service if playback is not ongoing or no session is playing; actively playing session is retained | **BLOCKED**: play audio, swipe task away, verify the documented Media3 branch and notification state on target OS |
| Natural list end in sequential mode | UI sets no next track; no explicit service stop call | **BLOCKED**: play final track to completion and inspect whether service/notification remains; define expected idle policy |
| Audio focus loss | No app-level focus-loss policy or test in this service | **BLOCKED**: exercise transient and permanent focus loss, verify pause/resume and notification state |
| Android reclaims process | Service player is released in `onDestroy`; no recreation/resume persistence is implemented here | **BLOCKED**: background playback under memory pressure, then return and verify whether playback resumes or presents a recoverable idle state |
| MediaController connection failure | Failed `buildAsync()` completion becomes a visible error state with a retry action; retry creates a new controller attempt. Controller disposal releases only the controller client. `ScreeningAudioConnectionTest` covers connecting, failed, and connected state resolution. | **Code-side PASS**; Android service reconnection and retry behavior still **device check BLOCKED** |

The rows above distinguish code ownership facts from Android/Media3 behavior that needs a verified runtime result. No device installation or playback was performed for this report.

## Wi-Fi playback gate (slice ⑤)

**Code-side status:** The preference defaults to `true`, matching the Settings screen; `true` means mobile playback is allowed without a prompt. The prompt is now checked only after playback becomes active or the default network changes while playback is active. The `ConnectivityManager.NetworkCallback` pauses active playback and prompts when the network becomes non-Wi-Fi and the preference/session override do not allow it. The one-session override uses `remember`, so leaving and reopening the sound track screen resets it. Focused `ScreeningSoundNetworkGateTest` passed.

**Device check BLOCKED:** With the preference disabled, (1) start a track on cellular and confirm it begins before the prompt; (2) confirm the prompt pauses it and Continue resumes; (3) switch active playback from Wi-Fi to cellular and confirm pause+prompt; (4) dismiss the prompt and confirm playback stays paused; (5) accept once, exit the sound track screen, reopen on cellular and confirm the one-session override reset. With the preference enabled (default), cellular playback should proceed without a prompt.

## Realtime subtitles

**Implemented:** ScreeningSound uses the Compose `RealtimeSubtitleRunner` and `CaptionOverlay`. The reprocessing URL prefers the active Media3 media URL, falls back to the selected track URL, and accepts only HTTP(S) or file paths; unsupported `content://` sources show a clear unavailable state. ASR credentials are checked before starting and the existing missing-credentials toast is shown. A 500 ms progress sampler restarts recognition after `realtimeSeekJumped` detects a seek, using the selected playback speed; pausing is excluded by the helper. The subtitle runner stops on track change and track screen disposal, independently of the service-owned audio player.

**Verification:** Focused `ScreeningSoundSubtitleTest` and existing `ScreeningRealtimeSubtitleTest` seek coverage passed. Full `:feature-video:testDebugUnitTest` and `:app:assembleChatDebug` remain to be run for this revision. No device playback was performed; runtime subtitle behavior remains **BLOCKED**.

## Static lyrics

**BLOCKED:** No static lyric field exists in the current `SoundItemsVo` schema (`core/src/main/java/com/openapi/core/model/SoundItemsVo.java:7-10` contains only `name`, `file`, and `fileDate`). The legacy Sound馆 subtitle behavior is realtime ASR, wired in `feature-content/src/main/java/com/openapi/feature/content/sound/MusicSubtitleBindings.java` to `speech/src/main/java/com/openapi/speech/subtitle/realtime/VideoRealtimeSubtitleSession.java`; it does not establish that server-provided lyric text exists.

**Owner:** Sound API/backend owner.

**Next action:** If static synchronized lyrics are still required, expose an explicit lyrics payload and timing contract in the sound item response, then implement and verify it. Do not fabricate lyric text from track metadata.
