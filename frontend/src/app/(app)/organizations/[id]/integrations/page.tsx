import type { Metadata } from "next";
import Link from "next/link";
import type { ReactNode } from "react";
import {
  CircleCheckIcon,
  FacebookIcon,
  InstagramIcon,
} from "@/components/icons";
import MetaConnectButton from "@/components/meta-connect-button";
import MetaPagePicker from "@/components/meta-page-picker";
import { Alert } from "@/components/ui/alert";
import { Badge } from "@/components/ui/badge";
import type { BadgeTone } from "@/components/ui/classes";
import { LocalTime } from "@/components/local-time";
import { formatDate } from "@/lib/format";
import { section, type Section } from "@/lib/inventory-view";
import {
  getMetaConnection,
  listAvailablePages,
  listConnectedPages,
  listInstagramAccounts,
  type MetaAvailablePage,
  type MetaConnectedPage,
  type MetaConnection,
  type MetaInstagramAccount,
} from "@/lib/meta-api";
import {
  connectPagesAction,
  startFacebookConnection,
  startInstagramConnection,
} from "@/lib/meta-actions";
import {
  canConnectMeta,
  connectionFlash,
  facebookDetailLine,
  facebookStatus,
  historyImportNotice,
  STATUS_LABELS,
  webhookLine,
  type ChannelStatus,
  type ConnectionResultParams,
} from "@/lib/meta-view";
import type { Page } from "@/lib/org-api";
import { loadOrganization, loadSession } from "../data";

type RouteParams = Promise<{ id: string }>;
type SearchParams = Promise<ConnectionResultParams>;

const LIST_SIZE = 20;

const STATUS_TONES: Record<ChannelStatus, BadgeTone> = {
  connected: "success",
  expired: "danger",
  not_connected: "neutral",
};

// A bordered list of rows: Pages and Instagram accounts share it, so both channels read the same.
const ROW_LIST =
  "flex flex-col divide-y divide-line-subtle rounded-control border border-line";
const ROW = "flex min-h-14 items-center justify-between gap-4 px-4 py-2";

export const metadata: Metadata = { title: "Integrations" };

// Connecting Meta is an admin's job, and every Meta endpoint here is admin-only, so an agent gets a notice and
// the page makes no Meta requests. Each section loads in parallel and shows its own alert when it fails.
export default async function IntegrationsPage({
  params,
  searchParams,
}: {
  params: RouteParams;
  searchParams: SearchParams;
}) {
  const { id } = await params;
  const flash = connectionFlash(await searchParams);
  const organization = await loadOrganization(id);
  if (!canConnectMeta(organization.callerRole)) {
    return (
      <>
        <Header orgId={id} orgName={organization.name} />
        <Alert tone="info">
          Only an admin of this organization connects Facebook and Instagram
          accounts.
        </Alert>
      </>
    );
  }

  const { accessToken } = await loadSession(id);
  const [connection, available, connectedPages, instagram] = await Promise.all([
    section(getMetaConnection(accessToken, id)),
    section(listAvailablePages(accessToken, id)),
    section(listConnectedPages(accessToken, id, 0, LIST_SIZE)),
    section(listInstagramAccounts(accessToken, id, 0, LIST_SIZE)),
  ]);

  return (
    <>
      <Header orgId={id} orgName={organization.name} />
      {flash && <Alert tone={flash.tone}>{flash.text}</Alert>}

      <div className="grid gap-8 lg:grid-cols-[minmax(0,1fr)_15rem] lg:items-start">
        <div className="flex flex-col divide-y divide-line rounded-panel border border-line bg-surface">
          <MessengerSection
            orgId={id}
            connection={connection}
            available={available}
            connectedPages={connectedPages}
          />
          <InstagramSection orgId={id} accounts={instagram} />
        </div>
        <BeforeYouConnect />
      </div>
    </>
  );
}

