USE inspection_db;
CREATE TABLE IF NOT EXISTS inspection (
 id VARCHAR(36) PRIMARY KEY,owner_user_id VARCHAR(36) NOT NULL,product_id VARCHAR(36),category_id VARCHAR(36),
 guide_version_id VARCHAR(36) NOT NULL,guide_snapshot MEDIUMTEXT NOT NULL,user_requirement MEDIUMTEXT NOT NULL,seller_claim MEDIUMTEXT NOT NULL,
 status VARCHAR(16) NOT NULL,lock_version BIGINT NOT NULL DEFAULT 0,latest_revision_no INT NOT NULL DEFAULT 0,
 request_key VARCHAR(80) NOT NULL,request_payload MEDIUMTEXT NOT NULL,created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
 updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,deleted_at TIMESTAMP NULL,
 UNIQUE(owner_user_id,request_key)
);
CREATE INDEX idx_inspection_owner ON inspection(owner_user_id,updated_at);
CREATE TABLE IF NOT EXISTS inspection_item (
 id VARCHAR(36) PRIMARY KEY,inspection_id VARCHAR(36) NOT NULL,source_item_id VARCHAR(36) NOT NULL,
 sort_order INT NOT NULL,content_snapshot MEDIUMTEXT NOT NULL,result_status VARCHAR(32) NOT NULL,note VARCHAR(3000) NOT NULL,
 FOREIGN KEY(inspection_id) REFERENCES inspection(id),UNIQUE(inspection_id,sort_order),UNIQUE(inspection_id,source_item_id)
);
CREATE TABLE IF NOT EXISTS inspection_revision (
 id VARCHAR(36) PRIMARY KEY,inspection_id VARCHAR(36) NOT NULL,revision_no INT NOT NULL,snapshot_json MEDIUMTEXT NOT NULL,
 snapshot_hash VARCHAR(64) NOT NULL,request_key VARCHAR(80) NOT NULL,created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
 FOREIGN KEY(inspection_id) REFERENCES inspection(id),UNIQUE(inspection_id,revision_no),UNIQUE(inspection_id,request_key)
);
CREATE TABLE IF NOT EXISTS outbox_event (
 id VARCHAR(36) PRIMARY KEY,aggregate_id VARCHAR(36) NOT NULL,event_type VARCHAR(32) NOT NULL,payload MEDIUMTEXT NOT NULL,
 status VARCHAR(16) NOT NULL DEFAULT 'PENDING',attempts INT NOT NULL DEFAULT 0,
 available_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
 published_at TIMESTAMP NULL
);
CREATE TABLE IF NOT EXISTS inspection_attachment (
 id VARCHAR(36) PRIMARY KEY,inspection_id VARCHAR(36) NOT NULL,item_id VARCHAR(36) NOT NULL,content_type VARCHAR(40) NOT NULL,
 size_bytes BIGINT NOT NULL,sha256 VARCHAR(64) NOT NULL,created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
 FOREIGN KEY(inspection_id) REFERENCES inspection(id),FOREIGN KEY(item_id) REFERENCES inspection_item(id)
);
CREATE TABLE IF NOT EXISTS audit_log (
 id VARCHAR(36) PRIMARY KEY,actor_id VARCHAR(36) NOT NULL,action VARCHAR(80) NOT NULL,target_id VARCHAR(36) NOT NULL,
 details MEDIUMTEXT NOT NULL,created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);
