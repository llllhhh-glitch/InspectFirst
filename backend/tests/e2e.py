#!/usr/bin/env python3
"""Real local MySQL + Kafka acceptance. Only creates labelled verification fixtures.
No passwords are printed. Run after all four services and the Vite proxy are running.
"""
import base64
import http.cookiejar
import json
import os
from pathlib import Path
import secrets
import time
import urllib.error
import urllib.parse
import urllib.request

ROOT = Path(__file__).resolve().parents[2]
BASE = os.environ.get('INSPECTFIRST_URL', 'http://127.0.0.1:5173')
ARTIFACT = Path(os.environ.get('INSPECTFIRST_QA_DIR', str(ROOT / '.local/qa')))
ARTIFACT.mkdir(parents=True, exist_ok=True)
checks = []


class Client:
    def __init__(self):
        self.open = urllib.request.build_opener(urllib.request.HTTPCookieProcessor(http.cookiejar.CookieJar()))

    def call(self, method, path, data=None, expected=200, csrf=True, form=False, raw=None, content_type=None):
        headers = {'Accept': 'application/json'}
        if method not in ['GET', 'HEAD'] and csrf:
            token = self.call('GET', '/api/auth/csrf')
            headers[token['headerName']] = token['token']
        if data is not None:
            headers['Content-Type'] = 'application/x-www-form-urlencoded' if form else 'application/json'
            raw = (urllib.parse.urlencode(data) if form else json.dumps(data)).encode()
        if content_type:
            headers['Content-Type'] = content_type
        request = urllib.request.Request(BASE + path, data=raw, headers=headers, method=method)
        try:
            response = self.open.open(request, timeout=30)
        except urllib.error.HTTPError as error:
            response = error
        body = response.read()
        if response.status != expected:
            # Responses contain no submitted passwords; never print request bodies.
            raise AssertionError(f'{method} {path}: expected {expected}, got {response.status}: {body[:250]!r}')
        if not body:
            return None
        if 'json' in response.headers.get('Content-Type', ''):
            return json.loads(body)
        return body

    def login(self, email, password):
        return self.call('POST', '/api/auth/login', {'email': email, 'password': password}, form=True)


def passed(name):
    checks.append(name)
    print('PASS:', name, flush=True)


def test_credentials():
    if os.environ.get('INSPECTFIRST_TEST_ADMIN_EMAIL'):
        return ({'email': os.environ['INSPECTFIRST_TEST_ADMIN_EMAIL'], 'password': os.environ['INSPECTFIRST_TEST_ADMIN_PASSWORD']},
                {'email': os.environ['INSPECTFIRST_TEST_USER_EMAIL'], 'password': os.environ['INSPECTFIRST_TEST_USER_PASSWORD']})
    props = dict(line.split('=', 1) for line in (ROOT / '.local/identity-db.properties').read_text().splitlines() if '=' in line)
    return ({'email': props['inspectfirst.admin.email'], 'password': props['inspectfirst.admin.password']},
            json.loads((ROOT / '.local/student-test-login.json').read_text()))


