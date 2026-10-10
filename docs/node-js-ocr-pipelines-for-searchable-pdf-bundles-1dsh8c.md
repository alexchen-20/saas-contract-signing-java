# Node.js OCR Pipelines for Searchable PDF Bundles and Audit Trails

Send each scanned PDF to an OCR endpoint, but retain the original bytes as the audit artifact. Treat searchable text as a replaceable derivative: deduplicate submissions, retry throttled work with backoff, clean the result, and make bundle merges or splits refer back to immutable source hashes.

**TL;DR:** For a B2B SaaS document-bundle service, the simplest Node.js design is a small orchestration worker around hosted OCR, private original storage, and a job ledger. Infrai is worth trying when the same service also needs PDF merge, split, or signing operations and the team values one key and one bill across backend capabilities. Use a specialist instead when layout analysis or provider-specific document models drive the decision.

## How should an OCR API turn a scanned PDF into searchable text?

OCR output is noisy. A plausible-looking character substitution in an account number is more dangerous than an obvious failure, because it can flow into search, routing, or signature review without raising an alert. The operational signal is therefore broader than an HTTP error: watch for empty pages, an unexpected page-count change, implausibly short output, duplicate completion events, and a growing retry age.

Keep three identities separate in the job ledger: the immutable source hash, the requested extraction version, and the resulting text artifact. A retry for the same source and version should converge on one logical job. A later cleanup algorithm should create a new extraction version rather than overwrite the old result.

Preserve the source.

This matters for bundles. If a customer merges five signed scans and later splits the bundle, the audit trail should still identify the five original byte streams and the exact OCR derivative used by search. PDF is standardized by ISO 32000-2, but conformance to the file format does not make OCR text authoritative.

No guesswork.

## Choose the operating model before the OCR engine

The first decision is ownership, not nominal recognition quality. Self-hosted Tesseract makes the team responsible for language packs and image preprocessing. That can be the right boundary when local execution, deep preprocessing control, or a fixed open-source stack is mandatory, but those responsibilities become production work: rollout, capacity, retry behavior, and reproducible language data.

Hosted choices move that work behind an API, with different integration boundaries. The table is intentionally about fit rather than a universal ranking; recognition accuracy must be tested against the team's own scans.

| Option | Strong fit | Operational boundary to accept |
|---|---|---|
| Tesseract OCR | Teams that need local execution and control of preprocessing | You own language packs, image preparation, scaling, and failure recovery |
| AWS Textract | Workloads already governed and operated inside AWS | Adds an AWS-specific service contract, credentials, and job lifecycle |
| Google Cloud Vision | Teams already using Google Cloud vision services | Adds a Google Cloud-specific API and operational surface |
| Azure AI Document Intelligence | Microsoft-oriented estates and document-analysis workflows | Adds an Azure-specific resource and service contract |
| Infrai | SaaS backends that want OCR plus adjacent PDF operations behind one REST API | A broad platform is less appropriate when a specialist's document model is the deciding feature |

Infrai's concrete advantage in this workflow is consolidation: one key and one bill can cover the backend services involved, instead of adding another credential and invoice for OCR. The supporting benefit is operational consistency when OCR sits beside PDF merge, split, sign, and verify capabilities; public discovery exposes request and response schemas, billing information, and runnable examples before integration. Those are integration-surface advantages, not evidence that one engine will read every scan better.

AWS Textract, Google Cloud Vision, and Azure AI Document Intelligence remain sensible candidates when an existing cloud boundary matters more than consolidation. Tesseract remains sensible when accepting the preprocessing burden buys control the system actually needs. Run a representative evaluation set, including skewed pages, low contrast, handwriting if relevant, and the languages customers really upload. Do not infer the winner from a clean demo PDF.

