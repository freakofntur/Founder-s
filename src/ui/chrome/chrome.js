'use strict';

// Browser UI: tab strip, navigation buttons and the address bar with its suggestion dropdown.
// Talks to the main process only through `window.browser` (src/preload/chrome.js).

const $ = (id) => document.getElementById(id);
const els = {
  tabs: $('tabs'),
  newTab: $('new-tab'),
  back: $('back'),
  forward: $('forward'),
  reload: $('reload'),
  address: $('address'),
  input: $('address-input'),
  bookmark: $('bookmark'),
  backdrop: $('backdrop'),
  suggestions: $('suggestions'),
};

let state = { tabs: [], activeId: null, bookmarked: false };
let suggestions = [];
let selected = -1;
let editing = false; // true once the user types; stops tab updates from clobbering their input
let requestSeq = 0;

const activeTab = () => state.tabs.find((t) => t.id === state.activeId);

// ---------- state from the main process ----------

window.browser.onState((next) => {
  state = next;
  renderTabs();
  renderToolbar();
});

window.browser.onFocusAddressBar(() => {
  els.input.focus();
  els.input.select();
});

// ---------- tab strip ----------

function renderTabs() {
  els.tabs.replaceChildren(
    ...state.tabs.map((tab) => {
      const el = document.createElement('div');
      el.className = 'tab' + (tab.id === state.activeId ? ' active' : '') + (tab.loading ? ' loading' : '');
      el.setAttribute('role', 'tab');
      el.title = tab.title || tab.url;

      let icon;
      if (tab.favicon && !tab.loading) {
        icon = document.createElement('img');
        icon.src = tab.favicon;
        icon.onerror = () => icon.replaceWith(Object.assign(document.createElement('span'), { className: 'tab-icon placeholder' }));
      } else {
        icon = document.createElement('span');
        if (!tab.loading) icon.classList.add('placeholder');
      }
      icon.classList.add('tab-icon');

      const title = document.createElement('span');
      title.className = 'tab-title';
      title.textContent = tab.title || displayUrl(tab.url) || 'New Tab';

      const close = document.createElement('button');
      close.className = 'tab-close icon-btn';
      close.title = 'Close tab (Ctrl+W)';
      close.innerHTML = '<svg viewBox="0 0 16 16"><path d="M4 4l8 8M12 4l-8 8"/></svg>';
      close.addEventListener('click', (e) => {
        e.stopPropagation();
        window.browser.closeTab(tab.id);
      });

      el.addEventListener('mousedown', (e) => e.button === 0 && window.browser.activateTab(tab.id));
      el.addEventListener('auxclick', (e) => e.button === 1 && window.browser.closeTab(tab.id));
      el.append(icon, title, close);
      return el;
    }),
  );
}

els.newTab.addEventListener('click', () => window.browser.newTab());

// ---------- toolbar ----------

/**
 * What the address bar shows for a URL: nothing for the new tab page, the query for our
 * results page, and the page that failed for the error page.
 */
function displayUrl(url) {
  if (!url || url === 'founder://newtab' || url === 'founder://newtab/') return '';
  try {
    const u = new URL(url);
    if (u.protocol === 'founder:' && u.host === 'search') return u.searchParams.get('q') || '';
    if (u.protocol === 'founder:' && u.host === 'error') return u.searchParams.get('url') || '';
  } catch {}
  return url;
}

function renderToolbar() {
  const tab = activeTab();
  els.back.disabled = !tab?.canGoBack;
  els.forward.disabled = !tab?.canGoForward;
  els.reload.classList.toggle('loading', !!tab?.loading);
  els.reload.title = tab?.loading ? 'Stop' : 'Reload (Ctrl+R)';
  els.bookmark.classList.toggle('on', !!state.bookmarked);
  els.bookmark.hidden = !tab || !/^https?:/.test(tab.url);
  if (!editing) els.input.value = displayUrl(tab?.url);
}

