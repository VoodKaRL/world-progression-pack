const { contextBridge, ipcRenderer } = require('electron');
contextBridge.exposeInMainWorld('launcher', {
  status: () => ipcRenderer.invoke('launcher:status'),
  signIn: () => ipcRenderer.invoke('launcher:sign-in'),
  install: () => ipcRenderer.invoke('launcher:install'),
  play: () => ipcRenderer.invoke('launcher:play'),
  openFolder: section => ipcRenderer.invoke('launcher:open-folder', section),
  selectProfile: id => ipcRenderer.invoke('launcher:select-profile', id),
  createProfile: name => ipcRenderer.invoke('launcher:create-profile', name),
  settings: ram => ipcRenderer.invoke('launcher:settings', ram)
});
