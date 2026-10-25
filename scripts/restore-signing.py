#!/usr/bin/env python3
"""Restore the project owner's private key; never log secret material."""
import os,json,base64,pathlib
raw=os.environ.get('MEICRAFT_SIGNING_JSON','')
if raw:
 payload=json.loads(raw)
 if payload.get('alias')!='meicraft': raise SystemExit('Unexpected signing alias')
 key=base64.b64decode(payload['keystore'],validate=True)
 password=payload['password']
 if not key or not isinstance(password,str) or not password.strip():raise SystemExit('Incomplete signing secret')
 print('::add-mask::'+password)
 target=pathlib.Path('.signing');target.mkdir(mode=0o700,exist_ok=True)
 for name,data in [('keystore.p12',key),('password',password.encode())]:
  path=target/name;path.write_bytes(data);path.chmod(0o600)
 print('Private signing key restored.')
else:
 print('No release signing secret: local build uses a temporary CI test key.')
