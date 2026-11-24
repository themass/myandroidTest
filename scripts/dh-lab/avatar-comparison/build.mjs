import { build } from 'esbuild';
import { mkdir, copyFile, cp } from 'node:fs/promises';
import { fileURLToPath } from 'node:url';
import path from 'node:path';
const here = path.dirname(fileURLToPath(import.meta.url));
const root = path.resolve(here, '../../..');
const assets = path.join(root, 'demos/avatar-lab/app/src/main/assets');

// 五个离线场景:VRM 三模式主页 + 四个官方示例页(Live2D / Spine / VRMA / TalkingHead)。
// 全部 esbuild IIFE 打包,资产整树拷入 APK assets —— 设备上零联网可跑。
async function bundle(entry, out, format = 'iife') {
  await build({ entryPoints: [path.join(here, entry)], bundle: true, format, platform: 'browser', target: 'chrome90', outfile: path.join(assets, out), minify: true, legalComments: 'eof' });
}

// 1) 本地 VRM 三模式场景(陪伴/讲课/全身)
await bundle('scene.js', 'scene/scene.bundle.js');
await copyFile(path.join(here, 'index.html'), path.join(assets, 'scene/index.html'));
await copyFile(path.join(root, 'demos/avatar-lab/models/HairSample_Female.vrm'), path.join(assets, 'scene/sample.vrm'));
await mkdir(path.join(assets, 'scene/models'), { recursive: true });
await copyFile(path.join(root, 'demos/avatar-lab/models/SOURCES.md'), path.join(assets, 'scene/models/SOURCES.md'));

// 2) Live2D 官方样例 Hiyori:core 先 <script> 挂全局,再 pixi+cubism4 bundle
await bundle('live2d.js', 'live2d/live2d.bundle.js');
await copyFile(path.join(here, 'live2d.html'), path.join(assets, 'live2d/index.html'));
await copyFile(path.join(here, 'samples/live2d/live2dcubismcore.min.js'), path.join(assets, 'live2d/live2dcubismcore.min.js'));
await cp(path.join(here, 'samples/live2d/Hiyori'), path.join(assets, 'live2d/Hiyori'), { recursive: true });

// 3) Spine 官方样例 spineboy
await bundle('spine.js', 'spine/spine.bundle.js');
await copyFile(path.join(here, 'spine.html'), path.join(assets, 'spine/index.html'));
await cp(path.join(here, 'samples/spine'), path.join(assets, 'spine'), { recursive: true });

// 4) VRMA 全身动画:test.vrma 驱动 scene 共用同一个 sample.vrm(../scene/sample.vrm)
await bundle('vrma.js', 'vrma/vrma.bundle.js');
await copyFile(path.join(here, 'vrma.html'), path.join(assets, 'vrma/index.html'));
await cp(path.join(here, 'samples/vrma'), path.join(assets, 'vrma'), { recursive: true });

// 5) TalkingHead:vendored modules(lipsync-en 等随 bundle 静态引用)+ CC0 mpfb.glb。
//    vendored talkinghead.mjs 顶层用 import.meta.url 定位 audio worklet,必须 esm 输出。
await bundle('talkinghead.js', 'talkinghead/talkinghead.bundle.js', 'esm');
await copyFile(path.join(here, 'talkinghead.html'), path.join(assets, 'talkinghead/index.html'));
await mkdir(path.join(assets, 'talkinghead/samples'), { recursive: true });
await cp(path.join(here, 'samples/talkinghead/modules'), path.join(assets, 'talkinghead/samples/talkinghead/modules'), { recursive: true });
await copyFile(path.join(here, 'samples/talkinghead/mpfb.glb'), path.join(assets, 'talkinghead/mpfb.glb'));
await copyFile(path.join(here, 'samples/talkinghead/TALKINGHEAD-LICENSE'), path.join(assets, 'talkinghead/samples/talkinghead/TALKINGHEAD-LICENSE'));

console.log('5 offline scenes packaged into ' + assets);
