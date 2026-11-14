#!/usr/bin/env python3
"""Additive project-only migration. Keeps existing accounts and published guide data."""
import getpass
import os
from pathlib import Path
import secrets
import subprocess
import tempfile
import datetime
import shutil
import re

ROOT = Path(__file__).resolve().parent.parent
LOCAL = ROOT / '.local'
MYSQL = '/usr/local/mysql/bin/mysql'


def private_write(path, text):
    temp = path.with_name(path.name + '.tmp-' + secrets.token_hex(4))
    fd = os.open(temp, os.O_WRONLY | os.O_CREAT | os.O_EXCL, 0o600)
    with os.fdopen(fd, 'w') as stream:
        stream.write(text)
    os.replace(temp, path)


def main():
    LOCAL.mkdir(exist_ok=True, mode=0o700)
    marker = LOCAL / 'backend-upgrade-v1.done'
    if marker.exists():
        print('Backend migration already completed. Existing credentials were retained.')
        return
    print('This migrates only InspectFirst databases. It does not reset MySQL or touch course databases.')
    password = getpass.getpass('Existing MySQL root password (local entry only): ')
    if any(c in password for c in '\r\n\x00'):
        raise RuntimeError('Unsupported control character in password.')
    escaped = password.replace('\\', '\\\\').replace('"', '\\"')
    with tempfile.TemporaryDirectory(prefix='inspectfirst-backend-') as folder:
        option = Path(folder) / 'client.cnf'
        private_write(option, f'[client]\nhost=127.0.0.1\nprotocol=tcp\nuser=root\npassword="{escaped}"\n')

        def sql(command):
            result = subprocess.run([MYSQL, f'--defaults-extra-file={option}', '--batch', '--skip-column-names'], input=command, text=True, capture_output=True)
            if result.returncode:
                # Never echo a SQL statement that may include generated credentials.
                match = re.search(r'ERROR (\d+)', result.stderr)
                raise RuntimeError('MySQL migration stopped' + (' (error ' + match.group(1) + ')' if match else '') + '. Keep the backup; do not reset MySQL.')
            return result.stdout.strip()

        names = ['check_guide_db', 'identity_db', 'inspection_db', 'report_db']
        existing = sql("SELECT SCHEMA_NAME FROM information_schema.SCHEMATA WHERE SCHEMA_NAME IN ('check_guide_db','identity_db','inspection_db','report_db');").splitlines()
        backup = LOCAL / 'database-backups' / datetime.datetime.now().strftime('%Y%m%d-%H%M%S')
        backup.mkdir(parents=True, mode=0o700)
        os.chmod(backup, 0o700)
        if existing:
            dump = backup / 'before-backend-upgrade.sql'
            fd = os.open(dump, os.O_WRONLY | os.O_CREAT | os.O_EXCL, 0o600)
            with os.fdopen(fd, 'wb') as stream:
                result = subprocess.run(['/usr/local/mysql/bin/mysqldump', f'--defaults-extra-file={option}', '--single-transaction', '--no-tablespaces', '--set-gtid-purged=OFF', '--databases', *existing], stdout=stream, stderr=subprocess.PIPE)
            if result.returncode:
                raise RuntimeError('Project backup failed. Migration was not started.')
        for db in names:
            sql(f'CREATE DATABASE IF NOT EXISTS {db} CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;')

        def column(db, table, name, definition):
            exists = sql(f"SELECT COUNT(*) FROM information_schema.COLUMNS WHERE TABLE_SCHEMA='{db}' AND TABLE_NAME='{table}' AND COLUMN_NAME='{name}';")
            if exists == '0':
                sql(f'ALTER TABLE {db}.{table} ADD COLUMN {name} {definition};')

        # Existing two services have already been initialized on this computer.
        sql('ALTER TABLE check_guide_db.guide MODIFY product_id VARCHAR(36) NULL;')
        column('check_guide_db', 'guide', 'category_id', 'VARCHAR(36) UNIQUE')
        column('check_guide_db', 'guide', 'withdrawal_reason', 'VARCHAR(600)')
        column('check_guide_db', 'guide_version', 'edit_version', 'BIGINT NOT NULL DEFAULT 0')
        column('identity_db', 'user_account', 'status', "VARCHAR(16) NOT NULL DEFAULT 'ACTIVE'")

        configs = []
        for service, db in zip(['guide', 'identity', 'inspection', 'report'], names):
            schema = (ROOT / 'backend' / (service + '-service') / 'src/main/resources/schema.sql').read_text()
            for statement in schema.split(';'):
                statement = statement.strip()
                if not statement:
                    continue
                index = re.match(r'CREATE INDEX (\w+) ON (\w+)', statement, re.I)
                if index:
                    present = sql(f"SELECT COUNT(*) FROM information_schema.STATISTICS WHERE TABLE_SCHEMA='{db}' AND TABLE_NAME='{index[2]}' AND INDEX_NAME='{index[1]}';")
                    if present != '0':
                        continue
                sql(f'USE {db};\n' + statement + ';')
            username = 'if_' + service[:4] + '_' + secrets.token_hex(4)
            secret = secrets.token_hex(24)
            permissions = 'SELECT, INSERT, UPDATE' if service == 'identity' else 'SELECT, INSERT, UPDATE, DELETE'
            sql(f"CREATE USER '{username}'@'localhost' IDENTIFIED BY '{secret}'; GRANT {permissions} ON {db}.* TO '{username}'@'localhost';")
            config = LOCAL / (service + '-db.properties')
            old = {}
            if config.exists():
                old = dict(line.split('=', 1) for line in config.read_text().splitlines() if '=' in line)
                shutil.copy2(config, backup / config.name)
                os.chmod(backup / config.name, 0o600)
            old.update({'spring.datasource.url': f'jdbc:mysql://127.0.0.1:3306/{db}?connectionTimeZone=UTC', 'spring.datasource.username': username, 'spring.datasource.password': secret})
            if service in ['inspection', 'report']:
                old['spring.kafka.bootstrap-servers'] = '127.0.0.1:19092'
            if service == 'inspection':
                old['inspectfirst.attachments'] = str(LOCAL / 'attachments')
            configs.append((config, ''.join(key + '=' + value + '\n' for key, value in old.items())))
        for path, text in configs:
            private_write(path, text)
        private_write(marker, 'Additive backend migration completed. Backup: ' + str(backup) + '\n')
    print('Backend databases and four private service accounts are ready.')
    print('Existing administrator and student accounts were preserved. No password needs to be sent to chat.')
    print('Tell Codex: backend migration completed. The application services need restarting.')


if __name__ == '__main__':
    try:
        main()
    except (Exception, KeyboardInterrupt) as error:
        print(str(error) if isinstance(error, RuntimeError) else 'Migration stopped. Keep all backup files and existing databases.')
        raise SystemExit(1)
