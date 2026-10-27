# Tennis Stats Android — Refonte des statistiques (V1.1)

> Complète `SPEC-FONCTIONNELLE.md` (§9 : formules des statistiques d'un match, inchangées) et `SPEC-TECHNIQUE.md`.
> Statut : **implémenté**. Les choix pris en l'absence d'indication sont regroupés en §8.

---

## 1. Étude

### 1.1 Constat sur la V1

Les statistiques n'existent qu'à l'échelle d'**un** match (onglet « Statistiques » de l'écran Match). Pour savoir si le joueur progresse, l'observateur doit ouvrir les matchs un par un et comparer de mémoire. Trois questions restent sans réponse :

| Question de l'observateur | Besoin |
|---|---|
| « Comment se passe ce match ? Est-ce mieux ou moins bien que d'habitude ? » | Statistiques du match courant **et** comparaison à ses moyennes. |
| « Quel est le profil du joueur ? » (service fiable ? revers fragile ?) | Statistiques **cumulées** sur l'historique. |
| « Est-ce qu'il progresse ? Est-ce que ça va mieux ces derniers temps ? » | **Évolution** match après match, avec une tendance récente. |

### 1.2 Ce qui fait sens avec les données disponibles

L'application ne compte pas le score : seuls les coups (type × résultat × 1er/2e service) sont connus. On en tire deux familles d'indicateurs :

- **Taux** (1er service, 2e service, réussite coup droit, réussite revers) : indépendants de la durée du match, donc comparables d'un match à l'autre. Ce sont les indicateurs clés de l'évolution.
- **Volumes** (aces, doubles fautes, winners) : dépendent de la longueur du match et de l'exhaustivité de la saisie. On les présente **par match** (moyenne) et on le dit.

### 1.3 Pièges identifiés et réponses

| Piège | Réponse |
|---|---|
| Petits échantillons (un match avec 2 revers → 0 % ou 100 %) | Toujours afficher l'effectif (« 12/20 », « sur 40 ») ; un match sans coup du type n'a **pas** de valeur (point absent du graphique, pas un 0). |
| Moyenne de pourcentages biaisée par les petits matchs | Les taux cumulés sont **poolés** : `pct(Σ réussis, Σ total)`, chaque coup pèse autant. |
| Saisie partielle (l'observateur n'a pas noté les services d'un match) | Les volumes par match ne comptent que les matchs où la famille de coups a été saisie (§4.2). |
| Le match en cours fausse l'historique | Historique et évolution ne portent que sur les **matchs terminés**. Le match en cours a son propre onglet, comparé à l'historique. |
| Vieux matchs non représentatifs du niveau actuel | Filtre de période : « Tous », « 10 derniers », « 5 derniers ». |
| Graphique seul inaccessible (TalkBack, daltonisme) | Liste détaillée par match sous le graphique, description vocale du graphique, couleur jamais seule porteuse de sens (signes « + »/« − », libellés). |
| Bibliothèque de graphiques exclue (spec technique §1) | Graphique en courbe dessiné avec `Canvas` Compose. |

### 1.4 Parti pris

Une page **« Statistiques »** dédiée, accessible depuis l'Accueil, organisée en trois onglets qui suivent les trois questions : **Match**, **Historique**, **Évolution**. L'onglet « Statistiques » de l'écran Match est conservé tel quel (consultation pendant la saisie).

---

## 2. Navigation

```
Accueil ──(icône « Statistiques » de la top app bar)──► Statistiques [onglet Match]
                                                          ├── « Ouvrir le match » ──► Match (Saisie si en cours, Statistiques sinon)
                                                          └── ligne « Détail par match » ──► Match [onglet Statistiques]
```

| Écran | Route | Entrée |
|---|---|---|
| Statistiques | `Stats` (data object) | bouton icône `bar_chart` (contentDescription « Statistiques ») dans la top app bar de l'Accueil, toujours visible |

Top app bar : flèche retour, titre « Statistiques ». Onglets (`PrimaryTabRow`) : « Match » / « Historique » / « Évolution ». Onglet initial : « Match ». Onglet, période et indicateur choisis sont conservés (`SavedStateHandle`).

Toutes les données sont observées : la page se met à jour en direct (coup saisi, match terminé ou supprimé).

---

## 3. Onglet « Match »

**Match affiché** (« match de référence ») : le match **en cours** s'il existe, sinon le **dernier match terminé** (`playedAt` le plus récent). Aucun match : « Aucun match pour le moment. » + « Créez un match pour suivre ses statistiques. »

| Zone | Contenu | Comportement |
|---|---|---|
| **Carte du match** (`FocusMatchCard`) | surtitre « Match en cours » ou « Dernier match terminé » ; « vs {adversaire} » ; « Niveau : {libellé} » (si renseigné) ; date et « {n} coup(s) » ; `MatchStatusBadge` ; bouton texte « Ouvrir le match » | « Ouvrir le match » → `Match(id, INPUT)` si en cours, `Match(id, STATS)` sinon |
| **Sections du match** | exactement les cartes de `SPEC-FONCTIONNELLE.md` §9 (Service, Coup droit / Revers, Coups spéciaux) ; aucun coup : « Aucune statistique disponible pour l'instant. » | en direct |
| **Carte « Comparaison »** (`ComparisonCard`) | titre « — COMPARAISON — », sous-titre « Par rapport à votre 1 autre match terminé » / « Par rapport à la moyenne de vos {n} autres matchs terminés » ; une ligne par indicateur (§4.1) : libellé, valeur du match, « moy. {moyenne} », écart | affichée s'il existe au moins un indicateur calculable à la fois sur le match et sur les **autres** matchs terminés (toute la période, filtre ignoré) |

**Écart** : valeur du match − moyenne de référence. Taux : « +5 pts » / « −3 pts » / « = » (arrondi à l'entier) ; volumes : « +1,2 » / « −0,5 » / « = » (1 décimale). Couleur : vert (`green`) si meilleur, rouge (`red`) si moins bon, neutre si égal à l'arrondi près. « Meilleur » = plus haut, sauf **doubles fautes** (plus bas). Le signe porte le sens, la couleur le renforce.

---

## 4. Indicateurs

### 4.1 Liste (ordre d'affichage)

| Indicateur (`StatMetric`) | Libellé | Nature | Valeur d'un match | Définie si | Meilleur |
|---|---|---|---|---|---|
| `FIRST_SERVE` | « 1er service » | taux | `pct(S1_in, S1)` | `S1 ≥ 1` | haut |
| `SECOND_SERVE` | « 2e service » | taux | `pct(S2_in, S2)` | `S2 ≥ 1` | haut |
| `FOREHAND` | « Coup droit » | taux | `pct(R, T)` des coups droits | `T ≥ 1` | haut |
| `BACKHAND` | « Revers » | taux | `pct(R, T)` des revers | `T ≥ 1` | haut |
| `ACES` | « Aces » | volume | nombre d'aces | ≥ 1 service | haut |
| `DOUBLE_FAULTS` | « Doubles fautes » | volume | nombre de doubles fautes | ≥ 1 service | **bas** |
| `WINNERS` | « Winners » | volume | nombre de `winner` (hors service) | ≥ 1 coup hors service | haut |

Notations de `SPEC-FONCTIONNELLE.md` §9 (`S1_in` inclut les aces, `R` = `in` + `winner`).

### 4.2 Agrégation sur plusieurs matchs

- **Taux** : poolé, `pct(Σ part, Σ total)` sur les matchs où l'indicateur est défini.
- **Volumes** : moyenne par match, `Σ valeur / nombre de matchs où l'indicateur est défini`, affichée avec 1 décimale (« 2,3 »).
- Aucun match où l'indicateur est défini → « — ».

---

## 5. Onglet « Historique »

Porte sur les matchs **terminés** de la période choisie. Sélecteur de période en tête (`PeriodSelector`, `SingleChoiceSegmentedButtonRow`, partagé avec « Évolution ») : « Tous » / « 10 derniers » / « 5 derniers » (les N matchs terminés les plus récents). Sous le sélecteur : « {n} match(s) terminé(s) » (le match en cours n'est jamais compté).

Aucun match terminé : « Aucun match terminé pour le moment. » + « Les statistiques cumulées apparaîtront à la fin de votre premier match. »

| Carte (affichée si…) | Contenu |
|---|---|
| « — BILAN — » (toujours) | tuiles : « Matchs » — n ; « Victoires » — V — sous-ligne « {pct}% » (`pct(V, n)`) — émeraude ; « Défaites » — D — rouge ; « Coups / match » — moyenne arrondie — sous-ligne « {total} au total » |
| « — SERVICE — » (≥ 1 service) | « 1er srv » et « 2e srv » poolés (comme §9.1) ; « Aces / match » — moyenne (§4.2) — sous-ligne « {total} au total » — émeraude ; « DF / match » — idem — rouge |
| « — COUP DROIT — » / « — REVERS — » | §9.2 calculé sur l'ensemble des coups de la période |
| « — COUPS SPÉCIAUX — » | §9.3 calculé sur l'ensemble des coups de la période |
| « — VICTOIRES / DÉFAITES — » (`ResultSplitCard`, ≥ 1 victoire **et** ≥ 1 défaite) | tableau : en-têtes « Victoires » / « Défaites » ; une ligne par indicateur de §4.1, valeurs agrégées (§4.2) sur les victoires puis sur les défaites, « — » si non calculable ; lignes sans aucune valeur omises. Montre ce qui distingue un match gagné d'un match perdu. |

---

## 6. Onglet « Évolution »

Porte sur les matchs terminés de la période choisie (même `PeriodSelector`), en ordre **chronologique** (le plus ancien à gauche).

| Zone | Contenu | Comportement |
|---|---|---|
| **Sélecteur d'indicateur** (`MetricSelector`) | `FilterChip` défilant horizontalement, un par indicateur de §4.1 ; initial « 1er service » | un seul sélectionné |
| **Graphique** (`TrendChart`, dans une carte) | titre = libellé de l'indicateur + unité (« en % » / « par match ») ; courbe (2 dp) de la couleur de l'indicateur (§6.1) reliant un point par match où l'indicateur est défini ; points de 10 dp remplis selon le résultat (émeraude = victoire, rouge = défaite) avec un anneau de la couleur de surface ; ligne horizontale en pointillés = moyenne de la période ; grille discrète (taux : 0, 50, 100 % ; volumes : 0, max/2, max arrondi) ; dates du premier et du dernier match sous l'axe ; légende « Victoire », « Défaite », « Moyenne » | tap sur le graphique → sélectionne le point le plus proche : il est agrandi et un encart au-dessus indique « {date} · vs {adversaire} : {valeur} » ; description d'accessibilité : « {indicateur} sur {n} matchs, du {premier} au {dernier}, moyenne {moyenne} » |
| **Moins de 2 points** | « Au moins 2 matchs terminés avec cet indicateur sont nécessaires pour afficher une évolution. » (le graphique et la synthèse sont masqués, la liste reste) | — |
| **Synthèse** (rangée de 4 `StatTile`) | « Moyenne » (§4.2) ; « Meilleur » — sous-ligne date ; « Dernier » — sous-ligne date ; « Tendance » — écart formaté comme §3, sous-ligne « 3 derniers » | Tendance = moyenne des valeurs des **3 derniers** matchs − moyenne des valeurs des matchs **précédents** de la période ; « — » s'il y a moins de 4 points. Couleur selon §3 (vert = progrès). |
| **Détail par match** (titre « Détail par match ») | une ligne (`TrendMatchRow`) par match où l'indicateur est défini, **le plus récent en premier** : « vs {adversaire} », date, valeur (« 62% » + « 18/29 », ou « 3 »), badge Victoire/Défaite | tap → `Match(id, STATS)` |
| Aucun match terminé | message de §5 | — |

### 6.1 Couleur des indicateurs

1er service `violet`, 2e service `amber`, Coup droit `blue`, Revers `cyan`, Aces `emerald`, Doubles fautes `red`, Winners `teal` (couleurs sémantiques existantes, `content`).

---

## 7. Exemples de validation (tests unitaires)

1. **Taux poolé** : match A 1ers services 1/2, match B 9/10 → historique 1er srv = 10/12 = **83%** (et non la moyenne 70 %).
2. **Volume par match** : A 2 doubles fautes (avec services), B 4, C sans aucun service → DF / match = 6/2 = **3,0** ; C n'est pas compté.
3. **Indicateur non défini** : un match sans revers n'a pas de point « Revers ».
4. **Tendance** : valeurs chronologiques 50, 60, 70, 80, 90 → 3 derniers = 80, précédents = 55 → **+25 pts**. Avec 3 points → « — ».
5. **Meilleur** : doubles fautes 3, 1, 2 → meilleur = **1**.
6. **Comparaison** : match en cours 1er srv 7/10 = 70 %, autres matchs poolés 60 % → « +10 pts », vert. Doubles fautes 4 contre une moyenne de 2,0 → « +2,0 », rouge.
7. **Période** : 12 matchs terminés, « 5 derniers » → seuls les 5 plus récents (`playedAt`) sont agrégés.
8. **Match de référence** : match en cours présent → il est affiché même s'il n'a aucun coup ; sinon le dernier match terminé.

---

## 8. Décisions

- **Page dédiée** plutôt qu'un enrichissement de l'onglet du match : l'onglet du match reste centré sur la saisie en direct, la page dédiée répond aux questions transverses.
- **Pas de sélecteur de match** dans l'onglet « Match » : tous les matchs restent consultables depuis l'Accueil ou « Détail par match ».
- **Comparaison** sur toute la période (filtre ignoré) et en excluant le match affiché, pour qu'un match ne se compare jamais à lui-même.
- **Tendance** sur les valeurs par match (moyenne simple) et non poolée : elle décrit la forme récente, match par match.
- **Points manquants** : un match sans valeur pour l'indicateur n'est pas tracé ; la courbe relie les points présents.
- **Volumes non normalisés par le nombre de coups** : plus lisibles pour un parent (« 3 doubles fautes par match ») ; le caractère « par match » est affiché.
- **Aucune donnée stockée en plus** : tout est calculé à la volée (RG-09) ; le comptage par match se fait en SQL (`GROUP BY`) pour ne pas charger tous les coups de l'historique. Pas de changement de schéma Room.
