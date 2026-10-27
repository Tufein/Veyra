#!/usr/bin/env python3
"""Export bookmarks/preferences from an installed debug alpha before changing signing keys.

Runs locally with USB debugging. Does not uninstall, clear, or modify the installed app.
Website cookies and login sessions are deliberately not exported. Normal tab addresses are optional.
"""
import argparse
import json
import os
from pathlib import Path
import subprocess
import xml.etree.ElementTree as ET
from urllib.parse import urlsplit

MAX_BYTES = 2 * 1024 * 1024


def convert(raw, include_tabs=False):
    if len(raw) > MAX_BYTES or b'<!DOCTYPE' in raw.upper():
        raise ValueError('Invalid or oversized preferences file')
    root = ET.fromstring(raw)
    if root.tag != 'map':
        raise ValueError('Expected Android preferences')
    values = {item.attrib.get('name'): item for item in root}
    def sites(key, limit):
        entries = json.loads(values[key].text or '[]') if key in values else []
        if not isinstance(entries, list) or len(entries) > limit:
            raise ValueError(f'Invalid {key} list')
        result = []
        for item in entries:
            if not isinstance(item, dict) or not isinstance(item.get('title'), str) or not isinstance(item.get('url'), str):
                raise ValueError('Invalid website entry')
            url = urlsplit(item['url'])
            if url.scheme not in ('https', 'http') or not url.hostname or url.username or url.password or any(c.isspace() for c in item['url']):
                raise ValueError('Unsafe website address; original data was not changed')
            if not item['title'].strip() or len(item['title']) > 512:
                raise ValueError('Invalid website title; original data was not changed')
            result.append({'title': item['title'], 'url': item['url']})
        return result
    preferences = {}
    for key in ('theme', 'accent', 'nickname', 'search'):
        if key in values and values[key].tag == 'string':
            preferences[key] = values[key].text or ''
    for key in ('dynamic_colors', 'compact_home', 'blocking', 'https_only'):
        if key in values and values[key].tag == 'boolean':
            preferences[key] = values[key].attrib.get('value') == 'true'
    if 'text_zoom' in values:
        preferences['text_zoom'] = int(values['text_zoom'].attrib['value'])
    if 'ad_exceptions' in values and values['ad_exceptions'].tag == 'set':
        preferences['ad_exceptions'] = [item.text for item in values['ad_exceptions'] if item.tag == 'string' and item.text]
    # Keep the same original four defaults if the preference was never edited.
    shortcuts = sites('shortcuts', 8) if 'shortcuts' in values else [
        {'title': 'Wikipedia', 'url': 'https://nl.wikipedia.org/'},
        {'title': 'NOS', 'url': 'https://nos.nl/'},
        {'title': 'YouTube', 'url': 'https://m.youtube.com/'},
        {'title': 'Zoeken', 'url': 'https://duckduckgo.com/'},
    ]
    result = {'format': 'veyra-backup', 'version': 1, 'bookmarks': sites('bookmarks', 5000), 'shortcuts': shortcuts, 'preferences': preferences}
    if include_tabs and 'tabs' in values:
        tabs = json.loads(values['tabs'].text or '[]')
        if not isinstance(tabs, list) or len(tabs) > 12:
            raise ValueError('Invalid tab list')
        result['tabs'] = []
        for tab in tabs:
            if not isinstance(tab, dict) or tab.get('private'):
                raise ValueError('Invalid or private tab; no backup written')
            url = urlsplit(tab.get('url', ''))
            home = bool(tab.get('home', False))
            if not home and (url.scheme not in ('http', 'https') or not url.hostname or url.username or url.password):
                raise ValueError('Invalid tab address')
            result['tabs'].append({'url': '' if home else tab['url'], 'title': tab.get('title', 'Tab'), 'home': home})
    return result


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--serial', help='ADB device serial when several devices are connected')
    parser.add_argument('--output', type=Path, required=True)
    parser.add_argument('--include-tabs', action='store_true', help='Also include normal tab addresses, without form data')
    args = parser.parse_args()
    command = ['adb'] + (['-s', args.serial] if args.serial else [])
    command += ['exec-out', 'run-as', 'nl.veyra.browser', 'cat', 'shared_prefs/veyra.xml']
    result = subprocess.run(command, capture_output=True)
    if result.returncode:
        raise SystemExit('Could not read the installed debug alpha. Confirm USB debugging/device authorization and keep the old app installed. No data changed.')
    try:
        backup = convert(result.stdout, args.include_tabs)
        os.umask(0o077)
        with args.output.open('x', encoding='utf-8') as output:
            json.dump(backup, output, ensure_ascii=False, indent=2)
            output.write('\n')
    except Exception as error:
        raise SystemExit(f'Export failed: {error}')
    print(f'Exported {len(backup["bookmarks"])} bookmarks and {len(backup["shortcuts"])} shortcuts to {args.output}')
    print('Keep this personal backup private. It excludes website logins. Tab addresses are included only with --include-tabs. Import it through Browser and data → Import and export in the new app.')


if __name__ == '__main__':
    main()
