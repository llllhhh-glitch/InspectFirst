-- Fresh-environment initialization only. Existing installations use Upgrade-Backend.command.
-- This file contains no real users, credentials, reports or QA fixtures.

CREATE DATABASE IF NOT EXISTS check_guide_db CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
USE check_guide_db;

CREATE TABLE IF NOT EXISTS category (
  id VARCHAR(36) PRIMARY KEY, slug VARCHAR(60) NOT NULL UNIQUE, name VARCHAR(80) NOT NULL
);
CREATE TABLE IF NOT EXISTS product (
  id VARCHAR(36) PRIMARY KEY, category_id VARCHAR(36) NOT NULL, brand VARCHAR(80) NOT NULL,
  model_code VARCHAR(80) NOT NULL, name VARCHAR(160) NOT NULL, status VARCHAR(16) NOT NULL,
  CONSTRAINT uq_product_brand_model UNIQUE(brand, model_code),
  CONSTRAINT fk_product_category FOREIGN KEY(category_id) REFERENCES category(id)
);
CREATE TABLE IF NOT EXISTS guide (
  id VARCHAR(36) PRIMARY KEY, product_id VARCHAR(36) UNIQUE, category_id VARCHAR(36) UNIQUE, withdrawal_reason VARCHAR(600), availability VARCHAR(16) NOT NULL,
  CONSTRAINT fk_guide_product FOREIGN KEY(product_id) REFERENCES product(id)
);
CREATE TABLE IF NOT EXISTS guide_version (
  id VARCHAR(36) PRIMARY KEY, guide_id VARCHAR(36) NOT NULL, version_no INT NOT NULL,
  status VARCHAR(16) NOT NULL, title VARCHAR(200) NOT NULL, tools VARCHAR(600) NOT NULL,
  reviewed_on DATE NOT NULL, content_note VARCHAR(1200) NOT NULL, edit_version BIGINT NOT NULL DEFAULT 0,
  CONSTRAINT uq_guide_version UNIQUE(guide_id, version_no),
  CONSTRAINT fk_version_guide FOREIGN KEY(guide_id) REFERENCES guide(id)
);
CREATE TABLE IF NOT EXISTS check_item (
  id VARCHAR(36) PRIMARY KEY, guide_version_id VARCHAR(36) NOT NULL, sort_order INT NOT NULL,
  title VARCHAR(200) NOT NULL, purpose VARCHAR(600) NOT NULL, method VARCHAR(2000) NOT NULL,
  expected_observation VARCHAR(1200) NOT NULL, abnormal_signs VARCHAR(1200) NOT NULL,
  limitations VARCHAR(1200) NOT NULL,
  CONSTRAINT uq_item_order UNIQUE(guide_version_id, sort_order),
  CONSTRAINT fk_item_version FOREIGN KEY(guide_version_id) REFERENCES guide_version(id)
);
CREATE TABLE IF NOT EXISTS source_ref (
  id VARCHAR(36) PRIMARY KEY, title VARCHAR(200) NOT NULL, url VARCHAR(1000) NOT NULL,
  publisher VARCHAR(80) NOT NULL
);
CREATE TABLE IF NOT EXISTS check_item_source (
  check_item_id VARCHAR(36) NOT NULL, source_ref_id VARCHAR(36) NOT NULL,
  PRIMARY KEY(check_item_id, source_ref_id),
  CONSTRAINT fk_citation_item FOREIGN KEY(check_item_id) REFERENCES check_item(id),
  CONSTRAINT fk_citation_source FOREIGN KEY(source_ref_id) REFERENCES source_ref(id)
);
-- Additive migration: preserves published guides and historical inspection snapshots.
CREATE TABLE IF NOT EXISTS check_item_image (
 id VARCHAR(36) PRIMARY KEY,
 check_item_id VARCHAR(36) NOT NULL,
 sort_order INT NOT NULL,
 url VARCHAR(1000) NOT NULL,
 alt VARCHAR(400) NOT NULL,
 caption VARCHAR(600) NOT NULL,
 kind VARCHAR(16) NOT NULL,
 source_url VARCHAR(1000) NOT NULL,
 source_title VARCHAR(200) NOT NULL,
 CONSTRAINT uq_check_image_order UNIQUE(check_item_id,sort_order),
 CONSTRAINT fk_check_image_item FOREIGN KEY(check_item_id) REFERENCES check_item(id) ON DELETE CASCADE
);

