# Connecta: Design Brief

Nov 16, 2026

## Design direction

Connecta should feel like a calm, dense work tool in the family of Linear, Front and Superhuman: quiet surfaces, sharp type, one accent, and speed you can see. The inbox is used for hours, so restraint beats decoration.

- **Mood:** editorial and precise. Warm-neutral grays, hairline borders, small confident type.
- **One accent:** a single deep navy used only for focus, selection and primary actions. A small brass highlight marks unread and status dots only.
- **Density over cards:** rows and dividers, not boxes inside boxes. Whitespace is spent between groups, not inside every element.
- **Channel identity is a detail, not a theme:** Instagram and Messenger appear as small badges. The UI never takes on their brand colors.
- **Speed is part of the look:** skeleton loading, optimistic sends, keyboard-first navigation.
- **Light and dark from day one,** built from the same token scale.

## Design tokens

Define these once as CSS variables in the Next.js app and map them into Tailwind and shadcn/ui. Palette: ink and bone with brass. Hex values are starting points to tune by eye. The same values are in `connecta-tokens.css`.

| Token | Light | Dark | Use |
| --- | --- | --- | --- |
| bg-app | #F6F4EF | #0E0F13 | App background |
| bg-surface | #FFFFFF | #15161B | Panes, composer |
| bg-hover | #EFECE5 | #1C1D23 | Row hover, tags |
| bg-selected | #E8EBF2 | #1B2233 | Selected conversation |
| border | #E4E0D6 | #272830 | 1px dividers |
| border-subtle | #EDE9E0 | #1F2027 | Row separators |
| text-primary | #14151A | #ECECEE | Names, message text |
| text-secondary | #6A6760 | #9A9AA2 | Previews, timestamps |
| accent | #1E2A47 | #8FA3D9 | Focus ring, primary button, outgoing messages |
| highlight | #A8802F | #D2A64A | Unread dot, status dot only |
| danger | #B42318 | #F0675C | Failed send, errors |

| Property | Value |
| --- | --- |
| Display / headings | Geist or Satoshi, 600 |
| Body / UI | Geist or Inter Tight, 13px base, 14px message text |
| Numbers | tabular-nums on counts and timestamps |
| Spacing scale | 4, 8, 12, 16, 24, 32 px |
| Radius | 8px controls, 10px panels, full for avatars and badges |
| Borders | 1px, no heavy drop shadows; one soft shadow only on popovers and the command palette |
| Motion | 150 to 200 ms ease-out; no bounce, no parallax |
| Focus | 2px accent ring with 2px offset on every interactive element |

## Layout

The main screen is a three-pane inbox with a narrow rail on the left. Panes are separated by 1px borders, not gaps or cards.

| Pane | Width (desktop) | Contents |
| --- | --- | --- |
| Rail | 56 px, icons only | Inbox, Contacts, Analytics, Settings, workspace switcher, user menu |
| Conversation list | 340 px, resizable | Search, filter chips (All, Unassigned, Mine, Instagram, Messenger), conversation rows |
| Thread | Flexible | Header (contact, channel, assignee, status), messages, composer |
| Details (optional) | 300 px, toggled | Contact info, tags, notes, assignment, history |

**Responsive behavior**

- Under 1100 px the details pane becomes a slide-over.
- Under 800 px the list and thread become separate screens with a back button; the rail turns into a bottom bar.
- The composer stays pinned; the list and thread scroll independently.

**Density rules**

- Conversation row height 64 px: avatar 36, name and time on one line, one-line preview below.
- Message groups collapse consecutive messages from the same sender; timestamps show on hover and at day breaks.
- Max message column width about 680 px so long lines stay readable on wide screens.

## Screens and states

Design each screen in light and dark, with its empty, loading and error states. Tie each to a backlog milestone when you create issues.

| Screen | States to design |
| --- | --- |
| Sign in / sign up | Default, error, loading |
| Workspace onboarding | Create workspace, connect Instagram, connect Messenger, connection failed |
| Inbox (three-pane) | Empty inbox, loading skeleton, unread, assigned, snoozed, closed |
| Thread | New contact, long history, image and attachment messages, failed send, sending, delivered, 24-hour window expired |
| Composer | Default, attachment added, saved reply picker, disabled when outside window |
| Contact details | Fresh contact, tagged, with notes, merged |
| Assignment and team | Assign menu, unassigned queue, member list, invite member, roles |
| Saved replies | List, create, edit |
| Search and command palette | Empty query, results, no results |
| Channel settings | Connected, token expired, disconnected |
| Analytics (if in scope) | Response time, volume by channel, per-agent load |
| Settings | Profile, workspace, notifications, billing placeholder |
| System | 404, 500, offline banner, session expired |

## Components and interactions

**Conversation row**

- Name in 500 weight, preview in text-secondary, time right-aligned in tabular numbers.
- Unread: bold name plus a 6px highlight dot; no colored row backgrounds.
- Channel badge: small glyph overlapping the avatar's bottom-right corner.
- Selected row uses bg-selected plus a 2px accent bar on the left edge.

**Message bubbles**

- Incoming: bg-surface with a 1px border. Outgoing: solid accent with light text.
- 10px radius, with the corner touching the sender squared off to 4px.
- Group consecutive messages; show avatar once per group.
- Status under the last outgoing message: sending, delivered, failed with a retry link.

**Composer**

- Auto-growing textarea, Enter to send, Shift+Enter for newline.
- Toolbar of icon buttons only: attach, saved replies, emoji.
- When the messaging window has expired, replace the input with a clear inline notice rather than a disabled box.

**Command palette (Cmd+K)**

- Jump to conversation, assign, close, change status, switch workspace.
- One soft shadow, 12px radius, results grouped with small uppercase labels.

**Keyboard shortcuts**

| Key | Action |
| --- | --- |
| J / K | Next / previous conversation |
| E | Close conversation |
| A | Assign |
| R | Focus composer |
| / | Focus search |
| ? | Show shortcut sheet |

**Loading and feedback**

- Skeleton rows matching real row height; never a centered spinner for the inbox.
- Optimistic send; toasts only for failures, bottom-right, auto-dismiss in 5 seconds.

## Anti-slop checklist and references

Run this checklist on every screen before calling it done.

- [ ] One accent color only; no purple-to-blue gradients, no glow effects.
- [ ] No emoji used as icons; one icon set (Phosphor or Lucide) at one stroke weight.
- [ ] No card inside a card; borders and spacing do the grouping.
- [ ] No generic text such as "Lorem ipsum", "John Doe" or "Welcome to your dashboard"; seed realistic names, messages and times.
- [ ] Every empty state has a specific message and a next action.
- [ ] Type scale uses at most five sizes; numbers are tabular.
- [ ] Contrast meets WCAG AA in light and dark; focus rings visible.
- [ ] Hover, active, disabled and loading states exist for every control.
- [ ] Motion is 150 to 200 ms and never blocks input.

**Study these**

- Products: Front, Missive, Superhuman, Linear, Attio, Intercom Inbox, Notion Mail, Respond.io, Crisp
- Galleries: Refero, Mobbin (free tier), Land-book, Godly, Awwwards, SaaSFrame, Dribbble, Behance
- Free building blocks: shadcn/ui, Radix Primitives, Radix Colors, Fontshare (Satoshi, General Sans), Google Fonts (Geist, Inter Tight, Instrument Sans), Phosphor Icons, Lucide

Process tip: collect 10 to 15 screenshots from the products above, mark what you like in each (spacing, row layout, empty state), then build the token set and the inbox screen first. Everything else inherits from them.
