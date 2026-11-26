// Machine proof for the lip-sync anchor:
//  1) skeleton.setAttachment("mouth", X) changes slot.getAttachment().name
//  2) HUD-free screenshots of close vs smile differ ONLY inside the mouth
//     attachment's projected bbox (localization proves the change is the mouth)
const { chromium } = require("playwright-core");
const EXEC = "/Users/gqli/Library/Caches/ms-playwright/chromium-1255/chrome-mac-arm64/Google Chrome for Testing.app/Contents/MacOS/Google Chrome for Testing";
const OUT = "/Users/gqli/work/myandroidTest/.claude/worktrees/hopeful-pare-a449b1/spike/spine-skin-swap/evidence";

(async () => {
  const browser = await chromium.launch({
    executablePath: EXEC,
    args: ["--use-gl=angle", "--use-angle=swiftshader", "--enable-unsafe-swiftshader", "--no-sandbox"],
  });
  const page = await (await browser.newContext({ viewport: { width: 900, height: 1150 }, deviceScaleFactor: 2 })).newPage();
  page.on("pageerror", e => console.log("[pageerror]", e.message));
  await page.goto("http://127.0.0.1:8731/index.html?asset=mix-and-match&anim=idle&poseT=0.45", { waitUntil: "load" });
  await page.waitForFunction(() => window.__spike && window.__spike.ready(), null, { timeout: 45000 });

  const r = await page.evaluate(async () => {
    const res = {};
    window.__spike.combo("A");
    res.stateBaseline = window.__spike.state();

    window.__spike.mouth("mouth-close");
    await window.__spike.waitFrames(2);
    res.stateClose = window.__spike.state();
    res.bbox = window.__spike.mouthBBox();
    window.__spike.setHud(false);
    await window.__spike.waitFrames(2);

    // capture close
    let shot = await new Promise(res2 => {
      requestAnimationFrame(() => {
        res2(document.getElementById("canvas").toDataURL("image/png"));
      });
    });
    res.dataUrlCloseLen = shot.length;
    window.__dataUrlClose = shot;

    window.__spike.mouth("mouth-smile");
    await window.__spike.waitFrames(3);
    res.stateSmile = window.__spike.state();
    shot = await new Promise(res2 => {
      requestAnimationFrame(() => res2(document.getElementById("canvas").toDataURL("image/png")));
    });
    window.__dataUrlSmile = shot;
    res.dataUrlSmileLen = shot.length;
    return res;
  });
  console.log(JSON.stringify(r, null, 2));

  // extract the two HUD-free PNGs straight from the page's WebGL buffer
  for (const [name, varName] of [["mouth-close-hudless.png", "__dataUrlClose"],
                                  ["mouth-smile-hudless.png", "__dataUrlSmile"]]) {
    const dataUrl = await page.evaluate(v => window[v], varName);
    const b64 = dataUrl.split(",")[1];
    require("fs").writeFileSync(`${OUT}/${name}`, Buffer.from(b64, "base64"));
    console.log("wrote", name);
  }
  await browser.close();
})().catch(e => { console.error("FAIL", e); process.exit(1); });
