'use strict';

// Preload for every tab. Web pages get nothing; internal founder:// pages get `window.founder`.
// The main process re-checks the sender on every call (see main/ipc.js), so this is not the only guard.
const { contextBridge, ipcRenderer } = require('electron');

if (location.protocol === 'founder:') {
  const invoke = (channel) => (...args) => ipcRenderer.invoke(channel, ...args);

  contextBridge.exposeInMainWorld('founder', {
    search: invoke('page:search'),
    resolve: invoke('page:resolve'),
    recent: invoke('page:recent'),
    getSettings: invoke('page:get-settings'),
    updateSettings: invoke('page:update-settings'),
    clear: invoke('page:clear'),
  });
}
