## État courant — symboles du choix des métiers, 2026-10-10

[ALIGN 1.54.0](../../../../../../Quintessences/22_PROJECT_MEMORY/ALIGNEMENT_FONDATEUR_2026-10-01.md) :
les symboles Material existants remplacent les photographies sur le choix métier,
selon la nouvelle décision prioritaire du Fondateur ; aucun nouveau visuel généré.
Les dix profils/codes, navigation, préférences et habilitations restent les mêmes.
Les neuf JPEG métier inutilisés sont retirés des ressources ; `profession_autre.jpg`
reste nécessaire au fond forêt de `CreateForestWizard`, hors choix métier.
`provenance.json` conserve l'historique des dix photographies et leurs attributions ;
il ne décrit plus dix illustrations actives du choix métier.
Les originaux sont [archivés avant modification](../../../../../../.onboarding/metiers-symboles-2026-10-10/before/GeoSylva/app/src/main/res/drawable-nodpi).
Le corps suivant est l'état historique de la sélection photographique, conservé littéralement.
Ce retrait du choix métier ne supprime pas les autres photos de l'application.

# Photographies du choix des métiers

Sélection révisée le 2026-10-03 : dix photographies réelles de terrain,
de propriétés ou d'instruments forestiers. Aucune image générée ou retouchée
par IA n'est incluse dans ces ressources.

Chaque JPEG embarqué est identique aux octets téléchargés de la source
officielle Wikimedia Commons. Le fichier `provenance.json` contient la page
source, l'auteur, le titre, la licence, les dimensions, la taille et le SHA-256
de chaque photographie. Les métadonnées Commons et les conditions officielles
Creative Commons ont été consultées pendant cette sélection.

Les licences retenues permettent l'utilisation commerciale et la
redistribution : CC BY 2.0, CC BY-SA 2.0/3.0/4.0 et CC0 1.0. Conserver les
attributions et liens de licence. Les licences CC BY-SA continuent à
s'appliquer aux photographies et à leurs adaptations ; ces médias restent
distincts du code de l'application et des données métier.

Le bouton « Crédits des photographies » ouvre les titres, les auteurs, les
licences, les sources et les descriptions précises dans l'application. Les
images sont cadrées avec `ContentScale.Crop` au rendu. Coil, déjà présent dans
le dépôt, adapte le décodage à la taille des cartes ; les originaux JPEG ne
sont pas réencodés. Les vignettes du domaine officiel `thumb.wikimedia.org`
étaient bloquées par le réseau de cet environnement (CONNECT 403) ; leur
présence dans les métadonnées ne vaut pas téléchargement réussi. Les photos
retenues ont été reçues via le domaine officiel `upload.wikimedia.org`.

Les profils ONF, CNPF et DDT sont des déclarations de métier. Ils ne valident
aucune appartenance à une organisation. Pour CNPF/DDT, le Fondateur autorise
une vraie scène d'activité pertinente sans prétendre montrer un salarié de
l'organisme. Les légendes le précisent : la photographie CNPF représente
un forestier du U.S. Army Corps of Engineers et la photographie DDT un relevé
GPS au Royaume-Uni. Aucun partenariat ni visuel institutionnel officiel
n'est revendiqué.

Pour les profils dont le statut individuel ne peut pas être attesté, la
légende décrit seulement l'activité ou le sujet réellement photographié :
mesure de circonférence, entrée de forêt privée, instruments de placette,
promenade générique. La présence d'une photographie n'atteste pas les
qualifications professionnelles d'une personne ni une méthode scientifique.

Les anciennes préférences `forestier_onf_cnpf` restent conservées telles
quelles, sans migration ni inférence vers ONF ou CNPF. Les nouvelles
déclarations ONF, CNPF et DDT sont enregistrées par le mécanisme existant.
