#!/usr/bin/env python3
"""Restore the source baseline into a new directory, checking bytes and modes."""

from __future__ import annotations

import argparse
import hashlib
import json
import os
import re
import stat
import zipfile
from pathlib import Path


def check(value: bool, message: str) -> None:
    if not value:
        raise ValueError(message)


def digest(data: bytes) -> str:
    return hashlib.sha256(data).hexdigest()


def main() -> None:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--destination', type=Path, required=True)
    parser.add_argument('--root', type=Path, default=Path(__file__).resolve().parents[1])
    parser.add_argument('--make-gradlew-executable', action='store_true',
                        help='Apply and report a launcher mode change after archive/default mode verification.')
    args = parser.parse_args()
    root = args.root.resolve()
    manifest = json.loads((root / 'MANIFEST.json').read_bytes())
    baseline = manifest['baseline']
    archive = root / baseline['archivePath']
    check(digest(archive.read_bytes()) == baseline['sha256'], 'Baseline archive SHA256 mismatch.')
    source_manifest = json.loads((root / baseline['sourceFileManifest']).read_bytes())
    expected = {row['path']: row for row in source_manifest['files']}
    check(len(expected) == baseline['internalSourceFiles'] == 1389, 'Baseline source count mismatch.')
    destination = args.destination.absolute()
    check(not destination.is_symlink(), 'Destination cannot be a symlink.')
    check(not destination.exists() or (destination.is_dir() and not any(destination.iterdir())),
          'Destination must be absent or an empty directory.')
    restored, directories = [], []
    recorded_mode_files = default_mode_files = 0
    with zipfile.ZipFile(archive) as z:
        infos = z.infolist()
        check(len(infos) == len({info.filename for info in infos}), 'Duplicate archive path.')
        for info in infos:
            relative = Path(info.filename)
            check(not relative.is_absolute() and '..' not in relative.parts
                  and '\\' not in info.filename and not re.match(r'^[A-Za-z]:', info.filename),
                  'Unsafe baseline member path.')
            check(info.filename.startswith(source_manifest['prefix']) and '.git' not in relative.parts,
                  'Unexpected baseline prefix or Git directory.')
            mode = info.external_attr >> 16
            check(not stat.S_ISLNK(mode) and not mode & 0o7000, 'Unsupported link or special permission mode.')
            if info.is_dir():
                directories.append((info, stat.S_IMODE(mode) or 0o755))
                continue
            name = info.filename[len(source_manifest['prefix']):]
            check(name in expected, 'Unmanifested baseline member.')
            data = z.read(info)
            check(len(data) == expected[name]['bytes'] and digest(data) == expected[name]['sha256'],
                  'Baseline member byte integrity failure.')
            file_mode = stat.S_IMODE(mode)
            if file_mode:
                recorded_mode_files += 1
            else:
                default_mode_files += 1
                file_mode = 0o644
            restored.append((info, data, file_mode))
        check(len(restored) == len(expected), 'Missing baseline source member.')
        check(z.testzip() is None, 'ZIP CRC failure.')
    destination.mkdir(parents=True, exist_ok=True)
    for info, mode in directories:
        target = destination / info.filename
        target.mkdir(parents=True, exist_ok=True)
        os.chmod(target, mode)
    for info, data, mode in restored:
        target = destination / info.filename
        target.parent.mkdir(parents=True, exist_ok=True)
        target.write_bytes(data)
        os.chmod(target, mode)
        check(target.read_bytes() == data, 'Extracted source bytes differ.')
        check(stat.S_IMODE(target.stat().st_mode) == mode, 'Extracted source mode differs.')
    for info, mode in directories:
        check(stat.S_IMODE((destination / info.filename).stat().st_mode) == mode,
              'Extracted directory mode differs.')
    gradlew = destination / source_manifest['prefix'] / 'gradlew'
    original_launcher_mode = stat.S_IMODE(gradlew.stat().st_mode)
    if args.make_gradlew_executable:
        os.chmod(gradlew, original_launcher_mode | 0o111)
        check(os.access(gradlew, os.X_OK), 'Gradle launcher mode fix failed.')
    print(json.dumps({
        'result': 'PASS', 'archiveSha256': baseline['sha256'], 'restoredFiles': len(restored),
        'sourceHashesVerified': len(restored), 'fileModesVerifiedBeforeLauncherFix': len(restored),
        'directoryModesVerified': len(directories),
        'filesWithArchiveUnixModes': recorded_mode_files, 'filesWithExplicitDefaultMode0644': default_mode_files,
        'gradlewModeBeforeExplicitFix': oct(original_launcher_mode),
        'explicitGradlewExecutableFixApplied': args.make_gradlew_executable,
        'gradlewModeAfterFix': oct(stat.S_IMODE(gradlew.stat().st_mode)),
        'gradlewExecutable': os.access(gradlew, os.X_OK), 'scriptsExecuted': False,
        'qualification': 'Old Room46 source base only; November13 recovery is not assembled or qualified.',
    }, ensure_ascii=False, indent=2))


if __name__ == '__main__':
    main()