CREATE TABLE IF NOT EXISTS saved_guide (
 owner_user_id VARCHAR(36) NOT NULL, guide_id VARCHAR(36) NOT NULL, seen_version INT NOT NULL,
 created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
 PRIMARY KEY(owner_user_id,guide_id), FOREIGN KEY(guide_id) REFERENCES guide(id)
);
CREATE TABLE IF NOT EXISTS feedback (
 id VARCHAR(36) PRIMARY KEY, owner_user_id VARCHAR(36) NOT NULL, request_key VARCHAR(80) NOT NULL,
 type VARCHAR(24) NOT NULL, product_id VARCHAR(36), guide_version_id VARCHAR(36), check_item_id VARCHAR(36), request_payload TEXT NOT NULL, subject VARCHAR(160) NOT NULL,
 body TEXT NOT NULL, status VARCHAR(24) NOT NULL, lock_version BIGINT NOT NULL DEFAULT 0,
 created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP, updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
 UNIQUE(owner_user_id,request_key), FOREIGN KEY(product_id) REFERENCES product(id)
);
CREATE INDEX idx_feedback_owner ON feedback(owner_user_id,created_at);
CREATE TABLE IF NOT EXISTS feedback_reply (
 id VARCHAR(36) PRIMARY KEY, feedback_id VARCHAR(36) NOT NULL, actor_id VARCHAR(36) NOT NULL,
 actor_role VARCHAR(10) NOT NULL, body TEXT NOT NULL, created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
 FOREIGN KEY(feedback_id) REFERENCES feedback(id)
);
CREATE TABLE IF NOT EXISTS audit_log (
 id VARCHAR(36) PRIMARY KEY, actor_id VARCHAR(36) NOT NULL, action VARCHAR(80) NOT NULL,
 target_id VARCHAR(36) NOT NULL, details TEXT NOT NULL, created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE DATABASE IF NOT EXISTS identity_db CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
USE identity_db;

CREATE TABLE IF NOT EXISTS user_account (
  id VARCHAR(36) PRIMARY KEY,
  email VARCHAR(254) NOT NULL UNIQUE,
  display_name VARCHAR(60) NOT NULL,
  password_hash VARCHAR(255) NOT NULL,
  status VARCHAR(16) NOT NULL DEFAULT 'ACTIVE',
  role VARCHAR(10) NOT NULL,
  admin_slot SMALLINT UNIQUE,
  created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
  CONSTRAINT account_role_slot CHECK (
    (role = 'USER' AND admin_slot IS NULL) OR
    (role = 'ADMIN' AND admin_slot IS NOT NULL AND admin_slot = 1)
  )
);

CREATE TABLE IF NOT EXISTS identity_audit (
 id VARCHAR(36) PRIMARY KEY, actor_id VARCHAR(36) NOT NULL, action VARCHAR(80) NOT NULL,
 target_id VARCHAR(36) NOT NULL, created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE DATABASE IF NOT EXISTS inspection_db CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
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

CREATE DATABASE IF NOT EXISTS report_db CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
USE report_db;

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

USE check_guide_db;
-- Initial editorial checks based on linked operating documentation, not manufacturer inspection certification.
-- INSERT IGNORE makes initialization repeatable without overwriting existing rows.
INSERT IGNORE INTO category VALUES
 ('10000000-0000-0000-0000-000000000001','cameras','Cameras'),
 ('10000000-0000-0000-0000-000000000002','monitors','Monitors'),
 ('10000000-0000-0000-0000-000000000003','consoles','Consoles'),
 ('10000000-0000-0000-0000-000000000004','audio','Audio');
INSERT IGNORE INTO product VALUES
 ('20000000-0000-0000-0000-000000000001','10000000-0000-0000-0000-000000000001','Sony','ILCE-6400','Sony Alpha a6400','ACTIVE');
INSERT IGNORE INTO guide(id,product_id,availability) VALUES
 ('30000000-0000-0000-0000-000000000001','20000000-0000-0000-0000-000000000001','ACTIVE');
INSERT IGNORE INTO guide_version(id,guide_id,version_no,status,title,tools,reviewed_on,content_note) VALUES
 ('40000000-0000-0000-0000-000000000001','30000000-0000-0000-0000-000000000001',1,'PUBLISHED',
 'Basic receiving checks for the Sony ILCE-6400',
 'Compatible lens, charged battery and a known-working compatible memory card; permission to operate the item.',
 '2026-11-02','Initial editorial guide. Linked Sony documentation supports the operating information, not a manufacturer-approved second-hand grading standard. Observations do not establish authenticity, repair history or freedom from hidden faults.');
INSERT IGNORE INTO source_ref VALUES
 ('60000000-0000-0000-0000-000000000001','Sony ILCE-6400: Specifications','https://helpguide.sony.net/ilc/1810/v1/en/contents/TP0002280326.html','Sony'),
 ('60000000-0000-0000-0000-000000000002','Sony ILCE-6400: Memory cards that can be used','https://helpguide.sony.net/ilc/1810/v1/en/contents/TP0002264633.html','Sony'),
 ('60000000-0000-0000-0000-000000000003','Sony ILCE-6400: Playing back still images','https://helpguide.sony.net/ilc/1810/v1/en/contents/TP0002241285.html','Sony');
INSERT IGNORE INTO check_item VALUES
 ('50000000-0000-0000-0000-000000000001','40000000-0000-0000-0000-000000000001',1,
 'Confirm the item and promised accessories','Check whether the received item matches the purchase agreement.',
 'Compare the body identification and promised accessories with the listing.
Use the model-specific manual to identify the camera; do not assume a kit lens was included.',
 'The visible identifiers and included accessories match the agreement.',
 'Record any mismatch, missing accessory or identifier you cannot verify.',
 'This is an editorial comparison check. Specifications help identify the product; a label or matching specification alone does not prove authenticity.'),
 ('50000000-0000-0000-0000-000000000002','40000000-0000-0000-0000-000000000001',2,
 'Check memory-card compatibility','Avoid mistaking incompatible media for a camera fault.',
 'Identify the type of the card intended for a still-image test.
Compare it with the supported-card table in the linked manual. Do not format or erase a seller''s card.',
 'For still images, the manual lists SD, SDHC and SDXC support; smaller card formats need the appropriate adapter.',
 'An unsupported card or adapter makes the test inconclusive rather than proving a camera defect.',
 'Video modes have additional requirements. Card condition and recording settings may affect results; this check does not prove the slot works.'),
 ('50000000-0000-0000-0000-000000000003','40000000-0000-0000-0000-000000000001',3,
 'Review a photo captured on this camera','Try basic playback using an image created on the device.',
 'With permission, use a photo already captured on this camera.
Press Playback, then use the control wheel to select the photo. Note any recurring error.',
 'The selected image is displayed during this observation.',
 'Record a recurring error or failure to display the selected on-camera image.',
 'Images from other devices or database issues can affect playback. One successful observation is not proof that all functions work.');
INSERT IGNORE INTO check_item_source VALUES
 ('50000000-0000-0000-0000-000000000001','60000000-0000-0000-0000-000000000001'),
 ('50000000-0000-0000-0000-000000000002','60000000-0000-0000-0000-000000000002'),
 ('50000000-0000-0000-0000-000000000003','60000000-0000-0000-0000-000000000003');
