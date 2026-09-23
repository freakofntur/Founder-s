'use strict';

const { ipcMain } = require('electron');
const { classifyInput, WEB_ENGINES } = require('../search/query');
const { isInternalFrame } = require('./protocol');

/**
 * Two IPC surfaces, each only reachable by its intended caller:
 *  - 'browser:*' from the browser UI (address bar, tab strip) — see preload/chrome.js
 *  - 'page:*'    from internal founder:// pages               — see preload/tab.js
 *
 * @param {ReturnType<import('./app-context').createAppContext>} ctx
 */
function registerIpc(ctx) {
  const { services } = ctx;

  /** Handler for the browser UI; receives the BrowserWindow the message came from. */
  const browser = (channel, fn) =>
    ipcMain.handle(channel, (event, ...args) => {
      const win = ctx.windowForChrome(event.sender);
      if (!win) throw new Error(`${channel}: sender is not a browser UI`);
      return fn(win, ...args);
    });

  /** Handler for founder:// pages. */
  const page = (channel, fn) =>
    ipcMain.handle(channel, (event, ...args) => {
      if (!isInternalFrame(event.senderFrame)) throw new Error(`${channel}: sender is not an internal page`);
      return fn(...args);
    });

  browser('browser:new-tab', (win, url) => void win.newTab(url || undefined));
  browser('browser:close-tab', (win, id) => win.tabs.close(id));
  browser('browser:activate-tab', (win, id) => {
    const owner = ctx.windowForTab(id) || win;
    owner.tabs.activate(id);
    owner.window.focus();
    owner.tabs.get(id)?.view.webContents.focus();
  });
  browser('browser:navigate', (win, url) => win.navigate(String(url)));
  browser('browser:submit', (win, input) => {
    const action = classifyInput(input, { defaultEngine: services.settings.get().searchEngine });
    if (action) win.navigate(action.url);
  });
  browser('browser:back', (win) => win.tabs.active?.view.webContents.navigationHistory.goBack());
  browser('browser:forward', (win) => win.tabs.active?.view.webContents.navigationHistory.goForward());
  browser('browser:reload', (win) => win.tabs.active?.view.webContents.reload());
  browser('browser:stop', (win) => win.tabs.active?.view.webContents.stop());
  browser('browser:suggest', (_win, text) => services.engine.suggest(String(text)));
  browser('browser:set-overlay', (win, open) => win.setOverlay(open));
  browser('browser:toggle-bookmark', (win) => {
    const tab = win.tabs.active;
    if (!tab || !/^https?:/.test(tab.url)) return false;
    const on = services.bookmarks.toggle(tab.url, tab.title);
    ctx.pushStateAll();
    return on;
  });

  page('page:search', (query) => services.engine.search(String(query)));
  page('page:resolve', (input) => {
    return classifyInput(input, { defaultEngine: services.settings.get().searchEngine })?.url ?? null;
  });
  page('page:recent', (limit = 12) => services.history.recent(Math.min(Number(limit) || 12, 100)));
  page('page:get-settings', () => ({
    settings: services.settings.get(),
    engines: Object.entries(WEB_ENGINES).map(([id, e]) => ({ id, name: e.name })),
    stats: { historyEntries: services.history.all().length, indexedPages: services.pageIndex.size },
  }));
  page('page:update-settings', (patch) => services.settings.update(patch));
  page('page:clear', (what) => {
    if (what === 'history') services.history.clear();
    if (what === 'index') {
      services.pageIndex.clear();
      services.savePageIndex();
    }
  });
}

module.exports = { registerIpc };
