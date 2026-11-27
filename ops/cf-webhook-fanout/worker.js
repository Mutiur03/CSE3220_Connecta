// Cloudflare Worker: Meta allows one webhook URL per app, so Meta calls this Worker and it relays the request to
// production and to the developer's tunnel. The raw body and the signature header are passed through untouched, so
// both backends can verify X-Hub-Signature-256 with the same app secret.
//
// - GET (Meta's verify handshake): whichever backend knows the verify token answers 200 with the challenge.
// - POST (events): Meta gets a success answer when at least one backend accepted the event, so a stopped laptop or a
//   backend that is not deployed yet never makes Meta retry.
// - DEV_URL empty = relay to production only.

const RELAY_TIMEOUT_MS = 8000; // Meta waits about 20 s for an answer
const FORWARDED_HEADERS = ["content-type", "x-hub-signature-256", "x-hub-signature"];

async function relay(base, request, body) {
  if (!base) return null;
  const url = new URL(request.url);
  const headers = new Headers({ "ngrok-skip-browser-warning": "1" });
  for (const name of FORWARDED_HEADERS) {
    const value = request.headers.get(name);
    if (value) headers.set(name, value);
  }
  try {
    return await fetch(base + url.pathname + url.search, {
      method: request.method,
      headers,
      body,
      signal: AbortSignal.timeout(RELAY_TIMEOUT_MS),
    });
  } catch {
    return null; // backend down or too slow
  }
}

export default {
  async fetch(request, env) {
    const body = request.method === "GET" || request.method === "HEAD" ? undefined : await request.arrayBuffer();
    const [prod, dev] = await Promise.all([relay(env.PROD_URL, request, body), relay(env.DEV_URL, request, body)]);
    const accepted = [prod, dev].find((response) => response?.ok);
    return accepted ?? new Response("no backend accepted the webhook", { status: 502 });
  },
};
