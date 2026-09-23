'use strict';

const $ = (id) => document.getElementById(id);

async function load() {
  const { settings, engines, stats } = await window.founder.getSettings();

  $('searchEngine').replaceChildren(
    h('option', { value: 'founder' }, 'Founder (your pages + web)'),
    ...engines.map((e) => h('option', { value: e.id }, e.name)),
  );
  $('webEngine').replaceChildren(...engines.map((e) => h('option', { value: e.id }, e.name)));

  for (const [key, value] of Object.entries(settings)) {
    const el = $(key);
    if (!el) continue;
    if (el.type === 'checkbox') el.checked = value;
    else el.value = value;
  }
  $('history-count').textContent = `${stats.historyEntries} pages`;
  $('index-count').textContent = `${stats.indexedPages} pages`;
}

document.addEventListener('change', (e) => {
  const el = e.target;
  if (!el.id) return;
  window.founder.updateSettings({ [el.id]: el.type === 'checkbox' ? el.checked : el.value });
});

document.addEventListener('click', async (e) => {
  const what = e.target.dataset?.clear;
  if (!what) return;
  await window.founder.clear(what);
  load();
});

load();
