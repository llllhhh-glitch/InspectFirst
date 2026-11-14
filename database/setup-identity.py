#!/usr/bin/env python3
"""Initialize only InspectFirst's identity database; never reset MySQL or other projects."""
import getpass
import os
from pathlib import Path
import secrets
import subprocess
import tempfile

ROOT = Path(__file__).resolve().parent.parent
LOCAL = ROOT / '.local'
MYSQL = '/usr/local/mysql/bin/mysql'


def private_write(path, text):
    fd = os.open(str(path), os.O_WRONLY | os.O_CREAT | os.O_EXCL, 0o600)
    with os.fdopen(fd, 'w') as stream:
        stream.write(text)


def main():
    LOCAL.mkdir(mode=0o700, exist_ok=True)
    config = LOCAL / 'identity-db.properties'
    login = LOCAL / 'administrator-login.txt'
    if config.exists() or login.exists():
        print('Identity configuration already exists. Nothing was changed.')
        print('Use backend/Start-Identity.command. Do not repeat MySQL recovery.')
        return
    print('This adds identity_db and a dedicated account. Existing course databases are retained.')
    print('Enter your existing MySQL root password here only. No password needs to be sent to chat.')
    password = getpass.getpass('MySQL root password: ')
    # MySQL option-file quoting is different from shell quoting.
    escaped = password.replace('\\', '\\\\').replace('"', '\\"')
    if '\n' in password or '\r' in password or '\x00' in password:
        raise ValueError('Unsupported control character in password.')
    username = 'inspectfirst_i_' + secrets.token_hex(4)
    db_password = secrets.token_hex(24)
    admin_password = secrets.token_urlsafe(20)
    schema = (ROOT / 'database' / '03-identity-schema.sql').read_text()
    sql = f"""CREATE DATABASE IF NOT EXISTS identity_db CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
USE identity_db;
{schema}
CREATE USER '{username}'@'localhost' IDENTIFIED BY '{db_password}';
GRANT SELECT, INSERT ON identity_db.* TO '{username}'@'localhost';
"""
    with tempfile.TemporaryDirectory(prefix='inspectfirst-identity-') as directory:
        options = Path(directory) / 'client.cnf'
        private_write(options, f'[client]\nuser=root\npassword="{escaped}"\nhost=127.0.0.1\nprotocol=tcp\n')
        result = subprocess.run([MYSQL, f'--defaults-extra-file={options}'], input=sql, text=True, capture_output=True)
        if result.returncode:
            raise RuntimeError('MySQL setup failed. Check the local root password and service. No existing data was deleted.')
    private_write(config,
        'spring.datasource.url=jdbc:mysql://127.0.0.1:3306/identity_db?connectionTimeZone=UTC\n'
        f'spring.datasource.username={username}\nspring.datasource.password={db_password}\n'
        f'inspectfirst.admin.email=admin@inspectfirst.local\ninspectfirst.admin.password={admin_password}\n')
    private_write(login,
        'InspectFirst — private administrator login\n\n'
        'Email: admin@inspectfirst.local\n'
        f'Password: {admin_password}\n\n'
        'Log in through http://127.0.0.1:5173/#login\n'
        'The administrator is created on the first successful identity-service startup.\n'
        'Keep this file private. Do not commit it or send its contents to chat.\n')
    print('Identity database and private configuration are ready.')
    print('The single administrator login is in InspectFirst/.local/administrator-login.txt.')
    print('Start backend/Start-Identity.command, then open the website.')


if __name__ == '__main__':
    try:
        main()
    except (Exception, KeyboardInterrupt) as error:
        print(str(error) if isinstance(error, (ValueError, RuntimeError)) else 'Setup stopped. Keep any existing configuration and contact the developer.')
        raise SystemExit(1)
