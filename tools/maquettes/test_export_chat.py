"""Tests du ciblage de la conversation et de sa lecture par blocs."""
import io
import json
import unittest
import zipfile
from unittest.mock import patch

import export_chat as chat

THREAD = '01a1968b-6cf3-75ae-8799-765a8b7a3047'
TITLE = 'Développement de Géosylva maquette'
MSG = '01a19d0d-44c8-7166-a0ca-83229feac8af'


def record(ident, title, parts):
    return {'mapping': {'n': {'message': {'id': MSG, 'content': {'parts': parts}}}},
            'title': title, 'conversation_id': ident}


def pointer(key):
    return {'content_type': 'image_asset_pointer', 'asset_pointer': 'file-service://' + key}


def scan(entries):
    data = io.BytesIO()
    with zipfile.ZipFile(data, 'w', compression=zipfile.ZIP_DEFLATED) as archive:
        for name, payload in entries.items():
            archive.writestr(name, payload if isinstance(payload, bytes) else json.dumps(payload, ensure_ascii=False))
    with zipfile.ZipFile(data) as archive:
        return chat.scan_conversations(archive, THREAD, TITLE)


class ExportChatTest(unittest.TestCase):
    def test_exact_id_wins_across_shards_and_ignores_free_text(self):
        foreign = record('foreign', TITLE, [pointer('file-PRIVATE1')])
        target = record(THREAD, 'Autre titre', ['file-service://file-DECOY01', pointer('file_Target01')])
        target['mapping']['n']['message']['metadata'] = {'attachments': [
            {'id': 'file-SECRET22', 'mime_type': 'application/pdf'},
            {'id': 'file-Asset222', 'mime_type': 'image/png'}]}
        with patch.object(chat, 'CHUNK', 5):
            proofs, warnings = scan({'classeur/conversations-000.json': [foreign],
                                     'classeur/conversations-001.json': [target]})
        self.assertEqual(set(proofs), {'file_Target01', 'file-Asset222'})
        self.assertEqual(proofs['file_Target01'], [{'source': 'image_asset_pointer', 'selection': 'id', 'message_id': MSG}])
        self.assertEqual(warnings, [])

    def test_ambiguous_title_fails_closed(self):
        target = record('uncertain', TITLE, [pointer('file-target01')])
        self.assertFalse(scan({'conversations.json': [target, {**target, 'conversation_id': 'other'}]})[0])
        self.assertFalse(scan({'conversations.json': [target], 'conversations-1.json': b'{broken'})[0])

    def test_wrapper_and_single_title(self):
        target = record('unknown', TITLE, [pointer('file-picked01')]); target['private'] = '"[\\\\]"'
        with patch.object(chat, 'CHUNK', 1):
            proofs, warnings = scan({'conversations.json': {'conversations': [target]}})
        self.assertEqual(proofs['file-picked01'][0]['selection'], 'titre')
        self.assertFalse(warnings)

    def test_oversized_message_then_usable_message(self):
        target = record(THREAD, TITLE, ['x' * 2000]); target['mapping']['b'] = {'message': {
            'content': {'parts': [pointer('file-usable01')]}}}
        with patch.object(chat, 'MAX_MESSAGE_BYTES', 300):
            proofs, warnings = scan({'conversation.json': target})
        self.assertIn('file-usable01', proofs); self.assertTrue(warnings)

    def test_structured_generation_requires_matching_id(self):
        name = 'exec-' + MSG
        target = {'thread': {'id': THREAD, 'title': TITLE}, 'turns': [{'items': [
            {'id': name, 'type': 'imageGeneration', 'savedPath': '/workspace/generated_images/' + name + '.png'},
            {'id': 'mismatch', 'type': 'imageGeneration', 'savedPath': '/workspace/exec-' + THREAD + '.png'}]}]}
        proofs, warnings = scan({'conversation.json': target})
        self.assertEqual(set(proofs), {name + '.png'}); self.assertFalse(warnings)

    def test_other_chat_with_nearly_identical_asset_id_never_appears(self):
        own = record(THREAD, TITLE, [pointer('file-AbCd1234')])
        other = record('different', 'Other', [pointer('file-aBcD1234')])
        proofs, warnings = scan({'conversations-part-001.json': [own, other]})
        self.assertEqual(list(proofs), ['file-AbCd1234'])
        self.assertFalse(warnings)

    def test_nonstandard_metadata_does_not_leak_text(self):
        target = record(THREAD, TITLE, [pointer('file-import01')]); target['mapping']['n']['message']['id'] = 'sensitive name'
        proofs, warnings = scan({'conversation.json': target})
        self.assertNotIn('sensitive', str(proofs)); self.assertFalse(warnings)


if __name__ == '__main__':
    unittest.main()
