#!/usr/bin/env python3
"""Check the original mockup image manifest without Android or third-party packages.

Missing originals warn by default while the historical stock is incomplete.
Run with --strict, and use that flag in .github/workflows/maquettes.yml, to make
every missing working reference blocking. Invalid metadata, false presence claims
and corrupted hashes are always blocking. This checks file headers and provenance
metadata, not visual conformity, personal data or the authenticity of a decision.
"""

import argparse
import datetime
import hashlib
import json
from pathlib import Path, PurePosixPath
import re
import struct
import sys


REGISTRY = "docs/developpement/PREUVES_MAQUETTES_ECRANS_2026-11-16.json"
MANIFEST = "docs/maquettes/MANIFESTE_IMAGES.json"
STATUSES = {"retenue", "remplacée", "non finalisée"}


def unique_keys(pairs):
    result = {}
    for key, value in pairs:
        if key in result:
            raise ValueError(f"clé JSON dupliquée : {key}")
        result[key] = value
    return result


def load_json(path):
    return json.loads(path.read_text(encoding="utf-8"), object_pairs_hook=unique_keys)


def image_dimensions(path):
    """Read PNG/JPEG/GIF/WebP dimensions, without decoding or altering pixels."""
    with path.open("rb") as source:
        head = source.read(32)
        if head.startswith(b"\x89PNG\r\n\x1a\n") and head[8:16] == b"\0\0\0\rIHDR":
            if len(head) < 24:
                raise ValueError("en-tête PNG tronqué")
            return struct.unpack(">II", head[16:24])
        if head[:6] in (b"GIF87a", b"GIF89a") and len(head) >= 10:
            return struct.unpack("<HH", head[6:10])
        if head.startswith(b"\xff\xd8"):
            source.seek(2)
            while True:
                if source.read(1) != b"\xff":
                    break
                marker = source.read(1)
                while marker == b"\xff":
                    marker = source.read(1)
                if marker in (b"", b"\xd9", b"\xda"):
                    break
                if marker == b"\x01" or b"\xd0" <= marker <= b"\xd8":
                    continue
                raw_length = source.read(2)
                if len(raw_length) != 2:
                    break
                length = int.from_bytes(raw_length, "big")
                if length < 2:
                    break
                if marker in [bytes([n]) for n in (0xC0, 0xC1, 0xC2, 0xC3,
                                                 0xC5, 0xC6, 0xC7, 0xC9,
                                                 0xCA, 0xCB, 0xCD, 0xCE, 0xCF)]:
                    frame = source.read(5)
                    if length < 7 or len(frame) != 5:
                        break
                    height, width = struct.unpack(">HH", frame[1:5])
                    return width, height
                source.seek(length - 2, 1)
            raise ValueError("en-tête JPEG sans dimensions lisibles")
        if head[:4] == b"RIFF" and head[8:12] == b"WEBP":
            if head[12:16] == b"VP8X" and len(head) >= 30:
                return (1 + int.from_bytes(head[24:27], "little"),
                        1 + int.from_bytes(head[27:30], "little"))
            if head[12:16] == b"VP8 " and head[23:26] == b"\x9d\x01\x2a":
                width, height = struct.unpack("<HH", head[26:30])
                return width & 0x3FFF, height & 0x3FFF
            if head[12:16] == b"VP8L" and head[20:21] == b"\x2f":
                bits = int.from_bytes(head[21:25], "little")
                return (bits & 0x3FFF) + 1, ((bits >> 14) & 0x3FFF) + 1
        raise ValueError("format ou en-tête non reconnu (PNG, JPEG, GIF, WebP attendus)")


def safe_image_path(root, value):
    if not isinstance(value, str) or "\\" in value:
        raise ValueError("chemin relatif POSIX requis")
    path = PurePosixPath(value)
    if path.is_absolute() or ".." in path.parts or path.parts[:2] != ("docs", "maquettes"):
        raise ValueError("chemin hors de docs/maquettes/")
    resolved = (root / path).resolve()
    if (not resolved.is_relative_to(root)
            or not resolved.is_relative_to((root / "docs/maquettes").resolve())):
        raise ValueError("lien sortant de docs/maquettes/")
    return resolved


def valid_date(value):
    if not isinstance(value, str):
        raise ValueError("date ISO AAAA-MM-JJ requise")
    datetime.date.fromisoformat(value)


