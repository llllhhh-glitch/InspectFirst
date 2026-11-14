#!/usr/bin/env python3
"""Run acceptance against the Compose website with disposable, labelled accounts."""
from pathlib import Path
import json,os,secrets,shutil,subprocess,sys,urllib.request
ROOT=Path(__file__).resolve().parent.parent
if not shutil.which('docker') and (Path.home()/'.docker/bin/docker').exists():
    os.environ['PATH']=str(Path.home()/'.docker/bin')+os.pathsep+os.environ.get('PATH','')
# Compose parses .env, including quoted special characters. Do not print its output.
result=subprocess.run(['docker','compose','config','--format','json'],cwd=ROOT,capture_output=True,text=True)
if result.returncode:
    raise SystemExit('Compose configuration could not be loaded. Configure .env and start Docker first.')
config=json.loads(result.stdout)
def literal(value):
    return value.replace('$$','$')  # Reusable Compose output escapes literal dollars.
admin=config['services']['identity']['environment']
port=config['services']['web']['ports'][0]['published']
os.environ.update({
 'INSPECTFIRST_URL':f'http://127.0.0.1:{port}',
 'INSPECTFIRST_DB_MODE':'docker',
 'INSPECTFIRST_QA_DIR':str(ROOT/'.local/qa-docker'),
 'INSPECTFIRST_TEST_ADMIN_EMAIL':literal(admin['ADMIN_EMAIL']),
 'INSPECTFIRST_TEST_ADMIN_PASSWORD':literal(admin['ADMIN_PASSWORD']),
 'INSPECTFIRST_TEST_USER_EMAIL':'docker-qa-'+secrets.token_hex(6)+'@example.test',
 'INSPECTFIRST_TEST_USER_PASSWORD':secrets.token_urlsafe(24),
})
sys.path.insert(0,str(ROOT/'backend/tests'))
import e2e
for path in ['/api/health','/api/auth/health','/api/inspections/health','/api/reports/health']:
    with urllib.request.urlopen(os.environ['INSPECTFIRST_URL']+path,timeout=30) as response:
        assert json.load(response)['status']=='UP'
client=e2e.Client()
client.call('POST','/api/auth/register',{
 'email':os.environ['INSPECTFIRST_TEST_USER_EMAIL'],
 'password':os.environ['INSPECTFIRST_TEST_USER_PASSWORD'],
 'displayName':'Docker QA fixture'},expected=201)
try:
    e2e.main()
finally:
    # Preserve audit history but prevent further login to the disposable customer.
    admin_client=e2e.Client()
    try:
        admin_client.login(os.environ['INSPECTFIRST_TEST_ADMIN_EMAIL'],os.environ['INSPECTFIRST_TEST_ADMIN_PASSWORD'])
        users=admin_client.call('GET','/api/ops/users?q='+os.environ['INSPECTFIRST_TEST_USER_EMAIL'])['items']
        for user in users:
            if user['email']==os.environ['INSPECTFIRST_TEST_USER_EMAIL'] and user['role']=='USER':
                admin_client.call('PATCH','/api/ops/users/'+user['id']+'/status',{'status':'DISABLED'})
        admin_client.call('POST','/api/auth/logout',{},expected=204)
    except Exception:
        print('Disposable-account cleanup requires review; no credentials printed.',file=sys.stderr)
