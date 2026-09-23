'use strict';

const { classifyInput, WEB_ENGINES, FOUNDER_ENGINE } = require('../query');

/**
 * Always contributes the top suggestion: exactly what pressing Enter will do
 * (open the URL, or run the search on the default engine / the engine picked by a !bang).
 */
function createNavigationProvider({ getSettings }) {
  return {
    id: 'navigation',
    name: 'Go',
    suggest(query) {
      const action = classifyInput(query, { defaultEngine: getSettings().searchEngine });
      if (!action) return [];
      if (action.kind === 'url') return [{ type: 'navigate', title: action.url, url: action.url, score: 1 }];
      const engineName = action.engine === FOUNDER_ENGINE ? 'Founder' : WEB_ENGINES[action.engine].name;
      return [{ type: 'search', title: action.query, description: `Search with ${engineName}`, url: action.url, score: 1 }];
    },
  };
}

module.exports = { createNavigationProvider };
