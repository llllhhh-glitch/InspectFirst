# InspectFirst

InspectFirst is a web application for checking second-hand products. Users find a guide by product model, record observations and photos, and download a report. Administrators maintain guides, handle feedback, manage users, and review inspection records.

## Repository structure

```text
InspectFirst/
├── frontend/                 Vue 3 single-page application
│   ├── src/                  Components, API requests, and styles
│   ├── public/images/        Product photos and check illustrations
│   └── vite.config.js        Development server and API proxy
├── backend/                  Maven multi-module project
│   ├── backend-common/       Database helpers, session access, and errors
│   ├── identity-service/     Accounts, authentication, and user management
│   ├── guide-service/        Products, guide versions, favorites, and feedback
│   ├── inspection-service/   Inspections, attachments, and completion events
│   ├── report-service/       Event consumption and report generation
│   └── tests/                Integration tests against running services
├── database/                 Schema, migration, and catalogue import scripts
└── .local/                   Private configuration, logs, and runtime files
```

## Architecture

The frontend uses Vue 3 and Vite. The backend uses Java 21, Spring Boot 3, Spring Security, and JPA/JDBC. MySQL stores business records, Kafka carries inspection events, and PDFBox exports PDF reports.

| Service | Default port | Database | Responsibility |
| --- | --- | --- | --- |
| identity-service | 8087 | identity_db | Accounts, sessions, roles, and account status |
| guide-service | 8086 | check_guide_db | Categories, products, guide versions, favorites, and feedback |
| inspection-service | 8088 | inspection_db | Results, attachments, completion snapshots, and event outbox |
| report-service | 8089 | report_db | Event deduplication, report jobs, and downloads |

These are four logical databases on one MySQL instance. Each service uses a separate database account and communicates through HTTP or Kafka rather than cross-database queries. `backend-common` is a shared library, not a running service.

During development, the browser accesses `http://127.0.0.1:5173`. The Vite proxy forwards requests to their owning services. See [vite.config.js](frontend/vite.config.js) for the routing rules.

### Authentication and authorization

The identity service maintains sessions through the `INSPECTFIRST_SESSION` cookie. Other services use `SessionAccess` to call `/api/auth/me` for reads or `/api/auth/authorize` for writes. Write requests also require a CSRF token. The frontend obtains a fresh token through [api.js](frontend/src/api.js).

Public registration creates USER accounts. The identity service creates the administrator from startup configuration, and a database constraint allows only one ADMIN. Disabled accounts are rejected on subsequent session requests. Ownership checks protect personal favorites, inspections, attachments, and reports; administrators use separate management endpoints.

### Guides and inspections

1. An administrator creates a guide draft with checks, references, and image metadata.
2. Draft saves use a version number to detect conflicting edits. Published versions are immutable; content updates require a new draft.
3. When an inspection starts, `GuideClient` fetches the selected guide version and the inspection service stores a snapshot.
4. Users record four result states: unchecked, no issue observed, issue observed, or unable to assess. Saves use optimistic locking.
5. Completion creates an immutable revision. Reopening makes the working record editable while retaining earlier completion revisions.

### Asynchronous reports

`InspectionService` saves a completion revision and an `outbox_event` in one transaction. `OutboxRelay` publishes pending events to `inspectfirst.inspection.events.v1` and retries failed sends.

In the report service, `KafkaEvents` receives events, `ReportEvents` deduplicates them by event ID and creates jobs, and `ReportWorker` triggers rendering. Reports use completion snapshots, so later guide edits do not change their contents. Deleting an inspection immediately blocks downloads; the deletion event subsequently cancels related jobs.

## Frontend code map

The application does not use Vue Router or Pinia. `App.vue` handles hash routes, session state, and public data loading. Individual components hold their own workflow state.

| File | Responsibility |
| --- | --- |
| [App.vue](frontend/src/App.vue) | Route dispatch, session restoration, catalogue loading, and page composition |
| [AccountPanel.vue](frontend/src/AccountPanel.vue) | Registration and login forms |
| [ProfileMenu.vue](frontend/src/ProfileMenu.vue), [UserLibrary.vue](frontend/src/UserLibrary.vue) | Account navigation, saved guides, and inspection lists |
| [StartInspection.vue](frontend/src/StartInspection.vue), [InspectionRecord.vue](frontend/src/InspectionRecord.vue) | Creation, result editing, attachments, completion, and history |
| [InspectionReports.vue](frontend/src/InspectionReports.vue) | Report status polling and downloads |
| [FeedbackPanel.vue](frontend/src/FeedbackPanel.vue) | Customer feedback and administrator replies |
| [AdminConsole.vue](frontend/src/AdminConsole.vue) | Navigation between the four management panels |
| [ContentManager.vue](frontend/src/ContentManager.vue), [GuideEditor.vue](frontend/src/GuideEditor.vue) | Categories, products, and structured guide editing |
| [AdminUsers.vue](frontend/src/AdminUsers.vue), [AdminInspections.vue](frontend/src/AdminInspections.vue) | User management, inspection lookup, and report jobs |
| [CheckExamples.vue](frontend/src/CheckExamples.vue), [ProductVisual.vue](frontend/src/ProductVisual.vue) | Check illustrations and product photos |
| [confirm.js](frontend/src/confirm.js), [ConfirmDialog.vue](frontend/src/ConfirmDialog.vue) | Unsaved-change confirmation |
| [style.css](frontend/src/style.css) | Shared styles and responsive layout |

