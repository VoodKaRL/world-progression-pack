const $ = id => document.getElementById(id);
let busy = false;
let launcherState = {};
let pendingPrismAction = null;
let toastTimer;
function ready() { return launcherState.signedIn && launcherState.installed && launcherState.checkedForSession; }
function view(name) {
  $('home-view').hidden = name !== 'home'; $('versions-view').hidden = name !== 'versions';
  $('nav-home').classList.toggle('active', name === 'home'); $('nav-versions').classList.toggle('active', name === 'versions');
  $('breadcrumb').textContent = name === 'home' ? 'Inicio' : 'Versiones / Verdania';
}
function updateButtons() {
  const verified = ready();
  $('launch').disabled = busy || !launcherState.configured;
  $('launch').classList.toggle('ready', Boolean(verified));
  $('launch').classList.toggle('verify', !verified);
  $('launch-title').textContent = busy ? 'PREPARANDO...' : verified ? 'LANZAR JUEGO' : 'VERIFICAR ARCHIVOS';
  $('launch-subtitle').textContent = `Minecraft ${launcherState.minecraftVersion || '1.21.1'} · Fabric`;
  $('launch-hint').textContent = busy ? 'Espera a que termine la preparación.' : verified ? 'Archivos verificados. Puedes iniciar tu partida.' : 'Verifica los archivos antes de jugar.';
  $('account-name').textContent = launcherState.signedIn ? launcherState.accountName : 'Iniciar sesión';
  $('account-initial').textContent = launcherState.signedIn ? launcherState.accountName.slice(0, 1).toUpperCase() : '?';
  $('account-dot').style.background = launcherState.signedIn ? '#00e66b' : '#6f7585';
  $('welcome-account').textContent = launcherState.signedIn ? `Hola, ${launcherState.accountName}` : 'Bienvenido a Verdania';
  $('selected-name').textContent = launcherState.displayName || 'Verdania';
  for (const id of ['account-button', 'version-picker', 'nav-add', 'new-profile', 'home-add', 'create-profile', 'save-settings']) $(id).disabled = busy;
  document.querySelectorAll('.version-card').forEach(button => button.disabled = busy);
}
function setStatus(title, message, progress = 0, success = false) {
  $('state-title').textContent = title; $('state-text').textContent = message;
  $('progress').style.width = `${progress}%`;
  $('status-dot').style.background = success ? '#00e66b' : '#9775fa';
  $('state-badge').textContent = success ? 'VERIFICADO' : 'CADA INICIO';
}
function toast(message) { $('toast').textContent = message; $('toast').classList.add('show'); clearTimeout(toastTimer); toastTimer = setTimeout(() => $('toast').classList.remove('show'), 6000); }
function pendingStatus() { setStatus('Verificación pendiente', 'Comprobaremos tus mods y el menú antes de iniciar Minecraft.'); }
async function refreshState() {
  launcherState = await window.launcher.status(); updateButtons(); renderProfiles();
  return launcherState;
}
async function action(run) {
  if (busy) return;
  busy = true; document.querySelector('.status-box').classList.add('working'); updateButtons();
  try { return await run(); }
  catch (error) { toast(error.message); setStatus('No se pudo completar', error.message); }
  finally { busy = false; document.querySelector('.status-box').classList.remove('working'); try { await refreshState(); } catch { updateButtons(); } }
}
function renderProfiles() {
  const grid = $('version-grid'); grid.replaceChildren();
  const query = $('profile-search').value.trim().toLowerCase();
  const profiles = (launcherState.profileState?.profiles || []).filter(profile => profile.name.toLowerCase().includes(query));
  for (const profile of profiles) {
    const card = document.createElement('button'); card.className = 'version-card'; card.disabled = busy;
    card.classList.toggle('selected', profile.id === launcherState.profileState.selected);
    const art = document.createElement('div'); art.className = 'version-art';
    const badge = document.createElement('span'); badge.textContent = profile.official ? 'MODPACK OFICIAL' : 'PERFIL PERSONAL';
    const logo = document.createElement('img'); logo.src = 'assets/verdania-logo.png'; logo.alt = '';
    const title = document.createElement('strong'); title.textContent = 'VERDANIA';
    const version = document.createElement('small'); version.textContent = 'Minecraft 1.21.1 · Fabric';
    art.append(badge, logo, title, version);
    const caption = document.createElement('div'); caption.className = 'version-caption';
    const name = document.createElement('strong'); name.textContent = profile.name;
    const selected = document.createElement('span'); selected.textContent = profile.id === launcherState.profileState.selected ? 'Seleccionado' : 'Seleccionar';
    caption.append(name, selected); card.append(art, caption); grid.append(card);
    card.onclick = async () => {
      const result = await action(() => window.launcher.selectProfile(profile.id));
      if (result) { pendingStatus(); view('home'); }
    };
  }
  $('no-results').hidden = profiles.length !== 0 || !query;
  if (!query) {
    const add = document.createElement('button'); add.className = 'new-card'; add.disabled = busy;
    add.innerHTML = '<svg><use href="#plus"/></svg><strong>Nuevo perfil</strong><small>El mismo pack.<br>Un espacio para otra partida.</small>';
    add.onclick = openProfile; grid.append(add);
  }
}
function openProfile() { if (busy) return; $('profile-name').value = ''; $('profile-dialog').showModal(); $('profile-name').focus(); }
function openSettings() { $('ram').value = String(launcherState.profileState?.maxRamMiB || 4096); $('settings-dialog').showModal(); }
function explainPrism(next) { pendingPrismAction = next; $('prism-dialog').showModal(); }
async function connect() {
  setStatus('Conecta tu cuenta', 'En Prism, agrega tu cuenta Microsoft en Ajustes → Cuentas y cierra su ventana para volver.');
  const result = await action(() => window.launcher.signIn());
  if (result) { toast('Cuenta conectada. Ahora verifica los archivos.'); pendingStatus(); }
  return result;
}
async function verify() {
  setStatus('Verificando archivos', 'Comprobando mods, recursos y menú. Solo se descargarán los archivos que falten o hayan cambiado.');
  const result = await action(() => window.launcher.install());
  if (result) setStatus('Todo listo para jugar', 'Los archivos están verificados. Pulsa Lanzar juego.', 100, true);
}
async function launchGame() {
  const result = await action(() => window.launcher.play());
  if (result) {
    localStorage.setItem('verdania-first-launch-explained', 'true');
    setStatus('Iniciando Minecraft', 'Prism preparará Java y Fabric si faltan. Minecraft se abrirá en unos momentos.', 100, true);
  }
}
$('launch').onclick = async () => {
  if (busy) return;
  if (!launcherState.signedIn) { explainPrism(async () => { if (await connect()) await verify(); }); return; }
  if (!ready()) { await verify(); return; }
  if (!localStorage.getItem('verdania-first-launch-explained')) {
    $('first-launch-dialog').showModal(); return;
  }
  await launchGame();
};
$('continue-first-launch').onclick = async () => { $('first-launch-dialog').close(); await launchGame(); };
$('account-button').onclick = () => { if (!busy) explainPrism(connect); };
$('continue-prism').onclick = async () => { $('prism-dialog').close(); const next = pendingPrismAction; pendingPrismAction = null; if (next) await next(); };
$('nav-home').onclick = () => view('home');
for (const id of ['nav-versions', 'version-picker', 'see-versions']) $(id).onclick = () => view('versions');
for (const id of ['nav-add', 'new-profile', 'home-add']) $(id).onclick = openProfile;
for (const id of ['nav-settings', 'hero-settings']) $(id).onclick = openSettings;
$('profile-search').oninput = renderProfiles;
$('create-profile').onclick = async () => {
  const result = await action(() => window.launcher.createProfile($('profile-name').value));
  if (result) { $('profile-dialog').close(); pendingStatus(); view('versions'); toast('Perfil creado. Selecciónalo y verifica sus archivos para jugar.'); }
};
$('profile-name').onkeydown = event => { if (event.key === 'Enter') $('create-profile').click(); };
$('save-settings').onclick = async () => {
  const result = await action(() => window.launcher.settings(Number($('ram').value)));
  if (result) { $('settings-dialog').close(); toast('Ajustes guardados. Se aplicarán al iniciar Minecraft.'); }
};
$('settings-account').onclick = () => { $('settings-dialog').close(); explainPrism(connect); };
$('settings-folder').onclick = () => action(() => window.launcher.openFolder());
$('open-mods').onclick = () => action(() => window.launcher.openFolder('mods'));
$('open-worlds').onclick = () => action(() => window.launcher.openFolder('saves'));
for (const close of document.querySelectorAll('[data-close]')) close.onclick = () => $(close.dataset.close).close();
refreshState().then(() => { view('home'); pendingStatus(); }).catch(error => setStatus('No se pudo preparar el launcher', error.message));
