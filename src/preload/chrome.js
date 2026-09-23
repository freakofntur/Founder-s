'use strict';

// Preload for the browser UI (tab strip + address bar). Exposes `window.browser`.
const { contextBridge, ipcRenderer } = require('electron');

const invoke = (channel) => (...args) => ipcRenderer.invoke(channel, ...args);
const subscribe = (channel) => (callback) => {
  const listener = (_event, payload) => callback(payload);
  ipcRenderer.on(channel, listener);
  return () => ipcRenderer.removeListener(channel, listener);
};

contextBridge.exposeInMainWorld('browser', {
  newTab: invoke('browser:new-tab'),
  closeTab: invoke('browser:close-tab'),
  activateTab: invoke('browser:activate-tab'),
  navigate: invoke('browser:navigate'),
  submit: invoke('browser:submit'),
  back: invoke('browser:back'),
  forward: invoke('browser:forward'),
  reload: invoke('browser:reload'),
  stop: invoke('browser:stop'),
  suggest: invoke('browser:suggest'),
  setOverlay: invoke('browser:set-overlay'),
  toggleBookmark: invoke('browser:toggle-bookmark'),

  onState: subscribe('tabs:state'),
  onFocusAddressBar: subscribe('ui:focus-address-bar'),
});