## Backend code map

Service code is under `src/main/java/com/inspectfirst/`. Configuration and schema files are under `src/main/resources/`.

- **identity:** start with `SecurityConfig` and `AuthController`, then read `Accounts`, `AdminBootstrap`, and `UserManagement`. `ActiveAccountFilter` rejects disabled accounts.
- **guide:** `GuideController` handles public reads. `ContentController` and `ContentService` handle editing and publication. `CommunityController` and `CommunityService` handle favorites and feedback. Entities and `Repositories` define the catalogue model.
- **inspection:** `InspectionController` and `InspectionService` manage the record lifecycle. `AttachmentController` and `AttachmentService` handle photos. `OutboxRelay` publishes completion and deletion events.
- **report:** follow `KafkaEvents` → `ReportEvents` → `ReportWorker`, then read `ReportController`, `ReportRenderer`, and `PdfExport`.
- **shared:** `SessionAccess` delegates authentication, `Store` wraps JDBC and JSON operations, and `Errors` provides common error responses.

See [backend/API.md](backend/API.md) for request fields, limits, and state transitions. See [database/README.md](database/README.md) for tables and migration scripts.

## Run with Docker

The source includes a separate Docker Compose environment with Nginx, four backend services, MySQL, Kafka and the complete public catalogue. See [deploy/README.md](deploy/README.md) for setup, credentials, persistent data and verification status.

```sh
python3 deploy/configure.py
docker compose up --build -d --wait --wait-timeout 600
```

The Docker website uses `http://127.0.0.1:8080`. Native development remains on port 5173. Assignment Word documents are distributed separately from this codebase.

## Local development

Requirements: JDK 21, Maven, Node.js 22.12 or later, MySQL, and Kafka. The current development environment uses Node.js 24.

### Configuration

The existing local databases are initialized. Ordinary startup does not require schema initialization or MySQL recovery. For a new environment, follow the [database setup instructions](database/README.md).

Service properties are stored in `.local/identity-db.properties`, `guide-db.properties`, `inspection-db.properties`, and `report-db.properties`. The launcher loads these through `SPRING_CONFIG_ADDITIONAL_LOCATION`. Each service's `application.yml` defines its property names and defaults, including database credentials, initial administrator settings, and `KAFKA_SERVERS` for inspection and report services.

### Build and start

From the project root:

```sh
cd backend
mvn test package
bash Start-Backends.command
```

The launcher starts four services and a project-local Kafka broker. Logs are written to `.local/logs/`. Ctrl+C stops its child processes. The Kafka launcher reads `KAFKA_HOME` or uses the existing local distribution, with project ports 19092/19093. Required ports must be free before startup.

In another terminal, from the project root:

```sh
cd frontend
npm ci
npm run dev
```

Open `http://127.0.0.1:5173`. Administrators enter `/#/ops` after login. Rebuild and restart the affected service after backend changes; Vite updates the frontend during development.

## Testing

```sh
# In backend: module tests
mvn test

# In backend: requires MySQL, Kafka, services, and the frontend proxy
python3 tests/e2e.py

# In frontend
npm run format:check
npm run build
```

Module tests use test configuration and H2. The integration suite uses real HTTP, MySQL, and Kafka to cover access control, version conflicts, favorites, feedback, attachments, snapshots, reports, and disabled accounts. It creates labelled QA fixtures and archives or cleans up its test records.

## Catalogue and scope

The catalogue contains 45 products across nine categories and 179 checks. Product photos are mapped in `frontend/src/data/product-images.json`; check image metadata is stored in MySQL. See the [catalogue import guide](database/catalog-2026-11/README.md) and [check image guide](database/check-examples/README.md).

Guides support user observations rather than professional authentication. Illustrations do not depict a seller's actual item. Email verification, password recovery, and shared multi-instance sessions are not implemented. Docker deployment has not been verified.

Private configuration, backups, credentials, and user attachments under `.local/` are not source deliverables.