els.back.addEventListener('click', () => window.browser.back());
els.forward.addEventListener('click', () => window.browser.forward());
els.reload.addEventListener('click', () => (activeTab()?.loading ? window.browser.stop() : window.browser.reload()));
els.bookmark.addEventListener('click', () => window.browser.toggleBookmark());

// ---------- address bar ----------

els.input.addEventListener('focus', () => els.input.select());

els.input.addEventListener('input', () => {
  editing = true;
  updateSuggestions();
});

els.input.addEventListener('keydown', (e) => {
  switch (e.key) {
    case 'ArrowDown':
    case 'ArrowUp':
      if (!suggestions.length) return;
      e.preventDefault();
      select((selected + (e.key === 'ArrowDown' ? 1 : -1) + suggestions.length) % suggestions.length);
      break;
    case 'Enter':
      e.preventDefault();
      commit(suggestions[selected]);
      break;
    case 'Escape':
      if (suggestions.length) closeSuggestions();
      else resetAddressBar();
      break;
  }
});

els.input.addEventListener('blur', () => {
  closeSuggestions();
  editing = false;
  renderToolbar();
});

function resetAddressBar() {
  editing = false;
  renderToolbar();
  els.input.blur();
}

/** Acts on a suggestion, or on the raw text when there is none. */
function commit(item) {
  const text = els.input.value.trim();
  if (!item && !text) return;
  if (!item) window.browser.submit(text);
  else if (item.type === 'tab') window.browser.activateTab(item.tabId);
  else if (item.url) window.browser.navigate(item.url);
  else return; // e.g. an answer: nothing to open, keep it on screen
  resetAddressBar();
}

async function updateSuggestions() {
  const seq = ++requestSeq;
  const text = els.input.value;
  if (!text.trim()) return closeSuggestions();
  const results = await window.browser.suggest(text);
  if (seq !== requestSeq || document.activeElement !== els.input) return; // stale
  suggestions = results;
  selected = results.length ? 0 : -1;
  renderSuggestions();
}

// ---------- suggestion dropdown ----------

const ICONS = { search: '⌕', answer: '=', tab: '⇥', history: '↺', bookmarks: '★', pages: '¶', navigate: '↗' };

function renderSuggestions() {
  if (!suggestions.length) return closeSuggestions();

  const box = els.address.getBoundingClientRect();
  Object.assign(els.suggestions.style, { left: `${box.left}px`, width: `${box.width}px` });

  els.suggestions.replaceChildren(
    ...suggestions.map((item, i) => {
      const li = document.createElement('li');
      li.className = `suggestion ${item.type}` + (i === selected ? ' selected' : '');
      li.setAttribute('role', 'option');

      const icon = document.createElement('span');
      icon.className = 'suggestion-icon';
      icon.textContent = ICONS[item.type === 'navigate' ? item.source : item.type] || ICONS.navigate;

      const text = document.createElement('span');
      text.className = 'suggestion-text';
      const title = document.createElement('span');
      title.className = 'suggestion-title';
      title.textContent = item.title;
      const description = document.createElement('span');
      description.className = 'suggestion-description';
      description.textContent = item.description || '';
      text.append(title, description);

      li.append(icon, text);
      li.addEventListener('mousemove', () => i !== selected && select(i));
      li.addEventListener('mousedown', (e) => e.preventDefault()); // keep focus in the input
      li.addEventListener('click', () => commit(item));
      return li;
    }),
  );

  if (els.suggestions.hidden) {
    window.browser.setOverlay(true);
    els.suggestions.hidden = false;
    els.backdrop.hidden = false;
  }
}

function select(i) {
  selected = i;
  [...els.suggestions.children].forEach((li, j) => li.classList.toggle('selected', j === i));
  els.suggestions.children[i]?.scrollIntoView({ block: 'nearest' });
}

function closeSuggestions() {
  requestSeq++;
  suggestions = [];
  selected = -1;
  if (els.suggestions.hidden) return;
  els.suggestions.hidden = true;
  els.backdrop.hidden = true;
  window.browser.setOverlay(false);
}

els.backdrop.addEventListener('mousedown', () => els.input.blur());
window.addEventListener('resize', () => suggestions.length && renderSuggestions());
