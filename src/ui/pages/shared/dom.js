'use strict';

// Small DOM helpers shared by internal pages. Everything user- or web-supplied goes in as text, never HTML.

/** h('a', { href, className }, 'text', child) */
function h(tag, props = {}, ...children) {
  const el = document.createElement(tag);
  for (const [key, value] of Object.entries(props)) {
    if (value === undefined || value === null || value === false) continue;
    if (key.startsWith('on')) el.addEventListener(key.slice(2).toLowerCase(), value);
    else if (key in el) el[key] = value;
    else el.setAttribute(key, value);
  }
  el.append(...children.flat().filter((c) => c !== null && c !== undefined && c !== false));
  return el;
}

/** Text with every occurrence of `terms` wrapped in <mark>. */
function highlight(text, terms = []) {
  const words = terms.filter(Boolean).map((t) => t.replace(/[.*+?^${}()|[\]\\]/g, '\\$&'));
  if (!text || words.length === 0) return [text || ''];
  const re = new RegExp(`(${words.join('|')})`, 'gi');
  return text.split(re).map((part, i) => (i % 2 ? h('mark', {}, part) : part));
}

/** Resolves typed input the same way the address bar does, then goes there. */
async function go(input) {
  const url = await window.founder.resolve(input);
  if (url) location.href = url;
}

function shortUrl(url) {
  return url.replace(/^https?:\/\/(www\.)?/, '').replace(/\/$/, '');
}
