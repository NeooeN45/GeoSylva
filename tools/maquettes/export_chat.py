"""Repère localement les médias du fil dans un ZIP d'export ChatGPT (stdlib).

Deux lectures en flux : identifier le fil, puis lire seulement ses messages.
Aucun texte de conversation ni chemin personnel n'est écrit ou retourné.
"""
import json
import re
import zipfile
from pathlib import PurePosixPath

CHUNK = 256 * 1024
MAX_JSON_BYTES = 12 * 1024**3
MAX_MESSAGE_BYTES = 16 * 1024**2
UUID = r"[0-9a-fA-F]{8}(?:-[0-9a-fA-F]{4}){3}-[0-9a-fA-F]{12}"
EXEC_NAME = re.compile(rf"exec-{UUID}\.png\Z")
FILE_POINTER = re.compile(r"(?:(?:file-service|sediment)://)?(file[-_][a-zA-Z0-9_-]{6,120})\Z")
MESSAGE_ID = re.compile(rf"(?:exec-)?{UUID}\Z")
MEMBER = re.compile(r"conversations?(?:-[a-z0-9_-]+)?\.json\Z", re.I)
STRUCTURE = re.compile(rb'[{}\[\]"]')
QUOTE = re.compile(rb'["\\]')
NONSPACE = re.compile(rb'[^ \r\n\t]')
PRIMITIVE_END = re.compile(rb'[\[\]{},: \r\n\t]')


class Reader:
    """Read byte offsets from UTF-8 JSON without buffering arbitrary values."""

    def __init__(self, source):
        self.source = source
        self.buf = b''
        self.pos = 0
        self.base = source.tell()
        self.capture = None
        self.limit = 0

    def fill(self):
        if self.pos == len(self.buf):
            self.base += self.pos
            self.buf = self.source.read(CHUNK)
            self.pos = 0
        return bool(self.buf)

    def advance(self, end):
        if self.capture is not None:
            if len(self.capture) + end - self.pos <= self.limit:
                self.capture.extend(self.buf[self.pos:end])
            else:
                self.capture = None
        self.pos = end

    def peek(self):
        while self.fill():
            hit = NONSPACE.search(self.buf, self.pos)
            self.advance(hit.start() if hit else len(self.buf))
            if hit:
                return self.buf[self.pos:self.pos + 1]
        return b''

    def consume(self, token):
        if self.peek() != token:
            raise ValueError('structure JSON non prise en charge')
        self.advance(self.pos + 1)

    def skip_string(self):
        self.advance(self.pos + 1)
        while self.fill():
            hit = QUOTE.search(self.buf, self.pos)
            if not hit:
                self.advance(len(self.buf))
                continue
            char = hit.group()
            self.advance(hit.end())
            if char == b'"':
                return
            if self.fill():
                self.advance(self.pos + 1)  # escape, including across a chunk boundary
        raise ValueError('chaîne JSON incomplète')

    def skip(self):
        token = self.peek()
        if token == b'"':
            self.skip_string()
        elif token in (b'{', b'['):
            depth = 0
            while self.fill():
                hit = STRUCTURE.search(self.buf, self.pos)
                if not hit:
                    self.advance(len(self.buf))
                    continue
                self.advance(hit.start())
                if hit.group() == b'"':
                    self.skip_string()
                    continue
                depth += 1 if hit.group() in (b'{', b'[') else -1
                self.advance(hit.end())
                if depth == 0:
                    return
            raise ValueError('valeur JSON incomplète')
        elif token in (b'', b',', b']', b'}', b':'):
            raise ValueError('valeur JSON attendue')
        else:
            while self.fill():
                end = PRIMITIVE_END.search(self.buf, self.pos)
                self.advance(end.start() if end else len(self.buf))
                if end:
                    return

    def value(self, limit):
        self.peek()
        self.capture = bytearray()
        self.limit = limit
        self.skip()
        raw, self.capture = self.capture, None
        return json.loads(raw) if raw is not None else None

    def entries(self, obj):
        start, end = (b'{', b'}') if obj else (b'[', b']')
        self.consume(start)
        first = True
        while self.peek() != end:
            if not first:
                self.consume(b',')
            first = False
            if obj:
                if self.peek() != b'"':
                    raise ValueError('clé JSON attendue')
                key = self.value(4096)
                self.consume(b':')
                yield key
            else:
                yield None
        self.consume(end)

    def offset(self):
        self.peek()
        return self.base + self.pos


def conversation(reader, root=False):
    result = {}
    for key in reader.entries(True):
        if root and key == 'conversations' and reader.peek() == b'[':
            yield from summaries(reader)
        elif key in ('id', 'title', 'conversation_id') and reader.peek() == b'"':
            result[key] = reader.value(4096)
        elif key == 'thread' and reader.peek() == b'{':
            for attr in reader.entries(True):
                if attr in ('id', 'title') and reader.peek() == b'"':
                    result[attr] = reader.value(4096)
                else:
                    reader.skip()
        elif key in ('mapping', 'turns'):
            result[key] = reader.offset()
            reader.skip()
        else:
            reader.skip()
    if result:
        yield result


