CREATE TABLE IF NOT EXISTS report_aggregate (
 inspection_id VARCHAR(36) PRIMARY KEY,deleted_flag BOOLEAN NOT NULL DEFAULT FALSE
);
CREATE TABLE IF NOT EXISTS inbox_event (
 event_id VARCHAR(36) PRIMARY KEY,created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);
CREATE TABLE IF NOT EXISTS report_job (
 id VARCHAR(36) PRIMARY KEY,inspection_id VARCHAR(36) NOT NULL,revision_id VARCHAR(36) NOT NULL UNIQUE,
 revision_no INT NOT NULL,owner_user_id VARCHAR(36) NOT NULL,status VARCHAR(16) NOT NULL,
 snapshot_json MEDIUMTEXT,snapshot_hash VARCHAR(64) NOT NULL,report_html MEDIUMTEXT,
 attempt_count INT NOT NULL DEFAULT 0,error_code VARCHAR(80),
 created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,finished_at TIMESTAMP NULL,
 FOREIGN KEY(inspection_id) REFERENCES report_aggregate(inspection_id)
);
