const path = require('node:path');
const fs = require('node:fs/promises');
const crypto = require('node:crypto');
const { spawn } = require('node:child_process');

const prismRelease = {
  url: 'https://github.com/PrismLauncher/PrismLauncher/releases/download/11.1.1/PrismLauncher-Windows-MSVC-Portable-11.1.1.zip',
  sha256: 'ab35a770fb06d89d2ccc098079db5db329fb4e68f42b72babd8b095efde3d2d7'
};
const safeName = value => typeof value === 'string' && value.length <= 180 && value !== '.' && value !== '..' &&
  path.basename(value) === value && !/[\\/\0]/.test(value);
const exists = async file => fs.access(file).then(() => true, () => false);

function run(executable, args) {
  return new Promise((resolve, reject) => {
    const child = spawn(executable, args, { windowsHide: true, stdio: ['ignore', 'pipe', 'pipe'] });
    let output = '';
    for (const stream of [child.stdout, child.stderr]) stream?.on('data', chunk => { output = (output + chunk.toString()).slice(-8000); });
    child.once('error', reject);
    child.once('close', code => code === 0 ? resolve(output) : reject(new Error(output.trim() || `${executable} terminó con código ${code}.`)));
  });
}

function roots(appData) {
  return {
    engine: path.join(appData, 'prism-engine'),
    data: path.join(appData, 'prism-data'),
    cache: path.join(appData, 'cache'),
    exe: path.join(appData, 'prism-engine', 'prismlauncher.exe'),
    pending: path.join(appData, 'prism-import-pending.json')
  };
}

async function ensurePrism(appData) {
  if (process.platform !== 'win32') throw new Error('El inicio directo de Minecraft está preparado para Windows.');
  const paths = roots(appData);
  if (await exists(paths.exe)) return paths;
  await fs.mkdir(paths.engine, { recursive: true });
  await fs.mkdir(paths.cache, { recursive: true });
  const archivePath = path.join(paths.cache, 'prismlauncher-windows-portable-11.1.1.zip');
  let archive;
  try { archive = await fs.readFile(archivePath); } catch {}
  if (!archive || crypto.createHash('sha256').update(archive).digest('hex') !== prismRelease.sha256) {
    const response = await fetch(prismRelease.url);
    if (!response.ok) throw new Error(`No se pudo descargar Prism Launcher desde su página oficial (HTTP ${response.status}).`);
    archive = Buffer.from(await response.arrayBuffer());
    if (crypto.createHash('sha256').update(archive).digest('hex') !== prismRelease.sha256) throw new Error('Falló la verificación SHA-256 de Prism Launcher.');
    await fs.writeFile(archivePath, archive);
  }
  await run('tar.exe', ['-xf', archivePath, '-C', paths.engine]);
  if (!(await exists(paths.exe))) throw new Error('El archivo oficial de Prism no contiene prismlauncher.exe.');
  await fs.mkdir(paths.data, { recursive: true });
  return paths;
}

async function findInstance(config, appData) {
  const paths = roots(appData);
  const instancesRoot = path.join(paths.data, 'instances');
  let entries;
  try { entries = await fs.readdir(instancesRoot, { withFileTypes: true }); } catch { return null; }
  for (const entry of entries) {
    if (!entry.isDirectory() || !safeName(entry.name) || !/^world-progression(?:-|$)/i.test(entry.name)) continue;
    if (config.instanceId && entry.name !== config.instanceId) continue;
    const instanceDir = path.join(instancesRoot, entry.name);
    const pack = await fs.readFile(path.join(instanceDir, 'mmc-pack.json'), 'utf8').then(JSON.parse).catch(() => null);
    if (!pack || !Array.isArray(pack.components)) continue;
    const components = new Map(pack.components.map(component => [component.uid, component.version]));
    if (components.get('net.minecraft') !== config.minecraftVersion || components.get('net.fabricmc.fabric-loader') !== config.loaderVersion) continue;
    const minecraftDir = path.join(instanceDir, '.minecraft');
    return { id: entry.name, instanceDir, minecraftDir, markerPath: path.join(minecraftDir, '.world-progression-profile.json') };
  }
  return null;
}

async function accountStatus(appData) {
  const list = await fs.readFile(path.join(roots(appData).data, 'accounts.json'), 'utf8').then(JSON.parse).catch(() => null);
  const accounts = Array.isArray(list?.accounts) ? list.accounts : [];
  const valid = accounts.filter(account => account.type === 'MSA' && account.profile?.id && account.profile?.name &&
    (account.entitlement?.ownsMinecraft || account.entitlement?.canPlayMinecraft));
  const account = valid.find(account => account.active) || valid[0];
  return { signedIn: Boolean(account), accountName: account?.profile.name || '' };
}

