# InspectFirst backend API

Implemented 2026-11-03. Responses are JSON unless downloading an attachment/report.
Use the development entry point `http://127.0.0.1:5173`. This proxy preserves session cookies and routes to the owning service.

## Authentication and errors

1. `GET /api/auth/csrf` returns `{token, headerName}` and a session cookie.
2. `POST /api/auth/login` accepts URL-encoded `email` and `password`, plus the returned CSRF header. Returns `{id,email,displayName,role}`.
3. Login changes the session identifier and clears the CSRF token. Fetch a new token before the next write. Cookies remain in the browser/Postman cookie jar.
4. All POST/PUT/PATCH/DELETE calls, including multipart uploads, require `X-CSRF-TOKEN`. JSON bodies never supply the acting user's identity or role.
5. Other business services validate cookies and write tokens through the identity service. Client-supplied user/role headers are ignored. If identity is unavailable, private operations fail closed with 503; public guides remain readable.

Errors: 400 invalid input, 401 login required/incorrect credentials, 403 admin/token check failed, 404 absent or other user's private resource, 409 state/version/duplicate conflict, 413 oversized photo, 429 auth attempt limit, 503 dependency unavailable. Error bodies contain `detail`; internal errors do not expose SQL or stack traces.

Lists default to page 0, size 20. Business lists allow size 1–100 and page 0–10000; existing public product search keeps size 1–48. Stable ordering includes an ID tie-breaker. User search escapes literal `%`/`_`.

## Identity service :8087 / identity_db

| Method | Path | Input / behavior |
|---|---|---|
| GET | `/api/auth/csrf` | Public token endpoint |
| POST | `/api/auth/register` | JSON `email`, `displayName` (1–60), `password` (12–128). 201; duplicate normalized email 409. Always creates USER |
| POST | `/api/auth/login` | URL-encoded credentials; returns USER or ADMIN |
| GET | `/api/auth/me` | Current account, checks ACTIVE status |
| POST | `/api/auth/authorize` | Same user data after identity + CSRF checks; used by the other services |
| POST | `/api/auth/logout` | Revoke session, 204 |
| GET | `/api/ops/overview` | ADMIN: account totals |
| GET | `/api/ops/users` | ADMIN: `q,page,size`; no password/hash fields |
| GET | `/api/ops/users/{id}` | ADMIN: account detail |
| PATCH | `/api/ops/users/{id}/status` | ADMIN: `{status:"ACTIVE"|"DISABLED"}`; cannot disable sole administrator; existing sessions rejected immediately |
| GET | `/api/ops/users/audit` | ADMIN: account status action log, `page,size` |

The database permits at most one ADMIN. No public administrator registration, promotion or account deletion API exists. Disabling accounts preserves their guide/inspection history. Sessions currently live in one identity-service instance and expire after 30 idle minutes. Set `SESSION_COOKIE_SECURE=true` with HTTPS. Email verification/recovery is not implemented; no fake email-sending API is provided.

## Guide service :8086 / check_guide_db

### Public information

| Method | Path | Behavior |
|---|---|---|
| GET | `/api/categories` | Available catalogue categories |
| GET | `/api/products?q=&category=&page=0&size=12` | ACTIVE products with published, ACTIVE model guides only |
| GET | `/api/products/{id}/guide` | Latest published model guide, ordered checks and sources |
| GET | `/api/categories/{id}/guide` | Explicit category-wide guide; 404 if no published general guide exists |
| GET | `/api/guides/versions/{id}` | Published version of an ACTIVE guide; drafts and withdrawn guides cannot be fetched publicly |

No category fallback is silently mixed with a model guide. Category guides have `scope:CATEGORY`, `category` and no `product`; model guides have `scope:MODEL` and `product`. Missing content stays missing until the team publishes sourced instructions. Existing Sony ILCE-6400 content is retained.

### Administrator content

| Method | Path | Behavior |
|---|---|---|
| GET/POST | `/api/ops/content/categories` | List / create `{slug,name}`; slug lowercase letters/hyphens |
| PUT | `/api/ops/content/categories/{id}` | Update `{slug,name}` |
| GET/POST | `/api/ops/content/products` | Search/list with `q,page,size` / create product |
| PUT | `/api/ops/content/products/{id}` | Edit/archive product; preserves history |
| GET | `/api/ops/content/guides` | All guide containers including drafts/withdrawn, `page,size` |
| POST | `/api/ops/content/guides` | `{productId}` OR `{categoryId}`; create first/next DRAFT; at most one editable draft per guide |
| GET | `/api/ops/content/guides/{id}/versions` | Version metadata, newest first |
| GET/PUT | `/api/ops/content/versions/{id}` | Read full editorial content / replace a DRAFT with version check |
| POST | `/api/ops/content/versions/{id}/publish` | `{expectedVersion}` matching `editVersion`; requires complete sourced checks |
| POST | `/api/ops/content/guides/{id}/withdraw` | `{reason}` (1–600); public visibility stops, old content retained |
| GET | `/api/ops/content/stats` | Products, guides, unresolved feedback counts |
| GET | `/api/ops/content/audit` | ADMIN: content/feedback operations log, `page,size` |

