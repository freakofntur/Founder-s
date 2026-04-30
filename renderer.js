// Get all DOM elements
const backBtn = document.getElementById('back-btn');
const forwardBtn = document.getElementById('forward-btn');
const refreshBtn = document.getElementById('refresh-btn');
const urlInput = document.getElementById('url-input');
const goBtn = document.getElementById('go-btn');
const browser = document.getElementById('browser');

// Back button - Go to previous page
backBtn.addEventListener('click', () => {
  if (browser.canGoBack()) {
    browser.goBack();
  }
});

// Forward button - Go to next page
forwardBtn.addEventListener('click', () => {
  if (browser.canGoForward()) {
    browser.goForward();
  }
});

// Refresh button - Reload current page
refreshBtn.addEventListener('click', () => {
  browser.reload();
});

// Go button - Navigate to URL or search
goBtn.addEventListener('click', () => {
  navigateToUrl(urlInput.value);
});

// Enter key in URL input - Navigate to URL or search
urlInput.addEventListener('keypress', (e) => {
  if (e.key === 'Enter') {
    navigateToUrl(urlInput.value);
  }
});

// Navigate to URL with smart detection
function navigateToUrl(input) {
  let url = input.trim();

  // If empty, do nothing
  if (!url) return;

  // Check if it looks like a URL (contains . or starts with http)
  if (url.includes('.') && !url.includes(' ')) {
    // Add https:// if no protocol specified
    if (!url.startsWith('http://') && !url.startsWith('https://')) {
      url = 'https://' + url;
    }
  } else {
    // Treat as search query - use Google search
    url = 'https://www.google.com/search?q=' + encodeURIComponent(url);
  }

  browser.loadURL(url);
  urlInput.value = url;
}

// Update URL input when page changes
browser.addEventListener('did-navigate', (e) => {
  urlInput.value = e.url;
  updateNavButtons();
});

// Update URL input when page finishes loading
browser.addEventListener('did-finish-load', () => {
  updateNavButtons();
});

// Update navigation button states
function updateNavButtons() {
  backBtn.disabled = !browser.canGoBack();
  forwardBtn.disabled = !browser.canGoForward();
}

// Initialize button states
updateNavButtons();
