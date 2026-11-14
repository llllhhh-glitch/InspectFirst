# Database

InspectFirst stores business records in MySQL. Four logical databases are owned by the identity, guide, inspection, and report services. Cross-service IDs are logical references passed through APIs or events; there are no cross-database foreign keys.

## Data model

### identity_db

| Table | Purpose |
| --- | --- |
| user_account | Email, password hash, display name, role, and account status |
| identity_audit | Authentication and user-management audit records |

Email addresses are unique. An administrator-slot constraint permits only one ADMIN. Sessions are managed by the identity service rather than these business tables.

### check_guide_db

The main relationship is `category → product → guide → guide_version → check_item`.

| Table | Purpose |
| --- | --- |
| category, product | Categories and products; brand/model pairs are unique |
| guide | Guide identity and availability, associated with a product or category |
| guide_version | Draft and published versions |
| check_item | Steps, expected observations, abnormal signs, and limitations |
| source_ref, check_item_source | References and their many-to-many links to checks |
| check_item_image | Image URL, description, type, and source metadata |
| saved_guide | Favorites, keyed by user and guide |
| feedback, feedback_reply | Feedback content, workflow status, and replies |
| audit_log | Content and feedback management audit records |

Images belong to checks within a specific version. Publishing a new version leaves old checks and images intact. Favorites refer to the guide identity so the UI can indicate updates or withdrawal.

### inspection_db

| Table | Purpose |
| --- | --- |
| inspection | Owner, guide snapshot, requirements, seller description, state, and lock version |
| inspection_item | Working results and notes |
| inspection_revision | Immutable completion snapshots |
| inspection_attachment | Photo metadata, hashes, and record/check associations |
| outbox_event | Events saved within the business transaction |
| audit_log | Inspection operation history |

Photo bytes are stored in `.local/attachments/`; MySQL stores content type, size, hash, and relationships. The attachment endpoint authorizes downloads before reading files. Photos are not served from a public static directory.

Completion revision numbers and request keys are unique within an inspection. Check order and source-check IDs are also unique within a record. Saves check the lock version. Reopening edits the working record while retaining completed snapshots.

### report_db

| Table | Purpose |
| --- | --- |
| report_aggregate | Inspection-level report state and deletion marker |
| inbox_event | Received event IDs for deduplication |
| report_job | Completion snapshot, job state, generated output, and retry data |

The inspection transaction writes a local outbox entry. Kafka delivery then creates a deduplicated job in a separate report-service transaction. No distributed database transaction is used. Deletion markers prevent delayed completion events from recreating reports for deleted inspections.

## SQL files

| File | Purpose |
| --- | --- |
| 00-initialize-all.sql | Create four databases and the initial Sony guide in a fresh environment |
| 01-schema.sql | Guide schema |
| 02-seed.sql | Initial guide data |
| 03-identity-schema.sql | Identity schema |
| 04-inspection-schema.sql | Inspection schema |
| 05-report-schema.sql | Report schema |
| 06-check-images.sql | Add check images to an existing guide database |

The corresponding service's `src/main/resources/schema.sql` is the source for schema copies. Initial guide data comes from `guide-service/src/main/resources/data.sql`. Schema changes require updates to initialization files and a migration for existing installations. Snapshot and event JSON fields use MEDIUMTEXT to hold complete guides.

`00-initialize-all.sql` does not contain the full 45-product catalogue. Import catalogue data and illustrations separately using the [catalogue tools](catalog-2026-11/README.md) and [image tools](check-examples/README.md).

## Fresh setup

1. Execute `00-initialize-all.sql` against a dedicated project MySQL environment.
2. Create one account per service, restricted to its own database. Identity needs SELECT/INSERT/UPDATE; the other services need SELECT/INSERT/UPDATE/DELETE.
3. Create `.local/*-db.properties` with each service's connection URL and credentials. Property names are defined in its `application.yml`.
4. Configure ADMIN_EMAIL and ADMIN_PASSWORD (12–128 characters) for initial administrator creation. Configure the Kafka address for inspection and report services.
5. Start the backend, then import the catalogue and images through administrator APIs.

Initialization SQL contains no real users, credentials, private inspections, or QA accounts. Application APIs handle ordinary data changes; import JSON files are not the runtime database.

## Existing installations and maintenance

- `Upgrade-Backend.command` calls `upgrade-backend.py` to back up and incrementally upgrade an existing installation and configure service accounts. This has already run on the current machine.
- `Setup-Accounts.command` and `setup-identity.py` support the earlier identity setup. The current four-service installation uses the upgrade workflow and current properties.
- `Recover-MySQL.command` and `recover-mysql-access.py` recover local MySQL access. They are not application startup steps.
- `create-database.sql` is an earlier setup file. Use `00-initialize-all.sql` for a fresh full installation.

Back up project databases before migration. Do not recreate databases to apply ordinary code changes. Product archival, guide withdrawal, and inspection soft deletion retain referenced history. Private properties, backups, and import progress belong under `.local/`, outside source delivery.
