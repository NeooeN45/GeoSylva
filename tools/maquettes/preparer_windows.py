"""Assembler le dossier Windows portable à partir de la distribution officielle."""
import argparse
import hashlib
import io
from pathlib import Path
import shutil
from urllib.request import urlopen
from zipfile import ZipFile

VERSION = '3.14.8'
URL = f'https://www.python.org/ftp/python/{VERSION}/python-{VERSION}-embed-amd64.zip'
# Empreinte du paquet Windows x64 publié sur python.org/downloads/release/python-3148/.
SHA256 = 'a93abe456ab01bd96d7a085b3cdb6566b3063f4241360d114142fbdb07f0a310'


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('destination', type=Path)
    parser.add_argument('--runtime', type=Path, help='copie locale du ZIP officiel')
    args = parser.parse_args()
    if args.runtime:
        package = args.runtime.read_bytes()
    else:
        with urlopen(URL, timeout=60) as source:
            package = source.read()
    if hashlib.sha256(package).hexdigest() != SHA256:
        raise SystemExit('Empreinte différente de la distribution Python officielle.')
    args.destination.mkdir(parents=True, exist_ok=False)
    runtime = args.destination / 'moteur'
    with ZipFile(io.BytesIO(package)) as archive:
        archive.extractall(runtime)
    sources = Path(__file__).resolve().parent
    for name in ('recuperer.py', 'export_chat.py', 'index.json'):
        shutil.copyfile(sources / name, runtime / name)
    launcher = '@echo off\r\n"%~dp0moteur\\python.exe" -X utf8 "%~dp0moteur\\recuperer.py"\r\nif errorlevel 1 pause\r\n'
    (args.destination / 'Ouvrir.cmd').write_bytes(launcher.encode('ascii'))
    shutil.copyfile(sources / 'LISEZ-MOI.md', args.destination / 'LISEZ-MOI.md')


if __name__ == '__main__':
    main()
