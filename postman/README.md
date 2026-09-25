# AccountFlow API collection

Generated from the live OpenAPI document (`/v3/api-docs`), so it cannot drift
from the API. 27 requests across 8 folders, verified end to end against the deployed API
(27/27 passing).

| File | Purpose |
|---|---|
| `AccountFlow.postman_collection.json` | The collection (Postman v2.1) |
| `AccountFlow.postman_environment.json` | Variables: `baseUrl` and the captured ids |

## Running it

1. Import both files into Postman (or Insomnia / Thunder Client / Bruno —
   all read v2.1).
2. `baseUrl` defaults to the deployed API
   (`https://accountflow-2eff.onrender.com`), so nothing needs to be running
   locally. Point it at `http://localhost:8082` to work against a local server.
3. Hit **Run collection**. It passes top to bottom with no manual editing, and
   can be re-run as many times as you like: each run mints a fresh user.

Captured automatically by the test scripts: `email`, `accessToken`,
`refreshToken`, `userId`, `accountId`, `secondAccountId`, `transactionId`,
`transferId`. Every request inherits the bearer token from the collection, so
nothing needs pasting by hand.

`Session end → logout` is last on purpose: it revokes every token.

### Health

`Health → Service health` hits `/health`, which is unauthenticated and pings
MongoDB — it returns `503 DOWN` if the database is unreachable rather than a
constant `UP`. It runs first so a failing collection run tells you immediately
whether the service is even reachable. On the free Render tier the first call
after ~15 minutes idle also wakes the instance, which takes a while.

## Building a React Native client against this

The five things that will bite you if you infer the client from the endpoint
list alone:

**1. Money is a string, never a number.** `"amount": "5000.00"`, not `5000.00`.
JSON numbers become IEEE-754 doubles in JavaScript, which silently loses
precision on money. Keep the string end to end, and do arithmetic with
`decimal.js` or by converting to integer paise — never `parseFloat`.

**2. Every response is enveloped.** Success is
`{ success, data, message?, pagination?, requestId }`; failure is
`{ success: false, code, message, timestamp, path, requestId }`. Unwrap `data`
in one shared fetch helper rather than at each call site.

**3. Branch on `code`, never on `message`.** Codes are stable API contract;
messages are for humans and will change. The ones a client must handle:
`INSUFFICIENT_BALANCE`, `ACCOUNT_CLOSED`, `CURRENCY_MISMATCH`,
`VALIDATION_ERROR` (carries `fieldErrors` for form highlighting),
`INVALID_CREDENTIALS`, `TOKEN_EXPIRED`, `USER_LOCKED`.

**4. Generate an `Idempotency-Key` per money operation, and reuse it on
retry.** This is what makes a flaky mobile network safe. Mint a UUID when the
user taps *Pay*, keep it for that attempt, and send the same value on every
retry — the server returns the original result instead of moving money twice.
Mint a new one only for a genuinely new operation.

**5. Refresh tokens rotate, and reuse is treated as theft.** Each
`/auth/refresh` consumes the presented token and returns a new one. Store the
new one immediately. If two screens refresh concurrently with the same token,
the second is rejected with `REFRESH_TOKEN_REUSED` and **the whole session is
revoked** — so funnel refresh through a single mutex'd call and queue the
requests waiting on it.

Access tokens last 15 minutes; refresh tokens 7 days.

## Regenerating

Re-run the generator after changing any endpoint; it reads the live API:

```bash
curl -s http://localhost:8082/v3/api-docs > /tmp/openapi.json
python3 scripts/gen_collection.py
```
