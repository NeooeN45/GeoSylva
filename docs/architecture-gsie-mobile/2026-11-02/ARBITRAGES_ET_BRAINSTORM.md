# Brainstorming et arbitrages proposés

2 novembre 2026. Cinq analyses spécialisées ont été produites séparément, puis
confrontées : données mobile, backend GSIE, pages/cartographie, algorithmes,
contre-expertise du dossier et sécurité. Leurs preuves figurent dans
[l'état des lieux](ETAT_EXISTANT.md). Les propositions ci-dessous ne sont pas
des décisions d'architecture déjà adoptées.

## 1. Les options d'ensemble

Les coûts sont relatifs et qualitatifs ; aucun délai ni budget n'a été chiffré.

| Option | Bénéfice | Coût et compromis | Avis proposé |
|---|---|---|---|
| Évolution modulaire de l'existant, stockages spécialisés, contrats communs | Réutilise fonctions, migrations et preuves ; progression par domaine | Effort moyen, nécessite des frontières et une reprise entre stockages explicites | **À privilégier** |
| Tout réunir dans un fichier Room et un grand moteur configurable | Transaction simple pour les seules données de ce fichier | Couplage des éditions scientifiques aux migrations ; médias/cartes lourds ; combinatoire des écrans | Limiter Room au métier et aux métadonnées pertinentes |
| Bases, services et pages par métier | Autonomie apparente au premier développement | Répétition des données, permissions, synchronisations et corrections ; exploitation plus lourde | Écarter comme principe d'organisation |
| Microservices et réécriture complète de langage maintenant | Isolation et optimisations potentielles | Requalification massive ; transactions réparties ; aucun goulot mesuré justifiant ce coût | Reporter ; étudier seulement un besoin isolé démontré |

Le backend actuel utilise déjà Python scientifique et FastAPI, le téléphone
Kotlin/Compose. La qualité dépend ici d'abord des contrats et responsabilités.
Un noyau natif ou un worker distinct reste une option future si un benchmark
représentatif justifie aussi son coût de maintenance et de validation.

## 2. Divergence : dix-huit pistes examinées

| Piste | Traitement proposé | Pourquoi |
|---|---|---|
| Une implémentation Carte par profession | Écarter | La Carte est déjà partagée ; elle doit recevoir un contexte adapté |
| Une seule page universelle avec de nombreux booléens | Écarter comme cible générale | Trop de combinaisons ; rend le contrôle des actions difficile |
| Contextes de tâche typés et composants composables | Retenir | Distingue données, outils, navigation et droits sans copier le rendu |
| Mutualiser le cycle de vie MapLibre répété | Retenir après caractérisation | Doublon exact identifié dans deux sélecteurs |
| Un catalogue déclaratif de parcours/capacités | Étudier progressivement | Organise les métiers sans transformer une sélection en habilitation |
| Un moteur de pages et de règles arbitraires en base | Écarter | Complexifie la validation et déplace les règles hors du code contrôlé |
| Une base métier par métier | Écarter | Les mêmes parcelles et bois seraient recopiés et divergeraient |
| Références scellées, métier chiffré, cartes et index séparés | Retenir | Unification des identités avec cycles de vie et budgets distincts |
| Deuxième registre local de paquets | Écarter | Une seule autorité d'activation existe déjà |
| Génération cohérente des dépendances actives | Retenir par lot | Évite les références mélangées pendant une mise à jour |
| Import immédiat des 135 méthodes du corpus | Écarter | Fiches documentaires, domaines et droits à qualifier |
| Contrat commun d'admissibilité, exécution et trace | Retenir | Même explication et même refus dans les interfaces et exports |
| Table universelle de tous les résultats | Écarter à ce stade | Les modèles arbre, bois et synthèse ont des responsabilités différentes |
| Noyau géométrique bois existant comme premier exemple de traçabilité | Retenir comme candidat | Réutilise un calcul réel, sans activer les tarifs sur pied |
| Fusion immédiate des deux synchronisations | Reporter | Garanties et compatibilités actuelles différentes ; convergence par adaptateurs |
| Contrat commun de pièces jointes | Étudier après mesure | Réutilise identité/empreinte/accès ; le stockage photo actuel est déjà transactionnel |
| Sauvegarde complète et restauration sur installation neuve | Prioriser le cadrage et la preuve | La synchronisation existante et les exports partiels ne la démontrent pas |
| Réécriture native ou calcul uniquement serveur | Reporter / exclure du terrain requis | Aucun gain mesuré ; la journée sans réseau doit rester possible |

## 3. Trois priorités recommandées

1. **Fixer les autorités et les contrats.** Conserver les identités et
   gestionnaires existants ; définir les versions de contrat avant extension.
   Relever la sauvegarde récupérable et les accès Diagnostics avant tout
   élargissement à de nouveaux parcours.
2. **Prouver un circuit local court.** Petit référentiel documentaire dont
   l'usage est autorisé, lecteur identifié et un calcul géométrique déjà codé
   expliqué avec sa trace. Ajouter ensuite une mise à jour compatible sans
   modifier le résultat antérieur.
3. **Prouver la réutilisation dans deux contextes réels.** Une capacité Carte
   existante dans le parcours Technicien et le dossier Conducteur ; mêmes
   composants et contrôles, données/actions adaptées. Maquette de la page
   concernée puis intégration, sans refonte du parcours Technicien.

Ces circuits peuvent être conduits par petits lots coordonnés. Ils ne demandent
pas d'achever un nouveau framework avant de reprendre la première page métier.

## 4. Tensions examinées et résolution proposée

| Tension | Ce que l'examen montre | Résolution proposée |
|---|---|---|
| Simplicité d'un fichier unique / spécialisation des données | Une seule transaction ne couvre déjà pas tous les fichiers externes | Contrats communs ; plusieurs stockages avec une autorité d'activation et une reprise explicites |
| Réutiliser tout calcul / préserver la vérification indépendante | Certaines formules répétées contrôlent les résultats capturés | Une implémentation de production par méthode ; cas de référence et contrôles indépendants conservés |
| `CalculationRun` commun / modèles réellement différents | FK actuelle vers observation arbre, historique bois propre | Contrat et aide partagés, adaptateurs de persistance par agrégat |
| Photos en fichiers / transactions actuelles SQLCipher | Les photos chantier sont déjà stockées en fragments chiffrés transactionnels | Mesurer les deux options ; ne pas déplacer les médias par principe |
| Écran commun / ergonomie par tâche et appareil | Carte d'itinéraire et carte de sélection n'ont pas les mêmes interactions | Partager infrastructure et composants, garder les compositions spécialisées utiles |
| Synchronisation unique / clients existants | Parcelles et hiérarchie ont des garanties différentes | Contrats versionnés et migration progressive ; aucun doublon de registre ajouté |
| Automatiser le choix de modèle / demander les informations utiles | Le territoire ou un nom proche ne démontre pas l'admissibilité | D'abord domaine et qualité ; demander uniquement les entrées indispensables manquantes |
| Journée hors réseau / retrait des droits | Un retrait distant ne peut être connu instantanément en forêt | Politique distincte de fraîcheur et d'accès ; aucune durée de bail inventée |
| Un calcul mobile / un calcul serveur réellement nécessaire | Certains outils scientifiques sont déjà adaptés au serveur | Contrats et jeux de référence communs si deux implémentations sont nécessaires ; pas de double implémentation systématique |

## 5. Registre d'arbitrages

Les identifiants ci-dessous sont locaux à cette étude. Ils ne sont pas des
`DEC` adoptées dans la gouvernance GSIE. Les principes demandés par le fondateur
sont distingués des choix proposés.

| ID | Sujet | Proposition / besoin acquis | Statut |
|---|---|---|---|
| A01 | Réutilisation | Une implémentation de référence par responsabilité, plusieurs contextes métier | Intention utilisateur acquise ; frontières techniques proposées |
| A02 | Terrain | Une journée sans connexion, travail conservé si retour tardif | Exigence confirmée le 2 novembre |
| A03 | Stockage | Room/SQLCipher métier + références immuables + actifs/index spécialisés | Recommandation à discuter |
| A04 | Backend | Faire évoluer Python/FastAPI et PostgreSQL/PostGIS en monolithe modulaire | Recommandation à discuter |
| A05 | Pages | Composants communs et contextes typés ; navigation Technicien conservée | Principe de réutilisation acquis ; contrat à définir |
| A06 | Calculs | Contrat commun ; noyaux existants ; persistance adaptée à l'agrégat | Recommandation à discuter |
| A07 | Publications | Étendre Data Registry et le gestionnaire existants ; nouveau contrat scientifique | Recommandation à discuter |
| A08 | Échanges | Références, dossiers et contributions séparés ; convergence progressive des syncs | Recommandation à discuter |
| A09 | Récupération | Export chiffré récupérable, sauvegarde distante autorisée, ou combinaison | Choix produit à préciser avant promesse de récupération complète |
| A10 | Photos | Conserver le stockage actuel jusqu'à comparaison et migration démontrée | Recommandation à discuter |
| A11 | Hors ligne partagé | Définir la politique de droits, de révocation et de fraîcheur par classe | À préciser avant partage opérationnel ; journée ≠ bail de droits |
| A12 | Premier circuit | Référentiel local minimal et trace bois ; première page métier Conducteur | Proposition d'ordre à discuter ; autres circuits courts possibles |

Le point de choix initial est A03–A08 : valider ou amender la direction
d'ensemble et les premières frontières. A09 et A11 devront recevoir une
spécification dédiée avant les fonctions correspondantes. Les délais de
droits, budgets mémoire et volumes quotidiens ne sont pas décidés par cette étude.
