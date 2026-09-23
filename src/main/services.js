'use strict';

const path = require('node:path');
const { JsonStore } = require('./storage/json-store');
const { History } = require('./storage/history');
const { Bookmarks } = require('./storage/bookmarks');
const { Settings } = require('./storage/settings');
const { InvertedIndex } = require('../search/index/inverted-index');
const { SearchEngine } = require('../search/engine');
const { createNavigationProvider } = require('../search/providers/navigation');
const { createCalculatorProvider } = require('../search/providers/calculator');
const { createPageIndexProvider } = require('../search/providers/page-index');
const { createBookmarksProvider } = require('../search/providers/bookmarks');
const { createHistoryProvider } = require('../search/providers/history');
const { createTabsProvider } = require('../search/providers/tabs');
const { createWebProvider } = require('../search/providers/web');

/**
 * Builds everything that is not UI: persistence, the page index and the search engine.
 * Nothing in here depends on Electron, so it can run (and be tested) in plain Node.
 *
 * @param {{ dataDir: string, listTabs?: () => {id, title, url, active}[] }} opts
 */
function createServices({ dataDir, listTabs = () => [] }) {
  const file = (name) => path.join(dataDir, name);

  const settingsStore = new JsonStore(file('settings.json'));
  const historyStore = new JsonStore(file('history.json'));
  const bookmarksStore = new JsonStore(file('bookmarks.json'));
  const pageIndex = InvertedIndex.fromJSON(JsonStore.read(file('page-index.json')));
  const indexStore = new JsonStore(file('page-index.json'), { debounceMs: 5000, serialize: () => pageIndex.toJSON() });

  const settings = new Settings(settingsStore);
  const history = new History(historyStore);
  const bookmarks = new Bookmarks(bookmarksStore);
  const getSettings = () => settings.get();

  const engine = new SearchEngine();
  engine.register(createNavigationProvider({ getSettings }));
  engine.register(createCalculatorProvider());
  engine.register(createPageIndexProvider(pageIndex));
  engine.register(createBookmarksProvider(bookmarks));
  engine.register(createHistoryProvider(history));
  engine.register(createTabsProvider(listTabs));
  engine.register(createWebProvider({ getSettings }));

  return {
    settings,
    history,
    bookmarks,
    pageIndex,
    engine,
    /** Call after mutating pageIndex. */
    savePageIndex: () => indexStore.save(),
    flush() {
      for (const store of [settingsStore, historyStore, bookmarksStore, indexStore]) store.flush();
    },
  };
}

module.exports = { createServices };
