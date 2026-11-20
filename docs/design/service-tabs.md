# Service tabs: the search results screen

Reference sketch: [`../references/service-tabs-sketch.png`](../references/service-tabs-sketch.png)

When you search, Foundry shows the best options for it on one screen, like a tab group made for that search. The
cards are live pages, ordered by priority: Foundry guesses what the search is about and puts the best fit first.

Why: the sources Google's AI answers pick are often better than Google's ranked results. Foundry puts several of
the best options side by side instead of one list.

## Results as a tab group (owner's design, 2026-11-20, supersedes "pages of three")

### Where Results lives: the tab view

The tabs view (the tabs tray) becomes a variant of the usual **tab groups** view found in other browsers:

- **Zoomed-out view:** every ungrouped tab and every group, side by side.
- **Group view:** the tabs of one group.
- **A search is a group.** Searching creates a group for that search, and the **group view is the default view for a
  result**, instead of opening a single web page.

### Slot names

Two rows, numbered separately from 0:

- **U0, U1, U2, …**: the upper row of tall (portrait) windows.
- **L0, L1, L2, …**: the lower row of landscape or square windows.

### What goes where

Foundry first runs the search on Google **in the background** and reads Google's AI answer and its sources.

| Slot | Holds |
|---|---|
| **L0** | Google AI answer (AI Mode) |
| **U0** | Google search results |
| **L1** | The main source of the Google AI answer |
| **U1, U2, U3, …** | The other sources the Google AI answer cites |
| **L2** | Claude: an "ask" window showing what you typed and Claude's symbol |
| **L3** | ChatGPT: the same, for ChatGPT |
| Further L slots | Other important results |

- **Videos and images:** if the Google AI answer cites videos or images, they go in the L row from **L1** on,
  pushing Claude and ChatGPT further along the L row.
- **Priority:** 1 = Google AI answer, 2 = Google results, 3 = any video the Google AI answer references.

### Opening position

U0 and L0 (Google's own results and AI answer) start **off screen**: you scroll to reach them. When the group opens,
the visible windows are **U1, U2 and L1**, the sources behind the answer.

### AI windows

L2, L3 and later AI windows don't send the question by themselves. They show what you typed and the AI's symbol, and
the question goes to that AI only when you ask. This also avoids AI sites rate-limiting or bot-checking automatic
questions.

### Superseded

Earlier on 2026-11-20: "pages of three cards" (priority 1 big at the bottom, 2 and 3 above, swiping turns a page) with a
word-based `FoundryPriority`. The slot mapping above replaces it; keep its swipe-paging lessons (turn at most one page,
half a page or a clear flick).

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
