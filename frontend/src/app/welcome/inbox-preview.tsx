import {
  InboxIcon,
  SendIcon,
  UserCheckIcon,
  UsersIcon,
} from "@/components/icons";
import BrandMark from "@/components/brand-mark";
import { badgeClasses } from "@/components/ui/classes";

type Row = {
  name: string;
  channel: "Instagram" | "Messenger";
  text: string;
  time: string;
  unread: boolean;
  selected?: boolean;
};

const ROWS: Row[] = [
  {
    name: "Nadia Rahman",
    channel: "Instagram",
    text: "Yes please, I'll pay on delivery.",
    time: "2m",
    unread: true,
    selected: true,
  },
  {
    name: "Tanvir Hossain",
    channel: "Messenger",
    text: "Thanks, the order arrived this morning.",
    time: "18m",
    unread: true,
  },
  {
    name: "Farhana Akter",
    channel: "Instagram",
    text: "Can I change the delivery address?",
    time: "1h",
    unread: false,
  },
  {
    name: "Sabbir Ahmed",
    channel: "Messenger",
    text: "Do you ship outside Dhaka?",
    time: "3h",
    unread: false,
  },
  {
    name: "Rumana Islam",
    channel: "Instagram",
    text: "The blue one, size 38 please.",
    time: "Mon",
    unread: false,
  },
];

const BUBBLE = "max-w-[75%] rounded-[10px] px-3.5 py-2.5";
const AVATAR =
  "inline-flex shrink-0 items-center justify-center rounded-full bg-hover text-caption font-medium text-secondary";

const initials = (name: string) =>
  name
    .split(" ")
    .map((part) => part[0])
    .join("");

// Decorative sample of the inbox: rail, conversation list, one open thread. Built from the same
// tokens as the real screens, so it never drifts from the product.
export default function InboxPreview() {
  return (
    <div
      aria-hidden="true"
      className="grid overflow-hidden rounded-panel border border-line bg-surface md:h-[26rem] md:grid-cols-[20rem_1fr] lg:grid-cols-[3.5rem_20rem_1fr]"
    >
      <div className="hidden flex-col items-center gap-2 border-r border-line py-4 text-secondary lg:flex">
        <BrandMark className="mb-3 size-6" />
        <span className="flex size-9 items-center justify-center rounded-control bg-selected text-primary">
          <InboxIcon />
        </span>
        <span className="flex size-9 items-center justify-center">
          <UserCheckIcon />
        </span>
        <span className="flex size-9 items-center justify-center">
          <UsersIcon />
        </span>
      </div>

      <div className="hidden min-w-0 flex-col border-r border-line md:flex">
        <div className="flex items-center justify-between border-b border-line px-4 py-3">
          <span className="font-medium">Inbox</span>
          <span className="flex gap-1.5">
            <span className={badgeClasses("accent")}>Mine</span>
            <span className={badgeClasses("neutral")}>All</span>
          </span>
        </div>
        <ul className="divide-y divide-line-subtle overflow-hidden">
          {ROWS.map((row) => (
            <li
              key={row.name}
              className={`relative flex h-16 items-center gap-3 px-4 ${row.selected ? "bg-selected" : ""}`}
            >
              {row.selected && (
                <span className="absolute inset-y-0 left-0 w-0.5 bg-accent" />
              )}
              <span className={`${AVATAR} size-9`}>{initials(row.name)}</span>
              <span className="flex min-w-0 flex-1 flex-col">
                <span className="flex items-baseline justify-between gap-2">
                  <span
                    className={`truncate ${row.unread ? "font-medium" : ""}`}
                  >
                    {row.name}
                  </span>
                  <span className="figure flex shrink-0 items-center gap-1.5 text-caption text-secondary">
                    {row.unread && (
                      <span className="size-1.5 rounded-full bg-highlight" />
                    )}
                    {row.time}
                  </span>
                </span>
                <span className="truncate text-sm text-secondary">
                  {row.text}
                </span>
              </span>
            </li>
          ))}
        </ul>
      </div>

      <div className="flex min-w-0 flex-col">
        <div className="flex flex-wrap items-center justify-between gap-x-4 gap-y-1 border-b border-line px-5 py-3">
          <span className="flex items-center gap-2">
            <span className="font-medium">Nadia Rahman</span>
            <span className={badgeClasses("neutral")}>Instagram</span>
          </span>
          <span className="flex items-center gap-2 text-sm text-secondary">
            <span className="size-1.5 rounded-full bg-highlight" />
            Assigned to Priya Das
          </span>
        </div>

        <div className="flex flex-1 flex-col justify-end gap-2 px-5 py-5">
          <span className="self-center text-caption text-secondary">Today</span>
          <div className="flex items-end gap-2">
            <span className={`${AVATAR} size-7`}>NR</span>
            <p className={`${BUBBLE} rounded-bl-[4px] border border-line`}>
              Is the linen shirt still available in M?
            </p>
          </div>
          <div className="flex flex-col items-end gap-1">
            <p
              className={`${BUBBLE} rounded-br-[4px] bg-accent text-accent-ink`}
            >
              Yes, two left in M. Want me to hold one for you?
            </p>
            <time className="figure text-caption text-secondary">
              Delivered 10:42
            </time>
          </div>
          <div className="flex items-end gap-2">
            <span className={`${AVATAR} size-7`}>NR</span>
            <p className={`${BUBBLE} rounded-bl-[4px] border border-line`}>
              Yes please, I&apos;ll pay on delivery.
            </p>
          </div>
        </div>

        <div className="flex items-center gap-3 border-t border-line px-5 py-3">
          <span className="flex-1 text-secondary">Reply to Nadia</span>
          <span className="flex size-8 items-center justify-center rounded-control bg-primary text-surface">
            <SendIcon />
          </span>
        </div>
      </div>
    </div>
  );
}
