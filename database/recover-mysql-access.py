#!/usr/bin/env python3
"""User-run local recovery. Never run this automatically from an agent session.

Uses Oracle's init-file method. Does not use --skip-grant-tables or initialize data.
Stops the known launch daemon, verifies a cold backup, then asks MySQL to change
root@localhost using a password entered privately by the human at the terminal.
"""
import datetime
import getpass
import hashlib
import json
import os
from pathlib import Path
import plistlib
import pwd
import secrets
import shutil
import signal
import stat
import subprocess
import sys
import tempfile
import time

PROJECT = Path(__file__).resolve().parent.parent
PLIST = Path('/Library/LaunchDaemons/com.oracle.oss.mysql.mysqld.plist')
LABEL = 'com.oracle.oss.mysql.mysqld'
BASEDIR = Path('/usr/local/mysql')
DATADIR = BASEDIR / 'data'
MYSQL = BASEDIR / 'bin/mysql'
LAUNCHCTL = '/bin/launchctl'


def run(args, **kwargs):
    return subprocess.run([str(a) for a in args], capture_output=True, text=True, **kwargs)


def require(result, message):
    if result.returncode:
        raise RuntimeError(message)
    return result.stdout


def write_private(path, text, uid, gid):
    fd = os.open(str(path), os.O_WRONLY | os.O_CREAT | os.O_EXCL, 0o600)
    with os.fdopen(fd, 'w') as out:
        out.write(text)
    os.chown(path, uid, gid)
    os.chmod(path, 0o600)


def digest(path):
    h = hashlib.sha256()
    with path.open('rb') as stream:
        for block in iter(lambda: stream.read(1024 * 1024), b''):
            h.update(block)
    return h.hexdigest()


def tree_size(path):
    total = 0
    for item in path.rglob('*'):
        if item.is_symlink():
            raise RuntimeError('Data/keyring symlink found. Manual backup review is required; nothing has been reset.')
        if item.is_file():
            total += item.stat().st_size
    return total


def cold_copy(source, target):
    shutil.copytree(source, target, copy_function=shutil.copy2)
    manifest = []
    for item in source.rglob('*'):
        if item.is_file():
            copied = target / item.relative_to(source)
            original_hash = digest(item)
            if digest(copied) != original_hash:
                raise RuntimeError('Backup verification failed. Password reset has not started.')
            info = item.stat()
            manifest.append({'file': str(item.relative_to(source)), 'sha256': original_hash,
                             'uid': info.st_uid, 'gid': info.st_gid, 'mode': stat.S_IMODE(info.st_mode)})
    return manifest


def process_alive(pid):
    try:
        os.kill(pid, 0)
        return True
    except ProcessLookupError:
        return False


