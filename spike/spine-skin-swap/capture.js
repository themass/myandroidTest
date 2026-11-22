// Playwright capture driver for the Spine skin-swap spike.
// Lives outside the fetched-asset dir on purpose (assets are third-party data).
const path = require("path");
const fs = require("fs");
const { chromium } = require("playwright-core");

const SPIKE_DIR = "/Users/gqli/work/myandroidTest/.claude/worktrees/hopeful-pare-a449b1/spike/spine-skin-swap";
const OUT = process.argv[2] || path.join(SPIKE_DIR, "evidence");
const BASE = process.env.SPIKE_URL || "http://127.0.0.1:8731/index.html";
const EXEC = "/Users/gqli/Library/Caches/ms-playwright/chromium-1255/chrome-mac-arm64/Google Chrome for Testing.app/Contents/MacOS/Google Chrome for Testing";

const shots = [];
const log = [];

async function main() {
  fs.mkdirSync(OUT, { recursive: true });
  const browser = await chromium.launch({
    executablePath: EXEC,
    args: ["--use-gl=angle", "--use-angle=swiftshader", "--enable-unsafe-swiftshader",
           "--disable-gpu-sandbox", "--no-sandbox"],
  });
  const page = await (await browser.newContext({
    viewport: { width: 900, height: 1150 }, deviceScaleFactor: 2,
  })).newPage();

  page.on("console", m => log.push(`[console:${m.type()}] ${m.text()}`));
  page.on("pageerror", e => log.push(`[pageerror] ${e.message}`));
  page.on("requestfailed", r => log.push(`[reqfail] ${r.url()} ${r.failure()?.errorText}`));

  const url = `${BASE}?asset=mix-and-match&anim=idle&poseT=0.45`;
  console.log("goto", url);
  await page.goto(url, { waitUntil: "load" });

  await page.waitForFunction(() => window.__spike && window.__spike.ready(), null,
    { timeout: 45000 });
  const info = await page.evaluate(() => window.__spike.info());
  fs.writeFileSync(path.join(OUT, "runtime-info.json"),
    JSON.stringify({ url, ...info }, null, 2));
  console.log(`loaded: ${info.skins.length} skins, anims=${info.animations.join(",")}`);

  let n = 0;
  const rev = () => page.evaluate(() => window.__spike.rev());
  async function shot(label, note) {
    n++;
    const file = `${String(n).padStart(2, "0")}-${label}.png`;
    // Let the app paint the current state deterministically before grabbing.
    await page.evaluate(() => { window.__spike.pause(); });
    await page.evaluate(() => new Promise(r => window.__spike.waitFrames(3).then(r)));
    const cap = await page.evaluate((l) => {
      const t = document.getElementById("caption").textContent;
      return t;
    }, label);
    await page.screenshot({ path: path.join(OUT, file) });
    shots.push({ file, label, note, caption: cap, rev: await rev() });
    console.log(`  shot ${file}  (rev ${shots.at(-1).rev})`);
  }
  async function run(fn, caption, note) {
    const r = await page.evaluate(fn);
    await page.evaluate(([c, d]) => window.__spike.caption(c, d), [caption, typeof r === "string" ? r : JSON.stringify(r)]);
    await shot(caption.replace(/[^\w.+-]+/g, "_").slice(0, 48), note || String(r).slice(0, 200));
    return r;
  }

  // ---- 1. Composited part-skin outfits: same skeleton, three skins -------
  await run(() => window.__spike.combo("A"), "combo A", "Skin.addSkin x9");
  await run(() => window.__spike.combo("B"), "combo B", "different parts");
  await run(() => window.__spike.combo("C"), "combo C", "different parts");

  // ---- 2. Pre-built full skins ------------------------------------------
  for (const f of ["full-skins/boy", "full-skins/girl", "full-skins/girl-blue-cape",
                   "full-skins/girl-spring-dress"])
    await run(new Function("return window.__spike.fullSkin(" + JSON.stringify(f) + ")"),
      "full " + f.split("/")[1], "setSkin(prebuilt)");

  // ---- 3. One-garment swap on top of a combo ----------------------------
  await run(() => window.__spike.combo("A"), "back to A");
  await run(() => window.__spike.comboWith("A", "clothes/dress-blue"),
    "A + dress-blue override", "single category swap");
  await run(() => window.__spike.comboWith("A", "accessories/cape-blue"),
    "A + cape-blue override", "single category swap");

  // ---- 4. Mouth attachment swap = the lip-sync anchor -------------------
  const mouthAtts = await page.evaluate(() => window.__spike.skinsForSlot("mouth").slice(0, 6));
  log.push("[mouth] skinsForSlot(mouth): " + JSON.stringify(mouthAtts));
  await run(() => window.__spike.combo("A"), "combo A (mouth baseline)");
  await run(() => window.__spike.mouth("mouth-close"), "mouth=CLOSE");
  await run(() => window.__spike.mouth("mouth-smile"), "mouth=SMILE");
  const mouthRes = await page.evaluate(() => window.__spike.lastMouth);
  log.push("[mouth] swap result: " + JSON.stringify(mouthRes));

  // ---- 5. Animation crossfade (proves mixing between animations) --------
  // Play 'idle' then cut to 'dance' and sample across the defaultMix window.
  await page.evaluate(() => { window.__spike.combo("B"); window.__spike.play("idle"); });
  await page.waitForTimeout(600);
  await page.evaluate(() => window.__spike.play("dance"));
  for (let i = 0; i < 4; i++) {
    await page.waitForTimeout(120);
    n++;
    const file = `${String(n).padStart(2, "0")}-crossfade-${i}.png`;
    await page.screenshot({ path: path.join(OUT, file) });
    shots.push({ file, label: `crossfade t=${i}`, note: "idle->dance defaultMix 0.25" });
    console.log(`  shot ${file}`);
  }

  // ---- 6. dress-up animation (skin keyframes authored in the asset) -----
  await page.evaluate(() => { window.__spike.play("dress-up"); });
  await page.waitForTimeout(400);
  await page.screenshot({ path: path.join(OUT, `${String(++n).padStart(2, "0")}-dressup-early.png`) });
  await page.waitForTimeout(1500);
  await page.screenshot({ path: path.join(OUT, `${String(++n).padStart(2, "0")}-dressup-late.png`) });
  shots.push({ file: "dressup", note: "authored dress-up animation" });

  await page.evaluate(() => window.__spike.pause());
  fs.writeFileSync(path.join(OUT, "shots.json"), JSON.stringify(shots, null, 2));
  fs.writeFileSync(path.join(OUT, "console.log"), log.join("\n"));
  await browser.close();
  console.log(`\n${shots.length} screenshots -> ${OUT}`);
}

main().catch(e => { console.error("DRIVER FAILED:", e); process.exit(1); });
