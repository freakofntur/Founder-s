'use strict';

// founder://error?url=...&code=...&description=... — shown when a page fails to load.
const params = new URLSearchParams(location.search);
const url = params.get('url') || '';

document.getElementById('url').textContent = shortUrl(url);
document.getElementById('description').textContent = `${params.get('description') || 'Unknown error'} (${params.get('code')})`;
document.getElementById('retry').addEventListener('click', () => /^https?:/.test(url) && location.replace(url));
document.getElementById('search').addEventListener('click', () => go(shortUrl(url).replace(/[/.]/g, ' ')));
