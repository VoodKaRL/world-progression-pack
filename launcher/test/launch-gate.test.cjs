const { test } = require('node:test');
const assert = require('node:assert/strict');
const fs = require('node:fs/promises');
const path = require('node:path');
const os = require('node:os');
const vm = require('node:vm');
const profiles = require('../src/profiles.cjs');
test('every session and profile must verify before playing; failed verification blocks play', async () => {
  const root = await fs.mkdtemp(path.join(os.tmpdir(), 'verdania-gate-'));
  try {
    const config = { manifestUrl: 'https://example.test/manifest.json', minecraftVersion: '1.21.1', loaderVersion: '0.19.5', displayName: 'Verdania' };
    const instance = { minecraftDir: root, markerPath: path.join(root, 'marker') };
    let fail = false;
    let launched = 0;
    const handlers = new Map();
    const source = await fs.readFile(path.join(__dirname, '../src/main.cjs'), 'utf8');
    const electron = { app: { getPath: () => root, whenReady: () => ({ then() {} }), on() {} },
      ipcMain: { handle: (name, callback) => handlers.set(name, callback) } };
    const mocks = {
      electron, './config.json': config, './profiles.cjs': profiles,
      './prism.cjs': { accountStatus: async () => ({ signedIn: true, accountName: 'Test' }), findInstance: async () => instance,
        prepareInstance: async () => instance, launchInstalled: async () => { launched++; return { ready: true }; } },
      './profile-installer.cjs': { installProfileFiles: async () => { if (fail) throw new Error('SHA-256 failed'); await fs.writeFile(instance.markerPath, '{}'); } }
    };
    const load = () => vm.runInNewContext(source, { require: name => name in mocks ? mocks[name] : require(name), __dirname: path.join(__dirname, '../src'), process,
      fetch: async () => ({ ok: true, json: async () => ({ minecraftVersion: '1.21.1', loader: 'fabric', loaderVersion: '0.19.5', files: [{}] }) }) });
    const invoke = (name, ...args) => handlers.get(`launcher:${name}`)(null, ...args);
    load();
    await assert.rejects(invoke('play'), /Antes de jugar/);
    await invoke('install');
    assert.equal((await invoke('status')).checkedForSession, true);
    await invoke('play'); assert.equal(launched, 1);
    await invoke('create-profile', 'Segundo');
    assert.equal((await invoke('status')).checkedForSession, false);
    await assert.rejects(invoke('play'), /Antes de jugar/);
    await invoke('install');
    fail = true; await assert.rejects(invoke('install'), /SHA-256/);
    await assert.rejects(invoke('play'), /Antes de jugar/);
    fail = false; await invoke('install');
    load(); // A new process always starts unchecked, even with installed files.
    assert.equal((await invoke('status')).checkedForSession, false);
  } finally { await fs.rm(root, { recursive: true, force: true }); }
});
