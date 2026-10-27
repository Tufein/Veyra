#!/usr/bin/env python3
"""Upload a local release key to GitHub Secrets using gh's encrypted secret API."""
import argparse
import base64
from pathlib import Path
import subprocess

parser = argparse.ArgumentParser(description=__doc__)
parser.add_argument('--keystore', type=Path, required=True)
parser.add_argument('--password-file', type=Path, required=True)
parser.add_argument('--alias', default='veyra')
parser.add_argument('--repo', default='Tufein/Veyra')
args = parser.parse_args()
password = args.password_file.read_bytes().strip()
values = {
    'VEYRA_KEYSTORE_BASE64': base64.b64encode(args.keystore.read_bytes()),
    'VEYRA_SIGNING_STORE_PASSWORD': password,
    'VEYRA_SIGNING_KEY_PASSWORD': password,
    'VEYRA_SIGNING_KEY_ALIAS': args.alias.encode(),
}
for name, value in values.items():
    result = subprocess.run(['gh', 'secret', 'set', name, '--repo', args.repo], input=value, capture_output=True)
    if result.returncode:
        raise SystemExit(f'Could not save {name}. Check GitHub API network access and repository secret-management permissions. No secret values printed.')
    print(f'Saved encrypted GitHub secret: {name}')
