#!/usr/bin/env python3
"""Stage only public signed APKs on a temporary orphan branch for Actions publication.

No signing key or password is copied. The ordinary source checkout is not switched.
"""
import argparse
import hashlib
import importlib.util
import io
import json
from pathlib import Path
import re
import shutil
import subprocess
import sys
import tarfile
from release_readiness import assess


def git(*arguments, **kwargs):
    return subprocess.check_output(['git', *arguments], **kwargs).decode().strip()


def tree(folder):
    entries = []
    for file in sorted(folder.iterdir(), key=lambda p: p.name):
        if file.is_dir():
            mode, kind, digest = '040000', 'tree', tree(file)
        else:
            if file.stat().st_size >= 50*1024*1024 or file.suffix in ('.keystore', '.jks'):
                raise ValueError('Unexpected payload file: ' + file.name)
            mode, kind, digest = '100644', 'blob', git('hash-object', '-w', str(file))
        entries.append(f'{mode} {kind} {digest}\t{file.name}\n')
    return git('mktree', input=''.join(entries).encode())


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--tag', required=True)
    parser.add_argument('--output', type=Path, required=True)
    parser.add_argument('--apksigner', required=True)
    parser.add_argument('--push', action='store_true')
    args = parser.parse_args()
    readiness = assess(args.tag, json.loads(Path('docs/release-readiness.json').read_text()))
    if not readiness['publication_allowed']:
        parser.error('Stable release gates are still open')
    if args.output.exists():
        parser.error('Choose a new output directory; existing attempts are retained')
    if git('status', '--porcelain'):
        parser.error('Commit and validate the source changes before staging a release')
    source = git('rev-parse', 'HEAD')
    branch = 'codex/publish-' + args.tag[1:]
    if git('ls-remote', 'origin', 'refs/heads/' + branch):
        parser.error('The temporary branch already exists; inspect the previous attempt first')
    notes_path = Path('docs/releases') / (args.tag + '.md')
    if not notes_path.is_file():
        parser.error('Commit accurate release notes first')
    args.output.mkdir(parents=True)
    spec = importlib.util.spec_from_file_location('prepare_release', '.github/scripts/prepare_release.py')
    module = importlib.util.module_from_spec(spec); spec.loader.exec_module(module)
    verified = args.output / 'verified'
    module.prepare(args.tag, verified, args.apksigner, Path('docs/signing-certificate-sha256.txt'), 'release')
    stage = args.output / 'stage'; artifacts = stage / 'artifacts'; artifacts.mkdir(parents=True)
    metadata_path = Path('app/build/outputs/apk/release/output-metadata.json')
    metadata = json.loads(metadata_path.read_text())
    archive = args.output / 'payload.tar.gz'
    with tarfile.open(archive, 'w:gz', compresslevel=6) as output:
        for entry in metadata['elements']:
            name = entry['outputFile']
            if Path(name).name != name:
                raise ValueError('Unsafe APK filename')
            output.add(metadata_path.parent / name, arcname=name, recursive=False)
        data = metadata_path.read_bytes(); info = tarfile.TarInfo('output-metadata.json'); info.size = len(data)
        output.addfile(info, io.BytesIO(data))
    with archive.open('rb') as input:
        part = 0
        while chunk := input.read(40*1024*1024):
            (artifacts / f'payload.part-{part:03}').write_bytes(chunk); part += 1
    with archive.open('rb') as input:
        digest = hashlib.file_digest(input, 'sha256').hexdigest()
    (artifacts / 'payload.sha256').write_text(digest + '  payload.tar.gz\n')
    shutil.copyfile(verified / 'SHA256SUMS.txt', artifacts / 'SHA256SUMS.txt')
    (artifacts / 'publication.json').write_text(json.dumps({'tag': args.tag, 'source_commit': source}, indent=2)+'\n')
    notes = re.sub(r'\]\(\.\./([^)]*)\)', lambda m: '](https://github.com/Tufein/Veyra/blob/'+args.tag+'/docs/'+m[1]+')', notes_path.read_text())
    (artifacts / 'release-notes.md').write_text(notes)
    for name in ('.github/workflows/publish-prebuilt.yml', '.github/scripts/prepare_release.py',
                 'tools/apk_certificate.py', 'tools/release_readiness.py', 'tools/publish_prebuilt.py',
                 'docs/signing-certificate-sha256.txt', 'docs/release-readiness.json'):
        destination = stage / name; destination.parent.mkdir(parents=True, exist_ok=True)
        shutil.copyfile(name, destination)
    commit = git('commit-tree', tree(stage), '-m', 'Publish verified existing APKs for '+args.tag)
    (args.output / 'commit').write_text(commit+'\n')
    print('Temporary artifact commit:', commit)
    print('Temporary branch:', branch)
    print('Only signed APKs, public metadata and verification tools are included.')
    if args.push:
        subprocess.run(['git', '-c', 'credential.helper=!gh auth git-credential', 'push',
                        'origin', commit+':refs/heads/'+branch], check=True)


if __name__ == '__main__':
    main()
