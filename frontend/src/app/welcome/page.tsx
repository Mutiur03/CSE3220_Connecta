import type { Metadata } from "next";
import Link from "next/link";
import type { CSSProperties, ReactNode } from "react";
import BrandMark from "@/components/brand-mark";
import {
  InboxIcon,
  KeyboardIcon,
  MailIcon,
  UserCheckIcon,
} from "@/components/icons";
import { badgeClasses, buttonClasses } from "@/components/ui/classes";
import InboxPreview from "./inbox-preview";

export const metadata: Metadata = {
  title: { absolute: "Connecta: one inbox for Instagram and Messenger" },
  description:
    "Answer Instagram and Messenger messages from one shared inbox, with every conversation assigned to someone on your team.",
};

// The landing page runs on its own scale (12, 14, 17, 32 and the fluid headline), a step up from
// the app's 13px base, because it is read once rather than worked in all day.
const CONTAINER = "mx-auto w-full max-w-6xl px-4 sm:px-6 lg:px-8";
const SECTION = "scroll-mt-20 border-t border-line py-16 sm:py-24";
const H2 =
  "text-[2rem] leading-[1.15] font-medium tracking-[-0.02em] text-balance";
const LEAD = "text-[1.0625rem] leading-relaxed text-secondary";

const NAV = [
  { href: "#features", label: "Features" },
  { href: "#how-it-works", label: "How it works" },
  { href: "#roles", label: "Roles" },
];

const FEATURES: { icon: ReactNode; title: string; text: string }[] = [
  {
    icon: <InboxIcon />,
    title: "One list for both channels",
    text: "Instagram and Messenger conversations arrive in the same inbox, newest first. A small badge shows where each one came from.",
  },
  {
    icon: <UserCheckIcon />,
    title: "Every conversation has an owner",
    text: "Assign by round-robin, to whoever has the fewest open conversations, or by hand. Nothing sits in a shared pile.",
  },
  {
    icon: <MailIcon />,
    title: "Invite your team by email",
    text: "Pick a role, send the invitation, and your teammate joins the workspace from one link.",
  },
  {
    icon: <KeyboardIcon />,
    title: "Built for the keyboard",
    text: "J and K move between conversations, R jumps to the reply box, E closes the conversation. Press ? for the full list.",
  },
];

const STEPS = [
  {
    title: "Create your workspace",
    text: "Sign up and name the workspace. You become its owner.",
  },
  {
    title: "Invite the people who reply",
    text: "Send an invitation to each teammate and choose whether they join as an admin or an agent.",
  },
  {
    title: "Answer from one inbox",
    text: "New messages get an assignee as they arrive, and everyone sees who is handling what.",
  },
];

const ROLES = [
  {
    role: "Owner",
    text: "Runs the workspace: renames it, manages admins and can do everything below.",
  },
  {
    role: "Admin",
    text: "Invites and removes members, changes their roles and reassigns conversations.",
  },
  {
    role: "Agent",
    text: "Answers the conversations assigned to them and picks up new ones.",
  },
];

const order = (i: number) => ({ "--i": i }) as CSSProperties;

