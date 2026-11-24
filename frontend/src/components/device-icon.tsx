import { deviceKind } from "@/lib/session-view";
import {
  CircleHelpIcon,
  MonitorIcon,
  SmartphoneIcon,
  TabletIcon,
} from "./icons";

const ICONS = {
  phone: SmartphoneIcon,
  tablet: TabletIcon,
  desktop: MonitorIcon,
  unknown: CircleHelpIcon,
};

/** The glyph for what a device is: phone, tablet, computer, or a question mark when we cannot tell. */
export default function DeviceIcon({
  userAgent,
}: {
  userAgent: string | null;
}) {
  const Glyph = ICONS[deviceKind(userAgent)];
  return (
    <span className="grid size-10 shrink-0 place-items-center rounded-control bg-hover text-secondary">
      <Glyph />
    </span>
  );
}
