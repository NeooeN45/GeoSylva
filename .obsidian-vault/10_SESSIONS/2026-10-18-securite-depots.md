# Sécurité des dépôts — 18 octobre 2026

- Instruction : améliorer les dépôts puis les sécuriser ; mise en privé explicitement confirmée.
- Dépôt GeoSylva encore public : refus administratif GitHub 403, pas de changement appliqué.
- Gitleaks : aucune alerte dans les sources présentes ni l’historique Git disponible.
- Nouveau workflow autonome, permissions en lecture, outil vérifié par SHA-256.
- Garde de fichiers sensibles avec trois tests, fixtures publiques validées par empreinte.
- Politiques SECURITY, contribution et guide qualité ; aucun code métier changé.
- Les 1 788 tests JVM et 37 tests Android du lot terrain restent les preuves du lot
  précédent ; ils ne sont pas présentés comme rejoués pour cette modification documentaire.
- Avant la diffusion du pilote : confidentialité réelle, validation des exports,
  données simulées identifiées et vérification sur téléphone.
