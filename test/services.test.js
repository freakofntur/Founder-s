'use strict';

const test = require('node:test');
const assert = require('node:assert/strict');
const fs = require('node:fs');
const os = require('node:os');
const path = require('node:path');
const { createServices } = require('../src/main/services');

test('services persist across restarts and answer searches end to end', async (t) => {
  const dataDir = fs.mkdtempSync(path.join(os.tmpdir(), 'founder-'));
  t.after(() => fs.rmSync(dataDir, { recursive: true, force: true }));

  const first = createServices({ dataDir });
  first.history.record('https://tides.test/', 'Tide tables');
  first.bookmarks.add('https://moon.test/', 'Moon phases');
  first.pageIndex.add({ url: 'https://tides.test/', title: 'Tide tables', text: 'High water at the harbour is at noon.' });
  first.savePageIndex();
  first.settings.update({ searchEngine: 'google', remoteSuggestions: 'yes', bogus: 1 });
  first.flush();

  const second = createServices({ dataDir });
  assert.equal(second.settings.get().searchEngine, 'google');
  assert.equal(second.settings.get().remoteSuggestions, false, 'wrongly typed values are ignored');
  assert.equal(second.settings.get().bogus, undefined, 'unknown keys are ignored');
  assert.equal(second.history.all().length, 1);
  assert.ok(second.bookmarks.has('https://moon.test/'));

  const { sections } = await second.engine.search('harbour');
  assert.deepEqual(sections.map((s) => s.id), ['pages', 'web']);
  assert.equal(sections[0].results[0].url, 'https://tides.test/');

  const suggestions = await second.engine.suggest('tide');
  assert.equal(suggestions[0].url, 'https://www.google.com/search?q=tide');
  assert.ok(suggestions.some((s) => s.source === 'history' && s.url === 'https://tides.test/'));
});
