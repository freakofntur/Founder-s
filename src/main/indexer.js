'use strict';

/**
 * Feeds visited pages into the local search index. Runs the extraction script in an
 * isolated world so the page's own JavaScript cannot interfere with it.
 */

const ISOLATED_WORLD_ID = 1001;
const SETTLE_MS = 1500; // let client-rendered pages paint their content first

const EXTRACT_SCRIPT = `(() => {
  const meta = document.querySelector('meta[name="description"], meta[property="og:description"]');
  return {
    title: document.title,
    description: meta ? meta.content : '',
    text: document.body ? document.body.innerText.slice(0, 20000) : '',
  };
})()`;

/** @param {ReturnType<import('./services').createServices>} services */
function createIndexer(services) {
  return {
    /** Call when a tab finished loading. */
    schedule(tab) {
      const url = tab.url;
      if (!/^https?:/.test(url) || !services.settings.get().indexPages) return;
      setTimeout(async () => {
        const wc = tab.view.webContents;
        if (wc.isDestroyed() || wc.getURL() !== url) return;
        try {
          const page = await wc.executeJavaScriptInIsolatedWorld(ISOLATED_WORLD_ID, [{ code: EXTRACT_SCRIPT }]);
          if (!page?.text) return;
          services.pageIndex.add({ url: url.replace(/#.*$/, ''), ...page });
          services.savePageIndex();
        } catch (err) {
          console.warn('[indexer] failed to index', url, err.message);
        }
      }, SETTLE_MS);
    },
  };
}

module.exports = { createIndexer };
