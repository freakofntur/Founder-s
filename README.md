# Founder

A browser built around search. The address bar talks to a **search engine that runs inside the browser**. That engine
queries the things you already have (the pages you've read, your history, bookmarks and open tabs) along with instant
answers, and the open web stays one click away.

This is the skeleton. It works end to end, and each part is built to be swapped out or added to.

```bash
npm install
npm start      # launch the browser
npm test       # unit tests (plain Node, no Electron needed)
```

## What's in the skeleton

- **Browser shell.** Tabs (`WebContentsView`), back/forward/reload/stop, bookmarks, `target=_blank` opening as tabs, an
  error page for failed loads, and multiple windows.
- **Address bar.** Suggestions from every provider as you type, keyboard navigation, and "switch to tab". Bangs send a
  query to a specific engine (`!w tides`, `rust !gh`).
- **Local page index.** The text of each page you visit is extracted and saved in a BM25 full-text index that stays on
  your device.
- **Results page (`founder://search?q=`).** Sections for answers, your pages (with highlighted snippets), bookmarks,
  history, and hand-off links to the web engines.
- **Internal pages.** `founder://newtab`, `founder://search`, `founder://settings`, `founder://error`.
- **Keyboard shortcuts.** Ctrl+T/W/N, Ctrl+L or Ctrl+K (address bar), Ctrl+R, Alt+←/→, Ctrl+Tab, Ctrl+D, Ctrl+Shift+I.

## Architecture

```
┌─────────────────────────── BaseWindow ───────────────────────────┐
│  chrome view (src/ui/chrome)      transparent, always on top      │
│  tab strip · toolbar · address bar + suggestion dropdown          │
├───────────────────────────────────────────────────────────────────┤
│  active tab (WebContentsView)     web page or founder:// page     │
└───────────────────────────────────────────────────────────────────┘
        │ window.browser (preload/chrome.js)        │ window.founder (preload/tab.js,
        ▼                                            ▼  founder:// pages only)
┌──────────────────────── main process ────────────────────────────┐
│ ipc.js ──► services.js                                            │
│              ├─ SearchEngine ──► providers (fan-out, timeout,     │
│              │                    merge, dedupe, rank)            │
│              ├─ InvertedIndex ◄── indexer.js (on page load)       │
│              └─ History · Bookmarks · Settings (JSON in userData) │
└───────────────────────────────────────────────────────────────────┘
```

```
src/
  main/
    index.js           app lifecycle
    app-context.js     open windows + wiring (history recording, indexing)
    browser-window.js  one window: chrome view + tabs, layout, overlay
    tabs.js            TabManager (one WebContentsView per tab)
    ipc.js             every IPC channel, with sender checks
    menu.js            menu = keyboard shortcuts
    protocol.js        founder:// → src/ui/pages/<page>/
    indexer.js         extracts page text into the index
    services.js        builds storage + search engine (no Electron, testable)
    storage/           JsonStore, History, Bookmarks, Settings
  search/              pure Node, no Electron
    engine.js          SearchEngine + the Provider/Result contract
    query.js           URL-vs-search classification, bangs, web engines
    index/             tokenizer + BM25 inverted index
    providers/         navigation, calculator, pages, bookmarks, history, tabs, web
  preload/
    chrome.js          window.browser for the browser UI
    tab.js             window.founder for founder:// pages (web pages get nothing)
  ui/
    chrome/            browser UI
    pages/             internal pages (+ shared/ css & DOM helpers)
test/                  node:test suites
```

### Security model

- Every view runs `sandbox: true`, `contextIsolation: true`, `nodeIntegration: false`.
- Web pages get **no** API. Internal `founder://` pages get `window.founder`. The main process re-checks the sender on
  every call: `page:*` channels require a `founder:` frame, and `browser:*` channels require the chrome view.
- Page text is extracted in an isolated world, so page scripts can't tamper with it.
- Remote suggestions are **off** by default because they send keystrokes to DuckDuckGo.

## Extending it

### Add a search provider (the main extension point)

A provider is an object with an `id`, a `name` and a `suggest` or `search` function (or both). Either function can be
async. Each returns a list of `Result`s scored 0–1. The full contract is in `src/search/engine.js`.

```js
// src/search/providers/weather.js
function createWeatherProvider() {
  return {
    id: 'weather',
    name: 'Weather',
    order: 5, // section position on the results page
    async search(query, { signal }) {
      if (!/\bweather\b/i.test(query)) return [];
      const data = await fetch(`https://example-weather.test?q=${encodeURIComponent(query)}`, { signal }).then((r) => r.json());
      return [{ type: 'answer', title: `${data.temp}°`, description: data.place, score: 0.95 }];
    },
  };
}
```

Register it in `src/main/services.js` with `engine.register(createWeatherProvider())`. The engine applies a timeout
(the `signal` aborts when it runs out), catches errors, merges and dedupes results. The results page shows the provider's
results as its own section. To give that section a custom layout, add an entry to `RENDERERS` in
`src/ui/pages/search/search.js`.

The same pattern works for:

- **AI answers.** Send the query plus the top local hits to an LLM and return a `type: 'answer'` result. The page index
  already gives you retrieval.
- **Semantic search.** Add embeddings next to BM25 in a provider of its own, or behind `InvertedIndex`'s
  `add`/`search`/`toJSON` surface.
- **Other sources.** Your files, email, notes, or a team wiki.

### Add an internal page

Create `src/ui/pages/<name>/index.html` and it is served at `founder://<name>`. Include
`founder://shared/page.css` and `founder://shared/dom.js` for the shared look and helpers (`h`, `highlight`, `go`).

### Give internal pages a new capability

1. Add a handler in `src/main/ipc.js` with `page('page:thing', (...args) => …)`.
2. Expose it in `src/preload/tab.js`.

For the browser UI, use `browser(...)` and `src/preload/chrome.js` in the same way.

### Add a keyboard shortcut

Add a menu item with an `accelerator` in `src/main/menu.js`.

## Known limits and next steps

- **Storage.** History and the index are JSON files, which is fine for thousands of pages. Beyond that, move to SQLite
  (FTS5) behind the same `History` and `InvertedIndex` interfaces.
- **Missing browser basics.** There's no tab drag/reorder, downloads UI, find-in-page, zoom, permission prompts or
  private windows yet.
