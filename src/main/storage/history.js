'use strict';

/** Visited pages, one entry per URL. */
class History {
  /** @param {import('./json-store').JsonStore} store */
  constructor(store, { maxEntries = 10_000 } = {}) {
    this.store = store;
    this.maxEntries = maxEntries;
    store.data.entries ??= {};
  }

  get #entries() {
    return this.store.data.entries;
  }

  record(url, title = '', now = Date.now()) {
    const entry = this.#entries[url] || { url, title, visitCount: 0 };
    entry.visitCount += 1;
    entry.lastVisit = now;
    if (title) entry.title = title;
    // Re-insert so object key order stays oldest-visit-first for pruning.
    delete this.#entries[url];
    this.#entries[url] = entry;
    this.#prune();
    this.store.save();
  }

  setTitle(url, title) {
    const entry = this.#entries[url];
    if (!entry || !title || entry.title === title) return;
    entry.title = title;
    this.store.save();
  }

  all() {
    return Object.values(this.#entries);
  }

  recent(limit = 12) {
    return this.all()
      .sort((a, b) => b.lastVisit - a.lastVisit)
      .slice(0, limit);
  }

  clear() {
    this.store.data.entries = {};
    this.store.save();
  }

  #prune() {
    const urls = Object.keys(this.#entries);
    for (let i = 0; i < urls.length - this.maxEntries; i++) delete this.#entries[urls[i]];
  }
}

module.exports = { History };
