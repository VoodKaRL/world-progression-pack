const { test } = require('node:test');
const assert = require('node:assert/strict');
const fs = require('node:fs/promises');
const os = require('node:os');
const path = require('node:path');
const profiles = require('../src/profiles.cjs');
test('profiles persist selection and use separate instance IDs', async () => {
  const root = await fs.mkdtemp(path.join(os.tmpdir(), 'verdania-profiles-'));
  try {
    assert.equal((await profiles.read(root)).selected, 'world-progression');
    const created = await profiles.create(root, 'Otra partida');
    const id = created.selected;
    assert.notEqual(id, 'world-progression');
    assert.equal((await profiles.configFor(root, { minecraftVersion: '1.21.1' })).instanceId, id);
    await profiles.select(root, 'world-progression');
    assert.equal((await profiles.read(root)).profiles.length, 2);
    await profiles.settings(root, 6144);
    assert.equal((await profiles.read(root)).maxRamMiB, 6144);
    await assert.rejects(profiles.select(root, '../bad'));
    await assert.rejects(profiles.create(root, '   '));
    await assert.rejects(profiles.settings(root, 0));
  } finally { await fs.rm(root, { recursive: true, force: true }); }
});
