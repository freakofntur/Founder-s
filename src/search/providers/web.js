'use strict';

const { WEB_ENGINES, webSearchUrl, searchUrl } = require('../query');

const SUGGEST_URL = 'https://duckduckgo.com/ac/?type=list&q=';

/**
 * The bridge to the open web:
 *  - suggest: remote query completions (opt-in: it sends every keystroke to DuckDuckGo)
 *  - search:  hand-off links to each web engine
 */
function createWebProvider({ getSettings, fetch = globalThis.fetch }) {
  return {
    id: 'web',
    name: 'Search the web',
    order: 90,
    async suggest(q, { signal }) {
      const settings = getSettings();
      if (!settings.remoteSuggestions || q.length < 2) return [];
      const res = await fetch(SUGGEST_URL + encodeURIComponent(q), { signal });
      if (!res.ok) return [];
      const [, completions = []] = await res.json();
      return completions
        .filter((c) => typeof c === 'string' && c.toLowerCase() !== q.toLowerCase())
        .slice(0, 4)
        .map((c, i) => ({ type: 'search', title: c, url: searchUrl(settings.searchEngine, c), score: 0.3 - i * 0.01 }));
    },
    search(q) {
      const preferred = getSettings().webEngine;
      return Object.entries(WEB_ENGINES).map(([id, engine]) => ({
        type: 'search',
        title: engine.name,
        url: webSearchUrl(id, q),
        score: id === preferred ? 0.9 : 0.5,
      }));
    },
  };
}

module.exports = { createWebProvider };
