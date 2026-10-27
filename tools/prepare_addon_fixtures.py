#!/usr/bin/env python3
"""Download official signed-package candidates for isolated device validation, verifying AMO hashes."""
import argparse
import hashlib
import json
from pathlib import Path
import urllib.request
from urllib.parse import urlsplit

parser = argparse.ArgumentParser(description=__doc__)
parser.add_argument('--output', type=Path, required=True)
args = parser.parse_args()
args.output.mkdir(parents=True, exist_ok=True)
class MozillaRedirects(urllib.request.HTTPRedirectHandler):
    def redirect_request(self, request, fp, code, msg, headers, newurl):
        parts = urlsplit(newurl)
        if parts.scheme != 'https' or parts.hostname not in ('addons.mozilla.org', 'addons.cdn.mozilla.net') or parts.username or parts.password:
            raise ValueError('Package redirected outside official Mozilla HTTPS hosts')
        return super().redirect_request(request, fp, code, msg, headers, newurl)
opener = urllib.request.build_opener(MozillaRedirects())
records = []
for slug in ('ublock-origin', 'darkreader', 'clearurls'):
    with opener.open(f'https://addons.mozilla.org/api/v5/addons/addon/{slug}/', timeout=30) as response:
        metadata = json.load(response)
    version = metadata['current_version']; package = version['file']
    if 'android' not in version.get('compatibility', {}) or package.get('status') != 'public':
        raise SystemExit(f'{slug}: no public Android package in current AMO metadata')
    expected = package['hash']
    if not expected.startswith('sha256:'):
        raise SystemExit('AMO did not supply SHA-256')
    parts = urlsplit(package['url'])
    if parts.scheme != 'https' or parts.hostname != 'addons.mozilla.org':
        raise SystemExit('Unexpected AMO package host')
    target = args.output / (slug + '.xpi')
    if target.exists():
        raise SystemExit(f'Fixture already exists; choose another output directory: {target}')
    digest = hashlib.sha256(); count = 0
    try:
        with opener.open(package['url'], timeout=45) as response, target.open('xb') as output:
            while chunk := response.read(65536):
                count += len(chunk)
                if count > 32 * 1024 * 1024:
                    raise ValueError('Package too large')
                digest.update(chunk); output.write(chunk)
        if digest.hexdigest() != expected.split(':', 1)[1]:
            raise ValueError('AMO checksum mismatch')
    except Exception:
        target.unlink(missing_ok=True)
        raise
    records.append({'slug': slug, 'guid': metadata['guid'], 'version': version['version'], 'sha256': digest.hexdigest(), 'source': package['url'], 'device_result': 'not run'})
(args.output / 'manifest.json').write_text(json.dumps(records, indent=2) + '\n')
print('Prepared official fixtures and hashes. Downloading is not an installation/compatibility test; run them on isolated devices.')
