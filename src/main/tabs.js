'use strict';

const path = require('node:path');
const { EventEmitter } = require('node:events');
const { WebContentsView } = require('electron');
const { HOME_URL } = require('../search/query');

const TAB_PRELOAD = path.join(__dirname, '..', 'preload', 'tab.js');

// Global so tab ids stay unique across windows (the tabs search provider spans all of them).
let nextTabId = 1;

/**
 * Owns the tabs of one window. Each tab is a WebContentsView; only the active one is attached.
 *
 * Events:
 *  - 'changed'              any tab state the UI shows changed
 *  - 'activated' (tab)      a different tab became active
 *  - 'navigated' (tab)      main-frame navigation committed (incl. in-page)
 *  - 'title' (tab)          page title changed
 *  - 'loaded' (tab)         page finished loading
 *  - 'empty'                last tab closed
 */
class TabManager extends EventEmitter {
  #tabs = new Map();
  #activeId = null;
  #bounds = { x: 0, y: 0, width: 0, height: 0 };

  /** @param {import('electron').BaseWindow} window */
  constructor(window) {
    super();
    this.window = window;
  }

  get active() {
    return this.#tabs.get(this.#activeId) || null;
  }

  get(id) {
    return this.#tabs.get(id) || null;
  }

  list() {
    return [...this.#tabs.values()];
  }

  create(url = HOME_URL, { activate = true } = {}) {
    const view = new WebContentsView({
      webPreferences: { preload: TAB_PRELOAD, sandbox: true, contextIsolation: true, nodeIntegration: false },
    });
    const tab = { id: nextTabId++, view, url, title: '', favicon: null, loading: true };
    this.#tabs.set(tab.id, tab);
    this.#wire(tab);
    view.webContents.loadURL(url);
    if (activate) this.activate(tab.id);
    else this.emit('changed');
    return tab;
  }

  activate(id) {
    const tab = this.#tabs.get(id);
    if (!tab || id === this.#activeId) return;
    const previous = this.active;
    if (previous) this.window.contentView.removeChildView(previous.view);
    this.window.contentView.addChildView(tab.view);
    tab.view.setBounds(this.#bounds);
    this.#activeId = id;
    this.emit('activated', tab);
    this.emit('changed');
  }

  close(id) {
    const tab = this.#tabs.get(id);
    if (!tab) return;
    if (id === this.#activeId) {
      const ids = [...this.#tabs.keys()];
      const i = ids.indexOf(id);
      const neighbour = ids[i + 1] ?? ids[i - 1];
      if (neighbour !== undefined) this.activate(neighbour);
    }
    if (this.#activeId === id) {
      this.window.contentView.removeChildView(tab.view);
      this.#activeId = null;
    }
    this.#tabs.delete(id);
    tab.view.webContents.close();
    this.emit('changed');
    if (this.#tabs.size === 0) this.emit('empty');
  }

  /** Activates the tab `offset` positions away from the current one, wrapping around. */
  cycle(offset) {
    const ids = [...this.#tabs.keys()];
    if (ids.length < 2) return;
    const i = ids.indexOf(this.#activeId);
    this.activate(ids[(i + offset + ids.length) % ids.length]);
  }

  setBounds(bounds) {
    this.#bounds = bounds;
    this.active?.view.setBounds(bounds);
  }

  navigate(id, url) {
    this.get(id)?.view.webContents.loadURL(url);
  }

  destroy() {
    for (const tab of this.#tabs.values()) if (!tab.view.webContents.isDestroyed()) tab.view.webContents.close();
    this.#tabs.clear();
  }

  /** Serializable state for the browser UI. */
  snapshot() {
    return {
      activeId: this.#activeId,
      tabs: this.list().map((t) => {
        const nav = t.view.webContents.navigationHistory;
        return {
          id: t.id,
          url: t.url,
          title: t.title,
          favicon: t.favicon,
          loading: t.loading,
          canGoBack: nav.canGoBack(),
          canGoForward: nav.canGoForward(),
        };
      }),
    };
  }

  #wire(tab) {
    const wc = tab.view.webContents;
    const update = (patch) => {
      Object.assign(tab, patch);
      this.emit('changed');
    };

    wc.on('did-start-loading', () => update({ loading: true }));
    wc.on('did-stop-loading', () => {
      update({ loading: false });
      this.emit('loaded', tab);
    });
    wc.on('did-navigate', (_e, url) => {
      update({ url, favicon: null });
      this.emit('navigated', tab);
    });
    wc.on('did-navigate-in-page', (_e, url, isMainFrame) => {
      if (!isMainFrame) return;
      update({ url });
      this.emit('navigated', tab);
    });
    wc.on('page-title-updated', (_e, title) => {
      update({ title });
      this.emit('title', tab);
    });
    wc.on('page-favicon-updated', (_e, favicons) => update({ favicon: favicons[0] || null }));
    wc.on('did-fail-load', (_e, code, description, url, isMainFrame) => {
      // -3 is ERR_ABORTED: the user stopped or navigated away, not an error worth a page.
      if (!isMainFrame || code === -3 || url.startsWith('founder:')) return;
      const params = new URLSearchParams({ url, code: String(code), description });
      wc.loadURL(`founder://error?${params}`);
    });

    // Links with target=_blank, window.open, middle-click: open as tabs instead of windows.
    wc.setWindowOpenHandler(({ url, disposition }) => {
      this.create(url, { activate: disposition !== 'background-tab' });
      return { action: 'deny' };
    });
  }
}

module.exports = { TabManager };
