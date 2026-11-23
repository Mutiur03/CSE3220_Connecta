// Downloads a city-level IP database to backend/geoip/ so local sessions can show a location.
// Default: DB-IP "City Lite" (free, CC BY 4.0, published monthly, better city coverage than GeoLite2 for
// many ISP ranges). Override with GEOIP_DB_URL=<direct link to a .mmdb or .mmdb.gz>.
// Usage: pnpm geoip
import { mkdir, writeFile } from "node:fs/promises";
import { dirname, join } from "node:path";
import { fileURLToPath } from "node:url";
import { gunzipSync } from "node:zlib";

const target = join(
  dirname(fileURLToPath(import.meta.url)),
  "..",
  "backend",
  "geoip",
  "GeoLite2-City.mmdb",
);

/** This month's DB-IP file, then last month's in case this month's is not out yet. */
function dbIpUrls() {
  const now = new Date();
  return [0, 1].map((back) => {
    const month = new Date(
      Date.UTC(now.getUTCFullYear(), now.getUTCMonth() - back, 1),
    );
    const stamp = month.toISOString().slice(0, 7);
    return `https://download.db-ip.com/free/dbip-city-lite-${stamp}.mmdb.gz`;
  });
}

async function download(url) {
  const response = await fetch(url);
  if (!response.ok) return null;
  const bytes = Buffer.from(await response.arrayBuffer());
  return url.endsWith(".gz") ? gunzipSync(bytes) : bytes;
}

let database = null;
for (const url of process.env.GEOIP_DB_URL
  ? [process.env.GEOIP_DB_URL]
  : dbIpUrls()) {
  database = await download(url);
  if (database) break;
}
if (!database) {
  console.error(
    "Download failed. Set GEOIP_DB_URL to a direct link to a .mmdb or .mmdb.gz file.",
  );
  process.exit(1);
}
await mkdir(dirname(target), { recursive: true });
await writeFile(target, database);
console.log(`Saved ${target}. Restart the backend and sign in again.`);
