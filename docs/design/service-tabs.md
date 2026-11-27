# Service tabs: the search results screen

Reference sketch: [`../references/service-tabs-sketch.png`](../references/service-tabs-sketch.png)

When you search, Foundry shows the best options for it on one screen, like a tab group made for that search. The
cards are live pages, ordered by priority: Foundry guesses what the search is about and puts the best fit first.

Why: the sources Google's AI answers pick are often better than Google's ranked results. Foundry puts several of
the best options side by side instead of one list.

## Results as a tab group (owner's design, 2026-11-20, supersedes "pages of three")

### Names for the three levels (proposed 2026-11-20, waiting for the owner's OK)

| Name | What it is |
|---|---|
| **Overview** | All tabs and groups, zoomed out (Fenix's tabs tray). Search groups show here like any group. |
| **Group view** | One group opened, as a panel over a dimmed Overview (like Chrome's tab groups); tap outside it to close. For a search: the U and L rows below. No header. |
| **Page** | One tab full screen (the normal browser screen), reached with a window's **Open** button. |

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
  - *How it's built (Founder's reading, owner to confirm):* with no cited video or image, L1 is the first cited page
    (the main source) and U1, U2, … are the next ones. With cited videos or images, they take L1, L2, … and **all**
    cited pages go in the U row, so the main source is U1. Either way the opening view (U1, U2, L1) shows the top
    sources. Videos are recognised by address (YouTube, youtu.be, Vimeo, TikTok, Instagram reels), images by file type.
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

- **Swipe left or right** anywhere on a row (even over a live page) to move that row by one window. Each row moves on
  its own. Pages never scroll sideways inside a window (owner's rule, 2026-11-20).
- **Scroll up and down inside a window** to scroll that page without leaving the Group view.
- **Open** on a window shows that tab as a Page. **Back on that Page returns to the Group view**, with both rows where
  you left them (2026-11-28). The address bar's back swipe still goes back within the page.
- **Tap outside the panel** (on the dimmed Overview) or press Back to close the Group view. The panel shrinks and fades
  back into Overview; with gesture navigation it follows the back swipe (predictive back) and returns if you cancel.
- **It is a normal tab group in Overview** (2026-11-28): rename, colour, close, delete, ungroup and add or move tabs
  with Fenix's own tools. Closed or moved-out tabs leave the rows; tabs added to the group join them. A search group
  whose tabs are all closed is deleted after the Undo message. Its card in Overview shows previews of its pages.
- **The search box** at the bottom starts a new search group (tapping it selects the old query, so typing replaces it).
- **The follow-up box in an AI window** sends the message to that AI only.

### How it's built (2026-11-20)

- A search creates a real tab group (the same kind as Fenix's own groups), named after the query. Each window is a tab
  in that group, created the first time the window is shown, so a search costs only what you look at.
- Google AI Mode loads in L0 in the background. Foundry's built-in extension reads the answer's cited sources (Google
  keeps them as data next to each citation chip, not as links) and sends them to the app in citation order. On the
  emulator the sources arrive 15 to 25 seconds after the search; with none after 45 s, the rows open on U0 and L0.
- Screenshots: [panel over Overview](../references/group-view-panel.png),
  [after tapping outside](../references/group-view-panel-closed.png), [light](../references/group-view-light.png), [dark](../references/group-view-dark.png),
  [more sources](../references/group-view-more-sources.png), [Claude ask window](../references/group-view-claude-ask.png),
  [Overview with search groups](../references/overview-with-search-groups.png),
  [Overview's groups page](../references/overview-groups-page.png),
  [Back from a Page returns to the Group view](../references/back-to-group-view.png),
  [page previews in Overview](../references/overview-group-thumbnails.png),
  [closing animation frames](../references/group-view-close-animation.png).

## Menus

The two menus may move later.

| Button | Holds |
|---|---|
| **`>`** | Profile, saved and active tabs, reading lists |
| **`⋮`** | A traditional browser menu (settings, history, downloads and so on) |

## Later

- **Ask several AIs at once.** A follow-up is sent to more than one AI card. The second, cut-off mockup in the
  reference photo sketches this.
