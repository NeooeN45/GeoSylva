"""Repérer les originaux Géosylva dans un export ZIP sur l'ordinateur du Fondateur."""
import argparse
from collections import defaultdict
from uuid import uuid4
from dataclasses import dataclass
from datetime import datetime
import hashlib
import json
import os
from pathlib import Path, PurePosixPath
import re
import shutil
import sys
import subprocess
import tempfile
import zipfile

from export_chat import scan_conversations

MAX_LOT = 24 * 1024 * 1024
COPY_BUFFER = 1024 * 1024
INDEX_PATH = Path(__file__).with_name('index.json')


def image_extension(header):
    if header.startswith(b'\x89PNG\r\n\x1a\n'):
        return '.png'
    if header.startswith(b'\xff\xd8\xff'):
        return '.jpg'
    if header[:6] in (b'GIF87a', b'GIF89a'):
        return '.gif'
    if header[:4] == b'RIFF' and header[8:12] == b'WEBP':
        return '.webp'
    return None


def sha256_stream(stream, header):
    digest = hashlib.sha256(header)
    for block in iter(lambda: stream.read(COPY_BUFFER), b''):
        digest.update(block)
    return digest.hexdigest()


def basename(path):
    return PurePosixPath(path.replace('\\', '/')).name


def conversation_links(filename, pointers):
    # Some exports append a descriptive name to the file-service identifier.
    name = basename(filename)
    candidates = [name, PurePosixPath(name).stem]
    for pattern in (r'^(file[_-][0-9a-fA-F]{8}(?:-[0-9a-fA-F]{4}){3}-[0-9a-fA-F]{12})(?=[._-]|$)',
                    r'^(file[_-][a-zA-Z0-9]+)(?=[._-]|$)'):
        match = re.match(pattern, name)
        if match:
            candidates.append(match.group(1))
    return [p for key in dict.fromkeys(candidates) for p in pointers.get(key, [])]


@dataclass
class Selection:
    member: zipfile.ZipInfo
    sha: str
    ext: str
    proof: str
    ids: list
    links: list

    @property
    def verified(self):
        return self.proof == 'sha256_historique' and len(self.ids) == 1


def scan_images(zf, index, pointers, warnings):
    by_hash, by_exec = defaultdict(set), {}
    by_historical_stem = defaultdict(set)
    saved = set()
    refs = {ref['id']: ref for ref in index['references']}
    for ref in refs.values():
        for digest in ref['sha256']:
            by_hash[digest].add(ref['id'])
        if ref['exec_id']:
            by_exec[ref['exec_id'] + '.png'] = ref['id']
        for evidence in ref.get('preuves_sha256', []) + ref.get('preuves_nom', []):
            stem = PurePosixPath(evidence['nom']).stem.casefold()
            by_historical_stem[stem].add(ref['id'])
        saved.update(ref['sha256_deja_conserves'])
    already_found, selections = set(), {}
    image_count = unreadable = 0
    for member in zf.infolist():
        if member.is_dir() or member.file_size < 3:
            continue
        try:
            with zf.open(member) as stream:
                header = stream.read(16)
                ext = image_extension(header)
                if not ext:
                    continue
                image_count += 1
                digest = sha256_stream(stream, header)
        except (OSError, RuntimeError, zipfile.BadZipFile):
            unreadable += 1
            continue
        if digest in saved:
            already_found.add(digest)
            continue
        linked = conversation_links(member.filename, pointers)
        filename = basename(member.filename).lower()
        # ChatGPT may prefix the original filename with its file-service ID.
        # Accept a changed image extension after recompression, keeping the whole stem.
        stem = re.sub(
            r'^file[_-](?:[0-9a-f]{8}(?:-[0-9a-f]{4}){3}-[0-9a-f]{12}|[a-z0-9]+)[_-]',
            '', PurePosixPath(filename).stem.casefold(), count=1,
        )
        if digest in by_hash:
            proof, ids = 'sha256_historique', sorted(by_hash[digest])
        elif filename in by_exec and ext == '.png':
            proof, ids = 'nom_generation', [by_exec[filename]]
            if refs[ids[0]]['sha256']:
                warnings.append(f'{ids[0]} : le nom de génération correspond, mais les octets diffèrent de la référence. Vérifier le candidat.')
        elif stem in by_historical_stem:
            proof, ids = 'nom_historique', sorted(by_historical_stem[stem])
        elif linked:
            proof, ids = 'conversation', []
        else:
            continue
        previous = selections.get(digest)
        if previous is None:
            selections[digest] = Selection(member, digest, ext, proof, ids, linked)
        else:
            for item in linked:
                if item not in previous.links:
                    previous.links.append(item)
            if proof in ('nom_generation', 'nom_historique') and previous.proof != 'sha256_historique':
                if proof == 'nom_generation' or previous.proof == 'conversation':
                    previous.proof = proof
                previous.ids = sorted(set(previous.ids + ids))
    if unreadable:
        warnings.append(f'{unreadable} membre(s) illisible(s). La recherche est partielle.')
    return selections, sorted(already_found), image_count


