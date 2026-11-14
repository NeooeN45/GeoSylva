#!/usr/bin/env python3
"""Verify local backup bytes/provenance only; no application code or network."""

from __future__ import annotations

import argparse
import hashlib
import json
import subprocess
from collections import Counter
from pathlib import Path


def sha(data: bytes) -> str:
    return hashlib.sha256(data).hexdigest()


def check(condition: bool, message: str) -> None:
    if not condition:
        raise ValueError(message)


def at_pointer(value, pointer: str):
    for component in pointer.lstrip('/').split('/'):
        component = component.replace('~1', '/').replace('~0', '~')
        value = value[int(component)] if isinstance(value, list) else value[component]
    return value


def main() -> None:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--root', type=Path, default=Path(__file__).resolve().parents[1])
    parser.add_argument('--baseline-commit', help='Optional local Git commit whose payload must be preserved.')
    args = parser.parse_args()
    root = args.root.resolve()
    manifest = json.loads((root / 'MANIFEST.json').read_bytes())
    rows = manifest['files']
    check(len(rows) == manifest['copiedFileCount'], 'Payload count differs from manifest.')
    by_path = {row['path']: row for row in rows}
    check(len(by_path) == len(rows), 'Duplicate manifest path.')
    expected_checksums = ''.join(f"{row['sha256']}  {row['path']}\n" for row in rows)
    check((root / 'MANIFEST.sha256').read_text() == expected_checksums,
          'Checksum list differs from manifest.')
    proof_cache = {}
    reference_count = 0
    kinds = Counter()
    for row in rows:
        relative = Path(row['path'])
        check(not relative.is_absolute() and '..' not in relative.parts,
              'Unsafe manifest path.')
        path = root / relative
        check(path.is_file() and not path.is_symlink(), f'Missing or nonregular payload: {relative}')
        check(path.name not in ('.env', 'local.properties', 'credentials.json', 'token.json')
              and not path.name.startswith('.env.'), f'Forbidden payload name: {relative}')
        data = path.read_bytes()
        check(len(data) == row['bytes'], f'Byte count mismatch: {relative}')
        check(sha(data) == row['sha256'], f'SHA256 mismatch: {relative}')
        kinds[row['kind']] += 1
        if row['kind'] == 'incomplete-code-fragment':
            check(relative.parts[0] == 'FRAGMENTS_INCOMPLETE' and row['completeSource'] is False,
                  f'Incomplete fragment mislabeled: {relative}')
            fragment = json.loads(data)
            check(fragment.get('complete', fragment.get('completeSource', False)) is False,
                  f'Fragment asserts completeness: {relative}')
            check(len(fragment['lines']) == row['codeLinesObserved'],
                  f'Fragment observed-line count mismatch: {relative}')
        for reference in row.get('proofReferences', []):
            proof_name = reference.get('proofPath')
            if proof_name is None:
                proof_name = str(Path(manifest['payloadRoot']) / reference['proof'])
            proof_relative = Path(proof_name)
            check(not proof_relative.is_absolute() and '..' not in proof_relative.parts,
                  'Unsafe proof path.')
            if proof_name not in proof_cache:
                proof_cache[proof_name] = json.loads((root / proof_relative).read_bytes())
            expected = at_pointer(proof_cache[proof_name], reference['pointer'])
            check(expected == reference['expectedSha256'] == row['sha256'],
                  f'Proof hash reference mismatch: {relative}')
            reference_count += 1
    for kind, key in [
        ('complete-source', 'sourceFileCount'),
        ('complete-recorded-patch', 'patchFileCount'),
        ('complete-recorded-file-change', 'recordedHunkFileCount'),
        ('incomplete-code-fragment', 'incompleteCodeFragmentCount'),
        ('recovery-proof', 'proofFileCount'),
    ]:
        check(kinds[kind] == manifest.get(key, 0), f'Kind count mismatch: {kind}')
    scan = json.loads((root / manifest['currentSecretScanReport']).read_bytes())
    check(scan['filesScanned'] == len(rows) and scan['findings'] == [],
          'Secret scan record is missing, stale, or has findings.')
    baseline_files = 0
    if args.baseline_commit:
        def baseline_file(name: str) -> bytes:
            result = subprocess.run(['git', '-c', 'core.hooksPath=/dev/null', 'show',
                                     f'{args.baseline_commit}:{name}'], cwd=root, capture_output=True)
            check(result.returncode == 0, f'Cannot read local baseline file: {name}')
            return result.stdout
        baseline = json.loads(baseline_file('MANIFEST.json'))
        for previous in baseline['files']:
            check(by_path.get(previous['path']) == previous,
                  f'Previous payload entry changed: {previous["path"]}')
            baseline_files += 1
        check((root / 'REMOTE_DESTINATION.json').read_bytes() == baseline_file('REMOTE_DESTINATION.json'),
              'Remote destination record changed.')
        check(manifest['remoteDestination'] == baseline['remoteDestination'],
              'Remote destination in manifest changed.')
    print(json.dumps({
        'result': 'PASS', 'payloadFilesVerified': len(rows),
        'proofHashReferencesVerified': reference_count, 'kindCounts': dict(kinds),
        'baselinePayloadEntriesPreserved': baseline_files,
        'baselinePreservationChecked': bool(args.baseline_commit),
        'manifestSha256': sha((root / 'MANIFEST.json').read_bytes()),
        'qualification': 'Backup integrity only; no application execution or network.',
    }, ensure_ascii=False, indent=2))


if __name__ == '__main__':
    main()