def check(root):
    """Return errors, missing working IDs and verified IDs; never modify files."""
    root = Path(root).resolve()
    errors, verified, missing = [], [], []
    try:
        registry = load_json(root / REGISTRY)
        manifest = load_json(root / MANIFEST)
        if not isinstance(registry, dict) or not isinstance(manifest, dict):
            raise ValueError("registre et manifeste doivent être des objets JSON")
        if type(manifest.get("schema_version")) is not int or manifest["schema_version"] != 1:
            raise ValueError("schema_version du manifeste doit être 1")
        valid_date(manifest.get("date_audit"))
        references = registry["references"]
        images = manifest["images"]
        if not isinstance(references, list) or not isinstance(images, list):
            raise ValueError("references et images doivent être des listes")
        reference_map = {}
        for reference in references:
            if not isinstance(reference, dict) or not isinstance(reference.get("id"), str):
                raise ValueError("référence sans identifiant")
            identifier = reference["id"]
            if identifier in reference_map:
                raise ValueError(f"identifiant dupliqué dans le registre : {identifier}")
            if type(reference.get("reference_de_travail")) is not bool:
                raise ValueError(f"reference_de_travail booléen requis : {identifier}")
            if reference.get("image_originale_locale") not in ("absente", "présente"):
                raise ValueError(f"présence de l'image invalide : {identifier}")
            reference_map[identifier] = reference
        working = {key for key, value in reference_map.items() if value["reference_de_travail"]}
        if (type(manifest.get("references_de_travail_attendues")) is not int
                or manifest["references_de_travail_attendues"] != len(working)):
            raise ValueError("nombre de références de travail différent entre registre et manifeste")
    except (OSError, ValueError, KeyError, TypeError) as problem:
        return {"errors": [str(problem)], "missing": [], "verified": [], "expected": 0}

    seen, seen_paths = set(), set()
    for index, image in enumerate(images):
        identifier = f"entrée {index + 1}"
        try:
            if not isinstance(image, dict) or not isinstance(image.get("identifiant"), str):
                raise ValueError("image sans identifiant")
            identifier = image["identifiant"]
            if identifier in seen:
                raise ValueError("identifiant dupliqué dans le manifeste")
            seen.add(identifier)
            if identifier not in reference_map:
                raise ValueError("identifiant absent du registre")
            path = safe_image_path(root, image.get("chemin"))
            if path in seen_paths:
                raise ValueError("chemin déjà attribué à une autre référence")
            seen_paths.add(path)
            if not re.fullmatch(r"[0-9a-f]{64}", str(image.get("sha256", ""))):
                raise ValueError("SHA-256 hexadécimal minuscule requis")
            if any(type(image.get(key)) is not int or image[key] <= 0 for key in ("largeur", "hauteur")):
                raise ValueError("dimensions entières positives requises")
            if image.get("statut_reference") not in STATUSES:
                raise ValueError("statut_reference attendu : retenue, remplacée ou non finalisée")
            valid_date(image.get("date_recuperation"))
            for key in ("source_recuperation", "decision_fondateur"):
                if not isinstance(image.get(key), dict) or not image[key]:
                    raise ValueError(f"{key} doit décrire la preuve ou la décision manquante")
            if not path.exists():
                if identifier not in working:
                    errors.append(f"{identifier} : fichier déclaré absent : {image['chemin']}")
                continue
            if not path.is_file():
                raise ValueError("le chemin ne désigne pas un fichier")
            with path.open("rb") as source:
                digest_builder = hashlib.sha256()
                for chunk in iter(lambda: source.read(1024 * 1024), b""):
                    digest_builder.update(chunk)
                digest = digest_builder.hexdigest()
            if digest != image["sha256"]:
                raise ValueError("empreinte SHA-256 différente du manifeste")
            if image_dimensions(path) != (image["largeur"], image["hauteur"]):
                raise ValueError("dimensions différentes de l'en-tête original")
            verified.append(identifier)
            if reference_map[identifier]["image_originale_locale"] != "présente":
                raise ValueError("fichier vérifié mais registre encore marqué absent")
        except (OSError, ValueError, TypeError, KeyError, struct.error) as problem:
            errors.append(f"{identifier} : {problem}")

    for identifier, reference in sorted(reference_map.items()):
        if identifier not in verified and reference["image_originale_locale"] == "présente":
            errors.append(f"{identifier} : présence annoncée sans fichier et empreinte vérifiés")
        if identifier in working and identifier not in verified:
            missing.append({"identifiant": identifier, "metier": reference.get("metier", "inconnu"),
                            "etat": reference.get("etat")})
    return {"errors": errors, "missing": missing, "verified": sorted(verified),
            "expected": len(working), "working_verified": len(working.intersection(verified))}


def main(argv=None):
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--root", type=Path, default=Path(__file__).resolve().parents[1],
                        help="racine du dépôt (défaut : parent de tools/)")
    parser.add_argument("--strict", action="store_true", help="rendre les images manquantes bloquantes")
    args = parser.parse_args(argv)
    result = check(args.root)
    for error in result["errors"]:
        print(f"ERREUR : {error}", file=sys.stderr)
    for entry in result["missing"]:
        print(f"IMAGE MANQUANTE : {entry['identifiant']} | {entry['metier']} | {entry['etat'] or 'non établi'}")
    print(f"Images de travail vérifiées : {result.get('working_verified', 0)}/{result['expected']} ; "
          f"manquantes : {len(result['missing'])} ; erreurs : {len(result['errors'])}.")
    if result["missing"] and not args.strict:
        print("AVERTISSEMENT : stock incomplet, non bloquant ; --strict rend les absences bloquantes.")
    return int(bool(result["errors"] or (args.strict and result["missing"])))


if __name__ == "__main__":
    sys.exit(main())
