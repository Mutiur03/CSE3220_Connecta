# Frontend design

The source of truth is [design-brief.md](design-brief.md) (Nov 16, 2026). This file maps it onto the code. Read both before restyling a screen. If they disagree, the brief wins: fix this file.

Tokens live in `frontend/src/app/globals.css`, base components in `frontend/src/components/ui/`, and contrast is enforced by `frontend/src/lib/design-tokens.test.ts`.

## Direction (from the brief)

A calm, dense work tool in the family of Linear, Front and Superhuman: quiet surfaces, sharp type, one accent, speed you can see. Used for hours, so restraint beats decoration.

- **Palette.** Ink and bone. Warm-neutral grays, hairline borders. Neutral graphite surfaces and one green accent (focus, selection, links, live dots). Primary buttons are ink on light and off-white on dark. A small brass highlight marks unread and status dots only.
- **Density over cards.** Rows and dividers, not boxes inside boxes. The one bordered `Panel` is for something that must stand apart (a one-time token).
- **Channel identity is a detail.** Instagram and Messenger are small badges. The UI never takes their brand colours.
- **Type.** Geist. 13px base, 14px message text, headings 600, tabular numbers on counts, dates and times. At most five sizes: caption 12, body 13 (`text-sm`), message 14, title 15 (`text-lg`), display 22.
- **Motion.** 150ms ease-out colour and press feedback; one 220ms fade when a page opens (`app/(app)/template.tsx`); one staggered reveal on the sign-in preview; All off under reduced motion. No bounce, no parallax.
- **Shell.** Signed-in pages live in `app/(app)` and get the sidebar; `/login` lives in `app/(auth)` and has none. A 240px labelled sidebar on the left (Home, Organizations; theme, sign out and the user at the bottom). Under 800px it becomes an icon bar along the bottom. The three-pane inbox (list 340px, thread, optional 300px details) arrives with the inbox milestone and uses the same tokens.
- **Light and dark** from one token scale.

## Tokens

Each token is `light-dark(<light>, <dark>)`. Brief names map to utilities:

| Brief token | Utility | Light | Dark |
|---|---|---|---|
| bg-app | `bg-app` | `#eeeeed` | `#141414` |
| bg-surface | `bg-surface` | `#ffffff` | `#1e1e1e` |
| bg-hover | `bg-hover` | `#f4f4f3` | `#272727` |
| bg-selected | `bg-selected` | `#ebebea` | `#2c2c2c` |
| border | `border-line` | `#e3e3e1` | `#2e2e2e` |
| border-subtle | `border-line-subtle` (row separators) | `#eeeeec` | `#272727` |
| text-primary | `text-primary` | `#151515` | `#ededed` |
| text-secondary | `text-secondary` | `#62625e` | `#a3a3a3` |
| accent | `bg-accent`, `text-accent`, focus ring | `#16793f` | `#4cc38a` |
| highlight | `bg-highlight` (unread and status dots only) | `#1a8f4c` | `#4cc38a` |
| danger | `text-danger`, `border-danger` | `#b42318` | `#f0675c` |

Added by us, not in the brief:

| Token | Utility | Light | Dark | Why |
|---|---|---|---|---|
| line-strong | `border-line-strong` | `#82827e` | `#6e6e6e` | The brief's hairline is about 1.3:1 on white. Inputs and buttons need 3:1 (WCAG 1.4.11), so controls use this. Dividers keep `line` and `line-subtle`. |
| line-field | `border-line-field` | `#d4d4d0` | `#3a3a3a` | Resting border of an input, select or textarea, softer than `line-strong`. A field darkens to `line-strong` on hover; on focus its border turns to the accent (3:1) with a 3px accent halo at 20%, instead of the global offset outline. Buttons and links keep the outline. |
| accent-hover | `bg-accent-hover` | `#0f5f31` | `#6ad3a0` | Hover for the primary button. |
| accent-ink | `text-accent-ink` | `#ffffff` | `#0e0f13` | Text on the accent. |
| danger-solid, on-danger | `bg-danger-solid`, `text-on-danger` | `#b42318`, `#ffffff` | `#c2362f`, `#ffffff` | Destructive fill. |
| success | `text-success` | `#17703c` | `#5fd49a` | Confirmation text. |

