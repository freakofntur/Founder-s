const { contextBridge } = require('electron');

// Expose a safe API to the renderer process
contextBridge.exposeInMainWorld('electronAPI', {
  // Add any APIs here if needed in the future
});
