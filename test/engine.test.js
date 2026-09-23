'use strict';

const test = require('node:test');
const assert = require('node:assert/strict');
const { SearchEngine } = require('../src/search/engine');
const { evaluate } = require('../src/search/providers/calculator');
const { createNavigationProvider } = require('../src/search/providers/navigation');
const { createHistoryProvider } = require('../src/search/providers/history');

const quiet = { onError: () => {} };

test('suggest merges providers, dedupes by url and sorts by score', async () => {
  const engine = new SearchEngine(quiet);
  engine.register({ id: 'a', suggest: () => [{ type: 'navigate', title: 'A', url: 'https://x.test/', score: 0.4 }] });
  engine.register({
    id: 'b',
    suggest: () => [
      { type: 'navigate', title: 'A better', url: 'https://x.test', score: 0.7 },
      { type: 'navigate', title: 'B', url: 'https://y.test', score: 0.5 },
    ],
  });
  const results = await engine.suggest('x');
  assert.deepEqual(
    results.map((r) => [r.title, r.source]),
    [
      ['A better', 'b'],
      ['B', 'b'],
    ],
  );
});

test('slow and failing providers do not break the others', async () => {
  const engine = new SearchEngine({ ...quiet, timeoutMs: 20 });
  engine.register({ id: 'slow', suggest: () => new Promise((r) => setTimeout(() => r([{ title: 'late', score: 1 }]), 200)) });
  engine.register({ id: 'broken', suggest: () => { throw new Error('boom'); } });
  engine.register({ id: 'ok', suggest: () => [{ type: 'answer', title: 'fine', score: 0.5 }] });
  assert.deepEqual((await engine.suggest('q')).map((r) => r.title), ['fine']);
});

test('search returns non-empty sections in provider order', async () => {
  const engine = new SearchEngine(quiet);
  engine.register({ id: 'late', name: 'Late', order: 90, search: () => [{ title: 'l', score: 1 }] });
  engine.register({ id: 'empty', name: 'Empty', order: 1, search: () => [] });
  engine.register({ id: 'early', name: 'Early', order: 10, search: () => [{ title: 'e', score: 1 }] });
  const { sections } = await engine.search('q');
  assert.deepEqual(sections.map((s) => s.id), ['early', 'late']);
});

test('the navigation provider always offers what Enter does', async () => {
  const engine = new SearchEngine(quiet);
  engine.register(createNavigationProvider({ getSettings: () => ({ searchEngine: 'founder' }) }));
  const entry = { url: 'https://example.com/docs', title: 'Example docs', visitCount: 9, lastVisit: Date.now() };
  engine.register(createHistoryProvider({ all: () => [entry] }));

  const [first, second] = await engine.suggest('example docs');
  assert.deepEqual([first.type, first.url, first.score], ['search', 'founder://search?q=example%20docs', 1]);
  assert.deepEqual([second.source, second.url], ['history', entry.url]);

  const [url] = await engine.suggest('example.com');
  assert.deepEqual([url.type, url.url], ['navigate', 'https://example.com']);
});

test('calculator evaluates arithmetic and ignores plain numbers and text', () => {
  assert.equal(evaluate('2 * (3 + 4)'), 14);
  assert.equal(evaluate('2^10 ='), 1024);
  assert.equal(evaluate('-3 + 5 % 3'), -1);
  assert.equal(evaluate('0.1 + 0.2'), 0.3);
  assert.equal(evaluate('2024'), null);
  assert.equal(evaluate('iphone 15 - review'), null);
  assert.equal(evaluate('1 / 0'), null);
  assert.equal(evaluate('(1 + 2'), null);
});
