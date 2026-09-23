'use strict';

const { matchEntries } = require('./match');

/** @param {{ all(): {url, title, visitCount, lastVisit}[] }} history */
function createHistoryProvider(history) {
  return {
    id: 'history',
    name: 'History',
    order: 30,
    suggest: (q) => matchEntries(history.all(), q, { limit: 4 }),
    search: (q) => matchEntries(history.all(), q, { limit: 20 }),
  };
}

module.exports = { createHistoryProvider };
