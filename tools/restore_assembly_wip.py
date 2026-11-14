#!/usr/bin/env python3
"""Restore immutable WIP source bytes and Git modes without the original repo."""

from __future__ import annotations

import argparse
import hashlib
import json
import os
import re
import stat
import zipfile
from pathlib import Path


def check(value, message):
    if not value:
        raise ValueError(message)


def sha(data):
    return hashlib.sha256(data).hexdigest()


def git_tree_identity(entries):
    tree = {}
    for name, mode, blob in entries:
        parts = Path(name).parts
        cursor = tree
        for part in parts[:-1]:
            cursor = cursor.setdefault(part, {})
        check(parts[-1] not in cursor, 'Conflicting Git-tree path.')
        cursor[parts[-1]] = (mode, blob)
    def encode(node):
        payload = bytearray()
        for name, value in sorted(node.items(), key=lambda pair: pair[0].encode() + (b'/' if isinstance(pair[1], dict) else b'')):
            mode, identity = ('40000', encode(value)) if isinstance(value, dict) else value
            payload.extend(mode.encode() + b' ' + name.encode() + b'\0' + bytes.fromhex(identity))
        return hashlib.sha1(f'tree {len(payload)}\0'.encode() + payload).hexdigest()
    return encode(tree)


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--destination', type=Path, required=True)
    parser.add_argument('--root', type=Path, default=Path(__file__).resolve().parents[1])
    parser.add_argument('--make-gradlew-executable', action='store_true')
    args = parser.parse_args()
    root = args.root.resolve()
    manifest = json.loads((root / 'MANIFEST.json').read_bytes())
    wip = manifest['assemblyWip']
    source_tree = json.loads((root / wip['sourceTreeManifest']).read_bytes())
    archive = root / wip['archivePath']
    check(sha(archive.read_bytes()) == wip['sha256'] == source_tree['archiveSha256'], 'WIP archive SHA256 mismatch.')
    check(source_tree['commit'] == wip['commit'] and source_tree['gitTree'] == wip['gitTree'], 'WIP commit/tree mismatch.')
    expected = {row['path']: row for row in source_tree['files']}
    check(len(expected) == source_tree['sourceFileCount'] == wip['internalSourceFiles'], 'WIP source count mismatch.')
    transformations = {row['path']: row['kind'] for row in source_tree['verifiedGitArchiveExportTransformations']}
    check(transformations == {'gradlew.bat': 'LF_TO_CRLF'}, 'Unexpected archive export transformation.')
    destination = args.destination.absolute()
    check(not destination.is_symlink(), 'Destination cannot be a symlink.')
    check(not destination.exists() or (destination.is_dir() and not any(destination.iterdir())), 'Destination must be absent or empty.')
    prepared, directories = [], []
    with zipfile.ZipFile(archive) as z:
        infos = z.infolist()
        check(len(infos) == len({row.filename for row in infos}), 'Duplicate WIP archive path.')
        for info in infos:
            path = Path(info.filename)
            check(not path.is_absolute() and '..' not in path.parts and '\\' not in info.filename
                  and not re.match(r'^[A-Za-z]:', info.filename), 'Unsafe WIP archive path.')
            check(info.filename.startswith(source_tree['prefix']) and '.git' not in path.parts, 'Unexpected WIP prefix or Git data.')
            check(not stat.S_ISLNK(info.external_attr >> 16), 'WIP archive link is unsupported.')
            if info.is_dir():
                directories.append(info.filename)
                continue
            name = info.filename[len(source_tree['prefix']):]
            check(name in expected, 'Unmanifested WIP member.')
            row = expected[name]
            exported = z.read(info)
            check(len(exported) == row['bytes'] and sha(exported) == row['sha256'], 'WIP export byte mismatch.')
            source = exported.replace(b'\r\n', b'\n') if name in transformations else exported
            check(len(source) == row['gitSourceBytes'] and sha(source) == row['gitSourceSha256'], 'WIP Git source byte mismatch.')
            check(hashlib.sha1(f'blob {len(source)}\0'.encode() + source).hexdigest() == row['gitBlobSha1'], 'WIP Git blob identity mismatch.')
            check(row['gitMode'] in ('100644', '100755'), 'Unexpected WIP Git file mode.')
            prepared.append((info.filename, source, int(row['gitMode'], 8) & 0o777))
        check(len(prepared) == len(expected), 'Missing WIP source member.')
        check(z.testzip() is None, 'WIP ZIP CRC failure.')
    destination.mkdir(parents=True, exist_ok=True)
    for relative in directories:
        target = destination / relative
        target.mkdir(parents=True, exist_ok=True)
        os.chmod(target, 0o755)
    restored_tree_entries = []
    for relative, data, mode in prepared:
        target = destination / relative
        target.parent.mkdir(parents=True, exist_ok=True)
        target.write_bytes(data)
        os.chmod(target, mode)
        check(target.read_bytes() == data and stat.S_IMODE(target.stat().st_mode) == mode, 'Restored WIP bytes or Git mode mismatch.')
        name = relative[len(source_tree['prefix']):]
        actual_mode = format(stat.S_IFREG | stat.S_IMODE(target.stat().st_mode), 'o')
        restored_tree_entries.append((name, actual_mode, hashlib.sha1(f'blob {len(data)}\0'.encode() + data).hexdigest()))
    restored_tree = git_tree_identity(restored_tree_entries)
    check(restored_tree == wip['gitTree'], 'Restored source does not reproduce the immutable Git tree.')
    gradlew = destination / source_tree['prefix'] / 'gradlew'
    before = stat.S_IMODE(gradlew.stat().st_mode)
    if args.make_gradlew_executable:
        os.chmod(gradlew, before | 0o111)
    print(json.dumps({'result': 'PASS', 'sourceCommit': wip['commit'], 'sourceGitTree': wip['gitTree'],
                      'restoredGitTreeBeforeLauncherFix': restored_tree, 'exactImmutableGitTreeRestored': True,
                      'sourceFilesRestored': len(prepared), 'gitBlobIdentitiesVerified': len(prepared),
                      'gitFileModesVerifiedBeforeLauncherFix': len(prepared), 'directoriesCreated0755': len(directories),
                      'reversedArchiveExportTransformations': list(transformations),
                      'gradlewModeBeforeExplicitFix': oct(before), 'explicitGradlewFixApplied': args.make_gradlew_executable,
                      'gradlewModeAfterFix': oct(stat.S_IMODE(gradlew.stat().st_mode)), 'applicationScriptsExecuted': False,
                      'qualification': 'WIP / NON_COMPILE / NON_COMPLET_13NOV; source restoration only.'}, ensure_ascii=False, indent=2))


if __name__ == '__main__':
    main()