function Header({ orgId, orgName }: { orgId: string; orgName: string }) {
  return (
    <header className="flex flex-col gap-1">
      <Link
        href={`/organizations/${orgId}`}
        className="self-start text-sm text-secondary underline-offset-4 hover:underline"
      >
        {orgName}
      </Link>
      <h1 className="text-display font-medium">Integrations</h1>
      <p className="text-secondary">
        Connect Facebook Pages and Instagram accounts so their messages arrive
        in the inbox.
      </p>
    </header>
  );
}

/** One channel: glyph, name, status and its single action, with the channel's accounts underneath. */
function ChannelSection({
  id,
  icon,
  name,
  status,
  detail,
  action,
  children,
}: {
  id: string;
  icon: ReactNode;
  name: string;
  status: ChannelStatus;
  detail: string;
  action: ReactNode;
  children?: ReactNode;
}) {
  return (
    <section
      aria-labelledby={`${id}-heading`}
      className="flex flex-col gap-4 p-4 sm:p-5"
    >
      <div className="flex flex-wrap items-start gap-x-4 gap-y-3">
        <span className="grid size-10 shrink-0 place-items-center rounded-control border border-line bg-app text-primary">
          {icon}
        </span>
        <div className="flex min-w-0 flex-1 basis-56 flex-col gap-1">
          <div className="flex flex-wrap items-center gap-2">
            <h2 id={`${id}-heading`} className="text-lg font-medium">
              {name}
            </h2>
            <Badge tone={STATUS_TONES[status]}>{STATUS_LABELS[status]}</Badge>
          </div>
          <p className="text-sm text-secondary">{detail}</p>
        </div>
        <div className="shrink-0 pl-14 sm:ml-auto sm:pl-0">{action}</div>
      </div>
      {children && (
        <div className="flex flex-col gap-3 sm:pl-14">{children}</div>
      )}
    </section>
  );
}

function MessengerSection({
  orgId,
  connection,
  available,
  connectedPages,
}: {
  orgId: string;
  connection: Section<MetaConnection>;
  available: Section<MetaAvailablePage[]>;
  connectedPages: Section<Page<MetaConnectedPage>>;
}) {
  if (!connection.ok) {
    return (
      <ChannelSection
        id="messenger"
        icon={<FacebookIcon />}
        name="Messenger"
        status="not_connected"
        detail="Messages sent to your Facebook Pages."
        action={null}
      >
        <Alert tone="danger">
          Facebook status could not be loaded. Refresh the page to try again.
        </Alert>
      </ChannelSection>
    );
  }

  const now = new Date();
  const status = facebookStatus(connection.value, now);
  const signedIn = status !== "not_connected";

  return (
    <ChannelSection
      id="messenger"
      icon={<FacebookIcon />}
      name="Messenger"
      status={status}
      detail={facebookDetailLine(connection.value, now, formatDate)}
      action={
        <MetaConnectButton
          action={startFacebookConnection.bind(null, orgId)}
          idleLabel={signedIn ? "Reconnect Facebook" : "Connect Facebook"}
          busyLabel="Opening Facebook…"
          variant={status === "connected" ? "secondary" : "primary"}
        />
      }
    >
      {signedIn && (
        <PagesBody
          orgId={orgId}
          available={available}
          connectedPages={connectedPages}
        />
      )}
    </ChannelSection>
  );
}

