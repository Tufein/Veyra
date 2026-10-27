#!/usr/bin/env python3
"""Record reproducible local-device startup and memory measurements; no performance claims inferred."""
import argparse
import json
from pathlib import Path
import re
import statistics
import subprocess
import time

parser = argparse.ArgumentParser(description=__doc__)
parser.add_argument('--serial', required=True)
parser.add_argument('--activity', default='nl.veyra.browser/.BrowserActivity')
parser.add_argument('--runs', type=int, default=20)
parser.add_argument('--output', type=Path, required=True)
args = parser.parse_args()
if args.output.exists():
    parser.error('Choose a new output report; existing results are retained')
if args.runs < 5 or args.runs > 100:
    parser.error('Use 5–100 runs')
package = args.activity.split('/', 1)[0]
adb = ['adb', '-s', args.serial]
def run(*command):
    return subprocess.run(adb + list(command), check=True, capture_output=True, text=True).stdout
result = {'model': run('shell', 'getprop', 'ro.product.model').strip(), 'android': run('shell', 'getprop', 'ro.build.version.release').strip(),
          'activity': args.activity, 'cold_ms': [], 'warm_ms': [], 'conditions': 'Record Wi-Fi, brightness, energy settings, temperature, and page fixtures separately.'}
for i in range(args.runs):
    run('shell', 'am', 'force-stop', package)
    for kind in ['cold_ms', 'warm_ms']:
        text = run('shell', 'am', 'start', '-W', '-n', args.activity)
        match = re.search(r'TotalTime:\s*(\d+)', text)
        if not match:
            raise SystemExit('Android did not report a measured TotalTime; do not substitute an invented value')
        result[kind].append(int(match.group(1)))
        run('shell', 'input', 'keyevent', 'KEYCODE_HOME')
        time.sleep(1)
result['summary'] = {kind: {'median_ms': statistics.median(result[kind]), 'min_ms': min(result[kind]), 'max_ms': max(result[kind])} for kind in ['cold_ms', 'warm_ms']}
result['memory_snapshot'] = run('shell', 'dumpsys', 'meminfo', package)
with args.output.open('x') as output:
    json.dump(result, output, indent=2)
print(f'Recorded actual device results: {args.output}. Review raw values before comparing browsers.')
