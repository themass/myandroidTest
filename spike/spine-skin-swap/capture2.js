// Part 2 of evidence: (a) screen recording of animated skin swaps, (b) goblins control run.
const fs = require("fs");
const { chromium } = require("playwright-core");
const EXEC = "/Users/gqli/Library/Caches/ms-playwright/chromium-1255/chrome-mac-arm64/Google Chrome for Testing.app/Contents/MacOS/Google Chrome for Testing";
const OUT = "/Users/gqli/work/myandroidTest/.claude/worktrees/hopeful-pare-a449b1/spike/spine-skin-swap/evidence";

(async () => {
  const browser = await chromium.launch({
    executablePath: EXEC,
    args: ["--use-gl=angle", "--use-angle=swiftshader", "--enable-unsafe-swiftshader", "--no-sandbox"],
  });

  // ---------- (a) screen recording: animated skin swaps ----------
  {
    const ctx = await browser.newContext({
      viewport: { width: 900, height: 1150 }, deviceScaleFactor: 1,
      recordVideo: { dir: `${OUT}/video`, size: { width: 900, height: 1150 } },
    });
    const page = await ctx.newPage();
    await page.goto("http://127.0.0.1:8731/index.html?asset=mix-and-match&anim=dance&poseT=0", { waitUntil: "load" });
    await page.waitForFunction(() => window.__spike && window.__spike.ready(), null, { timeout: 45000 });
    const beat = async (ms) => page.waitForTimeout(ms);
    await beat(1200);
    await page.evaluate(() => { window.__spike.play("idle"); window.__spike.caption("RECORDING · idle 动画中", "接下来运行时合成 skin 换装"); });
    await beat(1500);
    await page.evaluate(() => window.__spike.combo("A")); await beat(1400);
    await page.evaluate(() => window.__spike.combo("B")); await beat(1400);
    await page.evaluate(() => window.__spike.combo("C")); await beat(1400);
    await page.evaluate(() => window.__spike.fullSkin("full-skins/boy")); await beat(1300);
    await page.evaluate(() => window.__spike.fullSkin("full-skins/girl-blue-cape")); await beat(1300);
    await page.evaluate(() => window.__spike.play("walk")); await beat(1200);
    await page.evaluate(() => window.__spike.play("dance")); await beat(1200);
    await page.evaluate(() => { window.__spike.combo("A"); window.__spike.mouth("mouth-close"); }); await beat(700);
    await page.evaluate(() => window.__spike.mouth("mouth-smile")); await beat(700);
    await page.evaluate(() => window.__spike.mouth("mouth-close")); await beat(700);
    await page.evaluate(() => window.__spike.caption("recording end", ""));
    await beat(600);
    const vpath = await page.video().path();
    await ctx.close();
    fs.renameSync(vpath, `${OUT}/spine-skin-swap-recording.webm`);
    console.log("video ->", `${OUT}/spine-skin-swap-recording.webm`);
  }

  // ---------- (b) goblins control: same page code, different skeleton ----------
  {
    const page = await (await browser.newContext({ viewport: { width: 900, height: 1150 }, deviceScaleFactor: 2 })).newPage();
    await page.goto("http://127.0.0.1:8731/index.html?asset=goblins&anim=walk&poseT=0.4", { waitUntil: "load" });
    await page.waitForFunction(() => window.__spike && window.__spike.ready(), null, { timeout: 45000 });
    for (const [i, sk] of ["goblin", "goblingirl", "default"].entries()) {
      await page.evaluate((s) => { window.__spike.fullSkin(s); window.__spike.caption("goblins skin: " + s, ""); }, sk);
      await page.evaluate(() => window.__spike.pause());
      await page.evaluate(() => window.__spike.waitFrames(2));
      await page.screenshot({ path: `${OUT}/2${i}-goblins-${sk}.png` });
      console.log(`shot 2${i}-goblins-${sk}.png`);
    }
  }
  await browser.close();
  console.log("done");
})().catch(e => { console.error("FAIL", e); process.exit(1); });