function PagesBody({
  orgId,
  available,
  connectedPages,
}: {
  orgId: string;
  available: Section<MetaAvailablePage[]>;
  connectedPages: Section<Page<MetaConnectedPage>>;
}) {
  const connectedOn = connectedPages.ok
    ? Object.fromEntries(
        connectedPages.value.content.map((page) => [
          page.pageId,
          formatDate(page.connectedAt),
        ]),
      )
    : {};
  const now = new Date();
  const webhookLines = connectedPages.ok
    ? Object.fromEntries(
        connectedPages.value.content.map((page) => [
          page.pageId,
          webhookLine(page.lastWebhookAt, now),
        ]),
      )
    : {};
  const notices = connectedPages.ok
    ? Object.fromEntries(
        connectedPages.value.content.flatMap((page) => {
          const notice = historyImportNotice(page.historyImportError);
          return notice ? [[page.pageId, notice]] : [];
        }),
      )
    : {};

  if (!available.ok) {
    return (
      <Alert tone="danger">
        The Pages from Facebook could not be loaded. Refresh the page to try
        again.
      </Alert>
    );
  }
  if (available.value.length === 0) {
    return (
      <p className="text-sm text-secondary">
        Facebook shared no Pages. Reconnect Facebook and tick at least one Page
        in its login window.
      </p>
    );
  }
  return (
    <MetaPagePicker
      pages={available.value}
      connectedOn={connectedOn}
      notices={notices}
      webhookLines={webhookLines}
      action={connectPagesAction.bind(null, orgId)}
    />
  );
}

function InstagramSection({
  orgId,
  accounts,
}: {
  orgId: string;
  accounts: Section<Page<MetaInstagramAccount>>;
}) {
  const list = accounts.ok ? accounts.value.content : [];
  const total = accounts.ok ? accounts.value.totalElements : 0;
  const status: ChannelStatus = total > 0 ? "connected" : "not_connected";

  return (
    <ChannelSection
      id="instagram"
      icon={<InstagramIcon />}
      name="Instagram"
      status={status}
      detail={
        total > 0
          ? `${total === 1 ? "1 account" : `${total} accounts`} connected. Each account signs in with its own Instagram login.`
          : "Sign in with an Instagram professional account to receive its direct messages."
      }
      action={
        <MetaConnectButton
          action={startInstagramConnection.bind(null, orgId)}
          idleLabel={total > 0 ? "Add account" : "Connect Instagram"}
          busyLabel="Opening Instagram…"
          variant="secondary"
        />
      }
    >
      {!accounts.ok ? (
        <Alert tone="danger">
          Instagram accounts could not be loaded. Refresh the page to try again.
        </Alert>
      ) : (
        list.length > 0 && (
          <ul aria-label="Connected Instagram accounts" className={ROW_LIST}>
            {list.map((account) => (
              <li key={account.id} className={ROW}>
                <span className="flex min-w-0 flex-col">
                  <span className="truncate text-primary">
                    {account.username
                      ? `@${account.username}`
                      : "Unnamed account"}
                  </span>
                  <span className="figure text-caption text-secondary">
                    Account {account.externalAccountId}
                  </span>
                  <span className="text-caption text-secondary">
                    {webhookLine(account.lastWebhookAt, new Date())}
                  </span>
                  {historyImportNotice(account.historyImportError) && (
                    <span className="text-caption text-danger">
                      {historyImportNotice(account.historyImportError)}
                    </span>
                  )}
                </span>
                <span className="figure shrink-0 text-caption text-secondary">
                  <LocalTime iso={account.connectedAt} kind="date" />
                </span>
              </li>
            ))}
          </ul>
        )
      )}
    </ChannelSection>
  );
}

const REQUIREMENTS = [
  "You are an admin of each Facebook Page you connect.",
  "The Instagram account is a professional account, not a personal one.",
  "Allow access to messages is on in the Instagram app's message settings.",
];

function BeforeYouConnect() {
  return (
    <aside
      aria-labelledby="requirements-heading"
      className="flex flex-col gap-3 lg:pt-5"
    >
      <h2 id="requirements-heading" className="text-sm font-medium">
        Before you connect
      </h2>
      <ul className="flex flex-col gap-3">
        {REQUIREMENTS.map((text) => (
          <li
            key={text}
            className="flex gap-2.5 text-sm text-secondary [&_svg]:size-4 [&_svg]:shrink-0 [&_svg]:translate-y-0.5"
          >
            <CircleCheckIcon />
            <span>{text}</span>
          </li>
        ))}
      </ul>
      <p className="border-t border-line-subtle pt-3 text-sm text-secondary">
        Only messages sent after a channel is connected appear in the inbox.
      </p>
    </aside>
  );
}
