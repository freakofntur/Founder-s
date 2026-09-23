'use strict';

const test = require('node:test');
const assert = require('node:assert/strict');
const { classifyInput, parseBang, queryFromSearchUrl } = require('../src/search/query');

test('URLs with a scheme are opened as-is', () => {
  assert.deepEqual(classifyInput('https://example.com/a?b=1'), { kind: 'url', url: 'https://example.com/a?b=1' });
  assert.equal(classifyInput('founder://settings').url, 'founder://settings');
});

test('bare hostnames get https, local addresses get http', () => {
  assert.equal(classifyInput('example.com').url, 'https://example.com');
  assert.equal(classifyInput('news.ycombinator.com/item?id=1').url, 'https://news.ycombinator.com/item?id=1');
  assert.equal(classifyInput('localhost:3000/app').url, 'http://localhost:3000/app');
  assert.equal(classifyInput('192.168.1.1').url, 'http://192.168.1.1');
});

test('everything else is a search on the default engine', () => {
  assert.deepEqual(classifyInput('how do  tides work'), {
    kind: 'search',
    query: 'how do  tides work',
    engine: 'founder',
    url: 'founder://search?q=how%20do%20%20tides%20work',
  });
  assert.equal(classifyInput('version 1.5', { defaultEngine: 'google' }).url, 'https://www.google.com/search?q=version%201.5');
  assert.equal(classifyInput('   '), null);
});

test('bangs pick an engine, at either end of the query', () => {
  assert.deepEqual(parseBang('!w tides'), { engine: 'wikipedia', query: 'tides' });
  assert.deepEqual(parseBang('tides !W'), { engine: 'wikipedia', query: 'tides' });
  assert.equal(parseBang('!nope tides'), null);
  assert.equal(classifyInput('!gh electron').url, 'https://github.com/search?q=electron');
});

test('queryFromSearchUrl only reads founder://search', () => {
  assert.equal(queryFromSearchUrl('founder://search?q=a%20b'), 'a b');
  assert.equal(queryFromSearchUrl('https://example.com/search?q=a'), null);
});
