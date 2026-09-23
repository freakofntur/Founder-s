'use strict';

const { EventEmitter } = require('node:events');
const { WEB_ENGINES, FOUNDER_ENGINE } = require('../../search/query');

const DEFAULT_SETTINGS = {
  /** Where address-bar queries go: 'founder' (founder://search) or a web engine id. */
  searchEngine: FOUNDER_ENGINE,
  /** Preferred engine for "search the web" hand-offs. */
  webEngine: 'duckduckgo',
  /** Ask DuckDuckGo for completions while typing. Off by default: it leaks keystrokes. */
  remoteSuggestions: false,
  /** Save the text of visited pages into the local search index. */
  indexPages: true,
};

const VALIDATORS = {
  searchEngine: (v) => v === FOUNDER_ENGINE || v in WEB_ENGINES,
  webEngine: (v) => v in WEB_ENGINES,
};

class Settings extends EventEmitter {
  /** @param {import('./json-store').JsonStore} store */
  constructor(store) {
    super();
    this.store = store;
  }

  get() {
    return { ...DEFAULT_SETTINGS, ...this.store.data };
  }

  /** Applies the valid keys of `patch`, ignores the rest, and returns the new settings. */
  update(patch) {
    for (const [key, value] of Object.entries(patch || {})) {
      if (!(key in DEFAULT_SETTINGS) || typeof value !== typeof DEFAULT_SETTINGS[key]) continue;
      if (VALIDATORS[key] && !VALIDATORS[key](value)) continue;
      this.store.data[key] = value;
    }
    this.store.save();
    const next = this.get();
    this.emit('change', next);
    return next;
  }
}

module.exports = { Settings, DEFAULT_SETTINGS };
