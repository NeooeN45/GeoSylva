# Fixture réelle d’interopérabilité GSIE–GeoSylva

Ces ressources sont réservées aux tests JVM (`app/src/test/resources`).
Elles ne sont ni des assets de l’application ni un pack publié en production.

- `artifact.pack` : JSON non compressé construit par le builder GSIE réel,
  territoire `department:16` (Charente), un contour IGN exact et une ancre
  calculée, sans cadastre, observations ou données scientifiques.
- `manifest.json` : enveloppe `SignedPackManifest` signée en Ed25519.
- `signing-public-key.der` : clé publique de test, SubjectPublicKeyInfo DER.
  Son identifiant est le SHA-256 de ces octets.
- `evidence.json` : tailles, empreintes et date réelle de génération.

La clé privée de test est distincte des clés JWT et des clés de publication.
Elle reste exclusivement dans un dossier temporaire privé sous `/tmp` ;
aucune clé privée n’est incluse ici ni dans les configurations produit.
La clé publique ne doit jamais être ajoutée aux clés approuvées du produit.

Source : IGN ADMIN EXPRESS COG CARTO PE, édition 2026, qualifiée dans
`app/src/main/assets/packs/provenance.json`. Attribution et preuves complètes
sont conservées dans l’artefact. Licence : Licence Ouverte / Etalab 2.0.
Les dates d’acquisition et de dérivation sont celles de la provenance réelle ;
`created_at` est uniquement la date UTC de construction de la fixture.
