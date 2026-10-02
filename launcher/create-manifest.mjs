import fs from 'node:fs/promises';
import path from 'node:path';
import crypto from 'node:crypto';

const projectRoot = path.resolve(process.argv[2] ?? '..');
const modsDir = path.join(projectRoot, 'outputs', 'mods-fabric-1.21.1');
const packsDir = process.env.WP_RESOURCEPACK_DIR || path.join(process.env.APPDATA || '', 'ModrinthApp', 'profiles', 'pruebas', 'resourcepacks');
const baseUrl = process.env.WP_RELEASE_BASE_URL?.replace(/\/$/, '');
if (!baseUrl?.startsWith('https://') || /YOUR-HOST|USUARIO|REPOSITORIO|OWNER\/REPO/i.test(baseUrl)) {
  throw new Error('Define WP_RELEASE_BASE_URL con la URL real del release público HTTPS antes de generar el manifiesto.');
}
const releaseBase = baseUrl;
const profileVersion = process.env.WP_PROFILE_VERSION || '0.17.0';
const sha = (data, algorithm) => crypto.createHash(algorithm).update(data).digest('hex');

// Exact immutable Modrinth version IDs used by the local profile. The installer
// verifies the downloaded bytes against the local artifact's SHA-256.
const modrinthVersions = {
  'mobs_of_mythology-2.2.2.jar': 'JpQhYeB3',
  'architectury-13.0.8.jar': 'Wto0RchG',
  'azurelib-2.3.28.jar': 'eXbDHtlS',
  'smartbrainlib-1.16.7.jar': 'eB6ORhH9',
  'mythicmetals-0.24.6+1.21.jar': 'fKQ4feyG',
  'alloy-forgery-2.4.1+1.21.jar': 'czJc7eUX',
  'owo-lib-0.12.15.4+1.21.jar': 'JB1fLQnc',
  'spookis-unique-mobs-0.9.1+1.21.1+fabric.jar': 'JV6tiLDC',
  'entity_model_features-3.3.9-1.21-fabric.jar': 'FMPLuEEv',
  'entity_texture_features-7.2.4-1.21-fabric.jar': 'lmZfRqnz',
  'FreshAnimations_v1.10.4.zip': 'xN57JJts',
  'FA+All_Extensions-v1.8.1.zip': 'RfJ3uz2J'
};
const directUrls = {
  'fabric-api-0.115.6+1.21.1.jar': 'https://maven.fabricmc.net/net/fabricmc/fabric-api/fabric-api/0.115.6+1.21.1/fabric-api-0.115.6+1.21.1.jar'
};

async function entry(name, kind, filePath, url) {
  const data = await fs.readFile(filePath);
  return { name, kind, url, sha256: sha(data, 'sha256') };
}

const files = [];
for (const name of await fs.readdir(modsDir)) {
  if (!name.endsWith('.jar')) continue;
  const filePath = path.join(modsDir, name);
  let url;
  if (modrinthVersions[name]) {
    const version = await fetch(`https://api.modrinth.com/v2/version/${modrinthVersions[name]}`).then(response => {
      if (!response.ok) throw new Error(`Modrinth no respondió por ${name}: HTTP ${response.status}`);
      return response.json();
    });
    const remoteFile = version.files.find(file => file.primary) || version.files[0];
    const localSha1 = sha(await fs.readFile(filePath), 'sha1');
    if (remoteFile.hashes.sha1 !== localSha1) throw new Error(`El archivo local ${name} no coincide con la versión publicada en Modrinth.`);
    url = remoteFile.url;
  } else if (directUrls[name]) {
    url = directUrls[name];
  } else {
    // These files are either the project's own mod or CurseForge-only mods.
    // Keep them on a host the pack owner controls; never bundle them in the installer.
    url = `${releaseBase}/${encodeURIComponent(name)}`;
  }
  files.push(await entry(name, 'mod', filePath, url));
}

const packSources = [
  ['FreshAnimations_v1.10.4.zip', 'FreshAnimations_v1.10.4.zip'],
  ['FA+All_Extensions-v1.8.1.zip', 'FA+All_Extensions-v1.8.1.zip']
];
for (const [name, sourceName] of packSources) {
  const version = await fetch(`https://api.modrinth.com/v2/version/${modrinthVersions[name]}`).then(response => {
    if (!response.ok) throw new Error(`Modrinth no respondió por ${name}: HTTP ${response.status}`);
    return response.json();
  });
  const remoteFile = version.files.find(file => file.primary) || version.files[0];
  const filePath = path.join(packsDir, sourceName);
  const localSha1 = sha(await fs.readFile(filePath), 'sha1');
  if (remoteFile.hashes.sha1 !== localSha1) throw new Error(`El archivo local ${name} no coincide con la versión publicada en Modrinth.`);
  files.push(await entry(name, 'resourcePack', filePath, remoteFile.url));
}

// Keep the optional visual/client-side additions from the exported client
// profile in a small editable file. These use direct, version-pinned Modrinth
// URLs and SHA-256 values verified against the local profile export.
const clientExtras = JSON.parse(await fs.readFile(path.join(import.meta.dirname, 'client-extras.json'), 'utf8'));
for (const extra of clientExtras.files) {
  if (files.some(file => file.kind === extra.kind && file.name === extra.name)) continue;
  files.push(extra);
}

const manifest = {
  profileVersion, minecraftVersion: '1.21.1', loader: 'fabric',
  loaderVersion: '0.18.4', javaMajor: 21, files
};
const outDir = path.join(import.meta.dirname, 'distribution');
await fs.mkdir(outDir, { recursive: true });
await fs.writeFile(path.join(outDir, 'manifest.json'), `${JSON.stringify(manifest, null, 2)}\n`);
console.log(`Generé ${files.length} archivos en ${path.join(outDir, 'manifest.json')}`);
console.log(`Manifiesto listo para el release ${releaseBase}. Verifica autorización para alojar Framework y Goblin Traders.`);
