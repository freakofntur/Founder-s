'use strict';

const { BrowserWindow } = require('./browser-window');
const { createIndexer } = require('./indexer');

/**
 * App-wide state shared by IPC handlers and the menu: the services and the open windows.
 * @param {ReturnType<import('./services').createServices>} services
 */
function createAppContext(services) {
  /** @type {Set<BrowserWindow>} */
  const windows = new Set();
  const indexer = createIndexer(services);
  let lastFocused = null;

  const ctx = {
    services,
    windows,

    openWindow(url) {
      const win = new BrowserWindow({ services, url });
      windows.add(win);
      lastFocused = win;
      win.window.on('focus', () => (lastFocused = win));
      win.on('closed', () => {
        windows.delete(win);
        if (lastFocused === win) lastFocused = null;
      });

      win.tabs.on('navigated', (tab) => {
        if (/^https?:/.test(tab.url)) services.history.record(tab.url, tab.title);
        win.pushState(); // bookmark star depends on the url
      });
      win.tabs.on('title', (tab) => services.history.setTitle(tab.url, tab.title));
      win.tabs.on('loaded', (tab) => indexer.schedule(tab));
      return win;
    },

    /** The focused window, else the most recently focused one. */
    currentWindow() {
      return lastFocused && !lastFocused.isDestroyed ? lastFocused : [...windows][0] || null;
    },

    windowForChrome(webContents) {
      return [...windows].find((w) => w.chrome.webContents === webContents) || null;
    },

    windowForTab(tabId) {
      return [...windows].find((w) => w.tabs.get(tabId)) || null;
    },

    listTabs() {
      return [...windows].flatMap((w) =>
        w.tabs.list().map((t) => ({ id: t.id, url: t.url, title: t.title, active: t === w.tabs.active })),
      );
    },

    pushStateAll() {
      for (const w of windows) w.pushState();
    },
  };
  return ctx;
}

module.exports = { createAppContext };
