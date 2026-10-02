const { test } = require('node:test');
const assert = require('node:assert/strict');
const fs = require('node:fs/promises');
const path = require('node:path');
const os = require('node:os');
const vm = require('node:vm');
const prism = require('../src/prism.cjs');

test('isolated instance preserves worlds and reports only account metadata', async () => {
  const root = await fs.mkdtemp(path.join(os.tmpdir(), 'wp-flow-'));
  try {
    const config = { minecraftVersion: '1.21.1', loaderVersion: '0.19.5' };
    const instance = await prism.prepareInstance(config, root);
    await fs.mkdir(path.join(instance.minecraftDir, 'saves'));
    assert.equal((await prism.prepareInstance(config, root)).id, instance.id);
    assert.ok(await fs.stat(path.join(instance.minecraftDir, 'saves')));
    assert.equal((JSON.parse(await fs.readFile(path.join(instance.instanceDir, 'mmc-pack.json')))).components[1].version, config.loaderVersion);
    assert.equal((await prism.accountStatus(root)).signedIn, false);
    await fs.writeFile(path.join(root, 'prism-data', 'accounts.json'), JSON.stringify({ accounts: [
      { type: 'Offline', profile: { id: 'offline', name: 'Offline' }, entitlement: { ownsMinecraft: true } },
      { type: 'MSA', active: true, profile: { id: 'java', name: 'Player' }, entitlement: { ownsMinecraft: true }, msa: { token: 'private' } }
    ] }));
    assert.deepEqual(await prism.accountStatus(root), { signedIn: true, accountName: 'Player' });
  } finally { await fs.rm(root, { recursive: true, force: true }); }
});

test('backend blocks play until successful update; failed update blocks it again', async () => {
  const root = await fs.mkdtemp(path.join(os.tmpdir(), 'wp-gate-'));
  try {
    const handlers = new Map();
    let signedIn = false, failUpdate = false, launched = 0;
    const config = { minecraftVersion: '1.21.1', loaderVersion: '0.19.5', manifestUrl: 'https://example.com/manifest.json' };
    const fakePrism = {
      accountStatus: async () => ({ signedIn }),
      prepareInstance: () => prism.prepareInstance(config, root),
      findInstance: () => prism.findInstance(config, root),
      launchInstalled: async () => { launched++; return { ready: true }; }
    };
    vm.runInNewContext(await fs.readFile(path.join(__dirname, '../src/main.cjs'), 'utf8'), {
      require: name => name === 'electron' ? { app: { getPath: () => root, whenReady: () => ({ then() {} }), on() {} }, ipcMain: { handle: (name, handler) => handlers.set(name, handler) } } : name === './config.json' ? config : name === './prism.cjs' ? fakePrism : require(name),
      __dirname: path.join(__dirname, '../src'), process, Buffer,
      fetch: async () => { if (failUpdate) throw new Error('offline'); return { ok: true, json: async () => ({ ...config, loader: 'fabric', files: [{ name: 'test.jar', kind: 'mod', url: 'https://example.com/mod', sha256: require('node:crypto').createHash('sha256').update('test').digest('hex') } ] }), arrayBuffer: async () => Buffer.from('test') }; }
    });
    await assert.rejects(handlers.get('launcher:play')(), /actualizaciones/);
    await assert.rejects(handlers.get('launcher:install')(), /sesión/);
    signedIn = true;
    await handlers.get('launcher:install')();
    assert.equal((await handlers.get('launcher:status')()).checkedForSession, true);
    await handlers.get('launcher:play')();
    assert.equal(launched, 1);
    failUpdate = true;
    await assert.rejects(handlers.get('launcher:install')(), /offline/);
    await assert.rejects(handlers.get('launcher:play')(), /actualizaciones/);
  } finally { await fs.rm(root, { recursive: true, force: true }); }
});
