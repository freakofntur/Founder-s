'use strict';

document.getElementById('search').addEventListener('submit', (e) => {
  e.preventDefault();
  go(document.getElementById('q').value);
});

(async () => {
  const recent = await window.founder.recent(8);
  if (!recent.length) return;
  document.getElementById('recent-list').replaceChildren(
    ...recent.map((entry) =>
      h(
        'a',
        { className: 'tile', href: entry.url, title: entry.url },
        h('div', { className: 'tile-title' }, entry.title || shortUrl(entry.url)),
        h('div', { className: 'tile-url' }, shortUrl(entry.url)),
      ),
    ),
  );
  document.getElementById('recent').hidden = false;
})();