async function configureEngine(paths) {
  await fs.mkdir(paths.data, { recursive: true });
  const file = path.join(paths.data, 'prismlauncher.cfg');
  let content = await fs.readFile(file, 'utf8').catch(() => '[General]\n');
  // Change only the General section; preserve account and other settings.
  if (!/^\[General\]\r?$/m.test(content)) content = `[General]\n${content}`;
  content = content.replace(/(^\[General\]\r?\n)([\s\S]*?)(?=^\[|$(?![\s\S]))/m, (_match, header, body) => {
    for (const [key, value] of Object.entries({ CloseAfterLaunch: 'true', QuitAfterGameStop: 'true', ShowConsole: 'false', AutomaticJavaSwitch: 'true', AutomaticJavaDownload: 'true' })) {
      const pattern = new RegExp(`^${key}=.*$`, 'm');
      body = pattern.test(body) ? body.replace(pattern, `${key}=${value}`) : `${body.trimEnd()}\n${key}=${value}\n`;
    }
    return header + body;
  });
  await fs.writeFile(file, content);
}

async function signIn(appData) {
  const paths = await ensurePrism(appData);
  await requireEngineStopped(paths);
  await configureEngine(paths);
  // Prism owns the Microsoft authentication flow and refresh tokens.
  // Wait for it to close so a later --launch starts without a main window.
  await run(paths.exe, ['-d', paths.data]);
  const account = await accountStatus(appData);
  if (!account.signedIn) throw new Error('No se encontró una cuenta Microsoft con Minecraft Java. Agrega la cuenta en Prism y cierra su ventana para continuar.');
  return { ...account, message: `Cuenta ${account.accountName} conectada. Ahora instala o busca actualizaciones.` };
}

async function prepareInstance(config, appData) {
  const existing = await findInstance(config, appData);
  if (existing) return existing;
  const id = config.instanceId || 'world-progression';
  const instanceDir = path.join(roots(appData).data, 'instances', id);
  if (await exists(instanceDir)) throw new Error('La carpeta world-progression ya existe pero no coincide con la versión esperada. Revisa esa instancia antes de continuar.');
  const minecraftDir = path.join(instanceDir, '.minecraft');
  await fs.mkdir(minecraftDir, { recursive: true });
  await fs.writeFile(path.join(instanceDir, 'instance.cfg'), `[General]\nInstanceType=OneSix\nname=${config.displayName.replace(/[\r\n]/g, ' ')}\niconKey=default\n`);
  await fs.writeFile(path.join(instanceDir, 'mmc-pack.json'), JSON.stringify({ formatVersion: 1, components: [
    { uid: 'net.minecraft', version: config.minecraftVersion, important: true },
    { uid: 'net.fabricmc.fabric-loader', version: config.loaderVersion }
  ] }, null, 2));
  return { id, instanceDir, minecraftDir, markerPath: path.join(minecraftDir, '.world-progression-profile.json') };
}

async function launchInstalled(appData, config) {
  const account = await accountStatus(appData);
  if (!account.signedIn) throw new Error('Primero inicia sesión con tu cuenta Microsoft.');
  const paths = await ensurePrism(appData);
  await requireEngineStopped(paths);
  const instance = await findInstance(config, appData);
  if (!instance || !(await exists(instance.markerPath))) throw new Error('Primero instala o busca actualizaciones.');
  await configureEngine(paths);
  const instanceConfigPath = path.join(instance.instanceDir, 'instance.cfg');
  let instanceConfig = await fs.readFile(instanceConfigPath, 'utf8');
  const memory = { OverrideMemory: 'true', MinMemAlloc: '1024', MaxMemAlloc: String(config.maxRamMiB || 4096) };
  for (const [key, value] of Object.entries(memory)) {
    const regex = new RegExp(`^${key}=.*$`, 'm');
    instanceConfig = regex.test(instanceConfig) ? instanceConfig.replace(regex, `${key}=${value}`) : `${instanceConfig.trimEnd()}\n${key}=${value}\n`;
  }
  await fs.writeFile(instanceConfigPath, instanceConfig);
  await new Promise((resolve, reject) => {
    const child = spawn(paths.exe, ['-d', paths.data, '--launch', instance.id, '--profile', account.accountName],
      { detached: true, stdio: 'ignore', windowsHide: true });
    child.once('error', reject);
    child.once('spawn', () => { child.unref(); resolve(); });
  });
  return { ready: true, message: 'Inicio solicitado. Prism preparará Java/Fabric si faltan y abrirá Minecraft.' };
}

async function requireEngineStopped(paths) {
  const output = await run('powershell.exe', ['-NoProfile', '-NonInteractive', '-Command',
    "ConvertTo-Json -Compress -InputObject @(Get-CimInstance Win32_Process -Filter \"Name = 'prismlauncher.exe'\" -ErrorAction Stop | Select-Object ExecutablePath,CommandLine)"]);
  const processes = output.trim() ? JSON.parse(output) : [];
  if (processes.some(item => item.ExecutablePath?.toLowerCase() === paths.exe.toLowerCase() ||
    item.CommandLine?.toLowerCase().includes(paths.data.toLowerCase()))) {
    throw new Error('Cierra la ventana de Prism o la partida anterior y vuelve a intentarlo. Así Minecraft podrá iniciar sin dejar la ventana principal de Prism abierta.');
  }
}

module.exports = { findInstance, accountStatus, signIn, prepareInstance, launchInstalled };

