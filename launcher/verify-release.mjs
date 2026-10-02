import fs from 'node:fs/promises';
import path from 'node:path';
import crypto from 'node:crypto';
import { createRequire } from 'node:module';
const require = createRequire(import.meta.url);
const { installProfileFiles } = require('./src/profile-installer.cjs');
const manifest = JSON.parse(await fs.readFile(new URL('./distribution/manifest.json', import.meta.url), 'utf8'));
const project = path.resolve(import.meta.dirname, '..');
const release = path.join(import.meta.dirname, 'distribution', `github-v${manifest.profileVersion}`);
const root = await fs.mkdtemp(path.join(project, 'work', 'launcher-validation-'));
const markerPath = path.join(root, '.world-progression-profile.json');
const byUrl = new Map(manifest.files.map(item => [item.url, item]));
let downloads = 0;
// Release assets are staged locally until the owner publishes them on GitHub.
// Public third-party downloads are checked using their actual CDN URLs.
async function stagedFetch(url) {
  const item = byUrl.get(url);
  if (!item) throw new Error('Unexpected URL');
  downloads++;
  if (url.startsWith('https://github.com/VoodKaRL/world-progression-pack/releases/download/')) {
    const bytes = await fs.readFile(path.join(release, decodeURIComponent(path.basename(new URL(url).pathname))));
    return { ok: true, arrayBuffer: async () => bytes };
  }
  return fetch(url);
}
await fs.mkdir(path.join(root, 'mods'));
await fs.mkdir(path.join(root, 'saves/existing-world'), { recursive: true });
await fs.writeFile(path.join(root, 'saves/existing-world/level.dat'), 'keep-world');
await fs.writeFile(path.join(root, 'mods/world-progression-0.17.0.jar'), 'old-version');
await fs.writeFile(markerPath, JSON.stringify({ files: ['mod/world-progression-0.17.0.jar'] }));
await installProfileFiles(manifest, { minecraftDir: root, markerPath }, stagedFetch);
if (await fs.readFile(path.join(root, 'saves/existing-world/level.dat'), 'utf8') !== 'keep-world') throw new Error('World changed');
const firstDownloads = downloads;
await installProfileFiles(manifest, { minecraftDir: root, markerPath }, stagedFetch);
if (downloads !== firstDownloads) throw new Error('Second install redownloaded unchanged files');
const state = JSON.parse(await fs.readFile(markerPath, 'utf8'));
const report = { profileVersion: state.profileVersion, mods: state.installed.length,
  resourcePacks: state.resourcePacks.length, configFiles: manifest.files.filter(file => file.kind === 'config').length,
  allFilesVerified: true, oldModRemoved: !await fs.access(path.join(root, 'mods/world-progression-0.17.0.jar')).then(() => true, () => false),
  preservedWorld: true, repeatDownloads: downloads - firstDownloads, instance: root,
  manifestSha256: crypto.createHash('sha256').update(await fs.readFile(path.join(release, 'manifest.json'))).digest('hex') };
await fs.writeFile(path.join(project, 'outputs/verdania-launcher-validacion.json'), JSON.stringify(report, null, 2));
console.log(JSON.stringify(report, null, 2));
