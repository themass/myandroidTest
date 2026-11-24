# Avatar lab device evidence — 2026-11-24

Installed standalone `com.openapi.avatar.lab` on ADY-AL00 (2MH0224B20002068), from freshly assembled weifu-refactor commit 683c06640ab4a6605c060ec8ff840555c68a7685.
APK SHA-256: 2000fb44bcccbcc842e0f676999dbe7eab96c35aea4f0e85cf4ab14c87c8a2aa.

- cc0-companion.png: HairSample_Female loaded, 4 supported expressions; companion view, page counter 60 fps.
- cc0-lecture.png: lecture view and lesson overlay, page counter 77 fps.
- cc0-body.png: full body, page counter 60 fps.

Counters are instantaneous requestAnimationFrame estimates, not sustained GPU, thermal, battery or frame-pacing measurements. Teaching is only layout plus procedural pose; no teaching agent, speech or synchronized board is connected. New character source and embedded CC0 terms are in ../../../../demos/avatar-lab/models/SOURCES.md (repository path: demos/avatar-lab/models/SOURCES.md).

Browser preview additionally confirmed the character loaded and arm direction was corrected; error console was empty. Gradle `:app:testDebugUnitTest :app:assembleDebug` succeeded on working branch and merged mainline (unit tasks reused cached unchanged results).

Earlier /tmp/t-local.png is an unrelated application's screenshot and is explicitly excluded. Earlier constraint-sample screenshots are superseded for visual evaluation. No production application was replaced.
