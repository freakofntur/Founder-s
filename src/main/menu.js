'use strict';

const { Menu, app } = require('electron');

/**
 * Application menu. Its accelerators are the browser's keyboard shortcuts, and they
 * fire no matter which view (browser UI or page) has focus.
 *
 * @param {ReturnType<import('./app-context').createAppContext>} ctx
 */
function buildMenu(ctx) {
  const withWindow = (fn) => () => {
    const win = ctx.currentWindow();
    if (win) fn(win);
  };
  const withTab = (fn) => withWindow((win) => win.tabs.active && fn(win.tabs.active, win));
  const isMac = process.platform === 'darwin';

  const template = [
    ...(isMac ? [{ role: 'appMenu' }] : []),
    {
      label: 'File',
      submenu: [
        { label: 'New Tab', accelerator: 'CmdOrCtrl+T', click: withWindow((w) => w.newTab()) },
        { label: 'New Window', accelerator: 'CmdOrCtrl+N', click: () => ctx.openWindow() },
        { label: 'Close Tab', accelerator: 'CmdOrCtrl+W', click: withTab((t, w) => w.tabs.close(t.id)) },
        { type: 'separator' },
        { label: 'Settings', click: withWindow((w) => w.newTab('founder://settings')) },
        { type: 'separator' },
        isMac ? { role: 'close' } : { role: 'quit' },
      ],
    },
    { role: 'editMenu' },
    {
      label: 'View',
      submenu: [
        { label: 'Reload', accelerator: 'CmdOrCtrl+R', click: withTab((t) => t.view.webContents.reload()) },
        {
          label: 'Force Reload',
          accelerator: 'CmdOrCtrl+Shift+R',
          click: withTab((t) => t.view.webContents.reloadIgnoringCache()),
        },
        { type: 'separator' },
        {
          label: 'Developer Tools',
          accelerator: isMac ? 'Alt+Cmd+I' : 'Ctrl+Shift+I',
          click: withTab((t) => t.view.webContents.toggleDevTools()),
        },
        {
          label: 'Developer Tools (Browser UI)',
          accelerator: isMac ? 'Alt+Cmd+Shift+I' : 'Ctrl+Alt+Shift+I',
          click: withWindow((w) => w.chrome.webContents.openDevTools({ mode: 'detach' })),
        },
        { type: 'separator' },
        { role: 'togglefullscreen' },
      ],
    },
    {
      label: 'Go',
      submenu: [
        {
          label: 'Back',
          accelerator: isMac ? 'Cmd+[' : 'Alt+Left',
          click: withTab((t) => t.view.webContents.navigationHistory.goBack()),
        },
        {
          label: 'Forward',
          accelerator: isMac ? 'Cmd+]' : 'Alt+Right',
          click: withTab((t) => t.view.webContents.navigationHistory.goForward()),
        },
        { label: 'Home', accelerator: 'Alt+Home', click: withWindow((w) => w.navigate('founder://newtab')) },
        { type: 'separator' },
        { label: 'Focus Address Bar', accelerator: 'CmdOrCtrl+L', click: withWindow((w) => w.focusAddressBar()) },
        { label: 'Search', accelerator: 'CmdOrCtrl+K', click: withWindow((w) => w.focusAddressBar()) },
        { type: 'separator' },
        { label: 'Next Tab', accelerator: 'Ctrl+Tab', click: withWindow((w) => w.tabs.cycle(1)) },
        { label: 'Previous Tab', accelerator: 'Ctrl+Shift+Tab', click: withWindow((w) => w.tabs.cycle(-1)) },
      ],
    },
    {
      label: 'Bookmarks',
      submenu: [
        {
          label: 'Bookmark This Page',
          accelerator: 'CmdOrCtrl+D',
          click: withTab((t) => {
            if (!/^https?:/.test(t.url)) return;
            ctx.services.bookmarks.toggle(t.url, t.title);
            ctx.pushStateAll();
          }),
        },
      ],
    },
  ];

  Menu.setApplicationMenu(Menu.buildFromTemplate(template));
  app.setAboutPanelOptions?.({ applicationName: 'Founder' });
}

module.exports = { buildMenu };
