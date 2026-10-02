const { test } = require('node:test');
const assert = require('node:assert/strict');
const fs = require('node:fs/promises');
const os = require('node:os');
const path = require('node:path');
const crypto = require('node:crypto');
const { destinationFor, installProfileFiles } = require('../src/profile-installer.cjs');
const data = Buffer.from('verified-content');
const sha256 = crypto.createHash('sha256').update(data).digest('hex');
const entry = (kind, name) => ({ kind, name, sha256, url: 'https://example.test/asset' });
test('rejects manifest paths outside pack-owned folders on Windows', () => {
  for (const name of ['../outside', 'fancymenu/../../outside', 'fancymenu/C:/a', 'fancymenu/a\\b', 'fancymenu/NUL.txt', 'fancymenu/a.', '/fancymenu/a', 'other/a']) {
    assert.throws(() => destinationFor('C:/instance', entry('config', name)));
  }
  assert.throws(() => destinationFor('C:/instance', entry('mod', '../bad.jar')));
});
test('upgrades an old profile, installs panorama, and retains user worlds and options', async () => {
  const root = await fs.mkdtemp(path.join(os.tmpdir(), 'verdania-install-'));
  try {
    const markerPath = path.join(root, '.world-progression-profile.json');
    await fs.mkdir(path.join(root, 'mods'));
    await fs.mkdir(path.join(root, 'saves/world'), { recursive: true });
    await fs.writeFile(path.join(root, 'saves/world/level.dat'), 'user-world');
    await fs.writeFile(path.join(root, 'mods/old.jar'), 'old');
    await fs.writeFile(path.join(root, 'mods/user.jar'), 'user-mod');
    await fs.writeFile(path.join(root, 'options.txt'), 'volume:0.5\nresourcePacks:["vanilla"]\n');
    await fs.writeFile(markerPath, JSON.stringify({ files: ['mod/old.jar'] }));
    const manifest = { profileVersion: '0.31.1', files: [entry('mod', 'new.jar'), entry('resourcePack', 'Fresh.zip'), entry('config', 'fancymenu/panoramas/forest/panorama/panorama_0.png')] };
    let downloads = 0;
    const fetchFile = async () => { downloads++; return { ok: true, arrayBuffer: async () => data }; };
    await installProfileFiles(manifest, { minecraftDir: root, markerPath }, fetchFile);
    assert.equal(await fs.readFile(path.join(root, 'config/fancymenu/panoramas/forest/panorama/panorama_0.png'), 'utf8'), data.toString());
    await assert.rejects(fs.access(path.join(root, 'mods/old.jar')));
    assert.equal(await fs.readFile(path.join(root, 'mods/user.jar'), 'utf8'), 'user-mod');
    assert.equal(await fs.readFile(path.join(root, 'saves/world/level.dat'), 'utf8'), 'user-world');
    assert.match(await fs.readFile(path.join(root, 'options.txt'), 'utf8'), /volume:0.5/);
    await installProfileFiles(manifest, { minecraftDir: root, markerPath }, fetchFile);
    assert.equal(downloads, 3, 'unchanged verified files are reused');
  } finally { await fs.rm(root, { recursive: true, force: true }); }
});
test('hash failure does not overwrite installed assets or remove old mods', async () => {
  const root = await fs.mkdtemp(path.join(os.tmpdir(), 'verdania-hash-'));
  try {
    const markerPath = path.join(root, '.world-progression-profile.json');
    await fs.mkdir(path.join(root, 'mods'));
    await fs.writeFile(path.join(root, 'mods/old.jar'), 'old');
    await fs.writeFile(path.join(root, 'mods/new.jar'), 'existing');
    await fs.writeFile(markerPath, JSON.stringify({ files: ['mod/old.jar'] }));
    await assert.rejects(installProfileFiles({ files: [entry('mod', 'new.jar')] }, { minecraftDir: root, markerPath }, async () => ({ ok: true, arrayBuffer: async () => Buffer.from('wrong') })), /SHA-256/);
    assert.equal(await fs.readFile(path.join(root, 'mods/new.jar'), 'utf8'), 'existing');
    assert.equal(await fs.readFile(path.join(root, 'mods/old.jar'), 'utf8'), 'old');
  } finally { await fs.rm(root, { recursive: true, force: true }); }
});
