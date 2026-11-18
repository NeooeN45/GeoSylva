"""Vérifier l'index réellement distribué avec le lecteur d'export."""

import json
from pathlib import Path
import subprocess
import sys
import tempfile
import unittest
import zipfile


TOOLS = Path(__file__).resolve().parent
ROOT = TOOLS.parents[1]


def read_json(path):
    return json.loads(path.read_text(encoding="utf-8"))


class IndexTest(unittest.TestCase):
    def test_registre_et_chaines_de_preuve(self):
        registry = read_json(ROOT / "docs/developpement/PREUVES_MAQUETTES_ECRANS_2026-11-16.json")
        entries = read_json(TOOLS / "index.json")["references"]
        working = [r["id"] for r in registry["references"] if r["reference_de_travail"]]
        self.assertEqual(len(working), registry["nombre_references_de_travail"])
        self.assertCountEqual([r["id"] for r in entries], working)
        manifest = read_json(ROOT / "docs/maquettes/MANIFESTE_IMAGES.json")
        self.assertCountEqual(
            [(r["id"], sha) for r in entries for sha in r["sha256_deja_conserves"]],
            [(image["identifiant"], image["sha256"]) for image in manifest["images"]],
        )
        for ref in entries:
            with self.subTest(identifiant=ref["id"]):
                evidence = ref["preuves_sha256"]
                self.assertEqual(set(ref["sha256"]), {p["sha256"] for p in evidence})
                for p in evidence:
                    self.assertRegex(p["sha256"], r"^[a-f0-9]{64}$")
                    self.assertGreater(p["ligne_sortie"], 0)
                    self.assertTrue(p["nom"] and p["tour"])
                    for link in [p] + ([p["preuve_copie_exec"]] if "preuve_copie_exec" in p else []):
                        self.assertTrue(link["archive"] and link["element"])
                        self.assertTrue(link["pointeur"].startswith("/turns/"))

    def test_lecture_crlf_des_images_deja_conservees(self):
        manifest = read_json(ROOT / "docs/maquettes/MANIFESTE_IMAGES.json")["images"]
        with tempfile.TemporaryDirectory() as temp:
            directory = Path(temp)
            for name in ("recuperer.py", "export_chat.py", "index.json"):
                source = (TOOLS / name).read_text(encoding="utf-8")
                (directory / name).write_bytes(source.replace("\n", "\r\n").encode("utf-8"))
            archive = directory / "export test.zip"
            with zipfile.ZipFile(archive, "w") as export:
                for i, image in enumerate(manifest):
                    path = ROOT / image["chemin"]
                    export.write(path, f"image-{i}{path.suffix}")
            output = directory / "résultats"
            subprocess.run([sys.executable, str(directory / "recuperer.py"), str(archive),
                            "--destination", str(output)], check=True, capture_output=True)
            reports = list(output.glob("*/rapport.json"))
            self.assertEqual(len(reports), 1)
            report = read_json(reports[0])
            self.assertCountEqual(report["deja_conservees"], [image["sha256"] for image in manifest])
            self.assertEqual(report["images"], [])


if __name__ == "__main__":
    unittest.main()
