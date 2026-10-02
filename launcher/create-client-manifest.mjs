import fs from 'node:fs/promises';
import path from 'node:path';
import crypto from 'node:crypto';

const projectRoot = path.resolve(import.meta.dirname, '..');
const modsDir = process.env.WP_CLIENT_MODS_DIR || path.join(projectRoot, 'mods');
const defaultPacksDir = path.join(process.env.APPDATA || '', 'ModrinthApp', 'profiles', 'pruebas', 'resourcepacks');
const packsDir = process.env.WP_RESOURCEPACK_DIR || defaultPacksDir;
const sourcePath = path.join(import.meta.dirname, 'client-profile-sources.json');
const sourceText = (await fs.readFile(sourcePath, 'utf8')).replace(/^\uFEFF/, '');
const profile = JSON.parse(sourceText);
const profileVersion = process.env.WP_PROFILE_VERSION || profile.profileVersion;
const releaseBase = (process.env.WP_RELEASE_BASE_URL ||
  `https://github.com/VoodKaRL/world-progression-pack/releases/download/v${profileVersion}`).replace(/\/$/, '');
if (!releaseBase.startsWith('https://')) throw new Error('WP_RELEASE_BASE_URL debe comenzar con https://');

const sha = (data, algorithm) => crypto.createHash(algorithm).update(data).digest('hex');
const sources = new Map(profile.files.map(file => [`${file.kind}/${file.name}`, file]));
const files = [];
const uploadDir = path.join(import.meta.dirname, 'distribution', `github-v${profileVersion}`);
await fs.mkdir(uploadDir, { recursive: true });

async function addDirectoryFiles(directory, kind, extension) {
  const names = (await fs.readdir(directory)).filter(name => name.toLowerCase().endsWith(extension)).sort();
  for (const name of names) {
    const filePath = path.join(directory, name);
    const bytes = await fs.readFile(filePath);
    const source = sources.get(`${kind}/${name}`);
    if (source?.sha1 && sha(bytes, 'sha1') !== source.sha1.toLowerCase()) {
      throw new Error(`${name} no coincide con la version incluida en el modpack exportado.`);
    }

    let url = source?.url;
    if (!url) {
      url = `${releaseBase}/${encodeURIComponent(name)}`;
      await fs.copyFile(filePath, path.join(uploadDir, name));
    }
    files.push({ name, kind, url, sha256: sha(bytes, 'sha256') });
  }
}

await addDirectoryFiles(modsDir, 'mod', '.jar');
await addDirectoryFiles(packsDir, 'resourcePack', '.zip');
async function addMenuFiles(directory, relative = '') {
  for (const entry of await fs.readdir(directory, { withFileTypes: true })) {
    const source = path.join(directory, entry.name);
    const name = path.posix.join('fancymenu', relative, entry.name);
    // The current menu uses only this logo. Do not distribute abandoned
    // backgrounds, button prototypes, or the removed author-credit image.
    if (relative === 'assets' && entry.name !== 'verdania-logo.png') continue;
    if (entry.isDirectory()) await addMenuFiles(source, path.posix.join(relative, entry.name));
    else if (entry.isFile()) {
      const bytes = await fs.readFile(source);
      const asset = `menu-${sha(Buffer.from(name), 'sha256').slice(0, 16)}${path.extname(entry.name)}`;
      await fs.copyFile(source, path.join(uploadDir, asset));
      files.push({ name, kind: 'config', url: `${releaseBase}/${asset}`, sha256: sha(bytes, 'sha256') });
    }
  }
}
await addMenuFiles(path.join(projectRoot, 'config', 'fancymenu'));
files.sort((a, b) => a.kind.localeCompare(b.kind) || a.name.localeCompare(b.name));

const manifest = {
  profileVersion,
  minecraftVersion: profile.minecraftVersion,
  loader: profile.loader,
  loaderVersion: profile.loaderVersion,
  javaMajor: profile.javaMajor || 21,
  files
};
const outputPath = path.join(import.meta.dirname, 'distribution', 'manifest.json');
await fs.mkdir(path.dirname(outputPath), { recursive: true });
await fs.writeFile(outputPath, `${JSON.stringify(manifest, null, 2)}\n`);
await fs.copyFile(outputPath, path.join(uploadDir, 'manifest.json'));
const activeAssets = new Set(files.filter(file => file.kind === 'config').map(file => path.basename(new URL(file.url).pathname)));
for (const name of await fs.readdir(uploadDir)) {
  if (/^menu-[a-f0-9]{16}\./.test(name) && !activeAssets.has(name)) await fs.unlink(path.join(uploadDir, name));
}
console.log(`Manifiesto de cliente ${profileVersion}: ${files.length} archivos.`);
console.log(`Archivos que debes adjuntar al release: ${uploadDir}`);
