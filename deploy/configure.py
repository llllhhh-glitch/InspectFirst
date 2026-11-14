#!/usr/bin/env python3
"""Create private Compose settings; never overwrite existing credentials."""
from pathlib import Path
import getpass,os,re,secrets
root=Path(__file__).resolve().parent.parent
path=root/'.env'
if path.exists():
    raise SystemExit('.env already exists. Existing settings have been retained.')
email=input('Administrator email: ').strip()
if len(email)>254 or not re.fullmatch(r"[A-Za-z0-9.!#$%&'*+/=?^_`{|}~-]+@[A-Za-z0-9.-]+\.[A-Za-z]{2,}",email):
    raise SystemExit('Enter a valid email address.')
password=getpass.getpass('Administrator password (12–128 characters): ')
if not 12<=len(password)<=128 or any(ord(c)<32 or ord(c)==127 for c in password):
    raise SystemExit('Use 12–128 characters without control characters.')
if getpass.getpass('Confirm password: ')!=password:
    raise SystemExit('Passwords do not match.')
def quote(value):
    # Compose single-quoted values are literal, including $ and #.
    return "'"+value.replace("'", "\\'")+"'"
settings={'WEB_PORT':'8080','ADMIN_EMAIL':email,'ADMIN_PASSWORD':password}
for key in ['MYSQL_ROOT_PASSWORD','IDENTITY_DB_PASSWORD','GUIDE_DB_PASSWORD','INSPECTION_DB_PASSWORD','REPORT_DB_PASSWORD']:
    settings[key]=secrets.token_hex(24)
fd=os.open(path,os.O_WRONLY|os.O_CREAT|os.O_EXCL,0o600)
with os.fdopen(fd,'w') as f:
    f.write(''.join(k+'='+quote(v)+'\n' for k,v in settings.items()))
print('Created private .env. Start with: docker compose up --build -d --wait')
