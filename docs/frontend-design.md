# Frontend design

The source of truth is [design-brief.md](design-brief.md) (Nov 16, 2026). This file maps it onto the code. Read both before restyling a screen. If they disagree, the brief wins: fix this file.

Tokens live in `frontend/src/app/globals.css`, base components in `frontend/src/components/ui/`, and contrast is enforced by `frontend/src/lib/design-tokens.test.ts`.

## Direction (from the brief)

A calm, dense work tool in the family of Linear, Front and Superhuman: quiet surfaces, sharp type, one accent, speed you can see. Used for hours, so restraint beats decoration.

- **Palette.** Ink and bone. Warm-neutral grays, hairline borders. One deep navy accent, used only for focus, selection and primary actions. A small brass highlight marks unread and status dots only.
- **Density over cards.** Rows and dividers, not boxes inside boxes. The one bordered `Panel` is for something that must stand apart (a one-time token).
- **Channel identity is a detail.** Instagram and Messenger are small badges. The UI never takes their brand colours.
- **Type.** Geist. 13px base, 14px message text, headings 600, tabular numbers on counts, dates and times. At most five sizes: caption 12, body 13 (`text-sm`), message 14, title 15 (`text-lg`), display 22.
- **Motion.** 150ms ease-out colour changes only, off under reduced motion. No bounce, no parallax, no entrance animations.
- **Shell.** A 56px icon rail on the left (Home, Organizations, theme, sign out). Under 800px it becomes a bar along the bottom. The three-pane inbox (list 340px, thread, optional 300px details) arrives with the inbox milestone and uses the same tokens.
- **Light and dark** from one token scale.

## Tokens

Each token is `light-dark(<light>, <dark>)`. Brief names map to utilities:

| Brief token | Utility | Light | Dark |
|---|---|---|---|
| bg-app | `bg-app` | `#f6f4ef` | `#0e0f13` |
| bg-surface | `bg-surface` | `#ffffff` | `#15161b` |
| bg-hover | `bg-hover` | `#efece5` | `#1c1d23` |
| bg-selected | `bg-selected` | `#e8ebf2` | `#1b2233` |
| border | `border-line` | `#e4e0d6` | `#272830` |
| border-subtle | `border-line-subtle` (row separators) | `#ede9e0` | `#1f2027` |
| text-primary | `text-primary` | `#14151a` | `#ececee` |
| text-secondary | `text-secondary` | `#6a6760` | `#9a9aa2` |
| accent | `bg-accent`, `text-accent`, focus ring | `#1e2a47` | `#8fa3d9` |
| highlight | `bg-highlight` (unread and status dots only) | `#a8802f` | `#d2a64a` |
| danger | `text-danger`, `border-danger` | `#b42318` | `#f0675c` |

Added by us, not in the brief:

| Token | Utility | Light | Dark | Why |
|---|---|---|---|---|
| line-strong | `border-line-strong` | `#878277` | `#6b6c77` | The brief's hairline is about 1.3:1 on white. Inputs and buttons need 3:1 (WCAG 1.4.11), so controls use this. Dividers keep `line` and `line-subtle`. |
| accent-hover | `bg-accent-hover` | `#2d3d66` | `#a9b8e4` | Hover for the primary button. |
| accent-ink | `text-accent-ink` | `#ffffff` | `#0e0f13` | Text on the accent. |
| danger-solid, on-danger | `bg-danger-solid`, `text-on-danger` | `#b42318`, `#ffffff` | `#c2362f`, `#ffffff` | Destructive fill. |
| success | `text-success` | `#1f7a45` | `#6fd29a` | Confirmation text. |

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
6. Every list gets a skeleton that matches the real row height, an empty state with a specific message and a next action, and an error state. Never a centred spinner.
7. Numbers, dates and times use tabular figures (`tabular-nums`, or inside `tableClass`).
8. Never add `outline-none` without a replacement. The global `:focus-visible` is a 2px accent ring with 2px offset.
9. Wide tables go inside `TableScroll` with a label.
10. Seed realistic names, messages and times. No "Lorem ipsum", "John Doe" or "Welcome to your dashboard".
11. Run the checklist at the end of [design-brief.md](design-brief.md) on every screen.
