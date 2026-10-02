const { app, BrowserWindow, ipcMain, shell, Menu } = require('electron');
const path = require('node:path');
const fs = require('node:fs/promises');
const { installProfileFiles } = require('./profile-installer.cjs');
const config = require('./config.json');
const prism = require('./prism.cjs');
const profiles = require('./profiles.cjs');
const currentConfig = () => profiles.configFor(app.getPath('userData'), config);

let win;
let checkedForSession = false;
let engineBusy = false;
async function engineAction(task) {
  if (engineBusy) throw new Error('Espera a que termine la operación actual.');
  engineBusy = true;
  try { return await task(); } finally { engineBusy = false; }
}
const prismData = path.join(app.getPath('userData'), 'prism-data');
const manifestMissing = !config.manifestUrl || config.manifestUrl.includes('CONFIGURE-HOST') || !config.manifestUrl.startsWith('https://');
const safeName = (s) => typeof s === 'string' && s.length <= 180 && s !== '.' && s !== '..' && path.basename(s) === s && !/[\\/\0]/.test(s);

function createWindow() {
  Menu.setApplicationMenu(null);
  win = new BrowserWindow({ width: 1360, height: 850, minWidth: 1040, minHeight: 700,
    backgroundColor: '#0b0c11', title: 'Verdania Launcher', autoHideMenuBar: true,
    icon: path.join(__dirname, 'assets', 'verdania.ico'),
    webPreferences: { preload: path.join(__dirname, 'preload.cjs'), contextIsolation: true, nodeIntegration: false }
  });
  win.loadFile(path.join(__dirname, 'index.html'));
}
app.whenReady().then(createWindow);
app.on('window-all-closed', () => { if (process.platform !== 'darwin') app.quit(); });

ipcMain.handle('launcher:status', async () => {
  const selectedConfig = await currentConfig();
  const instance = await prism.findInstance(selectedConfig, app.getPath('userData'));
  const installed = Boolean(instance && await pathExists(instance.markerPath));
  return {
    displayName: selectedConfig.displayName, minecraftVersion: config.minecraftVersion,
    profileState: await profiles.read(app.getPath('userData')),
    instanceDir: instance?.minecraftDir || path.join(prismData, 'instances'),
    configured: !manifestMissing, needsManifest: manifestMissing, installed,
    ...(await prism.accountStatus(app.getPath('userData'))), checkedForSession
  };
});
ipcMain.handle('launcher:open-folder', async (_event, section) => {
  const instance = await prism.findInstance(await currentConfig(), app.getPath('userData'));
  if (section && !instance) throw new Error('Primero instala el modpack para explorar sus archivos.');
  if (section && !['saves', 'mods'].includes(section)) throw new Error('Carpeta no válida.');
  const root = instance?.minecraftDir || path.join(prismData, 'instances');
  const folder = section ? path.join(root, section) : root;
  await fs.mkdir(folder, { recursive: true });
  return shell.openPath(folder);
});

async function syncProfile() {
  if (manifestMissing) throw new Error('Falta publicar el manifiesto del modpack en una URL HTTPS y configurarla en src/config.json.');
  const res = await fetch(config.manifestUrl);
  if (!res.ok) throw new Error(`No se pudo consultar actualizaciones (HTTP ${res.status}).`);
  const manifest = await res.json();
  if (manifest.minecraftVersion !== config.minecraftVersion || manifest.loader !== 'fabric' || manifest.loaderVersion !== config.loaderVersion || !Array.isArray(manifest.files) || !manifest.files.length) throw new Error('El manifiesto no coincide con la versión de Minecraft/Fabric de este launcher.');
  return manifest;
}

ipcMain.handle('launcher:sign-in', () => engineAction(async () => {
  checkedForSession = false;
  return prism.signIn(app.getPath('userData'));
}));
ipcMain.handle('launcher:install', () => engineAction(async () => {
  checkedForSession = false;
  if (!(await prism.accountStatus(app.getPath('userData'))).signedIn) throw new Error('Primero inicia sesión con tu cuenta Microsoft.');
  const manifest = await syncProfile();
  const instance = await prism.prepareInstance(await currentConfig(), app.getPath('userData'));
  await installProfileFiles(manifest, instance);
  checkedForSession = true;
  return { message: `Perfil ${manifest.profileVersion} actualizado en la instancia de Verdania.` };
}));

async function pathExists(filePath) {
  try { await fs.access(filePath); return true; } catch { return false; }
}

ipcMain.handle('launcher:play', () => engineAction(async () => {
  if (!checkedForSession) throw new Error('Antes de jugar, pulsa Instalar / buscar actualizaciones y espera a que termine.');
  return prism.launchInstalled(app.getPath('userData'), await currentConfig());
}));

ipcMain.handle('launcher:select-profile', (_event, id) => engineAction(async () => {
  checkedForSession = false;
  return profiles.select(app.getPath('userData'), id);
}));
ipcMain.handle('launcher:create-profile', (_event, name) => engineAction(async () => {
  checkedForSession = false;
  return profiles.create(app.getPath('userData'), name);
}));
ipcMain.handle('launcher:settings', (_event, ram) => engineAction(() => profiles.settings(app.getPath('userData'), ram)));

