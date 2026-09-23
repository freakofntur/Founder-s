'use strict';

const path = require('node:path');
const { pathToFileURL } = require('node:url');
const { protocol, net } = require('electron');

const SCHEME = 'founder';
const PAGES_DIR = path.join(__dirname, '..', 'ui', 'pages');

/** Must run before app 'ready'. */
function registerSchemePrivileges() {
  protocol.registerSchemesAsPrivileged([
    { scheme: SCHEME, privileges: { standard: true, secure: true, supportFetchAPI: true } },
  ]);
}

/**
 * Serves internal pages: founder://<page>/<file> -> src/ui/pages/<page>/<file>,
 * with "/" mapping to index.html. founder://shared/ holds assets used by every page.
 */
function registerInternalProtocol() {
  protocol.handle(SCHEME, (request) => {
    const { host, pathname } = new URL(request.url);
    const pageDir = path.join(PAGES_DIR, host);
    const file = path.join(pageDir, pathname === '/' ? 'index.html' : decodeURIComponent(pathname));
    if (!host || !file.startsWith(pageDir + path.sep)) return new Response('Not found', { status: 404 });
    return net.fetch(pathToFileURL(file).toString()).catch(() => new Response('Not found', { status: 404 }));
  });
}

/** True when an IPC message comes from an internal founder:// page. */
function isInternalFrame(frame) {
  try {
    return !!frame && new URL(frame.url).protocol === `${SCHEME}:`;
  } catch {
    return false;
  }
}

module.exports = { registerSchemePrivileges, registerInternalProtocol, isInternalFrame };
