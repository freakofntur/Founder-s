'use strict';

const { stripUrl } = require('./match');

/**
 * Full-text search over the pages you have visited (see main/indexer.js for how pages get in).
 * @param {import('../index/inverted-index').InvertedIndex} index
 */
function createPageIndexProvider(index) {
  const toResults = (hits, { base, spread }) => {
    const top = hits[0]?.score || 1;
    return hits.map((hit) => ({
      type: 'navigate',
      title: hit.title || stripUrl(hit.url),
      url: hit.url,
      description: stripUrl(hit.url),
      snippet: hit.snippet,
      terms: hit.terms,
      score: base + spread * (hit.score / top),
    }));
  };
  return {
    id: 'pages',
    name: "From pages you've visited",
    order: 10,
    suggest: (q) => toResults(index.search(q, { limit: 3, prefix: true }), { base: 0.35, spread: 0.3 }),
    search: (q) => toResults(index.search(q, { limit: 20 }), { base: 0.3, spread: 0.6 }),
  };
}

module.exports = { createPageIndexProvider };
