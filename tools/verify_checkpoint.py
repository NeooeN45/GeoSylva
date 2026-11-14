#!/usr/bin/env python3
"""Verify local backup bytes/provenance only; no application code or network."""

from __future__ import annotations

import argparse
import hashlib
import json
import re
import stat
import subprocess
import zipfile
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
    overrides = manifest.get('classificationOverrides', {})
    check(set(overrides) <= set(by_path), 'Classification override references absent payload.')
    baseline_members_verified = 0
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
        effective_kind = overrides.get(row['path'], {}).get('kind', row['kind'])
        kinds[effective_kind] += 1
        if effective_kind == 'superseded-incomplete-source-excerpt':
            correction = overrides[row['path']]
            check(correction['completeSource'] is False and correction['originalSha256'] == row['sha256'],
                  'Superseded source classification mismatch.')
            replacement = by_path.get(correction['replacementPath'])
            check(replacement is not None and replacement['kind'] == 'complete-source'
                  and replacement['sha256'] == correction['replacementSha256'],
                  'Superseded source replacement mismatch.')
            replacement_data = (root / correction['replacementPath']).read_bytes()
            check(replacement_data.splitlines()[0].startswith(b'@file:OptIn')
                  and b''.join(replacement_data.splitlines(True)[2:]) == data,
                  'Superseded Gestures header difference does not match its erratum.')
            correction_proof = json.loads((root / correction['proofPath']).read_bytes())
            check(correction_proof['replacement']['sha256'] == replacement['sha256']
                  and correction_proof['replacement']['complete'] is True,
                  'Gestures correction proof mismatch.')
        if effective_kind == 'baseline-source-archive':
            baseline = manifest['baseline']
            check(baseline['archivePath'] == row['path'] and baseline['sha256'] == row['sha256']
                  and baseline['excludedFromRecoverySourceCounts'] is True, 'Baseline classification mismatch.')
            source_manifest = json.loads((root / baseline['sourceFileManifest']).read_bytes())
            expected = {entry['path']: entry for entry in source_manifest['files']}
            with zipfile.ZipFile(path) as z:
                members = z.infolist()
                check(len(members) == len({member.filename for member in members}), 'Duplicate baseline member.')
                actual = set()
                for member in members:
                    member_path = Path(member.filename)
                    check(not member_path.is_absolute() and '..' not in member_path.parts
                          and '\\' not in member.filename and not re.match(r'^[A-Za-z]:', member.filename)
                          and '.git' not in member_path.parts, 'Unsafe baseline archive path.')
                    check(member.filename.startswith(source_manifest['prefix'])
                          and not stat.S_ISLNK(member.external_attr >> 16), 'Unsafe baseline type or prefix.')
                    if member.is_dir(): continue
                    member_name = member.filename[len(source_manifest['prefix']):]
                    check(member_name in expected, 'Baseline source missing from source manifest.')
                    member_data = z.read(member)
                    check(len(member_data) == expected[member_name]['bytes']
                          and sha(member_data) == expected[member_name]['sha256'], 'Baseline member hash mismatch.')
                    actual.add(member_name)
                check(actual == set(expected) and len(actual) == baseline['internalSourceFiles'] == 1389,
                      'Baseline internal file count mismatch.')
                check(z.testzip() is None, 'Baseline ZIP CRC failure.')
                baseline_members_verified = len(actual)
        if effective_kind == 'incomplete-code-fragment':
            check(relative.parts[0] == 'FRAGMENTS_INCOMPLETE' and row['completeSource'] is False,
                  f'Incomplete fragment mislabeled: {relative}')
            if row.get('fragmentFormat') == 'text':
                data.decode('utf-8')
                check(len(data.splitlines()) == row['codeLinesObserved'],
                      f'Text fragment line count mismatch: {relative}')
                check(row['compilableAsSource'] is False,
                      f'Text fragment marked compilable: {relative}')
            else:
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
        ('baseline-source-archive', 'baselineArchiveCount'),
        ('recovered-project-document', 'recoveredProjectDocumentCount'),
        ('superseded-incomplete-source-excerpt', 'supersededIncompleteSourceExcerptCount'),
    ]:
        check(kinds[kind] == manifest.get(key, 0), f'Kind count mismatch: {kind}')
    scan = json.loads((root / manifest['currentSecretScanReport']).read_bytes())
    check(scan['filesScanned'] == len(rows) and scan['findings'] == [],
          'Secret scan record is missing, stale, or has findings.')
    catalogue_path = manifest.get('currentSourceCatalogue')
    distinct_paths = distinct_versions = None
    if catalogue_path:
        catalogue = json.loads((root / catalogue_path).read_bytes())
        if catalogue.get('schema') == 'geosylva.complete-source-catalogue.v2':
            distinct_paths = len(catalogue['paths'])
            distinct_versions = 0
            source_artifacts = set()
            for group in catalogue['paths']:
                check(not Path(group['sourcePath']).is_absolute(), 'Technical source path is absolute.')
                shas = set()
                for artifact in group['artifacts']:
                    row = by_path.get(artifact['backupPath'])
                    check(row is not None and row['kind'] == 'complete-source'
                          and row['sha256'] == artifact['sha256'], 'Source catalogue artifact mismatch.')
                    source_artifacts.add(artifact['backupPath'])
                    shas.add(artifact['sha256'])
                check(group['artifactCount'] == len(group['artifacts'])
                      and group['distinctByteVersionCount'] == len(shas), 'Source catalogue group count mismatch.')
                distinct_versions += len(shas)
            for artifact in catalogue['standaloneCompleteSources']:
                row = by_path.get(artifact['backupPath'])
                check(row is not None and row['kind'] == 'complete-source'
                      and row['sha256'] == artifact['sha256'], 'Standalone source catalogue mismatch.')
                source_artifacts.add(artifact['backupPath'])
            check(len(source_artifacts) == kinds['complete-source'] == catalogue['completeSourceArtifactCount'],
                  'Catalogue complete artifact count mismatch.')
            check(distinct_paths == catalogue['distinctTechnicalSourcePathCount']
                  == manifest['distinctCompleteSourcePathCount'], 'Distinct technical source path count mismatch.')
            check(distinct_versions == catalogue['distinctTechnicalSourceByteVersionCount']
                  == manifest['distinctCompleteSourceVersionCount'], 'Distinct byte version count mismatch.')
            for alias in catalogue['latestRecoveredAliases']:
                source = by_path.get(alias['path'])
                target = by_path.get(alias['aliasesSourceVersionPath'])
                check(source is not None and target is not None
                      and source['sha256'] == target['sha256'] == alias['sha256'],
                      'Latest recovered alias differs from its preserved source state.')
    fragment_index_path = manifest.get('currentIncompleteFragmentIndex')
    if fragment_index_path:
        fragment_index = json.loads((root / fragment_index_path).read_bytes())
        indexed = {row['path']: row for row in fragment_index['fragments']}
        check(len(indexed) == kinds['incomplete-code-fragment'], 'Incomplete fragment index count mismatch.')
        for path, entry in indexed.items():
            row = by_path.get(path)
            check(row is not None and row['kind'] == 'incomplete-code-fragment'
                  and row['sha256'] == entry['sha256'] and entry['completeSource'] is False,
                  'Incomplete fragment index entry mismatch.')
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
        'distinctTechnicalSourcePaths': distinct_paths,
        'distinctTechnicalSourceByteVersions': distinct_versions,
        'baselineInternalSourceFilesVerified': baseline_members_verified,
        'manifestSha256': sha((root / 'MANIFEST.json').read_bytes()),
        'qualification': 'Backup integrity only; no application execution or network.',
    }, ensure_ascii=False, indent=2))


if __name__ == '__main__':
    main()
