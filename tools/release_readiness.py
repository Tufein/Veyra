#!/usr/bin/env python3
"""Keep stable 1.x publication separate from prereleases awaiting device evidence."""
import argparse
import json
from pathlib import Path
import re
from urllib.parse import urlsplit

REQUIRED = (
    'signing_backup', 'automated_fixed_key_rebuild', 'signed_update',
    'native_release_smoke', 'device_matrix', 'privacy_storage',
    'extension_matrix', 'live_two_device_sync', 'accessibility_handheld',
    'benchmark_baseline', 'private_security_reporting',
)


def assess(tag, report):
    version = re.fullmatch(r'v(\d+)\.(\d+)\.(\d+)(?:-(alpha|beta|rc)\.(\d+))?', tag)
    if not version:
        raise ValueError('Invalid release tag')
    if report.get('schema') != 1 or not isinstance(report.get('gates'), dict):
        raise ValueError('Unsupported readiness report')
    gates = report['gates']
    missing = []
    for name in REQUIRED:
        gate = gates.get(name)
        if not isinstance(gate, dict) or gate.get('status') not in ('pending', 'failed', 'passed'):
            raise ValueError('Missing or invalid gate: ' + name)
        if gate['status'] == 'passed':
            proof = gate.get('evidence')
            if not isinstance(proof, str) or urlsplit(proof).scheme != 'https' or not urlsplit(proof).hostname:
                raise ValueError('Passed gate requires a reviewed HTTPS evidence link: ' + name)
        else:
            missing.append(name)
    prerelease = int(version[1]) < 1 or version[4] is not None
    return {'tag': tag, 'prerelease': prerelease, 'pending': missing,
            'stable_ready': not missing, 'publication_allowed': prerelease or not missing}


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--tag', required=True)
    parser.add_argument('--report', type=Path, default=Path('docs/release-readiness.json'))
    args = parser.parse_args()
    try:
        result = assess(args.tag, json.loads(args.report.read_text()))
    except (ValueError, OSError) as error:
        parser.exit(2, f'Readiness check failed: {error}\n')
    print(json.dumps(result, indent=2))
    if not result['publication_allowed']:
        parser.exit(2, 'Stable publication is blocked by unverified acceptance gates. Publish an accurately labelled prerelease until evidence is reviewed.\n')


if __name__ == '__main__':
    main()
