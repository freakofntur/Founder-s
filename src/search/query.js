'use strict';

/**
 * Turns whatever the user typed into something the browser can act on:
 * a URL to load, or a query plus the engine that should answer it.
 */

/** External engines a query can be handed to. `%s` is replaced with the encoded query. */
const WEB_ENGINES = {
  duckduckgo: { name: 'DuckDuckGo', url: 'https://duckduckgo.com/?q=%s', bang: 'ddg' },
  google: { name: 'Google', url: 'https://www.google.com/search?q=%s', bang: 'g' },
  bing: { name: 'Bing', url: 'https://www.bing.com/search?q=%s', bang: 'b' },
  brave: { name: 'Brave Search', url: 'https://search.brave.com/search?q=%s', bang: 'br' },
  wikipedia: { name: 'Wikipedia', url: 'https://en.wikipedia.org/w/index.php?search=%s', bang: 'w' },
  github: { name: 'GitHub', url: 'https://github.com/search?q=%s', bang: 'gh' },
  youtube: { name: 'YouTube', url: 'https://www.youtube.com/results?search_query=%s', bang: 'yt' },
};

/** The browser's own engine: the founder://search results page. */
const FOUNDER_ENGINE = 'founder';
const HOME_URL = 'founder://newtab';

const BANGS = Object.fromEntries(Object.entries(WEB_ENGINES).map(([id, e]) => [e.bang, id]));
const KNOWN_SCHEME_RE = /^(https?|file|founder|about|data|view-source):/i;
const IPV4_RE = /^\d{1,3}(\.\d{1,3}){3}$/;
const HOSTNAME_RE = /^([a-z0-9-]+\.)+[a-z]{2,}$/i;

function internalSearchUrl(query) {
  return `founder://search?q=${encodeURIComponent(query)}`;
}

function webSearchUrl(engineId, query) {
  const engine = WEB_ENGINES[engineId] || WEB_ENGINES.duckduckgo;
  return engine.url.replace('%s', encodeURIComponent(query));
}

/** URL that answers `query` on the given engine (the founder engine or a web engine id). */
function searchUrl(engineId, query) {
  return engineId === FOUNDER_ENGINE ? internalSearchUrl(query) : webSearchUrl(engineId, query);
}

/** "!w cats" or "cats !w" -> { engine: 'wikipedia', query: 'cats' } */
function parseBang(input) {
  const m = input.match(/^!(\w+)\s+(.+)$/) || input.match(/^(.+?)\s+!(\w+)$/);
  if (!m) return null;
  const [bang, query] = input.startsWith('!') ? [m[1], m[2]] : [m[2], m[1]];
  const engine = BANGS[bang.toLowerCase()];
  return engine ? { engine, query: query.trim() } : null;
}

function looksLikeHost(input) {
  if (/\s/.test(input)) return false;
  const host = input.split(/[/?#]/)[0].replace(/:\d+$/, '');
  return host === 'localhost' || IPV4_RE.test(host) || HOSTNAME_RE.test(host);
}

/**
 * @param {string} raw
 * @param {{ defaultEngine?: string }} [opts]
 * @returns {null | { kind: 'url', url: string } | { kind: 'search', query: string, engine: string, url: string }}
 */
function classifyInput(raw, { defaultEngine = FOUNDER_ENGINE } = {}) {
  const input = String(raw ?? '').trim();
  if (!input) return null;

  const bang = parseBang(input);
  if (bang) return { kind: 'search', query: bang.query, engine: bang.engine, url: webSearchUrl(bang.engine, bang.query) };

  if (KNOWN_SCHEME_RE.test(input)) return { kind: 'url', url: input };
  if (looksLikeHost(input)) {
    const host = input.split(/[/:?#]/)[0];
    const scheme = host === 'localhost' || IPV4_RE.test(host) ? 'http' : 'https';
    return { kind: 'url', url: `${scheme}://${input}` };
  }

  return { kind: 'search', query: input, engine: defaultEngine, url: searchUrl(defaultEngine, input) };
}

/** Extracts the query from a founder://search URL, or null for any other URL. */
function queryFromSearchUrl(url) {
  try {
    const u = new URL(url);
    return u.protocol === 'founder:' && u.host === 'search' ? u.searchParams.get('q') || '' : null;
  } catch {
    return null;
  }
}

module.exports = {
  WEB_ENGINES,
  FOUNDER_ENGINE,
  HOME_URL,
  classifyInput,
  parseBang,
  searchUrl,
  webSearchUrl,
  internalSearchUrl,
  queryFromSearchUrl,
};