def main():
    admin_credentials, student = test_credentials()
    admin, a, b = Client(), Client(), Client()
    admin_user = admin.login(admin_credentials['email'], admin_credentials['password'])
    student_user = a.login(student['email'], student['password'])
    suffix = secrets.token_hex(5)
    other_email = 'backend-qa-' + suffix + '@example.test'
    other_password = secrets.token_urlsafe(20)
    b.call('POST', '/api/auth/register', {'email': other_email, 'password': other_password, 'displayName': 'Backend QA fixture'}, expected=201)
    other_user = b.login(other_email, other_password)
    a.call('GET', '/api/ops/users', expected=403)
    anonymous = Client()
    anonymous.call('GET', '/api/saved-guides', expected=401)
    passed('anonymous and USER cannot access protected or admin resources')

    slug = 'qa-' + suffix.translate(str.maketrans('0123456789', 'abcdefghij'))
    category = admin.call('POST', '/api/ops/content/categories', {'slug': slug, 'name': 'Backend QA fixture - not buying advice'})
    product = admin.call('POST', '/api/ops/content/products', {'categoryId': category['id'], 'brand': 'QA fixture', 'modelCode': suffix, 'name': 'Backend verification fixture - not a real inspection guide', 'status': 'ACTIVE'})
    version = admin.call('POST', '/api/ops/content/guides', {'productId': product['id']})
    guide_id, version_id = version['id'], version['versionId']
    anonymous.call('GET', '/api/products/' + product['id'] + '/guide', expected=404)
    items = [{'title': 'Verification fixture check ' + str(i), 'purpose': 'Exercise backend data flow only.', 'steps': ['This is a software test fixture, not product advice.'], 'expectedObservation': 'A persisted result.', 'abnormalSigns': 'Missing data.', 'limitations': 'Not an inspection standard.', 'sources': [{'title': 'Reserved example documentation URL - fixture only', 'url': 'https://example.com/fixture', 'publisher': 'Backend QA'}]} for i in range(1, 5)]
    draft = {'expectedVersion': 0, 'title': 'Software test fixture only', 'tools': 'No real device required', 'reviewedOn': '2026-11-02', 'contentNote': 'Backend acceptance fixture. Not buying advice.', 'items': items}
    admin.call('PUT', '/api/ops/content/versions/' + version_id, draft)
    admin.call('PUT', '/api/ops/content/versions/' + version_id, draft, expected=409)
    admin.call('POST', '/api/ops/content/versions/' + version_id + '/publish', {'expectedVersion': 1})
    admin.call('PUT', '/api/ops/content/versions/' + version_id, {**draft, 'expectedVersion': 2}, expected=409)
    guide = anonymous.call('GET', '/api/products/' + product['id'] + '/guide')
    passed('draft visibility, optimistic locking, sourced publication and immutable versions')

    a.call('PUT', '/api/saved-guides/' + guide_id)
    a.call('PUT', '/api/saved-guides/' + guide_id)
    assert len([x for x in a.call('GET', '/api/saved-guides')['items'] if x['guideId'] == guide_id]) == 1
    assert not b.call('GET', '/api/saved-guides')['items']
    passed('saved guides are idempotent and isolated by account')

    create = {'productId': product['id'], 'guideVersionId': version_id, 'requestKey': secrets.token_hex(12), 'userRequirement': 'QA purchase requirement 中文验货要求', 'sellerClaim': 'QA seller claim'}
    inspection = a.call('POST', '/api/inspections', create)
    inspection_id = inspection['id']
    assert a.call('POST', '/api/inspections', create)['id'] == inspection_id
    b.call('GET', '/api/inspections/' + inspection_id, expected=404)
    results = [{'itemId': row['id'], 'status': status, 'note': '<script>fixture only</script>' if n == 0 else 'QA note'} for n, (row, status) in enumerate(zip(inspection['items'], ['ISSUE_OBSERVED', 'UNABLE_TO_ASSESS', 'NO_ISSUE_OBSERVED']))]
    save = {'expectedVersion': 0, 'userRequirement': create['userRequirement'], 'sellerClaim': create['sellerClaim'], 'results': results}
    inspection = a.call('PUT', '/api/inspections/' + inspection_id, save)
    a.call('PUT', '/api/inspections/' + inspection_id, save, expected=409)
    for status in ['ISSUE_OBSERVED', 'UNABLE_TO_ASSESS', 'NO_ISSUE_OBSERVED', 'UNCHECKED']:
        assert inspection['summary'][status] == 1
    passed('inspection retry, ownership, persistence, stale-save rejection and four-state summary')

    # Minimal PNG: attachments are served as downloads, never interpreted as HTML.
    image = base64.b64decode('iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAQAAAC1HAwCAAAAC0lEQVR42mP8/x8AAwMCAO+aF9sAAAAASUVORK5CYII=')
    boundary = 'InspectFirstBoundary' + secrets.token_hex(8)
    raw = ('--' + boundary + '\r\nContent-Disposition: form-data; name="file"; filename="fixture.png"\r\nContent-Type: image/png\r\n\r\n').encode() + image + ('\r\n--' + boundary + '--\r\n').encode()
    attachment = a.call('POST', '/api/inspections/' + inspection_id + '/items/' + inspection['items'][0]['id'] + '/attachments?expectedVersion=1', raw=raw, content_type='multipart/form-data; boundary=' + boundary)
    assert a.call('GET', '/api/attachments/' + attachment['id']) == image
    b.call('GET', '/api/attachments/' + attachment['id'], expected=404)
    passed('photo persistence and owner-only download')

    feedback_input = {'requestKey': secrets.token_hex(12), 'type': 'INACCURATE', 'productId': product['id'], 'guideVersionId': version_id, 'checkItemId': guide['items'][0]['id'], 'subject': 'Backend QA fixture feedback', 'body': 'Software verification request; no real product complaint.'}
    feedback = a.call('POST', '/api/feedback', feedback_input)
    feedback_id = feedback['id']
    assert a.call('POST', '/api/feedback', feedback_input)['id'] == feedback_id
    b.call('GET', '/api/feedback/' + feedback_id, expected=404)
    admin.call('PATCH', '/api/ops/feedback/' + feedback_id + '/status', {'expectedVersion': 0, 'status': 'IN_REVIEW', 'reply': 'QA review'})
    admin.call('PATCH', '/api/ops/feedback/' + feedback_id + '/status', {'expectedVersion': 1, 'status': 'NEED_DETAILS', 'reply': 'QA question'})
    a.call('POST', '/api/feedback/' + feedback_id + '/replies', {'expectedVersion': 2, 'body': 'QA detail'})
    admin.call('PATCH', '/api/ops/feedback/' + feedback_id + '/status', {'expectedVersion': 3, 'status': 'RESOLVED', 'reply': 'QA verified'})
    assert a.call('GET', '/api/feedback/' + feedback_id)['status'] == 'RESOLVED'
    passed('feedback deduplication, ownership, status transitions and public replies')

    complete = {'expectedVersion': 2, 'requestKey': secrets.token_hex(12)}
    revision = a.call('POST', '/api/inspections/' + inspection_id + '/complete', complete)
    assert a.call('POST', '/api/inspections/' + inspection_id + '/complete', complete)['id'] == revision['id']
    report = None
    for _ in range(50):
        matches = [r for r in a.call('GET', '/api/reports')['items'] if r['revisionId'] == revision['id']]
        if matches and matches[0]['status'] == 'READY':
            report = matches[0]
            break
        time.sleep(0.7)
    assert report is not None, 'Kafka-to-report job did not become READY'
    html = a.call('GET', '/api/reports/' + report['id'] + '/download').decode()
    assert '<script>' not in html and '&lt;script&gt;' in html
    b.call('GET', '/api/reports/' + report['id'] + '/download', expected=404)
    pdf = a.call('GET', '/api/reports/' + report['id'] + '/download.pdf')
    assert pdf.startswith(b'%PDF-')
    (ARTIFACT / 'InspectFirst-backend-verification.pdf').write_bytes(pdf)
    passed('transactional completion, Kafka outbox/inbox, asynchronous report, escaped HTML and PDF download')

    v2 = admin.call('POST', '/api/ops/content/guides', {'productId': product['id']})
    admin.call('PUT', '/api/ops/content/versions/' + v2['versionId'], draft)
    admin.call('POST', '/api/ops/content/versions/' + v2['versionId'] + '/publish', {'expectedVersion': 1})
    saved = [r for r in a.call('GET', '/api/saved-guides')['items'] if r['guideId'] == guide_id][0]
    assert saved['updated']
    assert a.call('GET', '/api/inspections/' + inspection_id)['guideSnapshot']['version'] == 1
    admin.call('POST', '/api/ops/content/guides/' + guide_id + '/withdraw', {'reason': 'QA complete. This fixture is not buying advice.'})
    saved = [r for r in a.call('GET', '/api/saved-guides')['items'] if r['guideId'] == guide_id][0]
    assert not saved['available']
    admin.call('GET', '/api/ops/inspections/' + inspection_id)
    assert admin.call('GET', '/api/ops/inspections?ownerUserId=' + student_user['id'])['total'] >= 1
    a.call('POST', '/api/inspections', {**create, 'requestKey': secrets.token_hex(12)}, expected=409)
    a.call('POST', '/api/inspections/' + inspection_id + '/reopen', {'expectedVersion': 3})
    a.call('PUT', '/api/inspections/' + inspection_id, {**save, 'expectedVersion': 4, 'userRequirement': 'Changed current workspace'})
    historical = a.call('GET', '/api/inspections/' + inspection_id + '/revisions/' + revision['id'])
    assert historical['snapshot']['userRequirement'] == create['userRequirement']
    passed('saved-guide update/withdrawal indicators, admin record lookup and immutable historical snapshot')

    a.call('DELETE', '/api/attachments/' + attachment['id'] + '?expectedVersion=5', expected=409)
    assert admin.call('GET', '/api/attachments/' + attachment['id']) == image
    a.call('DELETE', '/api/inspections/' + inspection_id + '?expectedVersion=5', expected=204)
    a.call('GET', '/api/reports/' + report['id'] + '/download', expected=404)
    a.call('GET', '/api/attachments/' + attachment['id'], expected=404)
    passed('deletion immediately blocks report and attachment access before event propagation')

    admin.call('PATCH', '/api/ops/users/' + other_user['id'] + '/status', {'status': 'DISABLED'})
    b.call('GET', '/api/auth/me', expected=401)
    b.call('POST', '/api/auth/login', {'email': other_email, 'password': other_password}, expected=401, form=True)
    admin.call('PATCH', '/api/ops/users/' + admin_user['id'] + '/status', {'status': 'DISABLED'}, expected=409)
    users = admin.call('GET', '/api/ops/users')
    assert 'password' not in json.dumps(users).lower()
    passed('user management, immediate disabled-session rejection, sole-admin protection and no password leakage')

    a.call('DELETE', '/api/saved-guides/' + guide_id, expected=204)
    admin.call('PATCH', '/api/ops/feedback/' + feedback_id + '/status', {'expectedVersion': 4, 'status': 'CLOSED', 'reply': 'QA cleanup complete'})
    admin.call('PUT', '/api/ops/content/products/' + product['id'], {'categoryId': category['id'], 'brand': 'QA fixture', 'modelCode': suffix, 'name': 'Backend verification fixture - not a real inspection guide', 'status': 'ARCHIVED'})
    a.call('POST', '/api/auth/logout', {}, expected=204)
    admin.call('POST', '/api/auth/logout', {}, expected=204)
    from cleanup_fixtures import main as cleanup_fixtures
    cleanup_fixtures()
    (ARTIFACT / 'backend-e2e-results.json').write_text(json.dumps({'passed': checks, 'count': len(checks), 'fixtureProductId': product['id'], 'inspectionId': inspection_id, 'reportId': report['id'], 'note': 'Labelled verification fixtures retained as archived/withdrawn/deleted records. No real user passwords in this artifact.'}, indent=2))
    print('All', len(checks), 'acceptance groups passed. Verification fixtures were closed and archived.', flush=True)


if __name__ == '__main__':
    main()