def main():
    if os.geteuid() != 0 or not os.environ.get('SUDO_UID'):
        raise RuntimeError('Open Recover-MySQL.command in Terminal. It requests local administrator authorization.')
    owner_uid, owner_gid = int(os.environ['SUDO_UID']), int(os.environ['SUDO_GID'])
    daemon = plistlib.loads(PLIST.read_bytes())
    args = daemon.get('ProgramArguments', [])
    if daemon.get('Label') != LABEL or '--datadir=/usr/local/mysql/data' not in args or '--user=_mysql' not in args:
        raise RuntimeError('The MySQL launch configuration differs from the inspected installation. Stopping for review.')
    if any(x.startswith(('--init-file', '--skip-grant-tables', '--defaults-file')) for x in args):
        raise RuntimeError('Unexpected MySQL recovery/default options found. Stopping for review.')
    require(run([LAUNCHCTL, 'print', 'system/' + LABEL]), 'The expected MySQL system service is not loaded. Review before proceeding.')
    mysql_user = pwd.getpwnam('_mysql')
    pid_file = DATADIR / 'mysqld.local.pid'
    original_pid = int(pid_file.read_text().strip())
    keyring = BASEDIR / 'keyring'
    size = tree_size(DATADIR) + (tree_size(keyring) if keyring.is_dir() else 0)
    backups = PROJECT / 'database/recovery-backups'
    backups.mkdir(mode=0o700, exist_ok=True)
    os.chmod(backups, 0o700)
    if shutil.disk_usage(backups).free < size * 1.3 + 200 * 1024 * 1024:
        raise RuntimeError('Not enough free space for a verified cold backup. Nothing has been reset.')

    print('MySQL access recovery — existing databases will be retained.', flush=True)
    print('This stops MySQL, verifies a cold backup, changes root@localhost, restores the service,', flush=True)
    print('then initializes only check_guide_db and creates a dedicated read-only application account.', flush=True)
    print('Other projects using the old root password will need their local configuration updated.', flush=True)
    print('The new root password is entered here only. Do not paste it into chat.', flush=True)
    new_password = getpass.getpass('Choose a NEW MySQL root password (12+ characters): ')
    repeat = getpass.getpass('Confirm the NEW MySQL root password: ')
    if new_password != repeat:
        raise RuntimeError('Passwords do not match. The service has not been stopped.')
    if len(new_password) < 12 or any(ord(c) < 32 or ord(c) == 127 for c in new_password):
        raise RuntimeError('Use at least 12 characters without control characters. The service has not been stopped.')
    repeat = None
    backup = backups / datetime.datetime.now().strftime('mysql-before-reset-%Y%m%d-%H%M%S')
    backup.mkdir(mode=0o700)
    recovery_dir = Path(tempfile.mkdtemp(prefix='inspectfirst-mysql-', dir='/private/tmp'))
    os.chown(recovery_dir, mysql_user.pw_uid, mysql_user.pw_gid)
    os.chmod(recovery_dir, 0o700)
    stopped = False
    temporary = None
    reset_verified = False
    normal_restored = False
    client_config = recovery_dir / 'client.cnf'
    recovery_socket = recovery_dir / 'recovery.sock'

    def sql(statement, normal=False):
        destination = ['--protocol=TCP', '--host=127.0.0.1', '--port=3306'] if normal else ['--protocol=SOCKET', '--socket=' + str(recovery_socket)]
        return run([MYSQL, '--defaults-file=' + str(client_config), *destination, '--batch', '--skip-column-names'], input=statement, timeout=25)

    try:
        print('[1/6] Stopping the existing MySQL service cleanly…', flush=True)
        require(run([LAUNCHCTL, 'bootout', 'system/' + LABEL]), 'Could not stop the MySQL service. Nothing has been reset.')
        stopped = True
        for _ in range(90):
            if not process_alive(original_pid):
                break
            time.sleep(1)
        else:
            raise RuntimeError('MySQL did not stop cleanly. No backup or password reset was attempted.')

        print('[2/6] Copying and SHA-256-verifying the stopped data directory…', flush=True)
        manifest = {'data': cold_copy(DATADIR, backup / 'data')}
        if keyring.is_dir():
            manifest['keyring'] = cold_copy(keyring, backup / 'keyring')
        shutil.copy2(PLIST, backup / PLIST.name)
        for config in (Path('/etc/my.cnf'), Path('/etc/mysql/my.cnf'), BASEDIR / 'my.cnf'):
            if config.is_file():
                shutil.copy2(config, backup / (str(config).strip('/').replace('/', '_')))
        (backup / 'manifest.json').write_text(json.dumps(manifest, indent=2))
        print('Verified private backup: ' + str(backup), flush=True)

        print('[3/6] Starting a temporary local-socket-only recovery server…', flush=True)
        init_file = recovery_dir / 'reset.sql'
        quoted = new_password.replace("'", "''")
        write_private(init_file, "SET SESSION sql_mode='NO_BACKSLASH_ESCAPES';\nALTER USER 'root'@'localhost' IDENTIFIED BY '" + quoted + "';\n", mysql_user.pw_uid, mysql_user.pw_gid)
        ini_password = new_password.replace('\\', '\\\\').replace('"', '\\"')
        write_private(client_config, '[client]\nuser=root\npassword="' + ini_password + '"\n', 0, 0)
        base_args = [x for x in args if not x.startswith(('--socket=', '--pid-file=', '--log-error='))]
        command = base_args + ['--skip-networking', '--mysqlx=OFF', '--socket=' + str(recovery_socket),
                               '--pid-file=' + str(recovery_dir / 'recovery.pid'),
                               '--log-error=' + str(recovery_dir / 'recovery.err'), '--init-file=' + str(init_file)]
        temporary = subprocess.Popen(command, stdout=subprocess.DEVNULL, stderr=subprocess.DEVNULL)
        for _ in range(60):
            if temporary.poll() is not None:
                raise RuntimeError('The recovery server stopped. The password change is unconfirmed; keep the backup for diagnosis.')
            if recovery_socket.exists():
                check = sql('SELECT CURRENT_USER();')
                if check.returncode == 0 and check.stdout.strip() == 'root@localhost':
                    reset_verified = True
                    break
            time.sleep(1)
        if not reset_verified:
            raise RuntimeError('Recovery authentication was not verified. Do not delete the backup.')
        init_file.unlink()
        print('[4/6] New root password verified; restoring normal service…', flush=True)
        temporary.send_signal(signal.SIGTERM)
        temporary.wait(timeout=90)
        require(run([LAUNCHCTL, 'bootstrap', 'system', PLIST]), 'Could not restore the normal launch daemon. See recovery instructions.')
        normal_restored = True
        stopped = False
        for _ in range(60):
            result = sql('SELECT CURRENT_USER();', normal=True)
            if result.returncode == 0:
                break
            time.sleep(1)
        else:
            raise RuntimeError('Root reset succeeded but normal TCP login was not verified. No project import was attempted.')

        print('[5/6] Importing only the InspectFirst guide database…', flush=True)
        initialization = (PROJECT / 'database/00-initialize-all.sql').read_text()
        require(sql(initialization, normal=True), 'Root reset succeeded, but the project SQL import failed. No existing course schema was targeted.')
        account = 'inspectfirst_g_' + secrets.token_hex(4)
        app_password = secrets.token_urlsafe(32)
        statement = "CREATE USER '" + account + "'@'localhost' IDENTIFIED BY '" + app_password + "';\nGRANT SELECT ON check_guide_db.* TO '" + account + "'@'localhost';\n"
        require(sql(statement, normal=True), 'Root reset and import succeeded, but application-account setup failed.')
        private_dir = PROJECT / '.local'
        private_dir.mkdir(mode=0o700, exist_ok=True)
        os.chown(private_dir, owner_uid, owner_gid)
        os.chmod(private_dir, 0o700)
        properties = private_dir / 'guide-db.properties'
        if properties.exists():
            raise RuntimeError('A local database config already exists. It was not overwritten; finish application setup manually.')
        write_private(properties, 'spring.datasource.url=jdbc:mysql://127.0.0.1:3306/check_guide_db?connectionTimeZone=UTC\nspring.datasource.username=' + account + '\nspring.datasource.password=' + app_password + '\n', owner_uid, owner_gid)
        counts = require(sql('SELECT COUNT(*) FROM check_guide_db.product; SELECT COUNT(*) FROM check_guide_db.check_item;', normal=True), 'Could not verify project row counts.')
        print('[6/6] Complete. Verified product/check counts:\n' + counts.strip(), flush=True)
        print('Existing course databases were not changed. Root password was not saved to the project.', flush=True)
        print('Application-only credentials: .local/guide-db.properties (private, git-ignored).', flush=True)
        print('Reconnect Workbench with the NEW root password you entered.', flush=True)
    finally:
        if temporary is not None and temporary.poll() is None:
            temporary.send_signal(signal.SIGTERM)
            try:
                temporary.wait(timeout=90)
            except subprocess.TimeoutExpired:
                print('Recovery server is still stopping; normal service was NOT started on the same data.', file=sys.stderr)
        safe_to_restart = temporary is None or temporary.poll() is not None
        if stopped and safe_to_restart and not normal_restored:
            restored = run([LAUNCHCTL, 'bootstrap', 'system', PLIST])
            print('Normal service restore attempt: ' + ('succeeded' if restored.returncode == 0 else 'needs local administrator attention'), flush=True)
        if safe_to_restart:
            shutil.rmtree(recovery_dir, ignore_errors=True)
        if reset_verified:
            print('Root password reset was verified. Keep your NEW password even if a later project step failed.', flush=True)
        new_password = None


if __name__ == '__main__':
    try:
        main()
    except (Exception, KeyboardInterrupt) as error:
        print('Stopped: ' + (str(error) or 'cancelled by the user'), file=sys.stderr)
        print('Do not reinitialize MySQL or delete any data directory. Keep any created backup.', file=sys.stderr)
        sys.exit(1)
