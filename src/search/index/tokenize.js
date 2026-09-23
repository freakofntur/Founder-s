'use strict';

const STOPWORDS = new Set(
  ('a an and are as at be but by for from has have he her his i if in into is it its me my not of on or our she so ' +
    'than that the their them then there these they this to was we were what when where which who will with you your')
    .split(' '),
);

/**
 * Lowercases, strips diacritics and splits on anything that is not a letter or digit.
 * Single-letter tokens and stopwords are dropped.
 */
function tokenize(text) {
  const tokens = String(text ?? '')
    .normalize('NFKD')
    .replace(/[̀-ͯ]/g, '')
    .toLowerCase()
    .match(/[\p{L}\p{N}]+/gu);
  if (!tokens) return [];
  return tokens.filter((t) => (t.length > 1 || /\d/.test(t)) && !STOPWORDS.has(t));
}

module.exports = { tokenize, STOPWORDS };
