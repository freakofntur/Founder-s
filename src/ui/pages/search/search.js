'use strict';

// founder://search?q=... — renders the engine's sections. To show a new kind of result,
// register a provider in src/main/services.js and (optionally) add a renderer to RENDERERS.

const query = new URLSearchParams(location.search).get('q') || '';
const input = document.getElementById('q');
const status = document.getElementById('status');
const sectionsEl = document.getElementById('sections');

input.value = query;
document.title = query ? `${query} — Founder` : 'Search';
document.getElementById('search').addEventListener('submit', (e) => {
  e.preventDefault();
  go(input.value);
});

/** Section renderers by provider id; anything else uses the default result list. */
const RENDERERS = {
  calculator: (section) =>
    section.results.map((r) =>
      h(
        'div',
        { className: 'answer' },
        h('div', { className: 'answer-question' }, r.description),
        h('div', { className: 'answer-value' }, r.title),
      ),
    ),
  web: (section) => [
    h(
      'div',
      { className: 'chips' },
      section.results.map((r) => h('a', { className: 'button' + (r.score > 0.5 ? ' primary' : ''), href: r.url }, r.title)),
    ),
  ],
};

function renderResult(r) {
  return h(
    'div',
    { className: 'result' },
    h('div', { className: 'result-url' }, shortUrl(r.url)),
    h('a', { className: 'result-title', href: r.url }, r.title),
    r.snippet ? h('p', { className: 'result-snippet' }, highlight(r.snippet, r.terms)) : null,
  );
}

function renderSection(section) {
  const render = RENDERERS[section.id] || ((s) => s.results.map(renderResult));
  return h('section', { id: `section-${section.id}` }, h('h2', {}, section.name), render(section));
}

function renderEmpty() {
  return h(
    'div',
    { className: 'empty' },
    h('p', {}, 'Nothing from your own browsing matches yet.'),
    h('p', { className: 'muted' }, 'Founder indexes the pages you visit, so results here grow as you browse.'),
  );
}

(async () => {
  if (!query) return input.focus();
  status.textContent = 'Searching…';
  const started = performance.now();
  const { sections } = await window.founder.search(query);
  const ms = Math.round(performance.now() - started);

  const count = sections.filter((s) => s.id !== 'web').reduce((n, s) => n + s.results.length, 0);
  status.textContent = `${count} result${count === 1 ? '' : 's'} in ${ms} ms`;

  const local = sections.filter((s) => s.id !== 'web');
  const web = sections.filter((s) => s.id === 'web');
  sectionsEl.replaceChildren(...(local.length ? local.map(renderSection) : [renderEmpty()]), ...web.map(renderSection));
})();
