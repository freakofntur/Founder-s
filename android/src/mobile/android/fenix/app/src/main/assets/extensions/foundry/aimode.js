/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/. */

/**
 * Foundry: reads the sources that Google's AI answer (AI Mode, udm=50) cites, in the order they appear, and sends
 * them to the app. The answer streams in, so this checks every couple of seconds and sends again whenever the list
 * grows and has held still for one check.
 */
(function () {
  const params = new URLSearchParams(location.search);
  if (params.get("udm") !== "50") {
    return;
  }
  const query = params.get("q") || "";
  const TICK_MS = 2000;
  const GIVE_UP_MS = 60000;
  const started = Date.now();
  let sentCount = -1;

  function isGoogleHost(host) {
    return /(^|\.)(google\.[a-z.]+|gstatic\.com|googleusercontent\.com|googleapis\.com|googleadservices\.com|doubleclick\.net)$/.test(
      host
    );
  }

  function unwrap(href) {
    try {
      const url = new URL(href, location.href);
      if (isGoogleHost(url.hostname) && url.pathname === "/url") {
        return url.searchParams.get("q") || url.searchParams.get("url");
      }
      return url.href;
    } catch (e) {
      return null;
    }
  }

  function kindOf(url) {
    const host = url.hostname.replace(/^www\.|^m\./, "");
    const path = url.pathname;
    if (
      (host === "youtube.com" && (path === "/watch" || path.startsWith("/shorts/"))) ||
      host === "youtu.be" ||
      host === "vimeo.com" ||
      (host === "tiktok.com" && path.includes("/video/")) ||
      (host === "instagram.com" && path.startsWith("/reel"))
    ) {
      return "video";
    }
    if (/\.(jpe?g|png|webp|gif|avif)$/i.test(path)) {
      return "image";
    }
    return "page";
  }

  function titleOf(anchor) {
    const label = anchor.getAttribute("aria-label") || anchor.textContent || "";
    return label.replace(/\s+/g, " ").trim().slice(0, 140);
  }

  /** Thumbnails and other files the answer's data mentions that are not sources. */
  function isNoise(url) {
    return /(^|\.)(ytimg\.com|ggpht\.com|fbcdn\.net|twimg\.com)$/.test(url.hostname) || url.pathname === "/";
  }

  /**
   * AI Mode keeps each citation's sources as escaped JSON in an HTML comment next to its chip
   * (`<!--TgQPHd|||[[...]]-->`), in the order the answer cites them. Real links come second, in case that changes.
   */
  function addressesInOrder() {
    const found = [];
    const walker = document.createTreeWalker(document.body, NodeFilter.SHOW_COMMENT);
    for (let node = walker.nextNode(); node; node = walker.nextNode()) {
      const data = node.data;
      if (!data.includes("|||") || !data.includes("http")) {
        continue;
      }
      const text = data.replace(/&quot;/g, '"').replace(/&amp;/g, "&");
      for (const match of text.matchAll(/"(https?:[^"]+)"/g)) {
        try {
          found.push({ href: JSON.parse('"' + match[1] + '"'), title: "" });
        } catch (e) {}
      }
    }
    for (const anchor of document.querySelectorAll("a[href]")) {
      found.push({ href: anchor.getAttribute("href"), title: titleOf(anchor) });
    }
    return found;
  }

  /**
   * YouTube redirects www to m on phones, and Foundry's "open in the YouTube app?" check stops that redirect in a
   * window that isn't the selected tab, so go straight to the mobile site.
   */
  function mobileYouTube(url) {
    if (url.hostname === "youtu.be") {
      const id = url.pathname.slice(1);
      url.href = "https://m.youtube.com/watch?v=" + id + url.search.replace(/^\?/, "&");
    } else if (url.hostname === "youtube.com" || url.hostname === "www.youtube.com") {
      url.hostname = "m.youtube.com";
    }
  }

  function collect() {
    const seen = new Set();
    const sources = [];
    for (const { href: raw, title } of addressesInOrder()) {
      const href = unwrap(raw);
      if (!href || !/^https?:/.test(href)) {
        continue;
      }
      const url = new URL(href);
      if (isGoogleHost(url.hostname) || isNoise(url)) {
        continue;
      }
      url.hash = "";
      mobileYouTube(url);
      const key = url.href;
      if (seen.has(key)) {
        continue;
      }
      seen.add(key);
      sources.push({ url: key, title, kind: kindOf(url) });
    }
    return sources;
  }

  let lastCount = -1;

  /** Sends when the list has grown and held still for one tick, so a half-streamed answer isn't sent. */
  function tick() {
    const sources = collect();
    if (sources.length > 0 && sources.length === lastCount && sources.length !== sentCount) {
      sentCount = sources.length;
      browser.runtime.sendNativeMessage("foundry", { type: "aimode-sources", query, sources });
    }
    lastCount = sources.length;
    if (Date.now() - started < GIVE_UP_MS) {
      setTimeout(tick, TICK_MS);
    }
  }

  setTimeout(tick, TICK_MS);
})();
