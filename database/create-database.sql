-- Execute with an administrator account only on the intended local MySQL server.
-- This does not create users, set passwords, or access existing course databases.
CREATE DATABASE IF NOT EXISTS check_guide_db CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
