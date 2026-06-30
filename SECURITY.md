# Politique de sécurité — GeoSylva

La sécurité des données forestières et personnelles de nos utilisateurs est une priorité.
GeoSylva applique le chiffrement au repos (SQLCipher), le certificate pinning SHA-256 et
une conformité RGPD documentée (registre des traitements, SCC pour les transferts hors-UE).

## Versions supportées

| Version | Support sécurité |
|---------|------------------|
| 2.4.x   | ✅ Supportée     |
| < 2.4   | ❌ Non supportée |

## Signaler une vulnérabilité

**Ne publiez jamais une vulnérabilité de sécurité dans une issue ou une discussion publique.**

Merci de la signaler de façon responsable :

1. Envoyez un e-mail à **hydrogene.bonde@gmail.com** avec l'objet `[SECURITY] GeoSylva`.
2. Décrivez la vulnérabilité, son impact potentiel et les étapes de reproduction.
3. Joignez si possible une preuve de concept (logs, captures, payload).

### Engagement de réponse

| Étape | Délai cible |
|-------|-------------|
| Accusé de réception | 72 heures |
| Évaluation initiale | 7 jours |
| Correctif ou plan de remédiation | 30 jours (selon sévérité) |

Nous nous engageons à vous tenir informé de l'avancement et à créditer publiquement
votre signalement (sauf demande contraire) une fois le correctif publié.

## Périmètre

Sont concernés : l'application Android GeoSylva, son stockage local chiffré, ses
communications réseau (tuiles cartographiques) et ses exports de données.

Merci de contribuer à rendre GeoSylva plus sûr pour toute la communauté forestière. 🌲
