"""Integration checks for the local export reader, using temporary ZIPs only."""

import hashlib
import json
import struct
import tempfile
import unittest
import zipfile
import zlib
from pathlib import Path

from recuperer import run_archive


THREAD = "01a1968b-6cf3-75ae-8799-765a8b7a3047"
MESSAGE = "d041183c-50bd-4b96-b704-d054f9da60f6"
GENERATION = "exec-66361a92-0e03-4913-87ad-b667608d4a77"
FILE = "file-00000000112233445566778899aabbcc"
OTHER_FILE = "file-00000000ffeeddccbbaa998877665544"


def pixel(red):
    """An actual 1x1 RGB PNG, solely an in-memory file-format fixture."""
    def chunk(kind, data):
        return struct.pack(">I", len(data)) + kind + data + struct.pack(">I", zlib.crc32(kind + data))

    ihdr = struct.pack(">IIBBBBB", 1, 1, 8, 2, 0, 0, 0)
    return (b"\x89PNG\r\n\x1a\n" + chunk(b"IHDR", ihdr)
            + chunk(b"IDAT", zlib.compress(bytes([0, red, 40, 80]))) + chunk(b"IEND", b""))


def digest(data):
    return hashlib.sha256(data).hexdigest()


def reference(identifier, *, generation=None, historical=(), preserved=()):
    return {"id": identifier, "exec_id": generation, "sha256": list(historical),
            "preuves_sha256": [], "sha256_deja_conserves": list(preserved)}


def conversation(identifier, title, file_key):
    content = {"content_type": "multimodal_text", "parts": [
        "Private conversation body must stay here.",
        {"content_type": "image_asset_pointer", "asset_pointer": "file-service://" + file_key},
    ]}
    message = {"id": MESSAGE, "author": {"role": "user"}, "content": content}
    return {"conversation_id": identifier, "title": title,
            "mapping": {MESSAGE: {"id": MESSAGE, "message": message}}}


