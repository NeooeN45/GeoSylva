# Vente, mesurage, classement et traçabilité des bois en France

**Domaine** : `docs/recherche/02_marche_prix/`  
**Identifiant** : GEOSYLVA-RECH-VENTE-007  
**Date** : 2026-08-30  
**Statut** : Draft  
**Agent** : Consortium recherche vente et conformité

## 1. Limite de responsabilité

Cette note est une documentation fonctionnelle et de recherche. Elle ne constitue pas un contrat, un avis de juriste, un conseil fiscal, une certification, ni une preuve de conformité à une norme dont le texte intégral n'a pas été consulté. Toute vente réelle doit être encadrée par les parties et, si nécessaire, par un expert forestier, un notaire, un assureur, un comptable ou un juriste.

## 2. Cycle d'une vente

```text
identification du propriétaire et du lot
→ autorisations et document de gestion
→ estimation du volume et de la qualité
→ consultation / appel d'offres / contrat
→ abattage, façonnage et tri
→ mesurage contradictoire
→ enlèvement et transport
→ facture, paiement et archivage
→ traçabilité de la transformation et revente
```

Les ventes sur pied et les ventes de bois façonné n'ont pas la même base de calcul. Le contrat doit préciser ce qui est vendu, quand le prix devient exigible, qui supporte les risques, qui mesure, selon quelle règle et avec quel recours.

## 3. Clauses à documenter

| Thème | Champs ou clause minimale |
|---|---|
| Parties | identité, qualité, coordonnées, mandat |
| Lot | parcelle, essence, limites, marquage, photos, plan |
| Produit | bois sur pied, grumes, billons, plaquettes, bûches, sciages |
| Volume | estimation ou mesurage, unité, écorce, découpe, tolérance |
| Qualité | norme ou grille contractuelle, défauts, classement par bille |
| Prix | montant, unité, HT/TTC, indexation, frais inclus/exclus |
| Logistique | délai, accès, dépôt, transport, remise en état |
| Risques | transfert de propriété, incendie, tempête, perte, vol, dégâts |
| Réception | contrôle, réserves, désaccord, tiers mesureur |
| Traçabilité | origine, certification, documents EUTR/EUDR selon applicabilité |
| Litiges | droit applicable, médiation, juridiction, expertise contradictoire |

## 4. Mesurage et cubage

Le CNPF distingue l'estimation d'un arbre sur pied du cubage d'une grume abattue. Une grume peut être divisée en billons et cubée avec une section médiane, tandis qu'un arbre sur pied exige d'estimer la longueur et le diamètre médian au moyen de la hauteur de découpe et de la décroissance métrique moyenne.

Pour l'application, chaque mesure doit indiquer :

- instrument et protocole ;
- position de mesure ;
- diamètre ou circonférence ;
- longueur ou hauteur de découpe ;
- sur écorce ou sous écorce ;
- méthode et arrondi ;
- opérateur, date, photographie et source.

Le mesurage contradictoire est une garantie contractuelle de comparabilité. Un volume estimé dans GeoSylva ne doit pas être présenté comme le volume facturable si le contrat impose un mesurage après façonnage.

## 5. Classement et qualité

Les familles de références comprennent notamment :

- NF EN 1310 : mesurage des singularités ;
- NF EN 1316 : classement qualitatif de certaines grumes feuillues ;
- NF EN 1927 : classement qualitatif de familles de bois ronds résineux ;
- NF B53-020 et NF EN 1309-2 : mesurage/cubage des bois ronds, selon édition et domaine ;
- NF B52-001, NF EN 338, NF EN 14081 et textes associés : produits sciés et classement mécanique/structurel.

Un grade de grume et une classe mécanique de sciage ne sont pas la même information. Une lettre A-D ne donne pas à elle seule un prix. Le produit possible dépend de la bille, de ses défauts et de la demande du marché.

## 6. Provenance et certifications

La provenance doit être stockée comme une affirmation vérifiable : lieu de récolte, période, chaîne documentaire, transformation et source. « Bois local », « bois français », « Bois de France », PEFC et FSC ne sont pas des synonymes.

Une certification de chaîne de contrôle ne remplace pas automatiquement les obligations réglementaires applicables à la mise sur le marché. GeoSylva doit donc afficher séparément :

- origine déclarée ;
- preuve d'origine ;
- certification ;
- chaîne de contrôle ;
- statut de vérification ;
- date de validité.

## 7. EUTR et EUDR

Le règlement (UE) n° 995/2010 et le règlement (UE) 2023/1115 concernent la mise sur le marché du bois et de produits dérivés selon leur champ d'application. Les dates d'application et les dispositions transitoires ont évolué ; la version consolidée d'EUR-Lex et les autorités françaises doivent être vérifiées au moment de la vente.

Pour une donnée applicative, prévoir sans préjuger de l'obligation finale :

```text
operator_role
country_of_production
harvest_region
harvest_department
plot_geolocation
species_scientific_name
quantity
supply_chain_documents
due_diligence_status
reference_statement
checked_at
```

Ne pas transformer GeoSylva en outil de certification automatique. L'application peut préparer un dossier de preuve et signaler les champs manquants ; l'opérateur reste responsable de la déclaration réglementaire.

## 8. Transport et fiscalité

Le transport doit conserver le point de départ, la destination, le transporteur, le poids ou volume déclaré, la lettre de voiture et les réserves. La CMR s'applique au transport international routier ; les transports nationaux obéissent à leurs règles propres.

La fiscalité dépend du statut du vendeur, de la nature de l'opération et du produit. Le régime des bénéfices forestiers, la TVA, les droits de mutation et les obligations de facturation nécessitent une vérification personnalisée. Aucun taux fiscal ne doit être codé dans le calculateur sans source juridique et date.

## 9. Recommandation pour GeoSylva

- Fournir des modèles de contrat comme aide à la préparation, jamais comme contrat juridiquement garanti.
- Marquer les documents `information_generale`, `verification_requise` ou `preuve_validee`.
- Séparer la qualité visuelle de la qualité mécanique et du produit final.
- Conserver les photos et les relevés contradictoires avec un identifiant de lot.
- Refuser une allégation « local », « français » ou « certifié » sans preuve associée.
- Ajouter une alerte de péremption pour les règles réglementaires et les normes.

## 10. Sources

- Code civil, articles 1196, 1585 et 1586 : https://www.legifrance.gouv.fr/
- CNPF, vente de bois : https://www.cnpf.fr/gestion-durable-des-forets/multifonctionnalite/vente-de-bois
- CNPF Nouvelle-Aquitaine, cubage et vente : https://nouvelle-aquitaine.cnpf.fr/gestion-durable-des-forets/coupes-et-travaux/la-vente-de-bois
- CNPF/IFC, *Estimer et vendre ses bois* : https://ifc.cnpf.fr/sites/ifc/files/2024-03/Fiche%20Gestion%2021%20-%20Estimer%20et%20Vendre%20ses%20Bois.pdf
- EUR-Lex, règlement (UE) 2023/1115 : https://eur-lex.europa.eu/legal-content/FR/ALL/?uri=CELEX:32023R1115
- PEFC France, chaîne de contrôle : https://www.pefc-france.org/
- FSC France, chaîne de contrôle : https://fr.fsc.org/fr-fr/tracabilite/chaine-de-controle
- Bois de France : https://bois-de-france.org/le-label/
- AFNOR, catalogue des normes : https://www.afnor.org/

## 11. Limites

Les normes complètes sont souvent accessibles sur abonnement ou achat. Les règles contractuelles, fiscales et réglementaires évoluent ; cette note doit être relue avant toute utilisation opérationnelle.
