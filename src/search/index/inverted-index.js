'use strict';

const { tokenize } = require('./tokenize');

const TITLE_WEIGHT = 3;
const DESCRIPTION_WEIGHT = 2;
const MAX_PREFIX_EXPANSIONS = 12;

/**
 * In-memory full-text index ranked with BM25. Documents are keyed by URL.
 *
 * This is deliberately simple so it is easy to replace: anything with the same
 * add/remove/search/toJSON surface (SQLite FTS, a vector store, a remote service)
 * can be dropped in behind the page-index provider.
 */
class InvertedIndex {
  constructor({ maxDocs = 2000, maxTextLength = 20_000, k1 = 1.2, b = 0.75 } = {}) {
    this.maxDocs = maxDocs;
    this.maxTextLength = maxTextLength;
    this.k1 = k1;
    this.b = b;
    /** @type {Map<string, {url, title, description, text, indexedAt, length, terms: string[]}>} */
    this.docs = new Map();
    /** @type {Map<string, Map<string, number>>} term -> (url -> weighted term frequency) */
    this.postings = new Map();
    this.totalLength = 0;
  }

  get size() {
    return this.docs.size;
  }

  add({ url, title = '', description = '', text = '', indexedAt = Date.now() }) {
    if (!url) throw new Error('document needs a url');
    this.remove(url);

    text = String(text).slice(0, this.maxTextLength);
    const counts = new Map();
    const bump = (source, weight) => {
      for (const term of tokenize(source)) counts.set(term, (counts.get(term) || 0) + weight);
    };
    bump(title, TITLE_WEIGHT);
    bump(description, DESCRIPTION_WEIGHT);
    bump(text, 1);

    let length = 0;
    for (const [term, tf] of counts) {
      length += tf;
      let posting = this.postings.get(term);
      if (!posting) this.postings.set(term, (posting = new Map()));
      posting.set(url, tf);
    }

    this.docs.set(url, { url, title, description, text, indexedAt, length, terms: [...counts.keys()] });
    this.totalLength += length;

    // Map iteration order is insertion order and re-adds remove first, so the first key is the stalest doc.
    while (this.docs.size > this.maxDocs) this.remove(this.docs.keys().next().value);
  }

  remove(url) {
    const doc = this.docs.get(url);
    if (!doc) return false;
    for (const term of doc.terms) {
      const posting = this.postings.get(term);
      posting.delete(url);
      if (posting.size === 0) this.postings.delete(term);
    }
    this.totalLength -= doc.length;
    this.docs.delete(url);
    return true;
  }

  clear() {
    this.docs.clear();
    this.postings.clear();
    this.totalLength = 0;
  }

  /**
   * @param {string} query
   * @param {{ limit?: number, prefix?: boolean }} [opts] prefix: treat the last term as a prefix (search-as-you-type)
   * @returns {{ url, title, description, snippet, terms: string[], score: number }[]}
   */
  search(query, { limit = 10, prefix = false } = {}) {
    const terms = [...new Set(tokenize(query))];
    if (terms.length === 0 || this.docs.size === 0) return [];

    // Each group is a set of alternatives; a doc matches the group if it contains any of them.
    const groups = terms.map((t) => [t]);
    if (prefix) groups[groups.length - 1].push(...this.#expandPrefix(terms[terms.length - 1]));

    const n = this.docs.size;
    const avgLength = this.totalLength / n || 1;
    const scores = new Map();
    const matched = new Map();

    groups.forEach((alternatives, groupIndex) => {
      for (const term of alternatives) {
        const posting = this.postings.get(term);
        if (!posting) continue;
        const idf = Math.log(1 + (n - posting.size + 0.5) / (posting.size + 0.5));
        for (const [url, tf] of posting) {
          const doc = this.docs.get(url);
          const norm = tf + this.k1 * (1 - this.b + (this.b * doc.length) / avgLength);
          scores.set(url, (scores.get(url) || 0) + (idf * tf * (this.k1 + 1)) / norm);
          if (!matched.has(url)) matched.set(url, { groups: new Set(), terms: new Set() });
          matched.get(url).groups.add(groupIndex);
          matched.get(url).terms.add(term);
        }
      }
    });

    return [...scores]
      .map(([url, score]) => {
        const coverage = matched.get(url).groups.size / groups.length;
        return { url, score: score * coverage * coverage };
      })
      .sort((a, b) => b.score - a.score)
      .slice(0, limit)
      .map(({ url, score }) => {
        const doc = this.docs.get(url);
        const hitTerms = [...matched.get(url).terms];
        return {
          url,
          title: doc.title,
          description: doc.description,
          snippet: makeSnippet(doc, hitTerms),
          terms: hitTerms,
          score,
        };
      });
  }

  #expandPrefix(stem) {
    if (stem.length < 2) return [];
    const out = [];
    for (const term of this.postings.keys()) {
      if (term !== stem && term.startsWith(stem)) {
        out.push(term);
        if (out.length >= MAX_PREFIX_EXPANSIONS) break;
      }
    }
    return out;
  }

  toJSON() {
    return {
      version: 1,
      docs: [...this.docs.values()].map(({ url, title, description, text, indexedAt }) => ({
        url,
        title,
        description,
        text,
        indexedAt,
      })),
    };
  }

  static fromJSON(data, opts) {
    const index = new InvertedIndex(opts);
    if (data?.version === 1 && Array.isArray(data.docs)) for (const doc of data.docs) index.add(doc);
    return index;
  }
}

function makeSnippet(doc, terms, radius = 90) {
  const text = doc.text.replace(/\s+/g, ' ').trim();
  const lower = text.normalize('NFKD').replace(/[̀-ͯ]/g, '').toLowerCase();
  let hit = -1;
  for (const term of terms) {
    const i = lower.indexOf(term);
    if (i !== -1 && (hit === -1 || i < hit)) hit = i;
  }
  if (hit === -1) return doc.description || text.slice(0, radius * 2);

  let start = Math.max(0, hit - radius);
  let end = Math.min(text.length, hit + radius * 2);
  if (start > 0) start = text.indexOf(' ', start) + 1 || start;
  if (end < text.length) end = text.lastIndexOf(' ', end) > hit ? text.lastIndexOf(' ', end) : end;
  return (start > 0 ? '… ' : '') + text.slice(start, end) + (end < text.length ? ' …' : '');
}

module.exports = { InvertedIndex };
