"""Runner control-flow checks with mocked ADB; these are not device test results."""
import json
from pathlib import Path
import runpy
import subprocess
import sys
import tempfile
import unittest
from unittest.mock import patch


class DeviceRunnerTests(unittest.TestCase):
    def exercise(self, installed=False, wrong_key=False, release_target=False, crashed=False):
        with tempfile.TemporaryDirectory() as folder:
            root = Path(folder); apk = root/'app.apk'; apk.write_bytes(b'mocked-apk')
            test_apk = root/'test.apk'; test_apk.write_bytes(b'mocked-test-apk'); report = root/'report.json'
            pin = Path('docs/signing-certificate-sha256.txt').read_text().strip(); calls = []; updated = False
            def command(arguments, **kwargs):
                nonlocal updated
                calls.append(arguments)
                text = kwargs.get('text', False); output = ''; code = 0
                if arguments[0] == 'test-apksigner':
                    digest = 'ff'*32 if wrong_key and arguments[-1].endswith('installed.apk') else pin
                    output = 'V2 Signer: certificate SHA-256 digest: '+digest+'\n'
                elif 'getprop' in arguments: output = 'mock-device\n'
                elif 'packages' in arguments: output = 'package:nl.veyra.browser\n' if installed else ''
                elif 'path' in arguments: output = 'package:/data/app/test/base.apk\n'
                elif 'pull' in arguments: Path(arguments[-1]).write_bytes(b'mocked-installed-apk')
                elif 'run-as' in arguments:
                    output = '<map><string name="nickname">Preserve</string></map>'
                    if updated and release_target: code = 1; output = ''
                elif 'install' in arguments:
                    if arguments[-1] == str(apk): updated = True
                    output = 'Success'
                elif 'instrument' in arguments: output = 'INSTRUMENTATION_FAILED: Process crashed' if crashed else 'OK (1 test)'
                return subprocess.CompletedProcess(arguments, code, output if text else output.encode(), '' if text else b'')
            arguments = ['device_acceptance.py', '--serial', 'mock-only', '--apk', str(apk), '--test-apk', str(test_apk),
                         '--apksigner', 'test-apksigner', '--output', str(report), '--confirm-isolated-test-device']
            error = None
            with patch.object(sys, 'argv', arguments), patch('subprocess.run', side_effect=command), patch('builtins.print'):
                try: runpy.run_path('tools/device_acceptance.py', run_name='__main__')
                except SystemExit as failure: error = failure
            return json.loads(report.read_text()) if report.exists() else None, calls, error

    def test_fresh_install_does_not_require_an_existing_package_path(self):
        report, calls, error = self.exercise()
        self.assertIsNone(error); self.assertEqual('fresh', report['installation_mode'])
        self.assertEqual('not_applicable', report['update_verification'])
        self.assertFalse(any('path' in command for command in calls))

    def test_switching_to_a_non_debuggable_build_does_not_claim_update_data_proof(self):
        report, calls, error = self.exercise(installed=True, release_target=True)
        self.assertIsNone(error); self.assertIsNone(report['preferences_retained_on_install'])
        self.assertEqual('unverified_target_not_debuggable', report['update_verification'])

    def test_signature_mismatch_never_installs_or_uninstalls(self):
        report, calls, error = self.exercise(installed=True, wrong_key=True)
        self.assertIsNotNone(error); self.assertIsNone(report)
        self.assertFalse(any('install' in command or 'uninstall' in command for command in calls))

    def test_a_crashed_instrumentation_run_is_saved_as_a_failure(self):
        report, calls, error = self.exercise(crashed=True)
        self.assertIsNotNone(error); self.assertFalse(report['instrumentation_passed'])