export default function WelcomePage() {
  return (
    <div id="top" className="flex min-h-dvh flex-col text-message">
      <header className="sticky top-0 z-10 border-b border-line bg-app">
        <div
          className={`${CONTAINER} flex h-16 items-center justify-between gap-4`}
        >
          {/* Already on this page, so a plain anchor: a Link to "/" is a no-op and never scrolls. */}
          <a
            href="#top"
            className="flex items-center gap-2.5 rounded-control text-[1.0625rem] font-medium"
          >
            <BrandMark className="size-7" />
            <span className="max-[420px]:sr-only">Connecta</span>
          </a>
          <nav
            aria-label="Sections"
            className="hidden items-center gap-8 md:flex"
          >
            {NAV.map((item) => (
              <a
                key={item.href}
                href={item.href}
                className="rounded-control text-secondary transition-colors duration-150 ease-out hover:text-primary"
              >
                {item.label}
              </a>
            ))}
          </nav>
          <div className="grid grid-cols-2 gap-2">
            <Link href="/login" className={buttonClasses("secondary")}>
              Sign in
            </Link>
            <Link href="/signup" className={buttonClasses("primary")}>
              Create account
            </Link>
          </div>
        </div>
      </header>

      <main className="flex-1">
        <section
          aria-labelledby="hero-title"
          className="pt-14 pb-16 sm:pt-24 sm:pb-24"
        >
          <div className={CONTAINER}>
            <div className="stagger grid gap-8 lg:grid-cols-[minmax(0,1.75fr)_minmax(0,1fr)] lg:items-end lg:gap-16">
              <h1
                id="hero-title"
                style={order(0)}
                className="text-[clamp(2.5rem,5.2vw,4.25rem)] leading-[1.02] font-medium tracking-[-0.035em] text-balance"
              >
                Every message answered by someone who owns it.
              </h1>
              <div style={order(1)} className="flex flex-col gap-6 lg:pb-2">
                <p className={LEAD}>
                  Connecta puts your Instagram and Messenger conversations in
                  one shared inbox and gives each one an assignee, so customers
                  stop waiting and your team stops asking who replied.
                </p>
                <div className="grid w-fit grid-cols-2 gap-3">
                  <Link href="/signup" className={buttonClasses("primary")}>
                    Create an account
                  </Link>
                  <Link href="/login" className={buttonClasses("secondary")}>
                    Sign in
                  </Link>
                </div>
              </div>
            </div>
            <div style={order(2)} className="stagger mt-12 sm:mt-16">
              <InboxPreview />
            </div>
            <p className="mt-6 flex flex-wrap items-center gap-2 text-secondary">
              Works with
              <span className={badgeClasses("neutral")}>Instagram</span>
              <span className={badgeClasses("neutral")}>Messenger</span>
            </p>
          </div>
        </section>

        <section
          id="features"
          aria-labelledby="features-title"
          className={SECTION}
        >
          <div className={CONTAINER}>
            <div className="grid gap-6 lg:grid-cols-2 lg:gap-16">
              <h2 id="features-title" className={H2}>
                A work tool you can keep open all day
              </h2>
              <p className={`${LEAD} max-w-xl`}>
                Quiet screens, one list, and every reply in its place. The inbox
                is built for people who answer customers for hours at a time.
              </p>
            </div>
            <ul className="mt-12 grid gap-x-16 sm:mt-16 md:grid-cols-2">
              {FEATURES.map((feature) => (
                <li
                  key={feature.title}
                  className="flex gap-4 border-t border-line py-8"
                >
                  <span className="mt-0.5 text-secondary">{feature.icon}</span>
                  <span className="flex flex-col gap-2">
                    <h3 className="text-[1.0625rem] font-medium">
                      {feature.title}
                    </h3>
                    <p className="max-w-md text-secondary">{feature.text}</p>
                  </span>
                </li>
              ))}
            </ul>
          </div>
        </section>

        <section
          id="how-it-works"
          aria-labelledby="how-title"
          className={SECTION}
        >
          <div className={CONTAINER}>
            <h2 id="how-title" className={`${H2} max-w-xl`}>
              Up and running in three steps
            </h2>
            <ol className="mt-12 grid gap-x-12 sm:mt-16 md:grid-cols-3">
              {STEPS.map((step, index) => (
                <li
                  key={step.title}
                  className="flex flex-col gap-2 border-t border-line py-8"
                >
                  <span className="figure text-caption text-secondary">
                    Step {index + 1}
                  </span>
                  <h3 className="text-[1.0625rem] font-medium">{step.title}</h3>
                  <p className="max-w-sm text-secondary">{step.text}</p>
                </li>
              ))}
            </ol>
          </div>
        </section>

        <section id="roles" aria-labelledby="roles-title" className={SECTION}>
          <div className={`${CONTAINER} grid gap-12 lg:grid-cols-2 lg:gap-16`}>
            <div className="flex flex-col gap-6">
              <h2 id="roles-title" className={H2}>
                Three roles, clear limits
              </h2>
              <p className={`${LEAD} max-w-md`}>
                Each person sees what their job needs. Owners hand out access,
                agents get on with the replies.
              </p>
            </div>
            <dl className="divide-y divide-line border-y border-line">
              {ROLES.map((item) => (
                <div
                  key={item.role}
                  className="grid gap-2 py-6 sm:grid-cols-[7rem_1fr] sm:gap-6"
                >
                  <dt className="font-medium">{item.role}</dt>
                  <dd className="text-secondary">{item.text}</dd>
                </div>
              ))}
            </dl>
          </div>
        </section>

        <section aria-labelledby="cta-title" className={SECTION}>
          <div
            className={`${CONTAINER} flex flex-col gap-8 lg:flex-row lg:items-end lg:justify-between`}
          >
            <div className="flex max-w-xl flex-col gap-4">
              <h2 id="cta-title" className={H2}>
                Start with your own workspace
              </h2>
              <p className={LEAD}>
                Create an account, name your workspace and invite the people who
                answer your customers.
              </p>
            </div>
            <div className="grid w-fit grid-cols-2 gap-3">
              <Link href="/signup" className={buttonClasses("primary")}>
                Create an account
              </Link>
              <Link href="/login" className={buttonClasses("secondary")}>
                Sign in
              </Link>
            </div>
          </div>
        </section>
      </main>

      <footer className="border-t border-line">
        <div
          className={`${CONTAINER} flex flex-col gap-4 py-8 text-caption text-secondary sm:flex-row sm:items-center sm:justify-between`}
        >
          <span className="flex items-center gap-2">
            <BrandMark className="size-5" />
            Connecta, a CSE 3220 project.
          </span>
          <nav aria-label="Footer" className="flex flex-wrap gap-6">
            {NAV.map((item) => (
              <a
                key={item.href}
                href={item.href}
                className="rounded-control hover:text-primary"
              >
                {item.label}
              </a>
            ))}
            <Link href="/login" className="rounded-control hover:text-primary">
              Sign in
            </Link>
          </nav>
        </div>
      </footer>
    </div>
  );
}
