import Link from "next/link";
import { BrandLogo } from "@/components/brand-mark";
import { badgeClasses } from "@/components/ui/classes";

type PreviewRow = {
  name: string;
  channel: "Instagram" | "Messenger";
  text: string;
  time: string;
  unread: boolean;
};

const ROWS: PreviewRow[] = [
  {
    name: "Nadia Rahman",
    channel: "Instagram",
    text: "Is the linen shirt still available in M?",
    time: "2m",
    unread: true,
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
];

const initials = (name: string) =>
  name
    .split(" ")
    .map((part) => part[0])
    .join("");

// Decorative: a sample of the inbox, so the sign-in page shows what is behind it. Rows rise in
// once on load, the only staggered motion in the app.
export default function LoginPreview() {
  return (
    <aside className="hidden border-r border-line bg-app p-12 lg:flex">
      {/* One centred column, so the content stays together on a very wide screen. */}
      <div className="mx-auto flex w-full max-w-lg flex-col justify-between gap-16">
        <Link href="/" className="self-start rounded-control">
          <BrandLogo className="h-24" />
        </Link>

        <div aria-hidden="true" className="flex flex-col gap-10">
          <p className="text-[2rem] leading-[1.15] font-medium text-balance">
            Instagram and Messenger, answered from one inbox.
          </p>
          <ul className="stagger divide-y divide-line-subtle overflow-hidden rounded-panel border border-line bg-surface">
            {ROWS.map((row, index) => (
              <li
                key={row.name}
                style={{ "--i": index } as React.CSSProperties}
                className="flex items-center gap-3 px-4 py-3"
              >
                <span className="inline-flex size-9 shrink-0 items-center justify-center rounded-full bg-hover text-caption font-medium text-secondary">
                  {initials(row.name)}
                </span>
                <span className="flex min-w-0 flex-1 flex-col">
                  <span className="flex items-center gap-2">
                    <span
                      className={
                        row.unread ? "font-medium text-primary" : "text-primary"
                      }
                    >
                      {row.name}
                    </span>
                    <span className={badgeClasses("neutral")}>
                      {row.channel}
                    </span>
                  </span>
                  <span className="truncate text-secondary">{row.text}</span>
                </span>
                <span className="flex items-center gap-2 figure text-caption text-secondary">
                  {row.unread && (
                    <span className="size-1.5 rounded-full bg-highlight" />
                  )}
                  {row.time}
                </span>
              </li>
            ))}
          </ul>
        </div>

        <p aria-hidden="true" className="text-caption text-secondary">
          One shared inbox for your whole team.
        </p>
      </div>
    </aside>
  );
}
