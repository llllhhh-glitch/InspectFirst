#!/usr/bin/env python3
"""Remove only this test workflow's labelled catalogue clutter; preserve real project data."""
import json
import os
from pathlib import Path
import subprocess
import tempfile
from e2e import Client, ROOT


def main():
    admin, student = Client(), Client()
    from e2e import test_credentials
    admin_props, credentials = test_credentials()
    admin.login(admin_props['email'], admin_props['password'])
    student.login(credentials['email'], credentials['password'])
    products = [p for p in admin.call('GET', '/api/ops/content/products?q=Backend%20verification%20fixture&size=100')['items'] if p['brand'] == 'QA fixture' and p['name'] == 'Backend verification fixture - not a real inspection guide']
    product_ids = {p['id'] for p in products}
    guides = [g for g in admin.call('GET', '/api/ops/content/guides?size=100')['items'] if g['productId'] in product_ids]
    guide_ids = {g['id'] for g in guides}
    for guide in guides:
        if guide['availability'] != 'WITHDRAWN':
            admin.call('POST', '/api/ops/content/guides/' + guide['id'] + '/withdraw', {'reason': 'Automated software QA fixture, not product inspection advice.'})
    for product in products:
        admin.call('PUT', '/api/ops/content/products/' + product['id'], {key: product[key] for key in ['categoryId', 'brand', 'modelCode', 'name']} | {'status': 'ARCHIVED'})
    for row in student.call('GET', '/api/inspections?size=100')['items']:
        if row['productId'] in product_ids:
            student.call('DELETE', '/api/inspections/' + row['id'] + '?expectedVersion=' + str(row['lockVersion']), expected=204)
    for row in student.call('GET', '/api/saved-guides?size=100')['items']:
        if row['guideId'] in guide_ids:
            student.call('DELETE', '/api/saved-guides/' + row['guideId'], expected=204)
    for row in admin.call('GET', '/api/ops/feedback?size=100')['items']:
        if row['subject'] == 'Backend QA fixture feedback' and row['status'] != 'CLOSED':
            admin.call('PATCH', '/api/ops/feedback/' + row['id'] + '/status', {'expectedVersion': row['lockVersion'], 'status': 'CLOSED', 'reply': 'Automated software QA fixture archived.'})
    for row in admin.call('GET', '/api/ops/users?q=backend-qa-&size=100')['items']:
        if row['role'] == 'USER' and row['email'].startswith('backend-qa-') and row['email'].endswith('@example.test') and row['status'] != 'DISABLED':
            admin.call('PATCH', '/api/ops/users/' + row['id'] + '/status', {'status': 'DISABLED'})
    # Only disposable empty categories created by this specific acceptance script are removed.
    # Model/revision snapshots remain intact; archived fixture products move to the real camera category.
    sql = "START TRANSACTION;\nUPDATE product SET category_id='10000000-0000-0000-0000-000000000001' WHERE brand='QA fixture' AND name='Backend verification fixture - not a real inspection guide' AND status='ARCHIVED';\nDELETE FROM category WHERE name='Backend QA fixture - not buying advice' AND slug LIKE 'qa-%' AND NOT EXISTS(SELECT 1 FROM product p WHERE p.category_id=category.id) AND NOT EXISTS(SELECT 1 FROM guide g WHERE g.category_id=category.id);\nCOMMIT;\n"
    if os.environ.get('INSPECTFIRST_DB_MODE') == 'docker':
        command = ['docker', 'compose', 'exec', '-T', 'mysql', 'bash', '-c',
                   'MYSQL_PWD="$GUIDE_DB_PASSWORD" mysql -uinspectfirst_guide check_guide_db']
        result = subprocess.run(command, cwd=ROOT, input=sql, text=True, capture_output=True)
    else:
        from e2e import BASE
        if BASE.rstrip('/') != 'http://127.0.0.1:5173':
            raise RuntimeError('Refusing local database cleanup for a different API. Set INSPECTFIRST_DB_MODE=docker for Docker.')
        dbprops = dict(x.split('=', 1) for x in (ROOT / '.local/guide-db.properties').read_text().splitlines() if '=' in x)
        with tempfile.TemporaryDirectory(prefix='inspectfirst-qa-cleanup-') as folder:
            option = Path(folder) / 'client.cnf'
            fd = os.open(option, os.O_WRONLY | os.O_CREAT | os.O_EXCL, 0o600)
            with os.fdopen(fd, 'w') as stream:
                stream.write('[client]\nhost=127.0.0.1\nprotocol=tcp\nuser=' + dbprops['spring.datasource.username'] + '\npassword=' + dbprops['spring.datasource.password'] + '\n')
            result = subprocess.run(['/usr/local/mysql/bin/mysql', '--defaults-extra-file=' + str(option), 'check_guide_db'], input=sql, text=True, capture_output=True)
    if result.returncode:
        raise RuntimeError('Fixture-only category cleanup failed; no credentials printed.')
    admin.call('GET', '/api/ops/users/audit')
    assert not any(c['name'] == 'Backend QA fixture - not buying advice' for c in student.call('GET', '/api/categories'))
    student.call('POST', '/api/auth/logout', {}, expected=204)
    admin.call('POST', '/api/auth/logout', {}, expected=204)
    print('PASS: labelled QA products archived, feedback closed, records deleted, temporary users disabled, empty QA categories removed; real catalogue retained')


if __name__ == '__main__':
    main()
