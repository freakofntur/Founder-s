# Service tabs: the search results screen

Reference sketch: [`../references/service-tabs-sketch.png`](../references/service-tabs-sketch.png)

When you search, Foundry shows the best options for it on one screen, like a tab group made for that search. The
cards are live pages, ordered by priority: Foundry guesses what the search is about and puts the best fit first.

Why: the sources Google's AI answers pick are often better than Google's ranked results. Foundry puts several of
the best options side by side instead of one list.

## Layout (owner's design, 2026-11-20)

Each screen page holds three cards. Swiping left turns one page and shows the next three; the next page peeks in at
the right edge.

```
┌────────────────────────────────────────┐
│ ┌────────────┐ ┌────────────┐ ┌─       │
│ │ 2 · …      │ │ 3 · …      │ │        │  upper row: two tall (portrait)
│ │            │ │            │ │        │  cards, priority 2 left, 3 right
│ │            │ │            │ │        │
│ └────────────┘ └────────────┘ └─       │
│ ┌──────────────────────────┐ ┌─        │
│ │ 1 · …                    │ │         │  bottom: one big square card,
│ │                          │ │         │  priority 1
│ │                          │ │         │
│ └──────────────────────────┘ └─        │
│ (        query  ← search bar         ) │
└────────────────────────────────────────┘
   page 2 holds priorities 4 (bottom), 5, 6 (top) …
```

- Each card's header shows its priority and name, for example "1 · AI Mode".
- A swipe turns at most one page. A drag must pass half the page width to turn it, unless it is a clear flick.
- Only the landed page's three cards load.

## Services and priority (first draft)

| Service | Loads |
|---|---|
| AI Mode | Google AI Mode (`udm=50`): an AI answer with its sources |
| Google | Google web results |
| Clips | YouTube search |
| Images | Google Images |
| Claude, ChatGPT, Gemini, Grok | The AI with the query |

`FoundryPriority` orders them by hints in the query until the on-device model ("Result priority") exists:

- Video words (video, watch, song, trailer…): Clips first.
- Image words (photo, wallpaper…): Images first.
- Quick facts (weather, price, near, news…): Google first.
- Questions (why, how, what…, a "?", or six words or more): AI Mode, then the AIs.
- Otherwise: AI Mode, Google, Claude, Clips, ChatGPT, Images, Gemini, Grok.

Each card shows that service's real page. The service-pages approach was agreed, so there are no APIs and your existing
logins work.

## Interactions

- **Swipe left or right** to turn to the next or previous three cards.
- **Scroll inside a card** to scroll that service's page without leaving the results screen.
- **Tap a card** to open it full screen. The results screen stays open in the background like a tab, with its other
  cards kept as they were, so you can go back to it.
- **The follow-up box in an AI card** sends the message to that AI only.

## Menus

The two menus may move later.

| Button | Holds |
|---|---|
| **`>`** | Profile, saved and active tabs, reading lists |
| **`⋮`** | A traditional browser menu (settings, history, downloads and so on) |

## Later

- **Ask several AIs at once.** A follow-up is sent to more than one AI card. The second, cut-off mockup in the
  reference photo sketches this.
