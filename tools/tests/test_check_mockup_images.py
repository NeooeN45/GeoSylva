"""Manifest contract tests; all binary fixtures live in temporary directories.

The one-pixel PNG is only a parser fixture, never a mockup or visual reference.
"""

import base64
import contextlib
import hashlib
import importlib.util
import io
import json
from pathlib import Path
import struct
import tempfile
import unittest


SPEC = importlib.util.spec_from_file_location(
    "check_mockup_images", Path(__file__).resolve().parents[1] / "check_mockup_images.py")
checker = importlib.util.module_from_spec(SPEC)
SPEC.loader.exec_module(checker)
PNG_FIXTURE = base64.b64decode(
    "iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAQAAAC1HAwCAAAAC0lEQVR42mP8/x8AAwMCAO+/"
    "a9sAAAAASUVORK5CYII=")


class MockupImagesTest(unittest.TestCase):
    def setUp(self):
        temporary = tempfile.TemporaryDirectory()
        self.addCleanup(temporary.cleanup)
        self.root = Path(temporary.name)
        self.registry = {
            "references": [{"id": "commun/test", "reference_de_travail": True,
                            "image_originale_locale": "absente", "metier": "commun",
                            "etat": "non codé"}]}
        self.manifest = {"schema_version": 1, "date_audit": "2026-11-16",
                         "references_de_travail_attendues": 1, "images": []}
        self.write_json()

    def write_json(self):
        for name, value in ((checker.REGISTRY, self.registry),
                            (checker.MANIFEST, self.manifest)):
            path = self.root / name
            path.parent.mkdir(parents=True, exist_ok=True)
            path.write_text(json.dumps(value, ensure_ascii=False), encoding="utf-8")

    def add_image(self):
        path = self.root / "docs/maquettes/commun/test.png"
        path.parent.mkdir(parents=True, exist_ok=True)
        path.write_bytes(PNG_FIXTURE)
        self.manifest["images"] = [{
            "identifiant": "commun/test", "chemin": "docs/maquettes/commun/test.png",
            "sha256": hashlib.sha256(PNG_FIXTURE).hexdigest(), "largeur": 1, "hauteur": 1,
            "source_recuperation": {"preuve": "fixture de test temporaire"},
            "statut_reference": "non finalisée", "date_recuperation": "2026-11-16",
            "decision_fondateur": {"statut": "aucune, fixture technique"}}]
        self.registry["references"][0]["image_originale_locale"] = "présente"
        self.write_json()
        return path

    def run_cli(self, *args):
        with contextlib.redirect_stdout(io.StringIO()) as output:
            with contextlib.redirect_stderr(io.StringIO()) as errors:
                status = checker.main(["--root", str(self.root), *args])
        return status, output.getvalue(), errors.getvalue()

    def test_empty_manifest_warns_with_every_working_id_and_does_not_block(self):
        status, output, errors = self.run_cli()
        self.assertEqual((status, errors), (0, ""))
        self.assertIn("IMAGE MANQUANTE : commun/test | commun | non codé", output)
        self.assertIn("0/1", output)

    def test_strict_blocks_missing_images(self):
        self.assertEqual(self.run_cli("--strict")[0], 1)

    def test_matching_original_passes_strict(self):
        self.add_image()
        result = checker.check(self.root)
        self.assertEqual(result["errors"], [])
        self.assertEqual(result["verified"], ["commun/test"])
        self.assertEqual(self.run_cli("--strict")[0], 0)

    def test_hash_corruption_always_blocks(self):
        path = self.add_image()
        path.write_bytes(PNG_FIXTURE + b"corruption")
        self.assertIn("SHA-256 différente", self.run_cli()[2])
        self.assertEqual(self.run_cli()[0], 1)

    def test_false_presence_without_manifest_always_blocks(self):
        self.registry["references"][0]["image_originale_locale"] = "présente"
        self.write_json()
        self.assertIn("présence annoncée", self.run_cli()[2])
        self.assertEqual(self.run_cli()[0], 1)

    def test_missing_declared_file_warns_if_not_marked_present(self):
        self.add_image().unlink()
        self.registry["references"][0]["image_originale_locale"] = "absente"
        self.write_json()
        self.assertEqual(self.run_cli()[0], 0)
        self.assertEqual(self.run_cli("--strict")[0], 1)

    def test_present_file_requires_registry_synchronization(self):
        self.add_image()
        self.registry["references"][0]["image_originale_locale"] = "absente"
        self.write_json()
        self.assertIn("registre encore marqué absent", self.run_cli()[2])

    def test_path_traversal_always_blocks(self):
        self.add_image()
        self.manifest["images"][0]["chemin"] = "docs/maquettes/../../secret.png"
        self.write_json()
        self.assertIn("chemin hors", self.run_cli()[2])

    def test_symlink_escape_always_blocks(self):
        self.add_image()
        outside = self.root / "outside.png"
        outside.write_bytes(PNG_FIXTURE)
        path = self.root / self.manifest["images"][0]["chemin"]
        path.unlink()
        path.symlink_to(outside)
        self.assertIn("lien sortant", self.run_cli()[2])

    def test_unknown_id_always_blocks(self):
        self.add_image()
        self.manifest["images"][0]["identifiant"] = "commun/inconnu"
        self.write_json()
        self.assertIn("identifiant absent du registre", self.run_cli()[2])

    def test_duplicate_manifest_id_always_blocks(self):
        self.add_image()
        self.manifest["images"].append(dict(self.manifest["images"][0]))
        self.write_json()
        self.assertIn("identifiant dupliqué", self.run_cli()[2])

    def test_duplicate_registry_id_always_blocks(self):
        self.registry["references"].append(dict(self.registry["references"][0]))
        self.write_json()
        self.assertIn("identifiant dupliqué dans le registre", self.run_cli()[2])

    def test_lowering_expected_count_cannot_hide_missing_reference(self):
        self.manifest["references_de_travail_attendues"] = 0
        self.write_json()
        self.assertIn("nombre de références", self.run_cli()[2])

    def test_wrong_dimensions_always_block(self):
        self.add_image()
        self.manifest["images"][0]["largeur"] = 2
        self.write_json()
        self.assertIn("dimensions différentes", self.run_cli()[2])

    def test_arbitrary_file_with_matching_hash_is_not_an_image(self):
        path = self.add_image()
        path.write_bytes(b"not an image")
        self.manifest["images"][0]["sha256"] = hashlib.sha256(path.read_bytes()).hexdigest()
        self.write_json()
        self.assertIn("en-tête non reconnu", self.run_cli()[2])

    def test_missing_manifest_is_error_even_without_strict(self):
        (self.root / checker.MANIFEST).unlink()
        self.assertEqual(self.run_cli()[0], 1)

    def test_invalid_schema_is_error(self):
        self.manifest["schema_version"] = 2
        self.write_json()
        self.assertIn("schema_version", self.run_cli()[2])

    def test_boolean_is_not_a_schema_version(self):
        self.manifest["schema_version"] = True
        self.write_json()
        self.assertEqual(self.run_cli()[0], 1)

    def test_metadata_proof_cannot_be_empty(self):
        self.add_image()
        self.manifest["images"][0]["source_recuperation"] = {}
        self.write_json()
        self.assertIn("source_recuperation", self.run_cli()[2])

    def test_duplicate_json_keys_are_rejected(self):
        (self.root / checker.MANIFEST).write_text('{"images": [], "images": []}', encoding="utf-8")
        self.assertIn("clé JSON dupliquée", self.run_cli()[2])

    def test_jpeg_dimensions_after_metadata_segment(self):
        # Header-only fixture tests the parser, not image authenticity or decoding.
        path = self.root / "parser-fixture.jpg"
        path.write_bytes(b"\xff\xd8\xff\xe0\x00\x04xx\xff\xc0\x00\x11\x08"
                         + struct.pack(">HH", 1774, 887) + b"\x03\x00" * 5)
        self.assertEqual(checker.image_dimensions(path), (887, 1774))

    def test_gif_dimensions(self):
        path = self.root / "parser-fixture.gif"
        path.write_bytes(b"GIF89a" + struct.pack("<HH", 887, 1774))
        self.assertEqual(checker.image_dimensions(path), (887, 1774))

    def test_extended_webp_dimensions(self):
        path = self.root / "parser-fixture.webp"
        path.write_bytes(b"RIFF\x00\x00\x00\x00WEBPVP8X\x0a\x00\x00\x00"
                         + b"\x00" * 4 + (886).to_bytes(3, "little")
                         + (1773).to_bytes(3, "little"))
        self.assertEqual(checker.image_dimensions(path), (887, 1774))

    def test_truncated_jpeg_is_rejected(self):
        path = self.root / "parser-fixture.jpg"
        path.write_bytes(b"\xff\xd8\xff\xe0\x00\x04xx")
        with self.assertRaisesRegex(ValueError, "JPEG sans dimensions"):
            checker.image_dimensions(path)


if __name__ == "__main__":
    unittest.main()
