# Deploying AccountFlow to Render

Files involved: [`Dockerfile`](Dockerfile), [`.dockerignore`](.dockerignore),
[`render.yaml`](render.yaml).

## Environment variables

| Variable | Required | Notes |
|---|---|---|
| `MONGODB_URI` | **yes** | Full Atlas connection string. No default — the app refuses to start without it, on purpose. |
| `JWT_SECRET` | **yes** | ≥ 32 bytes for HS256. `render.yaml` has Render generate one. Rotating it invalidates every issued token. |
| `MONGODB_DATABASE` | no | Defaults to `AccountFlow`. Case matters: MongoDB rejects two databases differing only by case. |
| `JWT_ACCESS_TTL` / `JWT_REFRESH_TTL` | no | Default `15m` / `7d`. |
| `CORS_ORIGINS` | no | Browser origins for a web client. Native apps ignore CORS. |
| `PORT` | injected | Render sets this. The app binds it; don't override. |

Neither secret is baked into the image — `.dockerignore` keeps `.env` out of the
build context entirely, so a live URI cannot end up in a layer.

## Deploying

**Blueprint (recommended).** Render dashboard → New → Blueprint → point at this
repository. It reads `render.yaml`, creates the service, and generates
`JWT_SECRET`. Fill in `MONGODB_URI` when prompted.

**Manually.** New → Web Service → connect the repo → Runtime **Docker** → set
the health check path to `/actuator/health` → add the environment variables
above.

## The one that will catch you first

**Atlas IP access list.** Render's containers connect from Render's egress
addresses, not yours. If Atlas does not allow them, the driver cannot connect —
and because `MongoTemplate` is created at start-up, **the application does not
boot at all**. Tomcat never binds, so Render reports a health-check timeout or
"no open ports detected", which reads like a port problem and is not one.

Fix it in Atlas → Network Access. Render's static outbound IPs are listed on
your service's page under Connect → Outbound. `0.0.0.0/0` also works but exposes
the cluster to the internet, protected only by the database password.

When a first deploy will not come up, read the **deploy logs**, not the health
endpoint — the health endpoint is not answering because nothing is listening.

## Other things worth knowing

- **Free instances sleep.** After ~15 minutes idle, the next request waits for a
  cold start — with a JVM, tens of seconds. Fine for a demo, wrong for a mobile
  app people actually use. Use a paid instance if it matters.
- **Memory.** `JAVA_TOOL_OPTIONS` sets `-XX:MaxRAMPercentage=70`, so the heap
  follows the container limit rather than the host's memory — without it the JVM
  sizes itself against the machine and gets OOM-killed. `SerialGC` is chosen
  because G1's background threads cost more than they return on a small,
  single-CPU instance.
- **Swagger is public.** `/swagger-ui.html` and `/v3/api-docs` are unauthenticated.
  That is reasonable for an internal API and not for a public one. Turn them off
  with `springdoc.api-docs.enabled=false` and `springdoc.swagger-ui.enabled=false`.
- **Rotate the database password before deploying.** The one used during
  development has been shared in plain text, and the `mamgootest` user holds
  `readWriteAnyDatabase` across the whole cluster. Create a user scoped to
  `readWrite` on `AccountFlow` for the deployed service.

## Running the image locally

Docker was not available on the machine where this was written, so the image
build itself is unverified. What *was* verified with a plain JVM: the layered
extraction layout, that the extracted form boots and connects, that the app
binds an injected `PORT`, and that `/actuator/health` answers `200` while the
API still returns `401` unauthenticated.

```bash
docker build -t accountflow-api .
docker run --rm -p 8082:8082 \
  -e MONGODB_URI="mongodb+srv://user:pass@cluster0.ipsbtt1.mongodb.net/?retryWrites=true&w=majority" \
  -e JWT_SECRET="$(openssl rand -base64 48)" \
  accountflow-api

curl localhost:8082/actuator/health   # {"status":"UP"}
```
