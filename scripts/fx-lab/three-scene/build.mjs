// Effects Lab Three.js 场景打包：src/main.js → app/src/debug/assets/fx-lab/three.bundle.js
//
// 沿用 scripts/dh-lab/vrm-scene/build.mjs 的约定：单 IIFE、minify、无 external ——
// WebView 伪源 fx-lab.local 只放行 three.html 与 three.bundle.js
// （ThreeWebViewOverlay.shouldInterceptRequest），任何额外分片都会被 BLOCKED。
// 重生成：cd scripts/fx-lab/three-scene && npm install --no-audit --no-fund && node build.mjs
import { build } from "esbuild";
import { fileURLToPath } from "url";
import path from "path";

const here = path.dirname(fileURLToPath(import.meta.url));
const out = path.resolve(here, "../../../app/src/debug/assets/fx-lab/three.bundle.js");

const result = await build({
  entryPoints: [path.join(here, "src/main.js")],
  outfile: out,
  bundle: true,
  format: "iife",
  minify: true,
  target: ["chrome100"],
  legalComments: "eof",
  metafile: true,
});

let bytes = 0;
for (const [, o] of Object.entries(result.metafile.outputs)) bytes += o.bytes;
console.log("wrote", out, Math.round(bytes / 1024) + "KB");