class RecoveryTest(unittest.TestCase):
    def setUp(self):
        temp = tempfile.TemporaryDirectory()
        self.addCleanup(temp.cleanup)
        self.root = Path(temp.name)
        self.archive = self.root / "private-export.zip"
        self.outputs = self.root / "results"
        self.outputs.mkdir()

    def make_archive(self, files):
        with zipfile.ZipFile(self.archive, "w", zipfile.ZIP_DEFLATED) as archive:
            for name, data in files:
                archive.writestr(name, data)

    def recover(self, *refs):
        index = {"schema": 1, "thread_id": THREAD, "title": "Développement de Géosylva maquette",
                 "references": list(refs)}
        location = run_archive(self.archive, self.outputs, index)
        self.assertEqual(location.parent, self.outputs)
        report = json.loads((location / "rapport.json").read_text(encoding="utf-8"))
        return location, report

    def test_renamed_and_duplicate_images_keep_exact_bytes(self):
        original = pixel(81)
        sha = digest(original)
        self.make_archive([("renamed/innocent.png", original),
                           ("another/copy.png", original), ("summary/unknown.png", pixel(6))])
        original_archive_hash = digest(self.archive.read_bytes())
        folder, report = self.recover(reference("technicien/synthese/proposition-02-a", historical=[sha]))
        self.assertEqual(len(report["images"]), 1)
        row = report["images"][0]
        self.assertEqual(row["preuve"], "sha256_historique")
        self.assertEqual(row["sha256"], sha)
        self.assertEqual(row["octets"], len(original))
        self.assertEqual(row["identifiants"], ["technicien/synthese/proposition-02-a"])
        with zipfile.ZipFile(folder / row["lot"]) as lot:
            self.assertEqual(lot.read(row["fichier"]), original)
            self.assertIn("preuves.json", lot.namelist())
        folder2, _ = self.recover(reference("technicien/synthese/proposition-02-a", historical=[sha]))
        self.assertNotEqual(folder, folder2)
        self.assertEqual(digest(self.archive.read_bytes()), original_archive_hash)

    def test_previously_preserved_pieces_are_counted_once_without_output(self):
        data = pixel(1)
        sha = digest(data)
        self.make_archive([("a.png", data), ("b.png", data)])
        folder, report = self.recover(reference("cartographie/72", historical=[sha], preserved=[sha]))
        self.assertEqual(report["deja_conservees"], [sha])
        self.assertEqual(report["images"], [])
        self.assertFalse(any(folder.rglob("*.zip")))

    def test_generation_filename_requires_exact_identifier(self):
        by_name = pixel(21)
        self.make_archive([("generated/" + GENERATION + ".png", by_name),
                           ("generated/" + GENERATION + "-miniature.png", pixel(22)),
                           ("generated/old-" + GENERATION + ".png", pixel(23)),
                           ("generated/" + GENERATION + ".png.tmp.png", pixel(24))])
        _, report = self.recover(reference("cartographie/59", generation=GENERATION))
        self.assertEqual([(r["sha256"], r["preuve"], r["identifiants"]) for r in report["images"]],
                         [(digest(by_name), "nom_generation", ["cartographie/59"])])

    def test_assets_from_target_chat_are_separate_and_original_names_stay_private(self):
        target = conversation(THREAD, "Développement de Géosylva maquette", FILE)
        unrelated = conversation("bb8cacf0-e9c5-4f60-81c0-5445791be3cb", "Private medical notes", OTHER_FILE)
        unknown, original, private = pixel(1), pixel(2), pixel(3)
        self.make_archive([("conversations-000.json", json.dumps([unrelated, target])),
                           ("media/" + FILE + "-private-filename.png", unknown),
                           ("media/" + OTHER_FILE + "-private-other.png", private),
                           ("other/unknown-name.png", original)])
        folder, report = self.recover(reference("cartographie/31", historical=[digest(original)]))
        rows = {r["sha256"]: r for r in report["images"]}
        self.assertEqual(set(rows), {digest(original), digest(unknown)})
        candidate = rows[digest(unknown)]
        self.assertEqual(candidate["preuve"], "conversation")
        self.assertEqual(candidate["identifiants"], [])
        self.assertNotEqual(candidate["lot"], rows[digest(original)]["lot"])
        visible = [(folder / "rapport.json").read_text(encoding="utf-8")]
        for pack in folder.glob("*.zip"):
            with zipfile.ZipFile(pack) as archive:
                visible.extend(archive.namelist())
                visible.append(archive.read("preuves.json").decode("utf-8"))
                self.assertTrue(all(".." not in Path(n).parts for n in archive.namelist()))
        for private_value in ("Private conversation body", "Private medical notes",
                              "private-filename", "private-other", "private-export.zip"):
            self.assertNotIn(private_value, "\n".join(visible))

    def test_untrusted_member_paths_cannot_escape_output(self):
        image = pixel(9)
        self.make_archive([("../../escaped.png", image)])
        _, report = self.recover(reference("cartographie/08", historical=[digest(image)]))
        self.assertFalse((self.root / "escaped.png").exists())
        self.assertFalse((self.outputs / "escaped.png").exists())
        for row in report["images"]:
            self.assertNotIn("..", Path(row["fichier"]).parts)
            self.assertNotIn("..", Path(row["lot"]).parts)

    def test_opaque_file_ids_remain_case_sensitive(self):
        first, second = "file-AbcDEF123", "file-abcdef123"
        selected = conversation(THREAD, "Développement de Géosylva maquette", first)
        other = conversation("bb8cacf0-e9c5-4f60-81c0-5445791be3cb", "Private", second)
        keep, private = pixel(92), pixel(93)
        self.make_archive([("conversations.json", json.dumps([selected, other])),
                           (first + "-picture.png", keep), (second + "-picture.png", private)])
        _, report = self.recover()
        self.assertEqual([row["sha256"] for row in report["images"]], [digest(keep)])

    def test_filename_cannot_override_contradictory_source_hash(self):
        self.make_archive([("conversations.json", json.dumps([conversation(
            THREAD, "Développement de Géosylva maquette", FILE)])), (GENERATION + ".png", pixel(17))])
        folder, report = self.recover(reference("cartographie/59", generation=GENERATION,
                                                historical=[digest(pixel(18))]))
        self.assertFalse(any(folder.glob("originaux-*.zip")))
        self.assertTrue(report["avertissements"])

    def test_smaller_lots_respect_size_and_conserve_every_image(self):
        # Incompressible RGB data is unnecessary since output is ZIP_STORED.
        # Use large valid PNG ancillary data to exercise the 24 MiB limit.
        def large_fixture(value):
            data = b"test\0" + (bytes([value]) * (13 * 1024**2))
            kind = b"tEXt"
            chunk = (struct.pack(">I", len(data)) + kind + data
                     + struct.pack(">I", zlib.crc32(kind + data)))
            image = pixel(value)
            return image[:-12] + chunk + image[-12:]

        a, b = large_fixture(65), large_fixture(66)
        self.make_archive([("01.png", a), ("02.png", b)])
        folder, report = self.recover(reference("cartographie/01", historical=[digest(a)]),
                                      reference("cartographie/02", historical=[digest(b)]))
        packs = {row["lot"] for row in report["images"]}
        self.assertEqual(len(packs), 2)
        for name in packs:
            self.assertLessEqual((folder / name).stat().st_size, 24 * 1024**2)
        self.assertEqual({r["sha256"] for r in report["images"]}, {digest(a), digest(b)})


if __name__ == "__main__":
    unittest.main()
