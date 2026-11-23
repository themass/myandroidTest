// VRM 场景页打包：src/main.js → feature-companion/src/main/assets/dh-vrm/scene.bundle.js
//
// 上一代的 bundle 是会话里一次性 esbuild 手产物、源码从未入库（d309e1c0）——
// 这次把源码收进来。改完 `node build.mjs`（或 npm run build）即重生成资产。
//
// 选项钉死：单 IIFE、minify、无 external —— WebView 伪源只放行
// index.html 与 scene.bundle.js（VrmDigitalHumanEngine.shouldInterceptRequest），
// 任何额外分片都会被 BLOCKED。
import { build } from "esbuild";
import { fileURLToPath } from "url";
import path from "path";

const here = path.dirname(fileURLToPath(import.meta.url));
const out = path.resolve(here, "../../../feature-companion/src/main/assets/dh-vrm/scene.bundle.js");

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