def as_json(data):
    return (json.dumps(data, ensure_ascii=False, indent=2) + '\n').encode('utf-8')


def pack_name(verified, seq):
    return ('originaux' if verified else 'candidats-a-verifier') + f'-{seq:03}.zip'


def pack_images(zf, selections, dest):
    rows = []
    for verified in (True, False):
        items = [s for s in selections.values() if s.verified == verified]
        groups = []
        group, weight = [], 0
        for item in items:
            row = {'sha256': item.sha, 'octets': item.member.file_size,
                   'fichier': item.sha + item.ext, 'preuve': item.proof,
                   'identifiants': item.ids, 'liens_fil': item.links}
            # ZIP_STORED: reserve both directory entries plus the JSON proof per image.
            cost = item.member.file_size + len(as_json(row)) + 1024
            if group and weight + cost + 4096 > MAX_LOT:
                groups.append(group)
                group, weight = [], 0
            group.append((item, row))
            weight += cost
        if group:
            groups.append(group)
        for n, group in enumerate(groups, 1):
            lot = pack_name(verified, n)
            with zipfile.ZipFile(dest / lot, 'x', compression=zipfile.ZIP_STORED) as output:
                for item, row in group:
                    row['lot'] = lot
                    with zf.open(item.member) as source, output.open(row['fichier'], 'w', force_zip64=True) as target:
                        shutil.copyfileobj(source, target, COPY_BUFFER)
                output.writestr('preuves.json', as_json([row for _, row in group]))
            rows.extend(row for _, row in group)
    return rows


def run_archive(archive, destination, index):
    destination.mkdir(parents=True, exist_ok=True)
    with zipfile.ZipFile(archive, 'r') as zf:
        pointers, warnings = scan_conversations(zf, index['thread_id'], index['title'])
        selections, known, checked = scan_images(zf, index, pointers, warnings)
        # A partial run never looks like a completed delivery. Preserve existing runs.
        with tempfile.TemporaryDirectory(dir=destination) as scratch:
            stage = Path(scratch) / 'selection'
            stage.mkdir()
            rows = pack_images(zf, selections, stage)
            oversize = [p.name for p in stage.glob('*.zip') if p.stat().st_size > MAX_LOT]
            warnings.extend(f'{n} contient une seule grande image et dépasse 24 Mio.' for n in oversize)
            stats = {'images_examinees': checked, 'images': rows, 'deja_conservees': known,
                     'avertissements': warnings}
            (stage / 'rapport.json').write_bytes(as_json(stats))
            final = destination / ('Geosylva-' + datetime.now().strftime('%Y%m%d-%H%M%S-') + uuid4().hex[:12])
            stage.rename(final)
    return final


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('archive', type=Path, nargs='?', help='ZIP reçu de ChatGPT')
    parser.add_argument('--destination', type=Path, help='dossier parent du résultat')
    args = parser.parse_args()
    archive = args.archive
    if archive is None and os.name == 'nt':
        # The standard Windows dialog works with the portable Python distribution.
        dialog = """
        Add-Type -AssemblyName System.Windows.Forms
        [Console]::OutputEncoding = [Text.UTF8Encoding]::new($false)
        $picker = New-Object System.Windows.Forms.OpenFileDialog
        $picker.Title = 'Choisir le ZIP exporté par ChatGPT'
        $picker.Filter = 'Archive ZIP (*.zip)|*.zip|Tous les fichiers (*.*)|*.*'
        if ($picker.ShowDialog() -eq 'OK') { [Console]::Write($picker.FileName) }
        """
        selected = subprocess.check_output(['powershell.exe', '-NoProfile', '-STA', '-Command', dialog], encoding='utf-8')
        if not selected:
            return 0
        archive = Path(selected)
    if archive is None:
        parser.error('Indiquer le chemin du ZIP.')
    print('Lecture locale du ZIP. Sur un export de plusieurs gigaoctets, garder cette fenêtre ouverte.', flush=True)
    index = json.loads(INDEX_PATH.read_text(encoding='utf-8'))
    output = run_archive(archive, args.destination or archive.resolve().parent, index)
    report = json.loads((output / 'rapport.json').read_text(encoding='utf-8'))
    exact = sum(row['lot'].startswith('originaux-') for row in report['images'])
    print(f"{exact} image(s) reconnue(s) par empreinte, {len(report['images']) - exact} candidate(s), "
          f"{len(report['deja_conservees'])} image(s) déjà sauvegardée(s) repérée(s).\nRésultat dans {output}")
    if not args.archive and os.name == 'nt':
        os.startfile(output)
    return 0


if __name__ == '__main__':
    try:
        sys.exit(main())
    except (OSError, ValueError, zipfile.BadZipFile, RuntimeError) as error:
        sys.exit(f'Lecture interrompue. {error}')