def summaries(reader):
    if reader.peek() == b'[':
        for _ in reader.entries(False):
            if reader.peek() == b'{':
                yield from conversation(reader)
            else:
                reader.skip()
    elif reader.peek() == b'{':
        yield from conversation(reader, root=True)
    else:
        raise ValueError('document JSON non pris en charge')


def file_key(value):
    match = FILE_POINTER.fullmatch(value) if isinstance(value, str) else None
    return match.group(1) if match else None


def pointers(message):
    kind = message.get('type')
    if kind == 'imageGeneration':
        name = PurePosixPath(str(message.get('savedPath', '')).replace('\\', '/')).name
        if EXEC_NAME.fullmatch(name) and message.get('id') == name[:-4]:
            yield name, 'image_generation'
    content = message.get('content')
    if isinstance(content, dict):
        parts = [content] + (content['parts'] if isinstance(content.get('parts'), list) else [])
    elif isinstance(content, list):
        parts = content
    else:
        parts = []
    for part in parts:
        if not isinstance(part, dict):
            continue
        if part.get('content_type') == 'image_asset_pointer':
            key = file_key(part.get('asset_pointer'))
            if key:
                yield key, 'image_asset_pointer'
        elif part.get('type') in ('image', 'input_image'):
            key = file_key(part.get('file_id')) or file_key(part.get('url'))
            if key:
                yield key, 'image_user_url'
    metadata = message.get('metadata')
    attachments = metadata.get('attachments') if isinstance(metadata, dict) else None
    for att in attachments if isinstance(attachments, list) else []:
        if isinstance(att, dict) and str(att.get('mime_type', '')).startswith('image/'):
            key = file_key(att.get('id')) or file_key(att.get('file_id'))
            if key:
                yield key, 'image_attachment'


def messages(reader, key, warnings):
    def one():
        raw = reader.value(MAX_MESSAGE_BYTES)
        if raw is None:
            warnings.add('Un message vide ou supérieur à la limite de lecture de 16 Mio a été omis.')
        return raw if isinstance(raw, dict) else {}

    if key == 'mapping':
        for _ in reader.entries(True):
            if reader.peek() != b'{':
                reader.skip()
                continue
            for prop in reader.entries(True):
                if prop == 'message' and reader.peek() == b'{':
                    yield one()
                else:
                    reader.skip()
    else:
        for _ in reader.entries(False):
            for prop in reader.entries(True):
                if prop != 'items' or reader.peek() != b'[':
                    reader.skip()
                    continue
                for _ in reader.entries(False):
                    yield one()


def scan_conversations(zf, thread_id, title):
    """Return candidate asset IDs mapped to message proofs, and privacy-safe warnings.

    Exact thread ID takes precedence. A unique exact title is accepted only if
    every supported index was readable. Unsupported/unreadable inputs warn.
    """
    exact, titled, warnings = [], [], set()
    members = [i for i in zf.infolist() if MEMBER.fullmatch(PurePosixPath(i.filename).name)]
    if not members:
        return {}, ['Index de conversations JSON introuvable dans le ZIP.']
    if sum(i.file_size for i in members) > MAX_JSON_BYTES:
        return {}, ['Les index JSON dépassent au total 12 Gio décompressés. Recherche par conversation indisponible.']
    errors = (ValueError, OSError, EOFError, RuntimeError, zipfile.BadZipFile, RecursionError)
    for info in members:
        try:
            with zf.open(info) as stream:
                reader = Reader(stream)
                if reader.fill() and reader.buf.startswith(b'\xef\xbb\xbf'):
                    reader.advance(3)
                for entry in summaries(reader):
                    if thread_id in (entry.get('id'), entry.get('conversation_id')):
                        exact.append((info, entry))
                    elif title and entry.get('title') == title:
                        titled.append((info, entry))
                if reader.peek():
                    raise ValueError('suffixe JSON non pris en charge')
        except errors:
            warnings.add('Au moins un index de conversations est illisible ou dans un format non pris en charge.')
    if exact:
        matches, selection = exact, 'id'
    elif len(titled) == 1 and not warnings:
        matches, selection = titled, 'titre'
    else:
        warnings.add('Fil introuvable par identifiant ou titre unique dans les index lisibles.')
        return {}, sorted(warnings)
    proofs = {}
    for info, entry in matches:
        section = 'mapping' if 'mapping' in entry else 'turns'
        if section not in entry:
            warnings.add('Le fil identifié utilise un format de messages non pris en charge.')
            continue
        try:
            with zf.open(info) as stream:
                stream.seek(entry[section])
                for msg in messages(Reader(stream), section, warnings):
                    ident = msg.get('id')
                    for key, nature in pointers(msg):
                        proof = {'source': nature, 'selection': selection}
                        if isinstance(ident, str) and MESSAGE_ID.fullmatch(ident):
                            proof['message_id'] = ident
                        entries = proofs.setdefault(key, [])
                        if proof not in entries:
                            entries.append(proof)
        except errors:
            warnings.add('Lecture partielle des messages du fil sélectionné.')
    if not proofs:
        warnings.add('Aucun pointeur image structuré récupérable dans le fil sélectionné.')
    return proofs, sorted(warnings)
