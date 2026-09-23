'use strict';

/** Shared matching/scoring for providers that search small lists of {url, title}. */

const DAY_MS = 24 * 60 * 60 * 1000;

function stripUrl(url) {
  return url.replace(/^https?:\/\/(www\.)?/, '');
}

/**
 * Scores an entry in [0, 1) against a lowercased query, or returns 0 if any query word is missing.
 * Prefix matches on the URL ("gith" -> github.com) weigh most; frequent and recent visits break ties.
 */
function scoreEntry(entry, query, now = Date.now()) {
  const words = query.toLowerCase().split(/\s+/).filter(Boolean);
  if (words.length === 0) return 0;
  const url = stripUrl(entry.url).toLowerCase();
  const haystack = `${(entry.title || '').toLowerCase()} ${url}`;
  if (!words.every((w) => haystack.includes(w))) return 0;

  let score = 0.5;
  if (url.startsWith(words[0])) score += 0.25;
  else if ((entry.title || '').toLowerCase().startsWith(words[0])) score += 0.1;
  if (entry.visitCount) score += 0.1 * Math.min(1, Math.log2(entry.visitCount + 1) / 5);
  const last = entry.lastVisit || entry.createdAt;
  if (last) score += 0.1 * Math.exp(-(now - last) / (30 * DAY_MS));
  return Math.min(score, 0.98);
}

function matchEntries(entries, query, { limit, type = 'navigate', boost = 0, now = Date.now() } = {}) {
  return entries
    .map((entry) => ({ entry, score: scoreEntry(entry, query, now) }))
    .filter((m) => m.score > 0)
    .sort((a, b) => b.score - a.score)
    .slice(0, limit)
    .map(({ entry, score }) => ({
      type,
      title: entry.title || stripUrl(entry.url),
      url: entry.url,
      description: stripUrl(entry.url),
      score: Math.min(score + boost, 0.98),
    }));
}

module.exports = { scoreEntry, matchEntries, stripUrl };
