# Service tabs: the search results screen

Reference sketch: [`../references/service-tabs-sketch.png`](../references/service-tabs-sketch.png)

When you search, Foundry shows the results from several services on one screen. The screen has two independent rows.
Each row gets its own ranking.

```
┌────────────────────────────────────────┐
│ [>]                              [⋮]   │  controls
│ ┌──────────┐ ┌──────────┐ ┌───         │
│ │          │ │          │ │            │  UPPER ROW: small preview cards
│ │  U1      │ │  U2      │ │ U3 …       │  scroll sideways
│ │ ▷ clips  │ │ G info   │ │ ▷          │
│ └──────────┘ └──────────┘ └───         │
│ ┌─┐ ┌──────────────────────────┐ ┌─┐   │
│ │ │ │   (chat bubbles)          │ │ │   │  LOWER ROW: large AI chat cards
│ │L│ │          L1               │ │L│   │  swipe sideways
│ │0│ │                           │ │2│   │  (neighbours peek at the edges)
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

The priority system fills U-slots and L-slots independently, so reordering one row never moves the other. A service
always belongs to one row.

## Services in each row (first draft)

| Row | Service | Icon in sketch | Loads |
|---|---|---|---|
| Upper | Clips (short videos) | ▷ | YouTube search for the query |
| Upper | Info | G | Google web results |
| Upper | Images | — | Google Images |
| Upper | Recommended pages | — | Pages Gemini recommends |
| Lower | ChatGPT | OpenAI knot (centre card) | chatgpt.com with the query |
| Lower | Gemini | G (left edge) | gemini.google.com with the query |
| Lower | Claude | orange (right edge) | claude.ai with the query |
| Lower | Grok | — | grok.com with the query |

Each card shows that service's real page. The service-pages approach was agreed, so there are no APIs and your existing
logins work.

## Open questions

- **`>` (top left):** is it collapse/expand, back, or a sidebar?
- **`⋮` (top right):** is it a menu, or choose/reorder services?
- **Tapping an upper card:** does it open full screen, or swap into the big lower area?
- **Lower input pill (`xyz`):** does a follow-up go only to that AI, or to every AI card at once?
- **Second mockup below the first:** is it a different state, such as a collapsed upper row?
