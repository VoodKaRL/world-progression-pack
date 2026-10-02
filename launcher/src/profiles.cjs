const fs = require('node:fs/promises');
const path = require('node:path');
const crypto = require('node:crypto');
const defaultProfile = { id: 'world-progression', name: 'Verdania', official: true };
const validId = id => id === defaultProfile.id || /^world-progression-[a-f0-9]{16}$/.test(id);
async function read(appData) {
  const stored = await fs.readFile(path.join(appData, 'verdania-profiles.json'), 'utf8').then(JSON.parse).catch(() => ({}));
  const profiles = [defaultProfile, ...(stored.profiles || []).filter(profile => !profile.official && validId(profile.id) && profile.id !== defaultProfile.id && typeof profile.name === 'string')];
  const selected = profiles.some(profile => profile.id === stored.selected) ? stored.selected : defaultProfile.id;
  return { profiles, selected, maxRamMiB: Number.isInteger(stored.maxRamMiB) && stored.maxRamMiB >= 2048 && stored.maxRamMiB <= 16384 ? stored.maxRamMiB : 4096 };
}
async function save(appData, state) {
  await fs.mkdir(appData, { recursive: true });
  await fs.writeFile(path.join(appData, 'verdania-profiles.json'), JSON.stringify(state, null, 2));
}
async function select(appData, id) {
  const state = await read(appData);
  if (!state.profiles.some(profile => profile.id === id)) throw new Error('Perfil no válido.');
  state.selected = id; await save(appData, state); return state;
}
async function create(appData, name) {
  if (typeof name !== 'string' || !name.trim() || name.trim().length > 40) throw new Error('Escribe un nombre de 1 a 40 caracteres.');
  const state = await read(appData);
  if (state.profiles.length >= 12) throw new Error('Puedes tener hasta 12 perfiles.');
  const profile = { id: `world-progression-${crypto.randomBytes(8).toString('hex')}`, name: name.trim(), official: false };
  state.profiles.push(profile); state.selected = profile.id;
  await save(appData, state); return state;
}
async function settings(appData, maxRamMiB) {
  if (!Number.isInteger(maxRamMiB) || maxRamMiB < 2048 || maxRamMiB > 16384) throw new Error('Selecciona entre 2 y 16 GB de memoria.');
  const state = await read(appData); state.maxRamMiB = maxRamMiB; await save(appData, state); return state;
}
async function configFor(appData, base) {
  const state = await read(appData);
  const profile = state.profiles.find(profile => profile.id === state.selected);
  return { ...base, instanceId: profile.id, displayName: profile.name, maxRamMiB: state.maxRamMiB };
}
module.exports = { read, select, create, settings, configFor };
