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
