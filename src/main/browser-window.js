'use strict';

const path = require('node:path');
const { EventEmitter } = require('node:events');
const { BaseWindow, WebContentsView } = require('electron');
const { TabManager } = require('./tabs');
const { HOME_URL } = require('../search/query');

/** Height of the tab strip + toolbar. Keep in sync with --chrome-height in ui/chrome/chrome.css. */
const CHROME_HEIGHT = 84;

/**
 * One browser window: the browser UI ("chrome", a transparent WebContentsView on top)
 * plus the tabs underneath. While the address-bar dropdown is open the chrome view is
 * stretched over the whole window so the dropdown can draw over the page.
 *
 * Events: 'closed'
 */
class BrowserWindow extends EventEmitter {
  #overlay = false;
  #pushQueued = false;

  /** @param {{ services: ReturnType<import('./services').createServices>, url?: string }} opts */
  constructor({ services, url = HOME_URL }) {
    super();
    this.services = services;

    this.window = new BaseWindow({
      width: 1280,
      height: 840,
      minWidth: 480,
      minHeight: 300,
      title: 'Founder',
      autoHideMenuBar: true,
      backgroundColor: '#ffffff',
    });

    this.chrome = new WebContentsView({
      webPreferences: {
        preload: path.join(__dirname, '..', 'preload', 'chrome.js'),
        sandbox: true,
        contextIsolation: true,
        nodeIntegration: false,
      },
    });
    this.chrome.setBackgroundColor('#00000000');
    this.chrome.webContents.loadFile(path.join(__dirname, '..', 'ui', 'chrome', 'index.html'));
    this.chrome.webContents.on('did-finish-load', () => this.pushState());
    this.window.contentView.addChildView(this.chrome);

    this.tabs = new TabManager(this.window);
    this.tabs.on('activated', () => {
      // Newly attached tab views land on top; keep the browser UI above them.
      this.window.contentView.addChildView(this.chrome);
      this.window.setTitle(this.#title());
    });
    this.tabs.on('title', (tab) => tab === this.tabs.active && this.window.setTitle(this.#title()));
    this.tabs.on('changed', () => this.pushState());
    this.tabs.on('empty', () => this.window.close());

    this.window.on('resize', () => this.layout());
    this.window.on('closed', () => {
      this.tabs.destroy();
      this.chrome.webContents.close();
      this.emit('closed');
    });

    this.layout();
    this.newTab(url);
  }

  get isDestroyed() {
    return this.window.isDestroyed();
  }

  layout() {
    const { width, height } = this.window.getContentBounds();
    this.chrome.setBounds({ x: 0, y: 0, width, height: this.#overlay ? height : CHROME_HEIGHT });
    this.tabs.setBounds({ x: 0, y: CHROME_HEIGHT, width, height: Math.max(0, height - CHROME_HEIGHT) });
  }

  setOverlay(open) {
    this.#overlay = !!open;
    this.layout();
  }

  newTab(url = HOME_URL) {
    const tab = this.tabs.create(url);
    if (url === HOME_URL) this.focusAddressBar();
    return tab;
  }

  /** Loads `url` in the active tab and hands keyboard focus to the page. */
  navigate(url) {
    const tab = this.tabs.active || this.newTab(url);
    this.tabs.navigate(tab.id, url);
    tab.view.webContents.focus();
  }

  focusAddressBar() {
    this.chrome.webContents.focus();
    this.chrome.webContents.send('ui:focus-address-bar');
  }

  /** Sends the tab state to the browser UI, coalescing bursts of changes into one message. */
  pushState() {
    if (this.#pushQueued) return;
    this.#pushQueued = true;
    setImmediate(() => {
      this.#pushQueued = false;
      if (this.chrome.webContents.isDestroyed()) return;
      const state = this.tabs.snapshot();
      const active = this.tabs.active;
      state.bookmarked = !!active && this.services.bookmarks.has(active.url);
      this.chrome.webContents.send('tabs:state', state);
    });
  }

  #title() {
    const title = this.tabs.active?.title;
    return title ? `${title} — Founder` : 'Founder';
  }
}

module.exports = { BrowserWindow, CHROME_HEIGHT };
