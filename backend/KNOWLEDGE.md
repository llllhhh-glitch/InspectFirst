# Course concepts used in InspectFirst

This maps implemented code to the Spring Boot / MySQL / Kafka topics already discussed in this project. It is not a claim that every item was explicitly required by the lecturer; confirm against the original slides when preparing the final presentation.

| Concept | Actual implementation | Demo explanation |
|---|---|---|
| HTTP and REST | Resource-oriented controllers, GET/POST/PUT/PATCH/DELETE, status codes, JSON DTOs | Query a public guide, create/save an inspection, inspect a 409 conflict |
| MVC and dependency injection | Controller → service → repository/JdbcTemplate; constructor injection | Show InspectionController and InspectionService |
| Spring Data JPA | Existing Category/Product/Guide/GuideVersion/CheckItem/SourceRef mappings and published-product queries | Explain entity relationships and why entities are converted to DTOs |
| JDBC and relational modeling | Prepared SQL, foreign keys, unique keys, role constraints and owned databases | Show user uniqueness, saved-guide composite key and revision uniqueness |
| MySQL transactions | Guide save/publish, feedback state changes and inspection completion each commit atomically | Completion inserts revision and outbox in the same local transaction |
| Concurrency | FOR UPDATE where necessary; expectedVersion/lockVersion conflict checks | Submit an old save; server returns 409 rather than overwriting newer results |
| Session authentication and authorization | Spring Security login, PBKDF2, HttpOnly cookie, CSRF, ADMIN routes and record ownership | Ordinary user receives 403 for admin, 404 for another user's record |
| Service-per-database boundary | Identity, guide, inspection and report each own one MySQL logical database/account | No cross-database JOINs; inspection takes a guide snapshot over HTTP |
| Synchronous service calls | RestClient for identity validation, guide capture and live report-access check | Dependency outage rejects private action without creating half a record |
| Kafka producer/consumer | OutboxRelay sends keyed events; KafkaEvents consumes into report inbox/job | Inspect completion while report arrives asynchronously |
| Reliable async processing | Stable event IDs, outbox retries, inbox dedup, unique revision job and deletion tombstone | Stop local Kafka, complete inspection, restart and see queued report generated |
| Scheduling | Separate outbox sender and report-render worker | Kafka listener commits a job quickly; rendering is not inside the consumer transaction |
| File upload and access control | Size/type signatures, private storage, integrity hash, owner checks and admin read audit | A second student cannot download someone else's photo |
| Automated verification | Spring Boot tests + isolated H2, plus real MySQL/Kafka acceptance script | Explain why H2 tests are not a replacement for real database verification |

## Key design decisions worth explaining

- A guide is platform-authored reference content, not a user marketplace listing. Product rows represent models, not individual physical items.
- Published guide versions and completed inspection revisions are immutable. Snapshots keep past observations tied to the instructions actually used.
- UNCHECKED and UNABLE_TO_ASSESS are separate from NO_ISSUE_OBSERVED. There is no invented overall quality/authenticity score.
- Saving feedback/favorites/results is synchronous. Reporting is asynchronous because its delay must not stop the user completing inspection.
- MySQL and Kafka are not one atomic transaction. Outbox plus idempotent consumption gives recoverable at-least-once delivery; it is not an exactly-once or distributed-transaction claim.
- ADMIN is one account. Staff access to user inspection detail/photos is audited. The frontend should disclose staff-review access before users submit private records.
- Local Kafka is separate from other course exercises, with project-only data and ports 19092/19093.

References: [Spring transactions](https://docs.spring.io/spring-framework/reference/data-access/transaction/declarative/annotations.html), [Spring Kafka producer](https://docs.spring.io/spring-kafka/reference/kafka/sending-messages.html), [Spring REST clients](https://docs.spring.io/spring-framework/reference/integration/rest-clients.html), [Spring Security](https://docs.spring.io/spring-security/reference/servlet/authentication/passwords/form.html).
