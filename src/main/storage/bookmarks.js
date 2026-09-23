'use strict';

class Bookmarks {
  /** @param {import('./json-store').JsonStore} store */
  constructor(store) {
    this.store = store;
    store.data.items ??= [];
  }

  all() {
    return this.store.data.items;
  }

  has(url) {
    return this.all().some((b) => b.url === url);
  }

  add(url, title = '', now = Date.now()) {
    if (this.has(url)) return;
    this.all().push({ url, title, createdAt: now });
    this.store.save();
  }

  remove(url) {
    this.store.data.items = this.all().filter((b) => b.url !== url);
    this.store.save();
  }

  /** @returns {boolean} whether the url is bookmarked afterwards */
  toggle(url, title) {
    if (this.has(url)) {
      this.remove(url);
      return false;
    }
    this.add(url, title);
    return true;
  }
}

module.exports = { Bookmarks };
