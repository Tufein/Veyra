#!/usr/bin/env python3
"""Validate installs/updates on an explicitly isolated Android test device.

Never uninstalls an app or bypasses a signature mismatch. Keep production data off this device.
"""
import argparse
import hashlib
import json
from pathlib import Path
import re
import subprocess
import tempfile
from apk_certificate import certificate_digest

parser = argparse.ArgumentParser(description=__doc__)
parser.add_argument('--serial', required=True)
parser.add_argument('--apk', type=Path, required=True)
parser.add_argument('--test-apk', type=Path, required=True)
parser.add_argument('--apksigner', required=True)
parser.add_argument('--output', type=Path, required=True)
parser.add_argument('--addon-dir', type=Path, help='Official fixtures from prepare_addon_fixtures.py')
parser.add_argument('--confirm-isolated-test-device', action='store_true')
args = parser.parse_args()
if not args.confirm_isolated_test_device:
    parser.error('Use an isolated test device and explicitly pass --confirm-isolated-test-device')
if args.output.exists():
    parser.error('Choose a new report file; existing reports are retained')
adb = ['adb', '-s', args.serial]
def run(command, **kwargs):
    return subprocess.run(command, check=True, capture_output=True, **kwargs).stdout

def fingerprint(apk):
    text = run([args.apksigner, 'verify', '--print-certs', str(apk)], text=True)
    return certificate_digest(text)
expected = Path(__file__).resolve().parents[1].joinpath('docs/signing-certificate-sha256.txt').read_text().strip().lower()
if fingerprint(args.apk) != expected or fingerprint(args.test_apk) != expected:
    raise SystemExit('The APKs do not use the pinned stable key. No app was installed or removed.')
report = {'model': run(adb + ['shell', 'getprop', 'ro.product.model'], text=True).strip(),
          'android': run(adb + ['shell', 'getprop', 'ro.build.version.release'], text=True).strip(),
          'abi': run(adb + ['shell', 'getprop', 'ro.product.cpu.abi'], text=True).strip(),
          'certificate_sha256': expected, 'app_sha256': hashlib.file_digest(args.apk.open('rb'), 'sha256').hexdigest()}
paths = run(adb + ['shell', 'pm', 'path', 'nl.veyra.browser'], text=True).strip()
before = None
if paths:
    with tempfile.TemporaryDirectory() as temporary:
        installed = Path(temporary) / 'installed.apk'
        path = paths.splitlines()[0].removeprefix('package:')
        run(adb + ['pull', path, str(installed)])
        if fingerprint(installed) != expected:
            raise SystemExit('Existing app has a different signature. Keep it installed; export data using tools/export_legacy_data.py first. No app was installed or removed.')
    result = subprocess.run(adb + ['exec-out', 'run-as', 'nl.veyra.browser', 'cat', 'shared_prefs/veyra.xml'], capture_output=True)
    if result.returncode == 0:
        before = hashlib.sha256(result.stdout).hexdigest()
run(adb + ['install', '-r', str(args.apk)])
if before is not None:
    after = run(adb + ['exec-out', 'run-as', 'nl.veyra.browser', 'cat', 'shared_prefs/veyra.xml'])
    report['preferences_retained_on_install'] = hashlib.sha256(after).hexdigest() == before
    if not report['preferences_retained_on_install']:
        raise SystemExit('Preferences changed during update. Investigate on this test device before releasing.')
run(adb + ['install', '-r', str(args.test_apk)])
result = run(adb + ['shell', 'am', 'instrument', '-w', 'nl.veyra.browser.test/androidx.test.runner.AndroidJUnitRunner'], text=True)
report['instrumentation_skipped'] = len(re.findall(r'INSTRUMENTATION_STATUS_CODE: -(?:3|4)\b',result))
report['addon_results'] = []
report['instrumentation_output'] = result
report['instrumentation_passed'] = bool(re.search(r'OK \(\d+ tests?\)', result)) and not any(word in result for word in ('FAILURES!!!', 'Process crashed', 'INSTRUMENTATION_FAILED'))
if args.addon_dir and report['instrumentation_passed']:
    records=json.loads((args.addon_dir/'manifest.json').read_text())
    for record in records:
        slug=record['slug']
        if not re.fullmatch(r'[a-z0-9-]+',slug):
            raise SystemExit('Unsafe fixture filename')
        fixture=args.addon_dir/(slug+'.xpi')
        if hashlib.file_digest(fixture.open('rb'),'sha256').hexdigest()!=record['sha256']:
            raise SystemExit('Fixture checksum mismatch')
        filename='validation-'+slug+'.xpi'
        run(adb+['exec-out','run-as','nl.veyra.browser','tee',f'cache/{filename}'],input=fixture.read_bytes())
        try:
            output=run(adb+['shell','am','instrument','-w','-e','addon_fixture',filename,'-e','class',
                'nl.veyra.browser.BrowserEngineInstrumentedTest#installsSignedAddonWithConsentAndManagesItsState',
                'nl.veyra.browser.test/androidx.test.runner.AndroidJUnitRunner'],text=True)
        finally:
            run(adb+['shell','run-as','nl.veyra.browser','rm',f'cache/{filename}'])
        skipped=bool(re.search(r'INSTRUMENTATION_STATUS_CODE: -(?:3|4)\b',output))
        passed=bool(re.search(r'OK \(1 tests?\)',output)) and not any(word in output for word in ('FAILURES!!!','Process crashed','INSTRUMENTATION_FAILED'))
        report['addon_results'].append({'slug':slug,'version':record['version'],'sha256':record['sha256'],
            'result':'skipped' if skipped else 'passed' if passed else 'failed','output':output})
with args.output.open('x') as output:
    json.dump(report, output, indent=2)
if not report['instrumentation_passed'] or any(item['result']!='passed' for item in report['addon_results']):
    raise SystemExit(f'Device checks did not pass. Review {args.output}; no release approval is inferred.')
print(f'Executed real-device checks passed (optional skips: {report["instrumentation_skipped"]}); report: {args.output}. Complete the manual matrix separately.')
