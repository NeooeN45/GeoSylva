"""A documentary filename can identify a private candidate after recompression."""

from io import BytesIO
import json
import unittest
import zipfile

from recuperer import INDEX_PATH, scan_images
from test_recuperer import digest, pixel, reference


class HistoricalNameTest(unittest.TestCase):
    def test_documented_names_preserve_candidates_and_hash_takes_precedence(self):
        original, copy = pixel(18), pixel(19)
        # A valid single-pixel GIF in a different encoding from the archived PNG.
        unknown = bytes.fromhex(
            '47494638396101000100800000000000ffffff2c00000000010001000002024401003b'
        )
        ref = reference('cartographie/98', historical=[digest(original)])
        ref['preuves_sha256'] = [{'nom': 'enregistrer-le-trace.png'}]
        recorded = reference('cartographie/99')
        recorded['preuves_nom'] = [{'nom': 'trace-enregistre.png'}]
        contents = BytesIO()
        with zipfile.ZipFile(contents, 'w') as zf:
            zf.writestr('medias/enregistrer-le-trace.PNG', copy)
            zf.writestr('file-12345678-1234-1234-1234-123456789abc-trace-enregistre.gif', unknown)
            zf.writestr('maquette-trace-enregistre.png', pixel(21))
            zf.writestr('prive/trace-enregistre-miniature.png', pixel(22))
            # An incorrect filename cannot take precedence over verified bytes.
            zf.writestr('trace-enregistre.png', original)
        with zipfile.ZipFile(contents) as zf:
            found, _, _ = scan_images(zf, {'references': [ref, recorded]}, {}, [])
        self.assertEqual(set(found), {digest(original), digest(copy), digest(unknown)})
        self.assertTrue(found[digest(original)].verified)
        self.assertEqual(found[digest(original)].ids, ['cartographie/98'])
        self.assertEqual(found[digest(unknown)].ext, '.gif')
        for candidate, identifier in ((copy, 'cartographie/98'), (unknown, 'cartographie/99')):
            self.assertEqual(found[digest(candidate)].proof, 'nom_historique')
            self.assertEqual(found[digest(candidate)].ids, [identifier])
            self.assertFalse(found[digest(candidate)].verified)

    def test_all_references_have_a_documented_basename(self):
        refs = json.loads(INDEX_PATH.read_text(encoding='utf-8'))['references']
        for ref in refs:
            with self.subTest(identifier=ref['id']):
                evidence = ref['preuves_sha256'] + ref.get('preuves_nom', [])
                self.assertTrue(evidence)
                for source in evidence:
                    self.assertTrue(source['nom'])
                    self.assertNotRegex(source['nom'], r'[/\\]')


if __name__ == '__main__':
    unittest.main()