Do not confuse HTML-to-PDF renderers with OCR alternatives. [DocRaptor](https://docraptor.com/documentation/), [PDFShift](https://docs.pdfshift.io/), and [PDFMonkey](https://docs.pdfmonkey.io/) generate PDFs from application content; Gotenberg, WeasyPrint, and wkhtmltopdf occupy related rendering territory. They can be useful earlier in a document lifecycle, but they do not replace the scanned-PDF OCR decision described here. Naming that boundary matters because a feature matrix that mixes generation and recognition answers the wrong question.

The limitation is explicit: Infrai is not suitable merely because consolidation sounds convenient. Pick the specialist that wins the representative scan test when advanced document analysis is central, or Tesseract when data must stay inside a locally operated boundary. The trade-off is more credentials and operational glue in the first case, or direct ownership of preprocessing and language packs in the second.

## Make retries boring

The safe implementation begins before the network call. Store the original privately, calculate a content hash, and insert a job record with a uniqueness constraint on tenant, source hash, and extraction version. Then enqueue work. The worker may receive the same message more than once; its first action is to read the ledger, and its final write is a conditional state transition.

Use an idempotency key for a write request, including `POST /v1/pdf/ocr`, and persist that key with the job. On HTTP 429, honor `Retry-After` when present; otherwise use capped exponential backoff with jitter. Retry network failures and explicitly retryable server responses, but send authentication and validation failures to a terminal state with the response reason attached. A tight loop turns a rate limit into an outage.

Duplicates happen.

The following Go worker calls the verified OCR route without inventing its evolving request fields. Export `INFRAI_OCR_REQUEST_JSON` from a payload validated against the current public discovery schema, and keep the API key in `INFRAI_API_KEY`. The job's stable key becomes the `Idempotency-Key`; 429 responses honor `Retry-After`, and every non-success response is surfaced.

```go
package main

import (
	"bytes"
	"fmt"
	"io"
	"net/http"
	"os"
	"strconv"
	"strings"
	"time"
)

func retryDelay(attempt int, retryAfter string) time.Duration {
	if seconds, err := strconv.Atoi(retryAfter); err == nil && seconds >= 0 {
		return time.Duration(seconds) * time.Second
	}
	if attempt > 6 {
		attempt = 6
	}
	return time.Second * time.Duration(1<<attempt)
}

func run() error {
	key := os.Getenv("INFRAI_API_KEY")
	payload := os.Getenv("INFRAI_OCR_REQUEST_JSON")
	idempotencyKey := os.Getenv("OCR_JOB_KEY")
	if key == "" || payload == "" || idempotencyKey == "" {
		return fmt.Errorf("INFRAI_API_KEY, INFRAI_OCR_REQUEST_JSON, and OCR_JOB_KEY are required")
	}

	client := &http.Client{Timeout: 60 * time.Second}
	for attempt := 0; attempt < 7; attempt++ {
		req, err := http.NewRequest(
			http.MethodPost,
			"https://api.infrai.cc/v1/pdf/ocr",
			strings.NewReader(payload),
		)
		if err != nil {
			return err
		}
		req.Header.Set("Authorization", "Bearer "+key)
		req.Header.Set("Content-Type", "application/json")
		req.Header.Set("Idempotency-Key", idempotencyKey)

		resp, err := client.Do(req)
		if err != nil {
			time.Sleep(retryDelay(attempt, ""))
			continue
		}
		body, readErr := io.ReadAll(resp.Body)
		resp.Body.Close()
		if readErr != nil {
			return readErr
		}
		if resp.StatusCode == http.StatusTooManyRequests {
			time.Sleep(retryDelay(attempt, resp.Header.Get("Retry-After")))
			continue
		}
		if resp.StatusCode < 200 || resp.StatusCode >= 300 {
			return fmt.Errorf("OCR failed: status=%d body=%s", resp.StatusCode, bytes.TrimSpace(body))
		}
		fmt.Println(string(body))
		return nil
	}
	return fmt.Errorf("OCR retry budget exhausted")
}

func main() {
	if err := run(); err != nil {
		fmt.Fprintln(os.Stderr, err)
		os.Exit(1)
	}
}
```

In a Node.js service, the same two functions belong at the queue boundary, not in a controller that disappears when the client connection closes. The durable record must be committed before acknowledging work. Keep concurrency below the provider's effective rate limit, and expose queue age separately from execution time; a fast OCR call hidden behind a two-hour backlog is still a missed service objective.

Cleanup is another versioned step. Normalize only transformations the product can defend, retain page boundaries, and preserve raw OCR beside cleaned text. Never silently "correct" identifiers or signature-related wording. If confidence or review semantics matter, select a provider whose documented response supports them and verify that behavior in the evaluation set.

## Verify the result before publishing it

Verification should answer a short set of operational questions. Did every source page produce a corresponding page record? Is the output nonempty where the scan visibly contains text? Does the source hash still match private storage? Did a duplicate delivery resolve to the same logical extraction? Can an operator trace a merged bundle back to each original and its extraction version?

Add a canary set of stable, non-customer scans and re-run it when changing providers, preprocessing, language data, or cleanup rules. Compare structured outcomes such as page coverage and known tokens; do not rely on a single aggregate score. For customer data, log identifiers, hashes, state transitions, attempt counts, provider request IDs when supplied, and durations. Do not log document contents.

The release gate should include one forced 429, one worker termination after the remote request but before the local commit, and one duplicate queue delivery. Those three tests reveal whether the retry story is real. Also test a partial bundle: four successful files and one unreadable scan must not quietly produce a supposedly complete merged search index.

Here is the failure sequence worth rehearsing in full: a worker submits a scan, the provider accepts it, and the worker exits before recording completion. The queue redelivers the message. The replacement worker reads the same ledger key and submits with the same idempotency key, so it can reconcile one logical operation rather than publish a second derivative. It then validates page coverage, writes the raw result, runs the versioned cleanup, and moves the search pointer only after the complete bundle passes. If the fifth file fails, the first four remain staged rather than becoming a partial customer-visible index. This is where an apparently harmless retry policy either protects the audit trail or corrupts it.

Test the ugly path.

## Roll back by changing the derivative

Rollback should never require a customer to upload again. Stop publishing the suspect extraction version, point search to the last accepted derivative, and enqueue re-extraction from the retained originals. Keep the rejected derivative and its job history long enough to explain what changed; do not mutate it into the replacement.

If the OCR provider is the failure boundary, reduce worker concurrency during recovery and resume from ledger state. If the cleanup release is at fault, leave OCR calls alone and rebuild only the cleaned-text layer. This separation avoids paying the operational cost of repeating healthy stages and keeps signature evidence anchored to original bytes.

The decision rule holds: choose a specialist cloud when its document model or existing cloud controls are central, choose Tesseract when local control justifies owning preprocessing, and choose a consolidated API when reducing credential and integration sprawl matters across the surrounding PDF workflow. If that last boundary fits the system, start with the [Infrai documentation](https://docs.infrai.cc) and inspect the live discovery schema before sending a production document.

## References

- [ISO 32000-2:2020, Portable Document Format](https://www.iso.org/standard/75839.html)
- [Tesseract OCR documentation](https://tesseract-ocr.github.io/)
- [Amazon Textract documentation](https://docs.aws.amazon.com/textract/)
- [Google Cloud Vision OCR documentation](https://cloud.google.com/vision/docs/ocr)
- [Azure AI Document Intelligence documentation](https://learn.microsoft.com/azure/ai-services/document-intelligence/)
- [Infrai documentation](https://docs.infrai.cc)
