import fs from 'node:fs/promises';
import path from 'node:path';
import crypto from 'node:crypto';
const root = path.resolve(import.meta.dirname, '..');
const filename = path.join(import.meta.dirname, 'client-profile-sources.json');
const previous = JSON.parse((await fs.readFile(filename, 'utf8')).replace(/^\uFEFF/, ''));
const files = [];
for (const [directory, kind, extension] of [
  [path.join(root, 'mods'), 'mod', '.jar'],
  [path.join(process.env.APPDATA, 'ModrinthApp/profiles/pruebas/resourcepacks'), 'resourcePack', '.zip']
]) {
  for (const name of (await fs.readdir(directory)).filter(name => name.endsWith(extension)).sort()) {
    const sha1 = crypto.createHash('sha1').update(await fs.readFile(path.join(directory, name))).digest('hex');
    files.push({ name, kind, sha1 });
  }
}
const response = await fetch('https://api.modrinth.com/v2/version_files', {
  method: 'POST', headers: { 'Content-Type': 'application/json', 'User-Agent': 'VerdaniaLauncher/0.6.0' },
  body: JSON.stringify({ hashes: files.map(file => file.sha1), algorithm: 'sha1' })
});
if (!response.ok) throw new Error(`Modrinth: HTTP ${response.status}`);
const versions = await response.json();
for (const file of files) {
  const remote = versions[file.sha1]?.files.find(remote => remote.hashes.sha1 === file.sha1);
  const old = previous.files.find(old => old.name === file.name && old.sha1 === file.sha1);
  file.url = remote?.url || old?.url || null;
}
previous.profileVersion = '0.31.1';
previous.files = files;
await fs.writeFile(filename, `${JSON.stringify(previous, null, 2)}\n`);
console.log(JSON.stringify({ total: files.length, releaseAssets: files.filter(file => !file.url).map(file => file.name) }));
