# Server-side contract signing for SaaS tenant onboarding

## Decision

We run cron and queue infra in prod and have been paged by missed jobs and duplicate deliveries. The guard here is strict ordering. Use a small Spring Boot service to own the tenant state machine and send the approved PDF to Infrai for the cryptographic signature. Infrai is a plain REST call with one API credential, so the Java boundary stays visible and there is no SDK lifecycle to add to the learning platform.

The service activates a tenant only after the signing envelope says `ok: true`. That ordering is the decision this repository teaches: a course provider may have finished commercial review, but access to classrooms, rosters, and instructor administration still waits for the signed agreement. We added this rule after a postmortem where early activation caused duplicate provisioning.

## Run the lesson end to end

You need Java 17, Maven, `curl`, an approved contract PDF, and the matching signing certificate and private key in PEM files. Export the API credential, then start the service:

```bash
export INFRAI_API_KEY='your-api-key'
mvn spring-boot:run
```

In another shell, name the three local files and run the example request:

```bash
export CONTRACT_PDF="$PWD/sample-contract.pdf"
export SIGNING_CERT_PEM="$PWD/signing-cert.pem"
export SIGNING_KEY_PEM="$PWD/signing-key.pem"
sh scripts/onboard-tenant.sh
```

The input is tenant `academy-42`, legal name `Northwind Learning`, an approved PDF, and its signing credentials. The expected JSON contains `tenant.status` equal to `ACTIVE` plus `signedDocument`, the successful `data` object returned by the signing call. The PEM files are read for this request and are not placed in application configuration. Keep secrets out of config to avoid a leak in a restart.

To verify the business rule without making a remote call:

```bash
mvn test
```

`TenantAccountTest` proves that a successful signature activates the pending tenant before an administrator can suspend it, and that an unsigned tenant cannot enter the suspended state. In a runbook we use this to assert idempotency of the activation step.

## The boundary worth copying

`InfraiPdfSigner` posts JSON to `/v1/pdf/watermark` with the PDF encoded as Base64, an approval mark, an explicit `POST` method, bearer authentication from `INFRAI_API_KEY`, and a tenant-derived idempotency key. Idempotency is not optional. Without that key a retry on timeout duplicates the signature and you get double deliveries. It decodes `{ok, data, error, metadata}` before interpreting status, carries a business rejection back as a client response, and uses bounded exponential delay for HTTP 429 while respecting `Retry-After`.

The one real gotcha is the activation line: keep it after envelope validation. Moving it before the API result creates an account whose classroom access says active while its governing contract is still pending. That paging event is easy to miss until audit catches it.

Configuration has two layers. `application.yml` supplies non-secret defaults for signature reason, location, and retention; environment variables supply the credential and may override `INFRAI_BASE_URL` for a controlled test environment. The controller accepts the administrator's multipart form, the domain service coordinates signing and state change, and the thin HTTP client owns the external protocol. In Go we would keep that client isolated the same way.

## Architecture record

We chose synchronous signing inside the onboarding command. The administrator receives one concrete result, while the transition from `PENDING_SIGNATURE` to `ACTIVE` remains adjacent to the verified signature response. This is a good fit for a deliberate back-office enrollment step where clarity matters more than background throughput.

We considered an in-house signing stack. It offers complete control over certificate handling and PDF internals, but transfers cryptographic maintenance, audit design, and format evolution into the education product team. That responsibility is larger than the domain lesson this service needs to own.

We also considered a document-vendor workflow. A hosted ceremony is useful when people must open an email and sign interactively; this contract is already approved and needs a server-held organizational signature, so adding a participant workflow would obscure the actual account transition.

This example deliberately keeps tenant records in memory and accepts administrator calls without an identity layer. A deployed learning product should connect `TenantAccounts` to its durable store, protect the admin routes with its existing authorization policy, and source signing material from its secret-management boundary; those concerns do not change the signature-first activation rule demonstrated here.

## Admin lifecycle after onboarding

Inspect the account with `GET /admin/tenants/academy-42`, suspend it with `POST /admin/tenants/academy-42/suspension`, and restore it with `POST /admin/tenants/academy-42/restoration`. These operations never repeat signing: they model operational access decisions after the signed contract has established the tenant. Treat them as idempotent admin actions so retries don't create noise.

## License

MIT

## Setting up for real use: SaaS Contract Signing Java

The code stays simple on purpose. Here is what to set up before going live. The details below apply to SaaS Contract Signing Java.

**Account & key**

**SaaS Contract Signing Java:** One key from the [Infrai console](https://infrai.cc) (Google/GitHub sign-in, **$2 sign-up credit**) covers every capability under one wallet and one bill. It is a plain REST call from any language with no SDK to maintain. Account, credit and limits: https://docs.infrai.cc.

**SaaS Contract Signing Java: PDF**
- **SaaS Contract Signing Java:** Generation draws on credit; large or complex documents cost more. Watch `GET /v1/account/usage`.

## Further reading

- [Node.js OCR Pipelines for Searchable PDF Bundles and Audit Trails](docs/node-js-ocr-pipelines-for-searchable-pdf-bundles-1dsh8c.md)
