#!/usr/bin/env python3
"""Load the fixed release key from GitHub Secrets; fail closed if missing or changed."""
import base64
import hashlib
import os
from pathlib import Path
import ssl
import subprocess

required = ('VEYRA_KEYSTORE_BASE64', 'VEYRA_SIGNING_STORE_PASSWORD', 'VEYRA_SIGNING_KEY_PASSWORD', 'VEYRA_SIGNING_KEY_ALIAS')
if any(not os.environ.get(name) for name in required):
    raise SystemExit('Configure the fixed Veyra signing secrets before publishing. A new random debug key is never substituted.')
root = Path(os.environ['RUNNER_TEMP']) / 'veyra-signing'
root.mkdir(mode=0o700, exist_ok=True)
key = root / 'release.keystore'
try:
    key.write_bytes(base64.b64decode(os.environ['VEYRA_KEYSTORE_BASE64'], validate=True))
    key.chmod(0o600)
    result = subprocess.run(['keytool', '-list', '-rfc', '-alias', os.environ['VEYRA_SIGNING_KEY_ALIAS'], '-keystore', str(key),
                             '-storepass:env', 'VEYRA_SIGNING_STORE_PASSWORD'], capture_output=True, check=True)
    certificate = result.stdout.decode()
    start = certificate.index('-----BEGIN CERTIFICATE-----')
    end = certificate.index('-----END CERTIFICATE-----') + len('-----END CERTIFICATE-----')
    actual = hashlib.sha256(ssl.PEM_cert_to_DER_cert(certificate[start:end])).hexdigest()
    expected = Path('docs/signing-certificate-sha256.txt').read_text().strip().lower()
    if actual != expected:
        raise ValueError('Certificate mismatch')
except Exception:
    key.unlink(missing_ok=True)
    raise SystemExit('Signing key validation failed; check the configured keystore, alias, password, and pinned certificate.')
with open(os.environ['GITHUB_ENV'], 'a') as output:
    output.write(f'VEYRA_SIGNING_STORE_FILE={key}\n')
print('Fixed signing certificate verified:', actual)
