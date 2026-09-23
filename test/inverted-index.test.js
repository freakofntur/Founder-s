'use strict';

const test = require('node:test');
const assert = require('node:assert/strict');
const { InvertedIndex } = require('../src/search/index/inverted-index');
const { tokenize } = require('../src/search/index/tokenize');

const pages = [
  { url: 'https://a.test/tides', title: 'How tides work', text: 'The moon pulls the ocean. Tides rise and fall twice a day.' },
  { url: 'https://a.test/moon', title: 'The Moon', text: 'The moon is Earth’s only natural satellite. It shapes the tides.' },
  { url: 'https://a.test/bread', title: 'Sourdough bread', text: 'Flour, water, salt and a lively starter.' },
];

function build(opts) {
  const index = new InvertedIndex(opts);
  pages.forEach((p, i) => index.add({ ...p, indexedAt: i }));
  return index;
}

test('tokenize lowercases, strips accents and stopwords', () => {
  assert.deepEqual(tokenize('The Café is OPEN, 24/7!'), ['cafe', 'open', '24', '7']);
});

test('ranks title matches first and builds a snippet around the hit', () => {
  const hits = build().search('tides');
  assert.deepEqual(
    hits.map((h) => h.url),
    ['https://a.test/tides', 'https://a.test/moon'],
  );
  assert.match(hits[1].snippet, /shapes the tides/);
});

test('documents matching every term beat partial matches', () => {
  const hits = build().search('sourdough moon satellite');
  assert.equal(hits[0].url, 'https://a.test/moon');
});

test('prefix mode completes the last word', () => {
  const index = build();
  assert.equal(index.search('sourd').length, 0);
  assert.equal(index.search('sourd', { prefix: true })[0].url, 'https://a.test/bread');
});

test('re-adding a url replaces it, remove drops it', () => {
  const index = build();
  index.add({ url: 'https://a.test/bread', title: 'Rye', text: 'dense loaf' });
  assert.equal(index.size, 3);
  assert.equal(index.search('sourdough').length, 0);
  assert.equal(index.search('rye')[0].url, 'https://a.test/bread');
  index.remove('https://a.test/bread');
  assert.equal(index.search('rye').length, 0);
  assert.equal(index.postings.has('rye'), false);
});

test('evicts the oldest documents beyond maxDocs', () => {
  const index = build({ maxDocs: 2 });
  assert.deepEqual([...index.docs.keys()], ['https://a.test/moon', 'https://a.test/bread']);
});

test('survives a JSON round trip', () => {
  const copy = InvertedIndex.fromJSON(JSON.parse(JSON.stringify(build())));
  assert.equal(copy.size, 3);
  assert.deepEqual(
    copy.search('tides').map((h) => h.url),
    build().search('tides').map((h) => h.url),
  );
});
