# Webhook fan-out (Cloudflare Worker)

A Meta app has one webhook URL, but production and a developer's laptop both need the events. Meta calls this Worker
(`https://hooks.torongo.dev/api/meta/webhooks`); it relays the same request to production and to the dev tunnel.

- The raw body and `X-Hub-Signature-256` are forwarded unchanged, so both backends verify the signature with the same app secret.
- Verify handshake (GET): whichever backend knows the verify token answers with the challenge, so the token in Meta must be
  the `META_WEBHOOK_VERIFY_TOKEN` of production or of the local `.env`.
- Events (POST): Meta gets a success answer when at least one backend accepted. Production not deployed yet, or the laptop off, does not
  make Meta retry.
- No secrets live in the Worker. Customer messages reach your laptop only while `DEV_URL` is set.

## Deploy (once, and whenever the tunnel URL changes)
```bash
cd ops/cf-webhook-fanout
npx wrangler login                       # once
npx wrangler deploy --var "DEV_URL:https://<your-tunnel>.ngrok-free.dev"
npx wrangler deploy                      # production only (DEV_URL empty)
```
`wrangler.toml` holds `PROD_URL`, the route (`hooks.torongo.dev`, a custom domain on the Cloudflare zone) and an empty `DEV_URL`.
Change the tunnel URL without redeploying code: Cloudflare dashboard, Workers, connecta-webhook-fanout, Settings, Variables.

## Meta settings
Set the webhook callback URL to the Worker URL in both places: Instagram (Use cases, Instagram, API setup with Instagram login,
step 3) and Messenger (Use cases, Messenger, Messenger API settings, Configure webhooks). OAuth redirect URIs are separate and
keep pointing at the backends.

## Test
`node --test ops/cf-webhook-fanout`
