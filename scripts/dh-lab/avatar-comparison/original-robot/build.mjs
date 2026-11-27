import { build } from 'esbuild';
import { cp, mkdir, readFile, writeFile } from 'node:fs/promises';
import path from 'node:path';
import { fileURLToPath } from 'node:url';

const here = path.dirname(fileURLToPath(import.meta.url));
const repo = path.resolve(here, '../../../..');
const [sourceArg, exportArg, outputArg] = process.argv.slice(2);
if (!sourceArg || !exportArg || !outputArg) {
  console.error('Usage: node build.mjs <original-source-dir> <live2d-export-dir> <output-dir>');
  process.exit(2);
}
const source = path.resolve(sourceArg);
const liveExport = path.resolve(exportArg);
const output = path.resolve(outputArg);
const layout = JSON.parse(await readFile(path.join(source, 'layout.json'), 'utf8'));
if (layout.canvas.width !== 640 || layout.canvas.height !== 896 || layout.layers.length !== 13) throw new Error('Unexpected original robot fixture shape');
await mkdir(path.join(output, 'live2d'), { recursive: true });
await mkdir(path.join(output, 'spine'), { recursive: true });
await cp(here, output, { recursive: true, filter: p => !p.includes(`${path.sep}node_modules${path.sep}`) && !p.endsWith('build.mjs') && !p.endsWith('runner.js') });
for (const entry of await (await import('node:fs/promises')).readdir(liveExport, { withFileTypes: true })) {
  await cp(path.join(liveExport, entry.name), path.join(output, 'live2d', entry.name), { recursive: true });
}
await cp(path.join(repo, 'scripts/dh-lab/avatar-comparison/samples/live2d/live2dcubismcore.min.js'), path.join(output, 'live2d/live2dcubismcore.min.js'));

const pivotFor = id => {
  if (id === 'head' || id === 'antenna' || id.startsWith('eye_') || id.startsWith('mouth_')) return { x: 320, y: 238 };
  if (id === 'left_arm') return { x: 238, y: 383 };
  if (id === 'right_arm') return { x: 403, y: 383 };
  if (id === 'cape') return { x: 320, y: 375 };
  if (id === 'legs' || id === 'boots') return { x: 320, y: 620 };
  return { x: 320, y: 470 };
};
const boneFor = id => {
  if (id === 'head' || id === 'antenna' || id.startsWith('eye_') || id.startsWith('mouth_')) return 'head';
  if (id === 'left_arm') return 'left_arm';
  if (id === 'right_arm') return 'right_arm';
  if (id === 'cape') return 'cape';
  if (id === 'legs' || id === 'boots') return 'legs';
  return 'body';
};
const bones = [
  { name: 'root' },
  { name: 'body', parent: 'root', x: 320, y: 426 },
  { name: 'head', parent: 'root', x: 320, y: 658 },
  { name: 'left_arm', parent: 'root', x: 238, y: 513 },
  { name: 'right_arm', parent: 'root', x: 403, y: 513 },
  { name: 'legs', parent: 'root', x: 320, y: 276 },
  { name: 'cape', parent: 'root', x: 320, y: 521 },
];
const slots = [];
const attachments = {};
const animationLayers = [];
const atlasParts = [];
for (const layer of layout.layers) {
  const id = layer.id;
  const image = layer.image;
  const rect = layer.rect;
  await cp(path.join(source, layer.image.path), path.join(output, 'spine', `${id}.png`));
  const pivot = pivotFor(id);
  const x = rect.left + rect.width / 2 - pivot.x;
  const y = 896 - rect.top - rect.height / 2 - (896 - pivot.y);
  const hiddenDefault = id === 'cape' || id === 'mouth_smile';
  const slotName = id.startsWith('mouth_') ? 'mouth' : id;
  slots.push({ name: slotName, bone: boneFor(id), attachment: hiddenDefault ? null : id });
  attachments[slotName] ??= {};
  attachments[slotName][id] = { type: 'region', path: id, x, y, width: image.width, height: image.height };
  atlasParts.push(`${id}.png\nsize: ${image.width}, ${image.height}\nformat: RGBA8888\nfilter: Linear, Linear\nrepeat: none\n${id}\n  rotate: false\n  xy: 0, 0\n  size: ${image.width}, ${image.height}\n  orig: ${image.width}, ${image.height}\n  offset: 0, 0\n  index: -1`);
  if (id === 'left_arm') animationLayers.push('left_arm');
  if (id === 'right_arm') animationLayers.push('right_arm');
}
// Collapse both mouth artwork layers into one slot while preserving one draw-order entry.
const mouthSlots = slots.filter(slot => slot.name === 'mouth');
if (mouthSlots.length > 1) slots.splice(slots.indexOf(mouthSlots[1]), 1);
const skeleton = {
  skeleton: { spine: '4.3.75-beta', x: 0, y: 0, width: 640, height: 896, images: './' },
  bones,
  slots,
  skins: [{ name: 'default', attachments }],
  animations: {
    idle: { bones: { root: { translate: [{ time: 0, y: 0 }, { time: 0.75, y: 5 }, { time: 1.5, y: 0 }] }, head: { rotate: [{ time: 0, value: 0 }, { time: 0.75, value: 1.2 }, { time: 1.5, value: 0 }] }, cape: { rotate: [{ time: 0, value: 0 }, { time: 0.75, value: 2 }, { time: 1.5, value: 0 }] } } },
    nod: { bones: { head: { rotate: [{ time: 0, value: 0 }, { time: 0.25, value: -9 }, { time: 0.55, value: 1 }, { time: 0.9, value: 0 }] } } },
    talk: { bones: { root: { translate: [{ time: 0, y: 0 }, { time: 0.3, y: 2 }, { time: 0.6, y: 0 }] }, left_arm: { rotate: [{ time: 0, value: 0 }, { time: 0.3, value: -6 }, { time: 0.6, value: 0 }] }, right_arm: { rotate: [{ time: 0, value: 0 }, { time: 0.3, value: 6 }, { time: 0.6, value: 0 }] } } },
  },
};
// Remove the mouth neutral slot attachment default duplication; both variants remain addressable in skin data.
const neutral = attachments.mouth.mouth_neutral;
const smile = attachments.mouth.mouth_smile;
void neutral; void smile; void animationLayers;
await writeFile(path.join(output, 'spine', 'original-robot.atlas'), atlasParts.join('\n\n') + '\n');
await writeFile(path.join(output, 'spine', 'original-robot.json'), JSON.stringify(skeleton, null, 2) + '\n');
await build({ entryPoints: [path.join(here, 'runner.js')], bundle: true, format: 'iife', platform: 'browser', target: 'chrome90', outfile: path.join(output, 'runner.bundle.js'), minify: true, legalComments: 'eof' });
console.log(`Built anonymous A/B fixture in ${output}`);
