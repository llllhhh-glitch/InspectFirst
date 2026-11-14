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
