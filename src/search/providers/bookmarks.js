'use strict';

const { matchEntries } = require('./match');

/** @param {{ all(): {url, title, createdAt}[] }} bookmarks */
function createBookmarksProvider(bookmarks) {
  return {
    id: 'bookmarks',
    name: 'Bookmarks',
    order: 20,
    suggest: (q) => matchEntries(bookmarks.all(), q, { limit: 3, boost: 0.05 }),
    search: (q) => matchEntries(bookmarks.all(), q, { limit: 20 }),
  };
}

module.exports = { createBookmarksProvider };
