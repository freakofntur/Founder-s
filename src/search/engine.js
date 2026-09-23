'use strict';

/**
 * The search engine is a fan-out over providers. Each provider knows one source of
 * answers (history, the local page index, a calculator, a web API, an LLM ...) and
 * returns scored results; the engine runs them in parallel, drops the slow or broken
 * ones, and merges what is left.
 *
 * @typedef {'navigate' | 'search' | 'answer' | 'tab'} ResultType
 *
 * @typedef {Object} Result
 * @property {ResultType} type    navigate: open url; search: run a query (url is the results page);
 *                                answer: show inline; tab: switch to an open tab
 * @property {string} title
 * @property {string} [url]
 * @property {string} [description]
 * @property {string} [snippet]   Text excerpt around the match.
 * @property {string[]} [terms]   Terms to highlight in the snippet.
 * @property {number} [tabId]
 * @property {number} score       0..1, higher ranks first. 1 is reserved for "what Enter does".
 * @property {string} [source]    Filled in by the engine with the provider id.
 *
 * @typedef {Object} Context
 * @property {'suggest' | 'search'} mode
 * @property {AbortSignal} signal Aborted when the provider runs past the engine's timeout.
 *
 * @typedef {Object} Provider
 * @property {string} id
 * @property {string} name        Section heading on the results page.
 * @property {number} [order]     Section position on the results page, lower first.
 * @property {(query: string, ctx: Context) => Result[] | Promise<Result[]>} [suggest]  Fast; feeds the address bar.
 * @property {(query: string, ctx: Context) => Result[] | Promise<Result[]>} [search]   Thorough; feeds founder://search.
 */
class SearchEngine {
  /** @param {{ timeoutMs?: number, onError?: (provider: Provider, err: Error) => void }} [opts] */
  constructor({ timeoutMs = 750, onError = (p, err) => console.warn(`[search] ${p.id} failed:`, err) } = {}) {
    this.timeoutMs = timeoutMs;
    this.onError = onError;
    /** @type {Map<string, Provider>} */
    this.providers = new Map();
  }

  /** @param {Provider} provider @returns {() => void} unregister */
  register(provider) {
    if (!provider?.id) throw new Error('provider needs an id');
    this.providers.set(provider.id, provider);
    return () => this.providers.delete(provider.id);
  }

  /** Merged, de-duplicated suggestions for the address bar. */
  async suggest(query, { limit = 8 } = {}) {
    const q = String(query ?? '').trim();
    if (!q) return [];
    const perProvider = await this.#run('suggest', q);
    return dedupe(perProvider.flatMap((r) => r.results))
      .sort((a, b) => b.score - a.score)
      .slice(0, limit);
  }

  /** Results grouped into one section per provider, for the results page. */
  async search(query, { perSection = 20 } = {}) {
    const q = String(query ?? '').trim();
    if (!q) return { query: q, sections: [] };
    const perProvider = await this.#run('search', q);
    const sections = perProvider
      .filter((r) => r.results.length > 0)
      .sort((a, b) => (a.provider.order ?? 50) - (b.provider.order ?? 50))
      .map(({ provider, results }) => ({
        id: provider.id,
        name: provider.name,
        results: dedupe(results)
          .sort((a, b) => b.score - a.score)
          .slice(0, perSection),
      }));
    return { query: q, sections };
  }

  async #run(mode, query) {
    const providers = [...this.providers.values()].filter((p) => typeof p[mode] === 'function');
    return Promise.all(
      providers.map(async (provider) => {
        const controller = new AbortController();
        let timer;
        const timeout = new Promise((resolve) => {
          timer = setTimeout(() => {
            controller.abort();
            resolve([]);
          }, this.timeoutMs);
        });
        try {
          const work = Promise.resolve().then(() => provider[mode](query, { mode, signal: controller.signal }));
          const results = (await Promise.race([work, timeout])) || [];
          return { provider, results: results.map((r) => ({ ...r, source: provider.id })) };
        } catch (err) {
          this.onError(provider, err);
          return { provider, results: [] };
        } finally {
          clearTimeout(timer);
        }
      }),
    );
  }
}

function resultKey(r) {
  if (r.type === 'tab') return `tab:${r.tabId}`;
  if (r.url) return `url:${r.url.replace(/#.*$/, '').replace(/\/$/, '')}`;
  return `${r.type}:${r.title}`;
}

/** Keeps the highest-scoring result for each key. */
function dedupe(results) {
  const best = new Map();
  for (const r of results) {
    const key = resultKey(r);
    const prev = best.get(key);
    if (!prev || r.score > prev.score) best.set(key, r);
  }
  return [...best.values()];
}

module.exports = { SearchEngine };
