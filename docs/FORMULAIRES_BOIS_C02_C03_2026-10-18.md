# Formulaires manuels bois — lot du 18 octobre 2026

## Périmètre du code

Tranche locale, depuis **Dépôts de bois → dépôt → Piles et grumes**.
Les liens chantier/parcelles et les enlèvements progressifs sont cadrés dans
[le complément du fondateur](CHANTIERS_PARCELLES_SUIVI_DATE_BOIS_2026-10-18.md) ; ils
ne sont pas encore raccordés par ce lot.

- Créer une pile ou une grume avec nom, plaquette, essence/qualité/origine déclarées
  et observations ; relire et modifier son identification.
- Ajouter un relevé manuel, même incomplet : date effective facultative et fuseau,
  dimensions brutes, unités visibles, déclarations propres au relevé.
- Pile : longueur, profondeur, hauteur moyenne ; longueur de billons distincte et
  facultative. Le calcul exige une enveloppe rectangulaire explicitement choisie
  et la référence/description du protocole effectivement utilisé.
- Grume : longueur et diamètres aux positions utiles ; Huber, Smalian ou Newton
  choisis explicitement, convention sur/sous écorce sans valeur présumée.
- Les sections déjà saisies restent conservées lorsqu'on change de méthode. Si
  elles sont incompatibles, le calcul explique le problème au lieu de les ignorer.
- Historique daté, corrections justifiées sans écrasement de l'original ; choix
  explicite du relevé de référence. Une référence corrigée devient obsolète et
  n'est pas remplacée automatiquement.
- Calcul géométrique réel via le noyau existant, volume de pile apparent clairement
  distingué du volume de grume. Ni quantité payable, ni mouvement de stock, ni
  conversion automatique de l'enveloppe en bois plein.

## Persistance et protection

Room **43 → 44**, migration additive et quatre tables : objets, observations,
sélection de référence et commandes/reçus. Les schémas antérieurs restent conservés.
Le codec v1 conserve les chaînes brutes et vérifie leur empreinte à la relecture ;
l'empreinte n'est pas une signature ni une attestation externe.

Chaque commande conserve son contenu et son identifiant pendant une reprise.
Un nouvel envoi identique retourne son reçu original ; une commande modifiée sous
le même identifiant est refusée. Les révisions sont contrôlées dans la transaction.
Une correction ajoute un nouvel identifiant et ne modifie pas la ligne d'origine.

Les dossiers locaux sans compte et les comptes personnels restent séparés. Une
identité changée invalide la saisie en cours ; aucun rattachement silencieux au
nouveau compte. Le métier n'accorde pas de droits de données. Un parent archivé
bloque les nouvelles écritures ; son historique est conservé en base.

La purge autorisée supprime commandes, sélections, observations, objets, puis
les dépôts dans la transaction existante. Une garde commune invalide les travaux
bois en attente avant l'effacement et reste active jusqu'au nettoyage des fichiers.
Une erreur après commit ne doit pas être présentée comme un retour arrière garanti.

## Interface

Compose Material 3 avec composants et ressources FR/EN du dépôt. Formulaires à
libellés persistants, clavier décimal, bouton d'enregistrement accessible, retour
avec confirmation d'abandon, états d'erreur sans disparition silencieuse du brouillon.
Les brouillons non enregistrés restent en mémoire de l'écran ; la persistance après
arrêt du processus concerne les **données enregistrées**, pas une autosauvegarde de
chaque frappe. Les dates ambiguës ou inexistantes au changement d'heure ne sont pas
interprétées automatiquement. La date d'enregistrement reste distincte.

La correction par ce formulaire ne réétiquette pas des unités provenant d'un autre
outil : les relevés avec unités/protocoles non pris en charge restent consultables.

## Validation

Compilation de l'application et de ses tests Android réussie. **1 776 tests JVM**
(121 suites, aucun échec, erreur ou test ignoré) et **36 tests Android distincts**
réussis au cours des passages ciblés. Il ne s'agit pas d'une recette de toute
l'application, ni de 36 tests exécutés dans un seul lancement.

- Migration 43 → 44, conservation des données antérieures, migration chiffrée
  42 → 44 et relecture après fermeture réelle de la base.
- Création, relevés partiels, corrections et sélection, reçus rejouables, révision
  concurrente, compte/session, parent archivé et altération du contenu enregistré.
- Purge sans réapparition d'une écriture en attente ; observations et sélection
  relues puis effacées dans une base SQLCipher de TEST.
- Formulaire pile, formulaire grume, relevés partiels successifs, français dans
  les menus et dates, clavier et police à 200 % vérifiés sur Android.
- Les 25 fichiers de schémas Room antérieurs présents dans l'archive C01 restent
  identiques octet par octet ; seul le schéma 44 est ajouté.

Incidents de qualification conservés dans les preuves : signature d'initialisation
JUnit corrigée ; menus de choix héritant de l'anglais corrigés en résolvant les
libellés dans la langue de l'écran ; un délai initial de sauvegarde partielle dépassé,
dont la cause n'est pas établie. Ce dernier scénario est passé ensuite, sans changement
des assertions ni du délai, lors du diagnostic et de la recette finale. Les quatre
tests de formulaire passent ensemble dans le dernier passage UI.

Les bases et valeurs de recette portent TEST ; aucune donnée client, aucun compte
Google/GSIE/FORLOG ni clé de production ne sert aux fixtures. Le simulateur utilise
l'identifiant distinct `com.forestry.counter.preview`.

Preuves locales : `/workspace/outputs/2026-10-18/wood-forms/VALIDATION.json`, journaux
Gradle/instrumentation, résultats JVM et captures `android/wood-objects/`.
La sauvegarde de sources est `GeoSylva-sources-reprises-2026-10-18.zip` dans ce même
dossier, avec manifeste et contrôle d'intégrité. Aucun commit/push GitHub de ce lot.

## Limites et suite

Ce lot n'est pas une réception contractuelle ni un état de stock. Restent à raccorder :
chantiers et parcelles, consultation des archives de dépôts, mouvements et contrôles
successifs, photos, exports bois/PDF, synchronisation et connecteur FORLOG. Les archives
et corrections serveur ne sont pas simulées. Le chiffrement déjà configuré dans
l'application est conservé ; aucun nouveau service ni dépendance n'est ajouté.
