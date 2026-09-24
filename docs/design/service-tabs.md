# Service tabs: the search results screen

Reference sketch: [`../references/service-tabs-sketch.png`](../references/service-tabs-sketch.png)

When you search, Foundry shows the results from several services on one screen. The screen has two independent rows.
Each row gets its own ranking.

```
┌────────────────────────────────────────┐
│ [>]                              [⋮]   │  two menus (see Menus)
│ ┌──────────┐ ┌──────────┐ ┌───         │
│ │          │ │          │ │            │  UPPER ROW: small preview cards
│ │  U1      │ │  U2      │ │ U3 …       │  scroll sideways
│ │ ▷ clips  │ │ G info   │ │ ▷          │
│ └──────────┘ └──────────┘ └───         │
│ ┌─┐ ┌──────────────────────────┐ ┌─┐   │
│ │ │ │   (chat bubbles)          │ │ │   │  LOWER ROW: large AI chat cards
│ │L│ │          L2               │ │L│   │  swipe sideways
│ │1│ │                           │ │3│   │  (neighbours peek at the edges)
│ │ │ │ ( xyz  ← follow-up input )│ │ │   │
│ └─┘ └──────────────────────────┘ └─┘   │
│ (        xyz  ← address/search bar   ) │  bottom bar
└────────────────────────────────────────┘
```

## Slot numbering

Slots are numbered **separately in each row**, and the number is the slot's position after ranking.

| Tag | Row | Meaning |
|---|---|---|
| **U1, U2, U3 …** | Upper | Small preview cards, left to right. **U1** is the first card, fully visible without scrolling. |
| **L1, L2, L3 …** | Lower | Large AI chat cards. **L1** is the card in front when the screen opens. L2, L3 … follow when you swipe left. |

In the sketch the lower row has already been swiped left once. **L2** (ChatGPT) is in front, **L1** (Claude) peeks on
the left and **L3** (Gemini) peeks on the right. The cut-off card at the right end of the upper row means more U-cards
are waiting to the right.

The priority system fills U-slots and L-slots independently, so reordering one row never moves the other. A service
always belongs to one row.

## Services in each row (first draft)

| Row | Service | Icon in sketch | Loads |
|---|---|---|---|
| Upper | Clips (short videos) | ▷ | YouTube search for the query |
| Upper | Info | G | Google web results |
| Upper | Images | — | Google Images |
| Upper | Recommended pages | — | Pages Gemini recommends |
| Lower | Claude | left edge in sketch (L1) | claude.ai with the query |
| Lower | ChatGPT | OpenAI knot, centre card (L2) | chatgpt.com with the query |
| Lower | Gemini | right edge in sketch (L3) | gemini.google.com with the query |
| Lower | Grok | — | grok.com with the query |

Each card shows that service's real page. The service-pages approach was agreed, so there are no APIs and your existing
logins work.

## Interactions

- **Swipe left or right on a row** to reveal more cards in that row. Each row scrolls on its own.
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