Spacing scale 4, 8, 12, 16, 24, 32. Radius: `rounded-control` 8px, `rounded-panel` 10px, `rounded-full` for avatars and badges. No drop shadows; one soft shadow is allowed on popovers and the command palette.

## Base components (`frontend/src/components/ui/`)

| Export | Notes |
|---|---|
| `Button` | `variant?: "primary" \| "secondary" \| "danger"`. 44px tall on coarse pointers. No default `type`, so inside a form it submits. |
| `Field` | `id`, `label` required; `hint?`, `error?`. Wires `aria-describedby` and `aria-invalid`. |
| `Badge` | `tone?: "neutral" \| "accent" \| "success" \| "danger"`. Full-radius tag on `bg-hover`. |
| `Select` | A native `<select>` (keyboard, form value and a11y stay the browser's). Where `appearance: base-select` is supported, the button and the option list follow the tokens: popover with the soft shadow, hover and checked rows, checkmark in the accent, 150ms fade. Other browsers keep a field-styled native select. Never hand-build a dropdown from divs. |
| `Alert` | `tone?: "info" \| "success" \| "danger"`. `danger` uses `role="alert"`, others `role="status"`. |
| `Panel` | Bordered panel. Rare. Never nest, never box a list. |
| `TableScroll` | `label` required. Named, focusable scroll region. |

Class builders for server code: `buttonClasses`, `fieldClasses`, `badgeClasses`, `alertClasses`, `panelClasses`, `tableScrollClasses` in `ui/classes.ts`. `org-styles.ts` keeps `fieldClass`, `buttonClass(tone)`, `tableClass`.

Shell: `AppRail` (`components/app-rail.tsx`), `RailLink` (active state: `bg-selected` plus a 2px accent bar), `ThemeToggle`, icons in `components/icons.tsx` (one set, one 1.75 stroke).

## Contrast (WCAG 2.1 AA)

Text needs 4.5:1, boundaries and focus need 3:1. Every pair, in both palettes, is checked in `design-tokens.test.ts`: add a pair when you add a text or boundary colour.

## Theme

The app follows the OS light or dark setting. The rail toggle pins a theme for this browser.

- Every token is `light-dark(<light>, <dark>)`. `color-scheme` on `:root` picks the half; `data-theme="light|dark"` on `<html>` overrides it.
- The choice lives in `localStorage` under `theme`. An inline script in `<head>` (`THEME_INIT_SCRIPT`) applies it before first paint.
- The toggle switches to the opposite of what is on screen. Choosing the OS's own theme clears the pin.
- No `.dark` classes or `dark:` utilities.

## Rules for new screens

1. Use role utilities (`text-primary`, `bg-surface`, `border-line`), never `zinc-*`, `red-*`, hex values or `dark:` pairs.
2. Use `Button`, `Field`, `Alert`, `Badge` instead of copying class strings.
3. Group with rows, `divide-y divide-line-subtle` and spacing, not cards. No card inside a card.
4. One accent only. No gradients, glows or emoji icons. Highlight is for dots, never text or buttons.
5. One primary action per view.
6. Every list gets an empty state with a specific message and a next action, and an error state. Data is fetched on the server, so the page arrives complete: no skeletons, no centred spinner.
7. Numbers, dates and times use tabular figures (`tabular-nums`, or inside `tableClass`).
8. Never add `outline-none` without a replacement. The global `:focus-visible` is a 2px accent ring with 2px offset.
9. Wide tables go inside `TableScroll` with a label.
10. Seed realistic names, messages and times. No "Lorem ipsum", "John Doe" or "Welcome to your dashboard".
11. Run the checklist at the end of [design-brief.md](design-brief.md) on every screen.


Theme reference (Nov 21, 2026): graphite neutrals, green accent, 16px panels, 10px controls, Geist Mono (`.figure`) for counts and times, weights 400 and 500 only. Delta and role badges are a 12% tint of their colour.

Landing page (`app/welcome`, shown at `/` to signed-out visitors by `proxy.ts`): read once, not worked in, so it runs one step up from the app: 14px body, 17px lead and item titles, 32px section headings, a fluid 40 to 68px headline at weight 500, 12px captions. Sections are split by full-width hairlines; features, steps and roles are rows with top borders, never cards. The inbox preview is built from the real tokens.
