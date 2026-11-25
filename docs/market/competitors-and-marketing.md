# Foundry: market and marketing (2026-11-25)

Written by Founder (acting as marketing lead) for the owner. Research date: 25 November 2026. Sources are linked
inline; where only one report supports a claim, it says so.

## The short version

- **AI in the browser is now standard, and the giants give it away.** Chrome on Android has had Gemini for all US users
  since 18 August 2026. Edge put Copilot into its mobile app in May 2026. Samsung Browser has Perplexity built in on
  over a billion Galaxy devices. Opera's AI is free. A small team can't win as "a browser with an AI".
- **Every one of them pushes one AI: its own.** Chrome has Gemini, Edge has Copilot, Samsung and Comet have Perplexity,
  Opera Neon resells its bundle. Nobody on mobile shows several AIs and the sources behind them side by side, with the
  user's own logins. That is Foundry's opening.
- **Standalone AI browsers are shaky.** OpenAI marks ChatGPT Atlas as deprecated. One report says Arc shuts down on
  22 December 2026. Opera Neon costs $19.90 a month. Distribution, not features, decides this market.
- **Foundry rides Google's AI Mode (1 billion+ monthly users), and that is also its biggest risk.** It depends on a
  hidden page format Google can change any day, and Google's terms bar automated access (it is suing SerpApi over
  scraping).
- **Foundry isn't ready to ship to strangers.** It is a solo project and a Firefox fork that must keep up with security
  updates. It still runs Mozilla telemetry and Mozilla's app ID. It has only been tested on an emulator.

## 1. Competitors

### 1.1 AI and search-first browsers