Product input: `{categoryId,brand,modelCode,name,status}`; status `ACTIVE` or `ARCHIVED`. Brand + model is unique.

Draft PUT:

```json
{
  "expectedVersion": 0,
  "title": "Model inspection guide",
  "tools": "Compatible accessories and permission to operate",
  "reviewedOn": "2026-11-03",
  "contentNote": "Editorial guide. Not a guarantee of condition or authenticity.",
  "items": [{
    "title": "Check title",
    "purpose": "What this observation helps assess",
    "steps": ["Step 1", "Step 2"],
    "expectedObservation": "What to observe",
    "abnormalSigns": "What to record",
    "limitations": "What this check cannot confirm",
    "sources": [{"title":"Official instructions","url":"https://example.com/manual","publisher":"Manufacturer"}]
  }]
}
```

The URL above illustrates the format, not an actual inspection source. Each guide needs 1–40 checks; each check needs sources, HTTP(S) URLs, steps and explicit limitations. Combined steps per check ≤2000 characters. Published/withdrawn versions cannot be edited. New content requires a new draft/version. `editVersion` changes on save/publish; stale writes return 409.

### Saved guides

| Method | Path | Behavior |
|---|---|---|
| GET | `/api/saved-guides?page=0&size=20` | Own list, `available`, `updated`, `seenVersion`, latest version and withdrawal reason |
| PUT | `/api/saved-guides/{guideId}` | Idempotent save of an available guide |
| DELETE | `/api/saved-guides/{guideId}` | Idempotent own unsave, 204 |
| POST | `/api/saved-guides/{guideId}/seen` | Acknowledge current published version |

Withdrawn/archived entries stay visible as unavailable in the saved list instead of vanishing. Private list ownership always comes from the session.

### Feedback

| Method | Path | Behavior |
|---|---|---|
| POST | `/api/feedback` | Own new feedback with idempotency key |
| GET | `/api/feedback` | Own list, `status,type,page,size` |
| GET | `/api/feedback/{id}` | Own detail and public reply thread |
| POST | `/api/feedback/{id}/replies` | Own reply `{body,expectedVersion}` |
| GET | `/api/ops/feedback` | ADMIN inbox, `status,type,page,size` |
| GET | `/api/ops/feedback/{id}` | ADMIN detail/thread |
| PATCH | `/api/ops/feedback/{id}/status` | ADMIN `{status,reply,expectedVersion}`; public explanation required |

Create: `{requestKey,type,subject,body,productId?,guideVersionId?,checkItemId?}`. Types: `INACCURATE`, `MISSING_PRODUCT`, `APP_PROBLEM`. Subject 1–160; body/reply 1–5000. Context IDs must match. Reusing a key for different content returns 409.

States: OPEN → IN_REVIEW/NEED_DETAILS/CLOSED; IN_REVIEW → NEED_DETAILS/RESOLVED/CLOSED; NEED_DETAILS → IN_REVIEW/CLOSED; RESOLVED → IN_REVIEW/CLOSED; CLOSED is terminal. User replies to NEED_DETAILS/RESOLVED reopen IN_REVIEW. Every mutation increments `lockVersion`, compared with `expectedVersion`.

## Inspection service :8088 / inspection_db

| Method | Path | Behavior |
|---|---|---|
| POST | `/api/inspections` | Create own workspace from a current published guide |
| GET | `/api/inspections` | Own nondeleted history, `status,page,size` |
| GET/PUT | `/api/inspections/{id}` | Read / save own workspace |
| POST | `/api/inspections/{id}/complete` | Freeze an immutable completion revision; queue report event atomically |
| POST | `/api/inspections/{id}/reopen` | `{expectedVersion}`; explicit completed→in-progress transition |
| DELETE | `/api/inspections/{id}?expectedVersion=N` | Soft-delete own workspace, revoke access and emit deletion event; 204 |
| GET | `/api/inspections/{id}/revisions` | Own completion-version list |
| GET | `/api/inspections/{id}/revisions/{revisionId}` | Immutable saved completion, including snapshot hash |
| POST | `/api/inspections/{id}/items/{itemId}/attachments?expectedVersion=N` | Multipart `file`; PNG/JPEG ≤5 MB; ≤5 photos/check |
| GET | `/api/attachments/{id}` | Owner or ADMIN download; record must not be deleted |
| DELETE | `/api/attachments/{id}?expectedVersion=N` | Own in-progress record only; 204; photos referenced by a completion revision cannot be removed separately |
| GET | `/api/ops/inspections` | ADMIN: `ownerUserId,status,page,size`; includes deleted metadata if requested |
| GET | `/api/ops/inspections/{id}` | ADMIN: inspect active/completed record and results; access audited |
| GET | `/api/ops/inspections/{id}/revisions` | ADMIN: completion history |
| GET | `/api/ops/inspections/{id}/revisions/{revisionId}` | ADMIN: historical content, access audited |
| GET | `/api/ops/inspections/stats` | ADMIN: totals by state |
| GET | `/api/ops/inspections/audit` | ADMIN access log, `page,size` |

