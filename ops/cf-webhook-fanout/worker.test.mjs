// Run: node --test ops/cf-webhook-fanout
import assert from "node:assert/strict";
import { afterEach, test } from "node:test";
import worker from "./worker.js";

const realFetch = globalThis.fetch;
afterEach(() => (globalThis.fetch = realFetch));

const env = { PROD_URL: "https://prod.test", DEV_URL: "https://dev.test" };
const post = () =>
  new Request("https://hooks.test/api/meta/webhooks", {
    method: "POST",
    headers: { "content-type": "application/json", "x-hub-signature-256": "sha256=abc" },
    body: '{"object":"page"}',
  });

test("relays the exact body and the signature to both backends", async () => {
  const calls = [];
  globalThis.fetch = async (url, init) => {
    calls.push({ url, signature: init.headers.get("x-hub-signature-256"), body: new TextDecoder().decode(init.body) });
    return new Response("ok");
  };
  const response = await worker.fetch(post(), env);
  assert.equal(response.status, 200);
  assert.deepEqual(calls.map((c) => c.url).sort(), [
    "https://dev.test/api/meta/webhooks",
    "https://prod.test/api/meta/webhooks",
  ]);
  for (const call of calls) {
    assert.equal(call.signature, "sha256=abc");
    assert.equal(call.body, '{"object":"page"}');
  }
});

test("answers 200 when only production accepts, and when only dev accepts", async () => {
  globalThis.fetch = async (url) =>
    url.startsWith("https://prod.test") ? new Response("ok") : Promise.reject(new Error("tunnel down"));
  assert.equal((await worker.fetch(post(), env)).status, 200);
  globalThis.fetch = async (url) =>
    url.startsWith("https://dev.test") ? new Response("ok") : new Response("nope", { status: 503 });
  assert.equal((await worker.fetch(post(), env)).status, 200);
});

test("answers 502 when no backend accepts", async () => {
  globalThis.fetch = async () => new Response("nope", { status: 500 });
  assert.equal((await worker.fetch(post(), env)).status, 502);
});

test("verify handshake: the backend that knows the token answers with the challenge", async () => {
  globalThis.fetch = async (url) =>
    url.startsWith("https://dev.test") ? new Response("12345") : new Response("forbidden", { status: 403 });
  const verify = new Request("https://hooks.test/api/meta/webhooks?hub.mode=subscribe&hub.verify_token=t&hub.challenge=12345");
  const response = await worker.fetch(verify, env);
  assert.equal(response.status, 200);
  assert.equal(await response.text(), "12345");
});

test("an empty DEV_URL relays to production only", async () => {
  const urls = [];
  globalThis.fetch = async (url) => (urls.push(url), new Response("ok"));
  await worker.fetch(post(), { PROD_URL: "https://prod.test", DEV_URL: "" });
  assert.deepEqual(urls, ["https://prod.test/api/meta/webhooks"]);
});
