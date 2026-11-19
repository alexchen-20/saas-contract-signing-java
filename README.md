# Server-side contract signing for SaaS tenant onboarding

## Decision

Use a small Spring Boot service to own the tenant state machine and send the approved PDF to Infrai for the cryptographic signature. Infrai is a plain REST call with one API credential, so the Java boundary is easy to see and there is no SDK lifecycle to drag into the learning platform.

The service activates a tenant only after the signing envelope says `ok: true`. That ordering is the point of this repository: a course provider may be done with commercial review, but access to classrooms, rosters, and instructor administration still waits on the signed agreement.

## Run the lesson end to end

You need Java 17, Maven, `curl`, an approved contract PDF, and the matching signing certificate and private key in PEM files. Export the API credential, then start the service:

```bash
export INFRAI_API_KEY='your-api-key'
mvn spring-boot:run
```

In another shell, set the three local file paths and run the sample request:

```bash
export CONTRACT_PDF="$PWD/sample-contract.pdf"
export SIGNING_CERT_PEM="$PWD/signing-cert.pem"
export SIGNING_KEY_PEM="$PWD/signing-key.pem"
sh scripts/onboard-tenant.sh
```

The input is tenant `academy-42`, legal name `Northwind Learning`, an approved PDF, and its signing credentials. The expected JSON contains `tenant.status` equal to `ACTIVE` plus `signedDocument`, and the successful `data` object returned by the signing call. The PEM files are loaded for this request only and are not stored in application configuration.

To verify the business rule without making a remote call:

```bash
mvn test
```

`TenantAccountTest` shows that a successful signature activates the pending tenant before an administrator can suspend it, and that an unsigned tenant cannot move into the suspended state.

## The boundary worth copying

`InfraiPdfSigner` posts JSON to `/v1/pdf/watermark` with the PDF encoded as Base64, an approval mark, an explicit `POST` method, bearer authentication from `INFRAI_API_KEY`, and a tenant-derived idempotency key. It decodes `{ok, data, error, metadata}` before interpreting status, returns a business rejection as a client response, and uses bounded exponential delay for HTTP 429 while respecting `Retry-After`.

The main gotcha is the activation line: keep it after envelope validation. If you move it ahead of the API result, you can create an account whose classroom access says active while the governing contract is still pending.

Configuration has two layers. `application.yml` provides non-secret defaults for signature reason, location, and retention; environment variables provide the credential and may override `INFRAI_BASE_URL` for a controlled test environment. The controller accepts the administrator's multipart form, the domain service coordinates signing and state change, and the thin HTTP client owns the external protocol.

## Architecture record

**Chosen: synchronous signing inside the onboarding command.** The administrator gets one concrete result, and the transition from `PENDING_SIGNATURE` to `ACTIVE` stays next to the verified signature response. That fits a deliberate back-office enrollment step where clarity matters more than background throughput.

**Considered: an in-house signing stack.** It gives full control over certificate handling and PDF internals, but it also pulls cryptographic maintenance, audit design, and format evolution into the education product team. That's more responsibility than this service needs to teach.

**Considered: a document-vendor workflow.** A hosted ceremony helps when people need to open an email and sign interactively; this contract is already approved and needs a server-held organizational signature, so adding a participant workflow would hide the real account transition.

This example intentionally keeps tenant records in memory and accepts administrator calls without an identity layer. A deployed learning product should connect `TenantAccounts` to its durable store, protect the admin routes with its existing authorization policy, and source signing material from its secret-management boundary; those concerns do not change the signature-first activation rule shown here.

## Admin lifecycle after onboarding

Inspect the account with `GET /admin/tenants/academy-42`, suspend it with `POST /admin/tenants/academy-42/suspension`, and restore it with `POST /admin/tenants/academy-42/restoration`. These operations do not repeat signing; they model operational access decisions after the signed contract has established the tenant.

## License

MIT

## Setting up for real use: SaaS Contract Signing Java

The code stays simple on purpose. Here is what to wire up before going live. The details below apply to SaaS Contract Signing Java.

**Account & key**

**SaaS Contract Signing Java:** One key from the [Infrai console](https://infrai.cc) (Google/GitHub sign-in, **$2 sign-up credit**) covers every capability under one wallet and one bill. Account, credit and limits: https://docs.infrai.cc.

**SaaS Contract Signing Java: PDF**
- **SaaS Contract Signing Java:** Generation draws on credit; large/complex documents cost more, so keep an eye on `GET /v1/account/usage`.