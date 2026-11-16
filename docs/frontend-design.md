# Frontend design direction

Read this before restyling any screen. The tokens live in `frontend/src/app/globals.css`, the base components in `frontend/src/components/ui/`, and the contrast rule is enforced by `frontend/src/lib/design-tokens.test.ts`.

## Direction

- **Product.** A unified Instagram and Messenger inbox for small teams: many conversations, several people answering, a lot of scanning.
- **Character.** Calm, exact, trustworthy. Fast to scan, nothing competes for attention.
- **World.** A shared front desk or message tray: paper-white surfaces, ink text, one cool teal used as a signal. The neutrals lean faintly green-grey so they feel like a tidy workspace, not a generic zinc template.
- **Accent.** Deep teal (`#0d6b66` light, `#6cc3bb` dark). It marks the primary action, focus and selection. It never tints surfaces.
- **Type.** Geist only (already loaded by `layout.tsx`, now used by `body`). Weights 400 and 500, 600 for rare emphasis. Sentence case. Tabular figures for numbers. No uppercase tracked labels, no italics for emphasis.
- **Spacing.** 4px grid (Tailwind default). Controls use 8px radius, cards 12px, panels 16px. Nested radii must add up.
- **Motion.** None added. Use `transition-colors` only, when a screen needs it.
- **Signature.** Not added yet. Reserved for the inbox screens, where a channel marker fits the product. One detail, used once.

Reasoning: an inbox is read for hours. Surfaces stay neutral so the conversation content carries the colour. Teal reads as "active" without the alarm of red or the template feel of violet.

## Token table

| Role | Utility | Light | Dark |
|---|---|---|---|
| Page background | `bg-canvas` | `#f6f7f5` | `#0f1312` |
| Surface (cards, fields) | `bg-surface` | `#ffffff` | `#161c1a` |
| Hover / active fill | `bg-hover` | `#eef0ed` | `#1e2624` |
| Text (primary) | `text-ink` | `#1d2321` | `#e6ebe8` |
| Text (muted) | `text-ink-muted` | `#56605c` | `#a3aca8` |
| Hairline divider | `border-line` | `#e1e5e2` | `#27302d` |
| Input and control boundary | `border-line-strong` | `#6f7873` | `#85908b` |
| Accent (primary action) | `bg-accent`, `text-accent` | `#0d6b66` | `#6cc3bb` |
| Accent hover | `bg-accent-hover` | `#0a5752` | `#9ddbd5` |
| Text on accent | `text-accent-ink` | `#ffffff` | `#0b1412` |
| Focus ring | `--focus` (global `:focus-visible`) | `#0d6b66` | `#6cc3bb` |
| Danger text and error border | `text-danger`, `border-danger` | `#b42318` | `#ff8a80` |
| Danger solid fill | `bg-danger-solid` | `#b42318` | `#c2362f` |
| Text on danger solid | `text-on-danger` | `#ffffff` | `#ffffff` |
| Success text | `text-success` | `#1f7a45` | `#6fd29a` |

Type scale: `text-caption` 13px, `text-sm` 14px (control text), `text-body` 15px, `text-title` 20px, `text-display` 28px (tight tracking).
Radius: `rounded-control` 8px, `rounded-card` 12px, `rounded-panel` 16px.

## Base components (`frontend/src/components/ui/`)

| Export | Props | Notes |
|---|---|---|
| `Button` | `variant?: "primary" \| "secondary" \| "danger"` plus native button props | 44px tall on coarse pointers. No default `type`, so inside a form it submits. |
| `Field` | `id` (required), `label` (required), `hint?`, `error?` plus native input props | Wires `aria-describedby` and `aria-invalid`. |
| `Badge` | `tone?: "neutral" \| "accent" \| "success" \| "danger"` | Caption-sized, bordered. |
| `Alert` | `tone?: "info" \| "success" \| "danger"` plus div props | `danger` uses `role="alert"`, others `role="status"`. |
| `Card` | div props | Surface, hairline border, panel radius. |
| `TableScroll` | `label` (required) plus div props | Named, focusable scroll region. |

Class builders for server code and older components: `buttonClasses`, `fieldClasses`, `badgeClasses`, `alertClasses`, `cardClasses`, `tableScrollClasses` in `ui/classes.ts`.

`org-styles.ts` keeps its exports (`fieldClass`, `buttonClass(tone)`, `tableClass`) and now resolves to the same tokens.

## Contrast (WCAG 2.1 AA)

Text needs 4.5:1, boundaries and focus need 3:1. Enforced for every pair in `design-tokens.test.ts`.

| Pair | Before | After (light / dark) |
|---|---|---|
| Input border vs field | 1.48 / 1.70 | 4.56 / 5.23 |
| Focus ring vs field | about 1.0 (dark, measured in audit) | 6.34 / 8.36 |
| Muted text vs page | 4.83 / 4.10 | 6.06 / 8.04 |
| Danger text vs page | 4.83 / 4.15 | 6.12 / 8.20 |

Before values: zinc-300 and zinc-700 borders, zinc-500 muted text, red-600 danger text, as measured in the M2 and M3 audits and recomputed here. After values measured in the running app with `getComputedStyle` (login page, both themes). Muted and danger "after" on the page background are the test values.

## Theme

The app follows the OS light or dark setting. The header toggle pins a theme for this browser.

- Every token in `globals.css` is `light-dark(<light>, <dark>)`, so a new token has both values in one line. `color-scheme` on `:root` picks the half; `data-theme="light|dark"` on `<html>` overrides it.
- The choice lives in `localStorage` under `theme`. An inline script in `<head>` (`THEME_INIT_SCRIPT`) applies it before first paint, so there is no flash.
- The toggle switches to the opposite of what is on screen. Choosing the OS's own theme clears the pin, so the page follows the OS again.
- No `.dark` classes or `dark:` utilities. Components use tokens only.

## Rules for new screens

1. Use role utilities (`text-ink`, `bg-surface`, `border-line-strong`), never `zinc-*`, `red-*` or `dark:` pairs.
2. Use `Button`, `Field`, `Alert` and the rest instead of copying class strings.
3. Keep one primary action per view. Use `secondary` and `danger` for the rest.
4. Never add `outline-none` without a replacement focus style. The global `:focus-visible` outline already covers inputs and links.
5. Wide tables go inside `TableScroll` with a label.
6. Add a token pair to `design-tokens.test.ts` when you add a new text or boundary colour.
