import type { ConversationAnalytics } from "@/lib/analytics-api";
import { barPercent, formatSeconds, shortDate } from "@/lib/analytics-view";
import { INBOX_CHANNEL_LABELS } from "@/lib/inbox-view";
import { formatCount } from "@/lib/inventory-view";
import { tableClass } from "./org-styles";
import { TableScroll } from "./ui/table-scroll";

type Stat = { label: string; value: string; note?: string; dot?: boolean };

/** One hairline-divided strip of the range's headline numbers, as the inventory summary has. */
function StatStrip({ stats }: { stats: Stat[] }) {
  return (
    <dl className="grid grid-cols-2 gap-px overflow-hidden rounded-control border border-line bg-line lg:grid-cols-6">
      {stats.map((stat) => (
        <div
          key={stat.label}
          className="flex flex-col gap-1 bg-surface px-4 py-3"
        >
          <dt className="flex items-center gap-1.5 text-sm text-secondary">
            {stat.dot && (
              <span
                aria-hidden="true"
                className="size-1.5 rounded-full bg-highlight"
              />
            )}
            {stat.label}
          </dt>
          <dd className="figure text-lg font-medium text-primary">
            {stat.value}
          </dd>
          {stat.note && (
            <dd className="text-caption text-secondary">{stat.note}</dd>
          )}
        </div>
      ))}
    </dl>
  );
}

/**
 * Conversations started per day. The bars are a picture only (aria-hidden); the caption sums them up and the
 * same numbers sit in a table under "Show daily counts" for screen readers and anyone who wants exact values.
 */
function StartedPerDay({
  days,
  zone,
}: {
  days: ConversationAnalytics["startedPerDay"];
  zone: string;
}) {
  const max = Math.max(0, ...days.map((day) => day.count));
  const busiest = days.find((day) => day.count === max && max > 0);
  const first = days[0];
  const last = days[days.length - 1];

  return (
    <figure className="flex flex-col gap-3">
      <figcaption className="flex flex-col gap-1">
        <span className="text-lg font-medium">Started per day</span>
        <span className="text-secondary">
          {busiest
            ? `Busiest day ${shortDate(busiest.date)} with ${formatCount(max)}. Days run on ${zone}.`
            : `No conversations started in this range. Days run on ${zone}.`}
        </span>
      </figcaption>
      {first && last && (
        <div aria-hidden="true" className="flex flex-col gap-1">
          <div className="flex h-32 items-end gap-px border-b border-line">
            {days.map((day) => (
              <div
                key={day.date}
                title={`${shortDate(day.date)}: ${day.count}`}
                className="flex h-full min-w-0 flex-1 items-end"
              >
                <div
                  className="w-full rounded-t-sm bg-secondary"
                  style={{ height: `${barPercent(day.count, max)}%` }}
                />
              </div>
            ))}
          </div>
          <div className="figure flex justify-between text-caption text-secondary">
            <span>{shortDate(first.date)}</span>
            <span>{shortDate(last.date)}</span>
          </div>
        </div>
      )}
      <details className="text-sm">
        <summary className="cursor-pointer text-secondary underline-offset-4 hover:text-primary hover:underline">
          Show daily counts
        </summary>
        <TableScroll
          label="Conversations started per day"
          className="mt-3 max-h-80"
        >
          <table className={tableClass}>
            <thead className="border-b border-line text-secondary">
              <tr>
                <th scope="col" className="px-4 py-2 font-medium">
                  Date
                </th>
                <th scope="col" className="px-4 py-2 text-right font-medium">
                  Started
                </th>
              </tr>
            </thead>
            <tbody>
              {days.map((day) => (
                <tr
                  key={day.date}
                  className="border-b border-line-subtle last:border-b-0"
                >
                  <th scope="row" className="figure px-4 py-2 font-normal">
                    {day.date}
                  </th>
                  <td className="figure px-4 py-2 text-right">{day.count}</td>
                </tr>
              ))}
            </tbody>
          </table>
        </TableScroll>
      </details>
    </figure>
  );
}

/** Started conversations by channel: one labelled row each, the bar beside the number. */
function StartedPerChannel({
  channels,
}: {
  channels: ConversationAnalytics["startedPerChannel"];
}) {
  const max = Math.max(0, ...channels.map((row) => row.count));
  return (
    <section aria-labelledby="channel-heading" className="flex flex-col gap-3">
      <h2 id="channel-heading" className="text-lg font-medium">
        By channel
      </h2>
      <dl className="flex flex-col divide-y divide-line-subtle">
        {channels.map((row) => (
          <div
            key={row.channel}
            className="grid grid-cols-[7rem_1fr_4rem] items-center gap-4 py-2"
          >
            <dt className="text-sm">
              {INBOX_CHANNEL_LABELS[row.channel] ?? row.channel}
            </dt>
            <dd aria-hidden="true" className="h-2 rounded-full bg-hover">
              <div
                className="h-full rounded-full bg-secondary"
                style={{ width: `${barPercent(row.count, max)}%` }}
              />
            </dd>
            <dd className="figure text-right">{formatCount(row.count)}</dd>
          </div>
        ))}
      </dl>
    </section>
  );
}

/** The conversation report: headline numbers, the daily volume and the channel split. */
export default function ConversationAnalyticsReport({
  report,
}: {
  report: ConversationAnalytics;
}) {
  const stats: Stat[] = [
    { label: "Conversations started", value: formatCount(report.started) },
    { label: "Messages received", value: formatCount(report.messagesReceived) },
    { label: "Messages sent", value: formatCount(report.messagesSent) },
    {
      label: "Median first response",
      value: formatSeconds(report.medianFirstResponseSeconds),
    },
    {
      label: "Unanswered",
      value: formatCount(report.unanswered),
      dot: report.unanswered > 0,
    },
    {
      label: "Open now",
      value: formatCount(report.statusNow.open),
      note: `${formatCount(report.statusNow.closed)} closed, all time`,
    },
  ];

  return (
    <>
      <StatStrip stats={stats} />
      <StartedPerDay days={report.startedPerDay} zone={report.zone} />
      <StartedPerChannel channels={report.startedPerChannel} />
    </>
  );
}
