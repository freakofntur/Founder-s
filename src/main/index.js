'use strict';

const { app } = require('electron');
const { registerSchemePrivileges, registerInternalProtocol } = require('./protocol');
const { createServices } = require('./services');
const { createAppContext } = require('./app-context');
const { registerIpc } = require('./ipc');
const { buildMenu } = require('./menu');

if (!app.requestSingleInstanceLock()) {
  app.quit();
} else {
  registerSchemePrivileges();
  let ctx = null;

  app.whenReady().then(() => {
    registerInternalProtocol();
    const services = createServices({ dataDir: app.getPath('userData'), listTabs: () => ctx.listTabs() });
    ctx = createAppContext(services);
    registerIpc(ctx);
    buildMenu(ctx);
    ctx.openWindow();
  });

  // Launching the app again opens a window in the running instance instead.
  app.on('second-instance', () => ctx?.openWindow());

  // macOS: re-open a window when the dock icon is clicked.
  app.on('activate', () => ctx && ctx.windows.size === 0 && ctx.openWindow());

  app.on('window-all-closed', () => {
    if (process.platform !== 'darwin') app.quit();
  });

  app.on('will-quit', () => ctx?.services.flush());
}