Create: `{productId OR categoryId, guideVersionId, requestKey, userRequirement, sellerClaim}`. Requirements and seller claims are strings, max 3000 each, empty allowed. `guideVersionId` must match the currently available guide; no half-created record on a guide-service failure. Same owner/key/content returns the existing record; changed content with the same key conflicts.

Save:

```json
{
  "expectedVersion": 0,
  "userRequirement": "My purchase requirements",
  "sellerClaim": "What the seller promised",
  "results": [{"itemId":"workspace-item UUID","status":"ISSUE_OBSERVED","note":"Observed issue"}]
}
```

Use the workspace item IDs from the inspection response, not the original guide check IDs. Partial result lists are allowed; duplicate/foreign items fail atomically. States: `UNCHECKED`, `NO_ISSUE_OBSERVED`, `ISSUE_OBSERVED`, `UNABLE_TO_ASSESS`. There is no automatic overall pass/authenticity verdict. Counts are calculated from actual stored results.

Save/upload/delete-photo/reopen/complete increment `lockVersion`. Complete accepts `{expectedVersion,requestKey}`. Repeating completion returns the same immutable revision, not a second report. Reopening preserves old revisions. Later guide edits/withdrawal do not change the workspace's frozen content.

ADMIN can read user records as explicitly requested for the Operations console; ADMIN cannot silently rewrite a user's observations through these endpoints. Future UI must tell users that authorized staff can review their submitted records. Soft-deleted data is retained for audit; neither users nor report downloads can access its content. Backup retention and physical erasure are separate future policies.

## Report service :8089 / report_db

| Method | Path | Behavior |
|---|---|---|
| GET | `/api/reports` | Own report statuses, `page,size` |
| GET | `/api/reports/{id}` | Own or ADMIN metadata after live inspection-access check |
| GET | `/api/reports/{id}/download` | READY report as escaped printable HTML |
| GET | `/api/reports/{id}/download.pdf` | READY report as PDF, embedded Latin/Chinese font |
| GET | `/api/reports/{id}/snapshot` | Original completion JSON |
| POST | `/api/reports/{id}/retry` | Requeue FAILED report after authorization/CSRF checks |
| GET | `/api/ops/reports` | ADMIN report-job status list, `page,size` |

Kafka topic `inspectfirst.inspection.events.v1`, keyed by inspection ID. Completion + revision + outbox are committed in one MySQL transaction. Scheduled relay waits for Kafka acknowledgement before marking SENT and retries outages. Consumer commits inbox + unique revision job in one transaction; duplicate delivery cannot create a second job. Deletion tombstones stop late completion events recreating deleted reports. Rendering runs outside the listener in its own worker.

HTML preparation is asynchronous. PDF representation is generated from the same immutable snapshot on an authorized download; HTML/PDF do not re-fetch a newer guide. Download rechecks the inspection service, so a deletion takes effect before Kafka catches up. Dependency failure denies download, rather than exposing a cached report.

## Deliberate boundaries

No cross-database joins/foreign keys, no distributed transaction or exactly-once claim. One local MySQL instance hosts four logical databases and four limited service accounts. Kafka has separate project-local data/ports 19092/19093. Report/image binaries are not public static resources. A new product needs human-reviewed sources; backend support does not mean an unlimited verified product database exists.

Automated tests: `mvn test package` from backend. Real MySQL/Kafka acceptance: `python3 tests/e2e.py` after services/proxy start; it creates labelled QA fixtures, then withdraws/archives/closes/deletes them. It does not use real purchase data or print passwords.

### Check-level examples

Guide item objects now include `images` (an ordered array, empty for legacy content). Administrator Draft item bodies optionally accept up to three images: `url` (local `/images/checks/` asset), `alt` (400 chars), `caption` (600), `kind` (`DIAGRAM`, `PHOTO`, `SCREENSHOT`), `sourceUrl` (HTTPS without credentials), `sourceTitle` (200). URLs disallow traversal and unsupported extensions. Existing item bodies omitting images remain accepted. Public product guides, public guide versions and admin versions return the same image metadata. Assets are served by the frontend static host, not fetched by the backend from arbitrary URLs.

Images are stored in `check_item_image` and belong to the check's guide version. Publish a new version to add/change examples; never edit a published version or old inspection snapshots. Existing installations apply additive `database/06-check-images.sql` before launching the updated guide service. Current 179 examples are labelled editorial SVG schematics, not real photographs or official screenshots.