| Product | Does well | Weak spots | Price / model | For whom | Several AIs and sources? | Mobile UX | Foundry vs it |
|---|---|---|---|---|---|---|---|
| **Perplexity Comet** ([Android launch](https://techcrunch.com/2025/11/20/perplexity-brings-its-ai-browser-comet-to-android/), [free on iPhone](https://engadget.com/apps/perplexitys-comet-ai-browser-is-now-available-for-iphones-183947569.html)) | An assistant that researches and shops for you; built-in ad blocker; voice | One AI (Perplexity); prompt-injection worries ([Neowin](https://www.neowin.net/news/perplexitys-ai-super-browser-comet-finally-launches-on-android/)); no password manager at launch | Free browser; Perplexity Pro $20 and Max $200 a month | Perplexity fans, shoppers, researchers | Perplexity only; it cites sources but writes the answer itself | Chromium; one assistant panel | **Beats:** neutral, shows the sources themselves as live pages, uses your own ChatGPT/Claude. **Loses:** money, agents, polish, an iOS app |
| **Dia / Arc Search** (The Browser Company, bought by Atlassian for [$610M](https://www.atlassian.com/blog/announcements/atlassian-acquires-the-browser-company)) | Dia: AI across your tabs for work. Arc Search: "Browse for me" summaries on phones | Arc is winding down; [one report](https://www.androidauthority.com/arc-browser-shutting-down-december-2026-3726900/) says shut-down on 22 Dec 2026. Dia is desktop-only and work-focused | Dia: undisclosed; part of Atlassian | Knowledge workers (Dia) | One built-in AI | Arc Search's gestures were loved, but it is ending | **Opening:** Arc Search fans on phones will need a new home within weeks. Foundry is the closest "search-first phone browser" |
| **Opera Neon / Opera with Aria** ([Neon $19.90/mo](https://techcrunch.com/2025/12/11/opera-wants-you-to-pay-20-a-month-to-use-its-ai-powered-browser-neon), [Opera browser AI on Android](https://blogs.opera.com/mobile/2026/01/say-hello-to-opera-browser-ai-on-opera-for-android/)) | Neon: agents, bundled top models. Opera for Android: free built-in AI, no account needed | Neon is paid, desktop-first ([one review](https://wetheflywheel.com/en/radar/opera-neon-review/) says no mobile app) | Neon $19.90/mo; Opera free, funded by search and ad deals | Power users (Neon), mass market (Opera) | Neon bundles several models behind Opera's own UI, not your accounts | Opera Android is mature, with a bottom bar option | **Beats:** free, your own logins, side by side. **Loses:** a 25-year-old brand with distribution deals |
| **Brave with Leo** ([pricing](https://www.eesel.ai/blog/brave-leo-pricing)) | Privacy brand; own search index; Leo cites Brave Search | Leo is one assistant; the free models are small | Leo Premium $14.99/mo; BAT ads; search ads | Privacy-minded users | Several models inside Leo, but one interface and answer at a time | Chromium; solid mobile app | **Beats:** compares real services side by side. **Loses:** privacy reputation, its own search engine, scale |
| **Chrome with Gemini** ([Android rollout to all US users, Aug 2026](https://blog.google/products-and-platforms/products/chrome/gemini-in-chrome-android-auto-browse/)) | Default on billions of phones; auto browse; AI Mode a tap away | Gemini only; auto browse needs AI Pro/Ultra; US-first | Free; paid tiers for agents; Google's ad business | Everyone | Gemini only | Top address bar; tab groups | **Beats:** neutral, side-by-side AIs, bottom-first gestures. **Loses:** it's the default; Google controls the AI Mode page Foundry reads |
| **Edge with Copilot** ([May 2026 update](https://blogs.windows.com/msedgedev/2026/05/13/new-updates-to-edge-across-desktop-and-mobile/)) | Copilot across open mobile tabs; voice and vision; "Journeys" | Copilot only; agents for M365 Premium, US only | Free; Microsoft 365 subscriptions | Windows / Microsoft 365 users | Copilot only | Chromium mobile | Same as Chrome: Foundry wins on neutrality and loses on distribution |
| **Samsung Browser** ([Perplexity built in](https://hub-prod.perplexity.ai/hub/blog/perplexity-apis-power-ai-browsing-on-samsung-devices), [Samsung newsroom](https://news.samsung.com/global/samsung-takes-its-browser-beyond-mobile-extending-agentic-ai-across-devices)) | Preinstalled on Galaxy phones; "Browsing Assist" with Perplexity; now on Windows too | Needs a Samsung account; Perplexity only | Free; partner deals | Galaxy owners | Perplexity only | Polished, bottom bar | **Loses** on reach. **Beats** on choice of AI |
| **Firefox** ([Smart Window](https://blog.mozilla.org/firefox/firefox-smart-window/), [AI controls on mobile](https://www.androidauthority.com/firefox-ai-controls-3637356/)) | Trust, privacy, extensions; opt-in AI with controls | Smart Window is desktop-only and still rolling out; little AI on Android | Free; Google search deal | Privacy and extension users | Choose one chatbot provider in a sidebar | Fenix is the base Foundry is built on | Foundry is Firefox plus multi-source search. **Risk:** Mozilla could ship a similar view |
| **ChatGPT Atlas** ([OpenAI](https://openai.com/index/introducing-chatgpt-atlas/)) | Showed that an AI company would build a browser | OpenAI's page now says "Atlas has since been deprecated"; it never shipped on Android | Was free with ChatGPT plans | ChatGPT users | ChatGPT only | macOS only | Proof that a browser alone doesn't hold users; distribution and habit win |

### 1.2 AI answer apps people use instead of a browser

| Product | Does well | Weak spots | Price | Several AIs and sources? | Foundry vs it |
|---|---|---|---|---|---|
| **Google AI Mode** ([1B+ monthly users](https://blog.google/products-and-platforms/products/search/ai-mode-us-insights/)) | Fast, free, built into Search; answers cite sources | One AI; the sources are small chips you rarely open | Free (ads) | Gemini only; cites sources | Foundry **uses** AI Mode as L0, then opens the cited sources as real pages. **Risk:** Google can change or block that page |
| **ChatGPT search** ([about 1.2B weekly ChatGPT users, ~20% of Google's search volume](https://www.cmswire.com/digital-experience/chatgpt-search-volume-gains-on-google-openais-consumer-lead-says/)) | Habit; memory; follow-ups | One AI; sources take extra taps | Free; Plus/Pro; ads | ChatGPT only | Foundry puts ChatGPT beside Claude and Google instead of replacing it |
| **Perplexity** ([pricing](https://www.finout.io/blog/perplexity-pricing-in-2026)) | Answers with citations; Pro search | Its own AI writes the answer; paid tiers | Free; Pro $20, Max $200 a month | Several models inside Perplexity, one answer | Foundry shows the cited pages themselves, side by side, with no subscription |

### 1.3 Closest to Foundry's idea: several AIs side by side

| Product | What it is | Gap Foundry fills |
|---|---|---|
| **MultiAIBrowser** ([App Store](https://apps.apple.com/app/id6757524860)) | iPad/Mac app: ChatGPT, Claude, Gemini, Grok, Perplexity and Copilot behind one search bar, side by side | Apple only; it isn't a full browser and doesn't open the sources |
| **MultipleChat, Poe, MultiLLM, PolyGPT** ([Yahoo roundup](https://tech.yahoo.com/articles/best-ai-chatbot-compare-side-153430150.html), [MultiLLM](https://chromewebstore.google.com/detail/hhnjngbbjkgfmbhkohjfcceafiipglgm)) | Workspaces and extensions that send one prompt to several models, mostly through APIs, often paid (~$9/mo) | Desktop or web first; API answers instead of your own accounts; none shows the cited web pages next to the AIs |

**The gap no one fills on Android:** a real browser in which one search becomes a group of live pages: Google's AI
answer, the pages it cites, and several AIs in your own accounts, with no API costs and no single-AI agenda.

### 1.4 Foundry's honest weaknesses

| Weakness | Why it matters | What to do |
|---|---|---|
| **Depends on Google's AI Mode page** | The sources come from a hidden format Google can change at any time. Google's terms bar automated access, and it sued SerpApi ([Google](https://blog.google/technology/safety-security/serpapi-lawsuit/); a judge dismissed the amended complaint in Nov 2026, [SER](https://seroundtable.com/google-loses-again-against-serpapi-42508.html), without ruling scraping lawful) | Foundry loads the page in the user's own browser, one search at a time, like a person would; never run it as a service. Add a fallback (Google's normal results, or Brave/Bing) and a visible "couldn't read sources" state. Get a legal opinion before charging money |
| **Firefox fork, solo developer** | Security updates every 4 weeks; one person; trust | Keep patches small (done: about 600 lines). Make a monthly rebase a ritual. Publish the source |
| **Not release-ready** | Mozilla telemetry, Mozilla app ID and keys; MPL and [trademark rules](https://www.mozilla.org/en-US/foundation/trademarks/) | Do the "Release readiness" item before any public listing |
| **AI logins on mobile web** | Claude and ChatGPT on the mobile web need a login; logged out, they loop or ignore the question | First-run setup: "log in to your AIs once" |
| **Phone performance** | Several live pages at once use memory, data and battery | Keep lazy loading; measure on a mid-range phone |
| **Not tested on a real phone yet** | Emulator only so far | Owner's phone test, then 10 to 20 beta users |
| **Tiny distribution** | Competitors are preinstalled | Win a narrow group first (below) |

## 2. Positioning and selling

### 2.1 First users (start narrow)

**"AI double-checkers" on Android:** people who already use two or more AI assistants and open the sources to check
them. In practice that means developers and builders, students and researchers, and journalists and analysts. They
are likely on r/ChatGPT, r/ClaudeAI, r/LocalLLaMA, r/Android and r/firefox, and already pay for or log in to Claude and
ChatGPT.

- **Their pain:** for anything that matters, they ask ChatGPT, then Claude, then Google, then open three sources, in
  four apps and tabs, copying the question each time. Every AI browser on the market pushes its own model.
- **What Foundry does for them:** one search gives Google's AI answer, the pages it cites as live windows, and Claude
  and ChatGPT a swipe away, using their own logins, free, with no API keys.
- **One-line pitch:** *"Foundry turns every search into a tab group: Google's AI answer, the sources behind it, and
  Claude and ChatGPT side by side, in your own accounts."*

### 2.2 Tagline options

| Tagline | Why |
|---|---|
| **"One search. Every answer. Side by side."** (recommended) | Says exactly what's different in six words. Works under a 5-second demo GIF |
| **"See the sources, not just the answer."** | Speaks to the double-checkers and to trust. Sets Foundry apart from AI-answer apps that hide sources |
| **"The neutral browser for AI."** | Positions against Chrome, Edge, Samsung and Comet, which each push one AI. Good for press and Hacker News |

"Built for builders" can stay as the brand line on the Notion page and website footer, but it doesn't tell a new user
what Foundry does. Lead with one of the above.

### 2.3 Where and how to launch (in order)

1. **Closed beta (now to 4 weeks):** GitHub APK plus a small Telegram or Discord group of 20 to 50 people recruited
   from r/ClaudeAI, r/ChatGPT and r/firefox. The goal is to learn what breaks and which windows people actually open.
   Needs the release-readiness basics: own app ID, telemetry off, privacy note.
2. **Demo first (week 2 onward):** a 30-second vertical video (script below) on X, YouTube Shorts and Reddit. Phone
   browsers sell on motion.
3. **Google Play open testing (after release readiness):** a listing with screenshots of the Group view. Play's Data
   safety form and a privacy policy are required.
4. **Show HN and Product Hunt (when Play testing is stable):** lead with the angle "a Firefox fork where one search is
   a tab group of AIs and their sources". Hacker News values open source, Gecko and no telemetry.
5. **F-Droid (later):** the Firefox-fork and privacy crowd lives there, but builds must be fully free software. Plan
   it after the Play Store, not before.
6. **Timely hook:** Arc Search users may need a new phone browser by late December 2026 (single report). Offer an
   "Arc Search refugees" post if the shut-down is confirmed.

**30-second demo script (vertical, phone screen recording, voice-over):**

| Time | Picture | Voice-over / text |
|---|---|---|
| 0–3 s | Thumb types "best running shoes for flat feet" in the bottom bar | "One search." |
| 3–8 s | The group view appears: two tall source pages on top (RunRepeat, Reddit) and the cited YouTube video below | "Foundry reads Google's AI answer and opens the sources it cites, as real pages." |
| 8–13 s | Swipe right: the AI Mode answer (L0) and Google results (U0) slide in | "The AI answer is one swipe away." |
| 13–20 s | Swipe left to Claude, tap Ask; then ChatGPT | "Ask Claude and ChatGPT too, in your own accounts. No API keys, no subscription." |
| 20–25 s | Tap outside: the panel shrinks into Overview, where the search sits as a group | "Every search is a tab group you can come back to." |
| 25–30 s | Logo with the tagline | "Foundry. One search. Every answer. Side by side." |

### 2.4 Pricing and business model

| Option | How it works | Upside | Risks |
|---|---|---|---|
| **Free core (recommended now)** | Everything above is free. Foundry has no API costs, because AIs run in the user's own accounts | Fastest adoption; matches "neutral" | No revenue yet |
| **Foundry Pro, later (~$3–5 a month)** | Paid extras on top: ask all AIs at once with one follow-up box, saved and synced groups, custom layouts, more AI windows, answer comparison | Clear value for heavy users; no conflict with neutrality | Only works with an engaged base; Play billing takes 15% |
| **Search revenue share** | A default search engine deal through a partner program (Google's is out of reach early; Brave Search, Ecosia, Kagi or Bing partner programs) | How Firefox and Opera make money | Needs volume; may clash with the Google-AI-Mode-first design; neutrality questions |
| **Affiliate links on shopping sources** | Earn when users buy from a source page | Shopping searches are common | Undermines "neutral"; easy to get wrong; avoid early |
| **Partnerships with AI companies** | A smaller AI (Mistral, Perplexity, DeepSeek or a new player) pays to be a default window | Real money if Foundry gets traction | Conflicts with neutrality; only after scale |
| **Donations / GitHub Sponsors** | Open-source goodwill | Easy and honest | Small |

### 2.5 What must be true before launch

1. **Release readiness (blocking):** own app ID and signing key; Mozilla telemetry, crash reporting and API keys off or
   replaced; Mozilla trademark and MPL obligations checked (the name and logo are already Foundry); a privacy policy.
2. **Works on real phones:** tested on the owner's Nothing Phone 2 and at least two other mid-range phones. No crashes
   in a week of daily use. Memory and data measured per search.
3. **Survives Google changes:** if AI Mode can't be read, the group still opens with Google results and the AIs, and
   says so. Have a 24-hour plan to fix the reader.
4. **Keeps up with Firefox:** a monthly update to the latest Firefox, documented and repeatable.
5. **Legal check** on reading AI Mode pages in the user's own browser, and on logging into third-party AIs inside
   Foundry, before taking money.

### 2.6 Top 3 product changes the market suggests

1. **"Ask all AIs at once" with one follow-up box** (already an Idea on the board). This is the feature no single-AI
   browser will build, and the clearest demo. Make it the headline of v1.
2. **Make sources robust and visible:** a fallback when AI Mode can't be read, a list of sources in the group, and an
   option for other engines (Brave, Bing, Perplexity). This lowers the Google platform risk and supports "see the
   sources".
3. **First-run AI setup plus comparison:** log in to Claude, ChatGPT and Gemini once during onboarding, then show at a
   glance where the AIs agree or disagree. This removes the logged-out failure and gives double-checkers their "aha".

## 3. Recommended go-to-market plan

- **Weeks 0–2:** release readiness, the owner's phone test, then a closed beta with 20–50 AI double-checkers from
  Reddit.
- **Weeks 2–6:** ship "ask all AIs at once", the source fallback and AI login onboarding. Post the 30-second demo
  weekly on X, Shorts and Reddit.
- **Weeks 6–10:** Google Play open testing; then Show HN and Product Hunt with the tagline "One search. Every answer.
  Side by side."
- **After:** Pro tier tests once weekly use is steady; F-Droid; search-partner talks only with real numbers.
