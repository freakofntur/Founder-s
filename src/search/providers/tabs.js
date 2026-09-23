'use strict';

const { scoreEntry, stripUrl } = require('./match');

/** "Switch to tab" suggestions. @param {() => {id, title, url, active}[]} listTabs */
function createTabsProvider(listTabs) {
  return {
    id: 'tabs',
    name: 'Open tabs',
    suggest(q) {
      return listTabs()
        .filter((t) => !t.active && /^https?:/.test(t.url))
        .map((t) => ({ tab: t, score: scoreEntry(t, q) }))
        .filter((m) => m.score > 0)
        .sort((a, b) => b.score - a.score)
        .slice(0, 2)
        .map(({ tab, score }) => ({
          type: 'tab',
          title: tab.title || stripUrl(tab.url),
          description: 'Switch to this tab',
          url: tab.url,
          tabId: tab.id,
          score: Math.min(score + 0.05, 0.98),
        }));
    },
  };
}

module.exports = { createTabsProvider };
