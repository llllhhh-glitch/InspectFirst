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
