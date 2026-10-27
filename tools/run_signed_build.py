#!/usr/bin/env python3
"""Run a build with the existing stable keystore; secret values never enter command arguments."""
import argparse
import os
from pathlib import Path
import subprocess

parser = argparse.ArgumentParser(description=__doc__)
parser.add_argument('--keystore', type=Path, required=True)
parser.add_argument('--password-file', type=Path, required=True)
parser.add_argument('--alias', default='veyra')
parser.add_argument('command', nargs=argparse.REMAINDER)
args = parser.parse_args()
command = args.command[1:] if args.command[:1] == ['--'] else args.command
if not command:
    parser.error('A build command is required')
password = args.password_file.read_text().strip()
environment = dict(os.environ)
environment.update(VEYRA_SIGNING_STORE_FILE=str(args.keystore.resolve()), VEYRA_SIGNING_STORE_PASSWORD=password,
                   VEYRA_SIGNING_KEY_PASSWORD=password, VEYRA_SIGNING_KEY_ALIAS=args.alias)
raise SystemExit(subprocess.run(command, env=environment).returncode)
