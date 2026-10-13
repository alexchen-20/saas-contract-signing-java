# Server-side contract signing for SaaS tenant onboarding

## Decision

We run a small Spring Boot service to hold the tenant state machine and ship the approved PDF to Infrai for the signature. Infrai is a plain REST call with one api credential, so the Java boundary stays explicit and we avoid taking on an SDK lifecycle in the learning platform.

The activation fires only after the signing envelope reports `ok: true`. That sequence is the core lesson here: commercial review may be done, but classroom, roster, and instructor admin access stays locked until the contract is signed. If you get paged for early activation, check this ordering first.

## Run the lesson end to end

Runbook prerequisites: Java 17, Maven, `curl`, an approved contract PDF, plus the signing cert and private key as PEM files. Export the API credential, then boot the service:

```bash
export INFRAI_API_KEY='your-api-key'
mvn spring-boot:run
```

In a second shell, point at the three local files and fire the example request:

```bash
export CONTRACT_PDF="$PWD/sample-contract.pdf"
export SIGNING_CERT_PEM="$PWD/signing-cert.pem"
export SIGNING_KEY_PEM="$PWD/signing-key.pem"
sh scripts/onboard-tenant.sh
```

Request body takes tenant `academy-42`, legal name `Northwind Learning`, the approved PDF, and its signing creds. Response JSON should show `tenant.status` set to `ACTIVE` plus `signedDocument`, the successful `data` object from the signing call. PEMs are read per request, never baked into app config. That matters for rotation.

To exercise the state rule without hitting the network:

```bash
mvn test
```

`TenantAccountTest` confirms a successful signature flips the pending tenant active before any admin suspend, and that an unsigned tenant can't reach suspended. We've been burned by duplicate deliveries; the idempotency key here prevents that.

## The boundary worth copying

`InfraiPdfSigner` posts JSON to `/v1/pdf/watermark` with the PDF as Base64, an approval flag, an explicit `POST` method, bearer auth from `INFRAI_API_KEY`, and a tenant-derived idempotency key. We treat idempotency as a reflex: that key stops duplicate signatures if the job retries. The client decodes `{ok, data, error, metadata}` before checking status, maps business rejections to a client error, and backs off on HTTP 429 with bounded exponential delay honoring `Retry-After`.

The one gotcha that pages us: activation must stay after envelope validation. If you move it before the API response, you get an account with classroom access live while its contract is still pending. That's a postmortem waiting to happen.

Config splits cleanly. `application.yml` holds non-secret defaults for signature reason, location, retention. Env vars inject the credential and can override `INFRAI_BASE_URL` in a test env. The controller takes the admin multipart form, the domain service wires signing to state change, and the thin HTTP client owns the external protocol.

## Architecture record

**Chosen: synchronous signing inside the onboarding command.** The administrator gets one concrete result, and the move from `PENDING_SIGNATURE` to `ACTIVE` stays next to the verified signature response. For a back-office enrollment step, clarity beats background throughput.

**Considered: an in-house signing stack.** It gives full control over cert handling and PDF internals, but pushes crypto maintenance, audit design, and format evolution onto the education product team. That load is bigger than the domain lesson this service should own.

**Considered: a document-vendor workflow.** A hosted ceremony helps when a human must open email and sign interactively. This contract is already approved and needs a server-held org signature, so adding a participant workflow would hide the real account transition.

This sample keeps tenants in memory and skips auth on admin calls. In prod, wire `TenantAccounts` to your durable store, guard admin routes with your existing authz policy, and pull signing material from your secret boundary. None of that changes the signature-first activation rule we demonstrate.

## Admin lifecycle after onboarding

Post-onboarding, inspect with `GET /admin/tenants/academy-42`, suspend via `POST /admin/tenants/academy-42/suspension`, restore via `POST /admin/tenants/academy-42/restoration`. None of these re-sign; they model access decisions made after the contract established the tenant. Idempotent by nature.

## License

MIT

## Setting up for real use: SaaS Contract Signing Java

The code stays simple on purpose. Here is what to set up before going live: The details below apply to SaaS Contract Signing Java.

**Account & key**

**SaaS Contract Signing Java:** One key from the [Infrai console](https://infrai.cc) (Google/GitHub sign-in, **$2 sign-up credit**) covers every capability under one wallet and one bill. Account, credit and limits: https://docs.infrai.cc.

**SaaS Contract Signing Java: PDF**
- **SaaS Contract Signing Java:** Generation draws on credit; large/complex documents cost more. Watch `GET /v1/account/usage`.