import type { Metadata } from "next";
import Link from "next/link";
import MetaConnectButton from "@/components/meta-connect-button";
import MetaPagePicker from "@/components/meta-page-picker";
import { tableClass } from "@/components/org-styles";
import { Alert } from "@/components/ui/alert";
import { TableScroll } from "@/components/ui/table-scroll";
import { formatDate } from "@/lib/format";
import { section, type Section } from "@/lib/inventory-view";
import {
  getMetaConnection,
  listAvailablePages,
  listConnectedPages,
  listInstagramAccounts,
  type MetaAvailablePage,
  type MetaConnectedPage,
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
  facebookStatusLine,
  type ConnectionResultParams,
} from "@/lib/meta-view";
import type { OrgActionState } from "@/lib/org-actions";
import type { Page } from "@/lib/org-api";
import { loadOrganization, loadSession } from "../data";

type RouteParams = Promise<{ id: string }>;
type SearchParams = Promise<ConnectionResultParams>;

const LIST_SIZE = 20;

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

  const isConnected = connection.ok && connection.value.connected;

  return (
    <>
      <Header orgId={id} orgName={organization.name} />
      {flash && <Alert tone={flash.tone}>{flash.text}</Alert>}

      <section
        aria-labelledby="facebook-heading"
        className="flex flex-col gap-3 border-t border-line-subtle pt-8"
      >
        <h2 id="facebook-heading" className="text-lg font-medium">
          Facebook
        </h2>
        {connection.ok ? (
          <>
            <p className="text-secondary">
              {facebookStatusLine(
                connection.value.connected,
                connection.value.connectedAt,
                formatDate,
              )}
            </p>
            <p className="text-sm text-secondary">
              Facebook opens a login where you choose which Pages to share with
              Connecta.
            </p>
            <MetaConnectButton
              action={startFacebookConnection.bind(null, id)}
              idleLabel={
                isConnected ? "Reconnect Facebook" : "Connect Facebook"
              }
              busyLabel="Opening Facebook…"
              variant={isConnected ? "secondary" : "primary"}
            />
          </>
        ) : (
          <Alert tone="danger">
            Facebook status could not be loaded. Refresh the page to try again.
          </Alert>
        )}
      </section>

      <section
        aria-labelledby="pages-heading"
        className="flex flex-col gap-4 border-t border-line-subtle pt-8"
      >
        <h2 id="pages-heading" className="text-lg font-medium">
          Pages
        </h2>
        {!connection.ok ? null : !isConnected ? (
          <p className="text-secondary">
            Connect Facebook first. Its login shows the Pages you can grant.
          </p>
        ) : available.ok ? (
          <AvailablePages
            pages={available.value}
            action={connectPagesAction.bind(null, id)}
          />
        ) : (
          <Alert tone="danger">
            The Pages from Facebook could not be loaded. Refresh the page to try
            again.
          </Alert>
        )}
        <ConnectedPagesTable result={connectedPages} />
      </section>

      <section
        aria-labelledby="instagram-heading"
        className="flex flex-col gap-3 border-t border-line-subtle pt-8"
      >
        <h2 id="instagram-heading" className="text-lg font-medium">
          Instagram
        </h2>
        <p className="text-sm text-secondary">
          Instagram Professional accounts connect with their own login, so each
          account must sign in itself.
        </p>
        <MetaConnectButton
          action={startInstagramConnection.bind(null, id)}
          idleLabel="Connect Instagram"
          busyLabel="Opening Instagram…"
          variant="secondary"
        />
        <InstagramTable result={instagram} />
      </section>
    </>
  );
}

function Header({ orgId, orgName }: { orgId: string; orgName: string }) {
  return (
    <header className="flex flex-col gap-1">
      <Link
        href={`/organizations/${orgId}`}
        className="text-sm text-secondary underline-offset-4 hover:underline"
      >
        {orgName}
      </Link>
      <h1 className="text-display font-medium">Integrations</h1>
      <p className="text-secondary">
        Connect this organization&apos;s Facebook Pages and Instagram accounts
        to receive their messages.
      </p>
    </header>
  );
}

function AvailablePages({
  pages,
  action,
}: {
  pages: MetaAvailablePage[];
  action: (
    previous: OrgActionState,
    formData: FormData,
  ) => Promise<OrgActionState>;
}) {
  if (pages.length === 0) {
    return (
      <p className="text-secondary">
        Facebook granted no Pages. Connect Facebook again and choose a Page in
        the login dialog.
      </p>
    );
  }
  return <MetaPagePicker pages={pages} action={action} />;
}

function ConnectedPagesTable({
  result,
}: {
  result: Section<Page<MetaConnectedPage>>;
}) {
  if (!result.ok) {
    return (
      <Alert tone="danger">
        Connected Pages could not be loaded. Refresh the page to try again.
      </Alert>
    );
  }
  if (result.value.content.length === 0) {
    return <p className="text-secondary">No Pages are connected yet.</p>;
  }
  return (
    <TableScroll label="Connected Pages">
      <table className={tableClass}>
        <thead className="text-secondary">
          <tr className="border-b border-line-subtle">
            <th scope="col" className="px-4 py-3 font-medium">
              Page
            </th>
            <th scope="col" className="px-4 py-3 font-medium">
              Page ID
            </th>
            <th scope="col" className="px-4 py-3 font-medium">
              Connected
            </th>
          </tr>
        </thead>
        <tbody>
          {result.value.content.map((page) => (
            <tr
              key={page.pageId}
              className="border-b border-line-subtle last:border-0"
            >
              <td className="px-4 py-3 text-primary">{page.name}</td>
              <td className="px-4 py-3 text-secondary">{page.pageId}</td>
              <td className="px-4 py-3 text-secondary">
                {formatDate(page.connectedAt)}
              </td>
            </tr>
          ))}
        </tbody>
      </table>
    </TableScroll>
  );
}

function InstagramTable({
  result,
}: {
  result: Section<Page<MetaInstagramAccount>>;
}) {
  if (!result.ok) {
    return (
      <Alert tone="danger">
        Instagram accounts could not be loaded. Refresh the page to try again.
      </Alert>
    );
  }
  if (result.value.content.length === 0) {
    return (
      <p className="text-secondary">No Instagram accounts are connected yet.</p>
    );
  }
  return (
    <TableScroll label="Connected Instagram accounts">
      <table className={tableClass}>
        <thead className="text-secondary">
          <tr className="border-b border-line-subtle">
            <th scope="col" className="px-4 py-3 font-medium">
              Account
            </th>
            <th scope="col" className="px-4 py-3 font-medium">
              Account ID
            </th>
            <th scope="col" className="px-4 py-3 font-medium">
              Connected
            </th>
          </tr>
        </thead>
        <tbody>
          {result.value.content.map((account) => (
            <tr
              key={account.id}
              className="border-b border-line-subtle last:border-0"
            >
              <td className="px-4 py-3 text-primary">
                {account.username ? `@${account.username}` : "Unnamed account"}
              </td>
              <td className="px-4 py-3 text-secondary">
                {account.externalAccountId}
              </td>
              <td className="px-4 py-3 text-secondary">
                {formatDate(account.connectedAt)}
              </td>
            </tr>
          ))}
        </tbody>
      </table>
    </TableScroll>
  );
}
