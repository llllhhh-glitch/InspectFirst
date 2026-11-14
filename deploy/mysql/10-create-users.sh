#!/bin/bash
set -eu
# MySQL entrypoint runs this only when the named data volume is empty.
# Passwords are generated as hex, so SQL interpolation cannot add SQL syntax.
for key in IDENTITY_DB_PASSWORD GUIDE_DB_PASSWORD INSPECTION_DB_PASSWORD REPORT_DB_PASSWORD; do
    value="${!key}"
    [[ "$value" =~ ^[a-f0-9]{48}$ ]] || { echo "Invalid generated database password: $key" >&2; exit 1; }
done
MYSQL_PWD="$MYSQL_ROOT_PASSWORD" mysql --protocol=socket -uroot <<SQL
CREATE USER 'inspectfirst_identity'@'%' IDENTIFIED BY '$IDENTITY_DB_PASSWORD';
GRANT SELECT, INSERT, UPDATE, DELETE ON identity_db.* TO 'inspectfirst_identity'@'%';
CREATE USER 'inspectfirst_guide'@'%' IDENTIFIED BY '$GUIDE_DB_PASSWORD';
GRANT SELECT, INSERT, UPDATE, DELETE ON check_guide_db.* TO 'inspectfirst_guide'@'%';
CREATE USER 'inspectfirst_inspection'@'%' IDENTIFIED BY '$INSPECTION_DB_PASSWORD';
GRANT SELECT, INSERT, UPDATE, DELETE ON inspection_db.* TO 'inspectfirst_inspection'@'%';
CREATE USER 'inspectfirst_report'@'%' IDENTIFIED BY '$REPORT_DB_PASSWORD';
GRANT SELECT, INSERT, UPDATE, DELETE ON report_db.* TO 'inspectfirst_report'@'%';
SQL
