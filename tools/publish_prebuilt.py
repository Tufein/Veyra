#!/usr/bin/env python3
"""Publish verified existing APKs from repository Actions without transferring a signing key."""
import hashlib
import json
import os
from pathlib import Path
import re
import subprocess
from release_readiness import assess


def sha256_file(path):
    with path.open('rb') as source:
        return hashlib.file_digest(source, 'sha256').hexdigest()

def validate_assets(assets, expected):
    seen = set()
    for asset in assets:
        name = asset['name']
        if name not in expected or name in seen:
            raise ValueError('Unexpected or duplicate release asset: ' + name)
        if asset['state'] != 'uploaded' or asset.get('digest') != 'sha256:' + expected[name]:
            raise ValueError('Existing asset is incomplete or differs; it will not be overwritten: ' + name)
        seen.add(name)
    return sorted(set(expected) - seen)


def find_release(pages, tag):
    matches = [release for page in pages for release in page if release.get('tag_name') == tag]
    if len(matches) > 1:
        raise ValueError('Duplicate release tags returned by GitHub')
    return matches[0] if matches else None


def release_pages(repo):
    pages = []
    page = 1
    while True:
        values = json.loads(gh('api', f'repos/{repo}/releases?per_page=100&page={page}'))
        pages.append(values)
        if len(values) < 100:
            return pages
        page += 1


def gh(*arguments, optional=False):
    result = subprocess.run(['gh', *arguments], capture_output=True, text=True)
    if result.returncode:
        if optional and ('HTTP 404' in result.stderr or 'release not found' in result.stderr):
            return None
        raise RuntimeError('GitHub operation failed: ' + result.stderr.strip())
    return result.stdout


def main():
    config = json.loads(Path('artifacts/publication.json').read_text())
    tag, source = config['tag'], config['source_commit']
    readiness = assess(tag, json.loads(Path('docs/release-readiness.json').read_text()))
    if not readiness['publication_allowed']:
        raise ValueError('Stable release gates are not complete')
    if not re.fullmatch(r'[0-9a-f]{40}', source):
        raise ValueError('Expected an immutable source commit')
    repo = os.environ['GH_REPO']
    if not re.fullmatch(r'[A-Za-z0-9_.-]+/[A-Za-z0-9_.-]+', repo):
        raise ValueError('Invalid repository')
    root = Path('release-assets')
    expected = {}
    for line in (root / 'SHA256SUMS.txt').read_text().splitlines():
        digest, name = line.split('  ', 1)
        if not re.fullmatch(r'[0-9a-f]{64}', digest) or Path(name).name != name:
            raise ValueError('Invalid checksum entry')
        if sha256_file(root / name) != digest:
            raise ValueError('Local release asset changed: ' + name)
        expected[name] = digest
    names = {f'Veyra-{tag[1:]}-{abi}.apk' for abi in ('arm64-v8a', 'armeabi-v7a', 'x86_64')}
    if set(expected) != names:
        raise ValueError('Expected exactly the three release APKs')
    expected['SHA256SUMS.txt'] = sha256_file(root / 'SHA256SUMS.txt')
    # An unpublished draft may not have a tag ref yet; the by-tag endpoint returns 404.
    state = find_release(release_pages(repo), tag)
    if state is None:
        gh('release', 'create', tag, '--repo', repo, '--target', source, '--draft',
           '--prerelease=' + str(readiness['prerelease']).lower(), '--title',
           f'Veyra {tag[1:]}' + (' — Release candidate' if readiness['prerelease'] else ''),
           '--notes-file', 'artifacts/release-notes.md')
        state = find_release(release_pages(repo), tag)
    if state is None:
        raise ValueError('New draft release was not returned by GitHub')
    release_id = state['id']
    if not state['draft'] or state['target_commitish'] != source:
        raise ValueError('Refusing to modify a published release or a different source commit')
    missing = validate_assets(state['assets'], expected)
    if missing:
        gh('release', 'upload', tag, '--repo', repo, *[str(root / name) for name in missing])
    uploaded = json.loads(gh('api', f'repos/{repo}/releases/{release_id}'))
    if not uploaded['draft'] or uploaded['target_commitish'] != source:
        raise ValueError('Draft changed during upload; publication stopped')
    if validate_assets(uploaded['assets'], expected):
        raise ValueError('Some verified release assets are still missing')
    gh('release', 'edit', tag, '--repo', repo, '--draft=false',
       '--prerelease=' + str(readiness['prerelease']).lower())
    print(f'https://github.com/{repo}/releases/tag/{tag}')


if __name__ == '__main__':
    main()
