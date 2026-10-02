const path = require('node:path');
const fs = require('node:fs/promises');
const crypto = require('node:crypto');

const safeName = name => typeof name === 'string' && name.length <= 180 && name !== '.' && name !== '..' && !/[\\/:\0]/.test(name);
function destinationFor(root, item) {
  if (['mod', 'resourcePack'].includes(item.kind) && safeName(item.name)) {
    return path.join(root, item.kind === 'mod' ? 'mods' : 'resourcepacks', item.name);
  }
  // Only pack-owned FancyMenu configuration is managed. Never accept a
  // manifest path that could write elsewhere in the instance or on Windows.
  if (item.kind === 'config' && typeof item.name === 'string') {
    const segments = item.name.split('/');
    if (segments.length >= 2 && segments[0] === 'fancymenu' && segments.every(safeName) &&
        segments.every(segment => !/[. ]$/.test(segment) && !/^(con|prn|aux|nul|com[1-9]|lpt[1-9])(?:\.|$)/i.test(segment))) {
      return path.join(root, 'config', ...segments);
    }
  }
  throw new Error(`Ruta o tipo de archivo no válido: ${item.name}`);
}
const digest = data => crypto.createHash('sha256').update(data).digest('hex');
async function downloadVerified(item, destination, fetchFile = fetch) {
  if (!/^https:\/\//i.test(item.url) || !/^[a-f0-9]{64}$/i.test(item.sha256)) throw new Error(`Entrada de manifiesto no válida: ${item.name}`);
  try { if (digest(await fs.readFile(destination)) === item.sha256.toLowerCase()) return; } catch {}
  const response = await fetchFile(item.url);
  if (!response.ok) throw new Error(`Descarga fallida: ${item.name} (HTTP ${response.status}).`);
  const data = Buffer.from(await response.arrayBuffer());
  if (digest(data) !== item.sha256.toLowerCase()) throw new Error(`La verificación SHA-256 falló para ${item.name}`);
  await fs.mkdir(path.dirname(destination), { recursive: true });
  const tmp = `${destination}.download`;
  await fs.writeFile(tmp, data);
  await fs.rm(destination, { force: true });
  await fs.rename(tmp, destination);
}
async function installProfileFiles(manifest, instance, fetchFile = fetch) {
  const { minecraftDir: root, markerPath } = instance;
  // Validate the whole manifest before any download or filesystem changes.
  const destinations = manifest.files.map(item => destinationFor(root, item));
  const unique = new Set(destinations.map(destination => destination.toLowerCase()));
  if (unique.size !== destinations.length) throw new Error('El manifiesto contiene archivos duplicados.');
  const old = await fs.readFile(markerPath, 'utf8').then(JSON.parse).catch(() => ({ files: [] }));
  for (let i = 0; i < manifest.files.length; i++) await downloadVerified(manifest.files[i], destinations[i], fetchFile);
  const newNames = new Set(manifest.files.map(item => `${item.kind}/${item.name}`));
  for (const oldPath of old.files || []) {
    if (typeof oldPath !== 'string' || newNames.has(oldPath)) continue;
    const separator = oldPath.indexOf('/');
    const item = { kind: oldPath.slice(0, separator), name: oldPath.slice(separator + 1) };
    let destination;
    try { destination = destinationFor(root, item); } catch { continue; }
    await fs.rm(destination, { force: true });
  }
  const packs = manifest.files.filter(item => item.kind === 'resourcePack').map(item => item.name);
  const optionsPath = path.join(root, 'options.txt');
  let options = await fs.readFile(optionsPath, 'utf8').catch(() => '');
  const serialized = `resourcePacks:${JSON.stringify(['vanilla', 'fabric', ...packs.map(name => `file/${name}`)])}`;
  if (/^resourcePacks:.*$/m.test(options)) options = options.replace(/^resourcePacks:.*$/m, serialized);
  else options += `${options.endsWith('\n') || !options ? '' : '\n'}${serialized}\n`;
  await fs.writeFile(optionsPath, options);
  const state = { profileVersion: manifest.profileVersion, minecraftVersion: manifest.minecraftVersion,
    loader: manifest.loader, loaderVersion: manifest.loaderVersion, javaMajor: manifest.javaMajor || 21,
    files: [...newNames], installed: manifest.files.filter(item => item.kind === 'mod').map(item => item.name),
    resourcePacks: packs, updatedAt: new Date().toISOString() };
  await fs.writeFile(markerPath, JSON.stringify(state, null, 2));
}
module.exports = { destinationFor, downloadVerified, installProfileFiles };
