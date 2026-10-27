# Tennis Stats Android — Spécification fonctionnelle (V1 Beta)

> Document dérivé de l'analyse complète de l'application web Tennis Stats (Next.js) : code source, schéma Prisma et `CLAUDE.md`.
> Lorsque le code web et sa documentation divergeaient, **le code a fait foi**. Les écarts volontaires introduits pour Android sont signalés par le marqueur **[ANDROID]**.

---

## 1. Objectif du produit

**Tennis Stats** permet à un **observateur** (parent, proche, entraîneur) assis au bord du court de **saisir en temps réel chaque coup joué par UN joueur** pendant un match, puis de consulter les **statistiques de performance** de ce joueur sur le match.

L'application ne compte **pas les points ni le score**. Elle mesure la **qualité d'exécution** :

- au **service** : % de 1ers et 2es services rentrés, aces, doubles fautes ;
- en **coup droit** et en **revers** : % de réussite, % de winners, % de fautes au filet, % de fautes out ;
- sur les **coups spéciaux** (volée, smash, lob, amorti) : % de réussite et volume.

### Contraintes d'usage qui orientent l'UX

| Contrainte | Conséquence produit |
|---|---|
| Saisie pendant le jeu, un échange dure quelques secondes | Un coup = **1 tap** (2 au maximum avec un modificateur). Aucune saisie au clavier pendant le match. |
| L'observateur regarde le court, pas l'écran | Grandes zones tactiles à position fixe, codage couleur fort, retour haptique. |
| Les erreurs de saisie sont fréquentes | **Annulation du dernier coup** en 1 tap, toujours visible. |
| Un match dure 1 à 3 h, en extérieur | Écran maintenu allumé pendant la saisie, données persistées à chaque coup (aucune perte si l'app est tuée). |
| Utilisateur francophone | Interface **en français** uniquement. |

### Périmètre V1 Beta

- **Inclus** : création de match, saisie des coups, annulation, fin de match (victoire/défaite), annulation/suppression du match en cours, statistiques du match, historique complet, suppression d'un match terminé, **page « Statistiques »** (match courant, cumul de l'historique, évolution match après match — voir `SPEC-STATISTIQUES.md`).
- **Exclus** : authentification, synchronisation cloud, multi-joueurs, score, export, tablettes/paysage (portrait uniquement).
- **Stockage** : base de données **locale** sur l'appareil uniquement.

---

## 2. Concepts et vocabulaire

| Terme | Définition |
|---|---|
| **Joueur suivi** | Le joueur dont on saisit les coups. Implicite : il n'est pas nommé dans l'app (V1). |
| **Adversaire** | Nom libre + niveau optionnel, renseignés à la création du match. |
| **Match** | Session de saisie. Statut `in_progress` (en cours) ou `completed` (terminé). |
| **Coup (Shot)** | Un coup frappé par le joueur suivi, caractérisé par un **type** et un **résultat**. |
| **Mode service** | État de la saisie où la colonne droite enregistre des services (1er ou 2e). |
| **Modificateur** | Volée, Smash, Lob, Amorti : change le type du prochain coup enregistré. |

---

## 3. Modèle de données

> Les valeurs textuelles (types, résultats, statuts) sont **identiques à celles de l'app web**, afin de permettre une future synchronisation cloud sans conversion.

### 3.1 Match

| Champ | Type | Règles |
|---|---|---|
| `id` | String (UUID v4) | Généré à la création. (Web : cuid.) |
| `opponentName` | String | Obligatoire, `trim()` appliqué, non vide. |
| `opponentLevel` | String? | Optionnel. Stocke la **valeur** du niveau (ex. `"debutant"`, `"15/4"`), voir §3.4. |
| `result` | String? | `"win"` \| `"loss"` ; `null` tant que le match est en cours. |
| `status` | String | `"in_progress"` (défaut) \| `"completed"`. |
| `playedAt` | Instant | = date/heure de création (non modifiable en V1). |
| `createdAt` / `updatedAt` | Instant | Techniques. |

### 3.2 Shot

| Champ | Type | Règles |
|---|---|---|
| `id` | String (UUID v4) | — |
| `matchId` | String | FK vers `Match.id`, **suppression en cascade**. |
| `type` | String | `serve`, `forehand`, `backhand`, `volley`, `smash`, `lob`, `drop_shot` |
| `outcome` | String | `in`, `winner`, `ace`, `fault`, `net` (+ `unforced_error`, voir ci-dessous) |
| `isSecondServe` | Boolean | `true` uniquement pour un 2e service ; `false` sinon. |
| `createdAt` | Instant | Ordre chronologique des coups (tri secondaire stable en cas d'égalité). |

**Combinaisons valides produites par la saisie :**

| Type | Résultats possibles |
|---|---|
| `serve` | `in`, `ace`, `fault`, `net` |
| `forehand`, `backhand`, `volley`, `smash`, `lob`, `drop_shot` | `in`, `winner`, `fault`, `net` |

> `unforced_error` existe dans le modèle web mais **n'est jamais produit** par l'interface. Le conserver dans l'énumération pour compatibilité (libellé « Faute »), sans l'exposer à la saisie.

### 3.3 Libellés

| Type | Libellé | | Résultat | Libellé |
|---|---|---|---|---|
| `serve` | Service | | `in` | En jeu |
| `forehand` | Coup droit | | `winner` | Winner |
| `backhand` | Revers | | `ace` | Ace |
| `volley` | Volée | | `fault` | Out |
| `smash` | Smash | | `net` | Filet |
| `lob` | Lob | | `unforced_error` | Faute |
| `drop_shot` | Amorti | | | |

### 3.4 Niveaux de l'adversaire (ordre d'affichage)

| Valeur stockée | Libellé |
|---|---|
| `debutant` | Débutant |
| `intermediaire` | Intermédiaire |
| `avance` | Avancé |
| `expert` | Expert |
| `nc` | Non classé |
| `40/2`, `40/1`, `30/5`, `30/4`, `30/3`, `30/2`, `30/1`, `30`, `15/5`, `15/4`, `15/3`, `15/2`, `15/1`, `15`, `5/6`, `4/6`, `3/6`, `2/6`, `1/6`, `0`, `-2/6`, `-4/6`, `-15`, `-30` | identique à la valeur |

**[ANDROID]** Toujours **afficher le libellé** (le web affiche la valeur brute, ex. « debutant »). Une valeur inconnue s'affiche telle quelle.

---

## 4. Règles métier globales

| # | Règle |
|---|---|
| RG-01 | **Un seul match en cours à la fois.** Créer un match est refusé si un match `in_progress` existe (message : « Un match est déjà en cours. Terminez-le avant d'en créer un nouveau. »). |
| RG-02 | Le nom de l'adversaire est obligatoire (message : « Le nom de l'adversaire est requis. »). |
| RG-03 | On ne peut ajouter un coup qu'à un match `in_progress`. |
| RG-04 | **Terminer** un match : `status = completed`, `result = win|loss`. Uniquement depuis `in_progress`. Irréversible en V1. |
| RG-05 | **Annuler** un match en cours : supprime le match **et tous ses coups**, retour à l'accueil. Confirmation obligatoire. |
| RG-06 | **Annuler le dernier coup** : supprime le coup le plus récent du match. Répétable jusqu'à 0 coup. **[ANDROID]** Uniquement si le match est en cours (le web l'autorise par erreur sur un match terminé). |
| RG-07 | **[ANDROID]** **Supprimer un match terminé** : supprime le match et ses coups, après confirmation. |
| RG-08 | Chaque coup est **persisté immédiatement** (pas de brouillon en mémoire). |
| RG-09 | Les statistiques sont **calculées à la volée** à partir des coups (aucune valeur agrégée stockée) et sont consultables **pendant** le match (mise à jour en direct) et après. |

---

## 5. Navigation

```
Accueil ──► Nouveau match ──(Démarrer)──► Match [onglet Saisie]
   │                                        ▲   │
   ├──(carte match en cours)────────────────┘   ├── onglet Statistiques
   ├──(ligne historique)──► Match [onglet Statistiques]
   └──(icône Statistiques)──► Statistiques [Match / Historique / Évolution] ──► Match
```

| Écran | Route (Navigation Compose) | Entrée |
|---|---|---|
| Accueil | `Home` | Écran de démarrage |
| Nouveau match | `NewMatch` | ExtendedFAB « Nouveau match » |
| Match | `Match(matchId, initialTab)` | Carte « en cours » (onglet Saisie) ou ligne d'historique (onglet Statistiques) |
| Statistiques | `Stats` | Icône « Statistiques » de la top app bar de l'Accueil (voir `SPEC-STATISTIQUES.md`) |

- Après « Démarrer » : naviguer vers `Match` en **retirant `NewMatch` de la pile** (le retour ramène à l'Accueil).
- Si l'utilisateur ouvre `NewMatch` alors qu'un match est en cours : redirection vers ce match (comportement web).
- Après annulation ou suppression d'un match : retour à l'Accueil.
- Retour système et **predictive back** supportés partout.

---

## 6. Écran Accueil

### Contenu

| Zone | Contenu |
|---|---|
| **Top app bar** | Titre « Tennis Stats » ; action icône `bar_chart` « Statistiques » → page Statistiques. (Pas de menu utilisateur : pas d'authentification en V1.) |
| **Carte match en cours** | Visible si un match `in_progress` existe. Style vert/émeraude, mis en avant. Contient : pastille animée **« En cours »**, « vs {adversaire} », « Niveau : {libellé} » (si renseigné), « {n} coup(s) », date (`dd/MM/yyyy`), libellé d'action **« Reprendre le match »**. Toute la carte est cliquable → écran Match, onglet Saisie. |
| **Message d'information** | Si un match est en cours : « Terminez le match en cours avant d'en démarrer un nouveau. » |
| **Section « Derniers matchs »** | **[ANDROID]** **Tous** les matchs terminés (le web en limite l'affichage à 5), triés par `playedAt` décroissant, dans une `LazyColumn`. Chaque ligne : « vs {adversaire} », date, « {n} coup(s) », badge **Victoire** (vert) ou **Défaite** (rouge). Clic → écran Match, onglet Statistiques. |
| **État vide** | Icône + « Aucun match terminé pour le moment. » + « Commencez par créer un nouveau match ! » |
| **ExtendedFAB « Nouveau match »** | **[ANDROID]** Visible uniquement s'il n'y a **aucun** match en cours (le web affiche un bouton grisé : un FAB désactivé n'est pas un pattern Material). |

Pluriel : « 1 coup », « 0 coups », « 2 coups » (règle web : « coup » + « s » si n ≠ 1).

---

## 7. Écran Nouveau match

| Élément | Détail |
|---|---|
| Top app bar | Titre « Nouveau match », bouton retour. |
| Champ **« Nom de l'adversaire »** | `OutlinedTextField`, placeholder « Ex : Jean Dupont », **focus et clavier automatiques** à l'ouverture, capitalisation des mots, action IME « Terminé » = soumettre. |
| Champ **« Niveau (classement) »** | Menu déroulant (`ExposedDropdownMenuBox`), placeholder « Sélectionner un niveau », **optionnel** ; liste du §3.4. **[ANDROID]** Prévoir une entrée « Aucun » pour effacer le choix. |
| Message d'erreur | Sous le champ concerné (RG-01, RG-02). |
| Bouton **« Démarrer »** | Icône lecture. Pendant la création : « Création... », bouton désactivé. Succès : écran Match (Saisie). |

> Le bouton « Retour » présent dans le formulaire web est remplacé par la flèche de la top app bar et le retour système.

---

## 8. Écran Match

### 8.1 Structure

| Zone | Contenu |
|---|---|
| **Top app bar** | Retour ; titre « vs {adversaire} » ; sous-titre : libellé du niveau (si renseigné) + badge de statut (**« En cours »** vert avec point pulsant / **« Victoire »** vert / **« Défaite »** rouge). Actions : si en cours → bouton **« Terminer »** (ouvre la feuille de fin de match, §8.4) ; si terminé → menu ⋮ avec **« Supprimer le match »** (RG-07). |
| **Onglets** | `PrimaryTabRow` : **« Saisie »** / **« Statistiques »**. Onglet initial : Saisie pour un match en cours, Statistiques pour un match terminé **[ANDROID]**. |

### 8.2 Onglet Saisie

De haut en bas :

1. **Fil du dernier coup (ShotFeed)**
   - Libellé « Dernier coup ({N} total) ».
   - Bouton texte **« Annuler »** (icône undo, couleur d'erreur) → RG-06. Visible uniquement si le match est en cours et N > 0. Désactivé pendant l'opération.
   - Carte du dernier coup, teintée selon le résultat : point coloré + « {Type} → {Résultat} ».
     - Pour un service, le type devient « Service (1er) » ou « Service (2e) ».
     - Un 2e service `fault`/`net` s'affiche **« Double faute »** (rouge foncé).
   - Si N = 0 : « Aucun coup enregistré. Commencez à saisir ! » (cadre en pointillés).
2. **Barre de saisie (ShotTracker)** : fixée en bas de l'écran, au-dessus de la barre de navigation système (gestion des insets), visible **uniquement si le match est en cours**. Le contenu au-dessus défile si l'écran est petit ; la barre de saisie ne défile jamais.

Si le match est terminé, l'onglet Saisie ne montre que le fil du dernier coup (sans « Annuler »).

### 8.3 Barre de saisie (ShotTracker) — cœur de l'application

#### Disposition

```
┌───────────────────────────────────────────────┐
│  [① 1ER SERVICE]        │    [② 2E SERVICE]   │   ← Sélecteur de service
├───────────────┬─────────┬─────────────────────┤
│  ↘ Revers     │ [Volée] │      Coup droit ↗   │   ← En-têtes de colonnes
│  ✓ En jeu     │ [Smash] │      ✓ En jeu       │
│  ★ Winner     │ [Lob]   │      ★ Winner       │
│  ✗ Out        │ [Amorti]│      ✗ Out          │
│  ≡ Filet      │         │      ≡ Filet        │
└───────────────┴─────────┴─────────────────────┘
```

- **Colonne gauche** (Revers) collée au bord gauche de l'écran, **colonne droite** (Coup droit / Service) collée au bord droit : correspond à la position du joueur vue par un observateur droitier. Les colonnes ont des coins arrondis côté centre.
- **Colonne centrale** étroite (~20 % de la largeur) : 4 modificateurs empilés.
- Boutons de résultat : hauteur **64 dp**, icône + libellé gras. Les boutons « Winner », « Out », « Filet » sont légèrement en retrait (≈ 20 dp) côté bord extérieur, « En jeu » occupe toute la largeur — cela crée un repère tactile qui distingue « En jeu », le résultat le plus fréquent.
- Toutes les cibles tactiles ≥ 48 dp.

#### État de la barre

| État | Valeurs | Initialisation |
|---|---|---|
| `activeMode` | `null` (normal) \| `SERVE` \| `VOLLEY` \| `SMASH` \| `LOB` \| `DROP_SHOT` | `null` |
| `serveNumber` | `1` \| `2` | **Dérivé de la base** : `2` si le dernier coup de type `serve` du match est un 1er service (`isSecondServe = false`) en `fault` ou `net` ; sinon `1`. |

> `serveNumber` est **re-dérivé** à l'ouverture de l'écran et après chaque « Annuler » ; `activeMode` n'est pas modifié par « Annuler ». L'état doit survivre à la rotation et à la mort du processus (`SavedStateHandle`).

#### Sélecteur de service (ServeSelector)

| Action | Effet |
|---|---|
| Tap « 1er service » alors que (`activeMode = SERVE` **et** `serveNumber = 1`) | Désactive le mode service (`activeMode = null`). |
| Tap « 1er service » dans tout autre état | `serveNumber = 1`, `activeMode = SERVE`. |
| Tap « 2e service » alors que (`activeMode = SERVE` **et** `serveNumber = 2`) | Désactive le mode service. |
| Tap « 2e service » dans tout autre état | `serveNumber = 2`, `activeMode = SERVE`. |

Le bouton actif est coloré (1er = **violet**, 2e = **ambre/orange**), avec une pastille numérotée « 1 »/« 2 » ; le contour du sélecteur prend la couleur active. Inactif : fond neutre.

#### Modificateurs

| Action | Effet |
|---|---|
| Tap sur un modificateur non sélectionné | `activeMode = ce modificateur` (remplace un éventuel mode service ou autre modificateur). |
| Tap sur le modificateur déjà sélectionné | `activeMode = null`. |

Le modificateur sélectionné prend sa couleur, avec anneau et élévation ; les autres sont neutres.

#### En-têtes et comportement des colonnes selon le mode

| `activeMode` | Colonne gauche | Colonne droite | Résultats colonne droite |
|---|---|---|---|
| `null` | « Revers » (cyan), active | « Coup droit » (bleu) | En jeu, Winner, Out, Filet |
| `SERVE` | « Revers », **grisée et désactivée** | « 1er service » (violet) ou « 2e service » (ambre), icône éclair | En jeu, **Ace** (éclair), Out, Filet |
| modificateur X | « X » (couleur de X), active | « X » (couleur de X) | En jeu, Winner, Out, Filet |

#### Enregistrement d'un coup (tap sur un bouton de résultat)

| Mode | Coup enregistré | Transition d'état après enregistrement |
|---|---|---|
| `null`, colonne gauche | `{ backhand, outcome }` | inchangé |
| `null`, colonne droite | `{ forehand, outcome }` | inchangé |
| modificateur X, **l'une ou l'autre colonne** | `{ X, outcome }` (pas de distinction coup droit / revers sur les coups spéciaux) | `activeMode = null` |
| `SERVE`, `serveNumber = 1`, outcome `in` ou `ace` | `{ serve, outcome, isSecondServe = false }` | `serveNumber = 1`, `activeMode = null` |
| `SERVE`, `serveNumber = 1`, outcome `fault` ou `net` | `{ serve, outcome, isSecondServe = false }` | `serveNumber = 2`, **reste en mode service** |
| `SERVE`, `serveNumber = 2`, n'importe quel outcome | `{ serve, outcome, isSecondServe = true }` | `serveNumber = 1`, `activeMode = null` |

> Un 2e service `fault`/`net` constitue une **double faute**.

#### Pendant l'enregistrement

- Tous les boutons sont **désactivés** et la barre est semi-transparente (≈ 50 %) jusqu'à la fin de l'écriture ; un **indicateur de progression** discret s'affiche en haut à droite de la barre.
- Les taps reçus pendant une écriture sont **ignorés** (protection anti double-tap).
- En cas d'échec : **[ANDROID]** Snackbar « Impossible d'enregistrer le coup » (le web ignore l'erreur silencieusement) ; l'état n'est pas modifié.

#### Retour utilisateur après un coup — [ANDROID]

- **Retour haptique** à chaque coup enregistré (confirmation).
- **Bandeau de feedback** animé en haut de la barre de saisie, sans masquer les boutons, visible **2 secondes** et remplacé à chaque nouveau coup :
  - coup standard : « {Type} → {Résultat} » (ex. « Coup droit → Winner », « Volée → Filet ») ;
  - service : « 1er service → Ace », « 2e service → En jeu », « 1er service → Out » ;
  - double faute : « Double faute ».
- Couleur du bandeau = couleur du résultat.

#### Confort de saisie — [ANDROID]

- **Écran maintenu allumé** tant que l'écran Match d'un match en cours est affiché.
- **Orientation portrait** verrouillée (V1).

### 8.4 Fin de match (feuille modale)

Remplace le panneau dépliable du web (« Terminer ou annuler le match »). Ouverte par le bouton « Terminer » de la top app bar : `ModalBottomSheet` intitulée « Terminer le match ».

| Élément | Effet |
|---|---|
| Bouton **« Victoire »** (trophée, vert) | RG-04 avec `win`. La feuille se ferme, l'écran passe en mode « terminé » (badge, onglet Statistiques). |
| Bouton **« Défaite »** (pouce bas, rouge) | RG-04 avec `loss`. Même suite. |
| Séparateur | — |
| Bouton texte **« Annuler et supprimer le match »** (corbeille, couleur d'erreur) | Ouvre une `AlertDialog` : titre « Supprimer le match ? », texte « Tous les coups saisis seront définitivement supprimés. », actions « Annuler » / **« Supprimer »**. Confirmation → RG-05. (Remplace le double tap « Confirmer la suppression » du web.) |

Les boutons sont désactivés avec un indicateur de progression pendant l'opération.

### 8.5 Onglet Statistiques

Voir §9. Mise à jour **en direct** pendant le match (observation réactive de la base).
Si le match ne compte aucun coup : « Aucune statistique disponible pour l'instant. »

---

## 9. Statistiques — définitions exactes

> Statistiques d'**un** match. Le cumul sur l'historique, l'évolution et la comparaison d'un match à ses moyennes sont spécifiés dans `SPEC-STATISTIQUES.md` (mêmes formules, appliquées à plusieurs matchs).

Notations : `pct(a, b) = round(a / b × 100)` (arrondi au plus proche, demi vers le haut). Affichage `"{pct}%"`, ou **« — »** si la valeur n'est pas calculable.

### 9.1 Section « Service » — affichée s'il existe ≥ 1 coup `serve`

| Tuile | Valeur | Sous-ligne | Couleur |
|---|---|---|---|
| **1er srv** | `pct(S1_in, S1)` ; « — » si `S1 = 0` | `"{S1_in}/{S1}"` | vert |
| **2e srv** | `pct(S2_in, S2)` ; « — » si `S2 = 0` | `"{S2_in}/{S2}"` | ambre |
| **Aces** | nombre de services `ace` (1er et 2e confondus) | — | émeraude |
| **Dbl. faute** | nombre de 2es services `fault` ou `net` | — | rouge |

- `S1` = services avec `isSecondServe = false` ; `S2` = services avec `isSecondServe = true`.
- `S1_in` / `S2_in` = parmi ceux-ci, outcome `in` **ou** `ace`.

### 9.2 Section « Coup droit » / « Revers » — une carte commune

Affichée si au moins un coup `forehand` ou `backhand` existe. Sous-section « Coup droit » si ≥ 1 `forehand`, sous-section « Revers » si ≥ 1 `backhand`, séparées par un trait si les deux sont présentes. Pour un type donné, avec `T` = total, `R` = réussis (`in` + `winner`), `W` = `winner`, `N` = `net`, `O` = `fault` :

| Tuile | Valeur | Sous-ligne | Couleur |
|---|---|---|---|
| **Succès** | `pct(R, T)` | `"sur {T}"` | vert |
| **Winners** | `pct(W, R)` ; « — » si `R = 0` | « sur succès » | émeraude |
| **Filet** | `pct(N, T)` | — | rouge |
| **Out** | `pct(O, T)` | — | orange |

> « Winners » est un pourcentage **des coups réussis**, pas du total.

### 9.3 Section « Coups spéciaux »

Affichée si au moins un coup spécial existe. Une tuile par type joué, dans l'ordre Volée, Smash, Lob, Amorti (types sans coup omis) :

| Tuile | Valeur | Sous-ligne | Couleur |
|---|---|---|---|
| **{Libellé du type}** | `pct(R, T)` (R = `in` + `winner`) | `"{T} coup"` / `"{T} coups"` (pluriel si T > 1) | vert |

### 9.4 Présentation

- Chaque section est une carte avec un titre centré entre deux filets (style « — SERVICE — », petites capitales).
- Tuiles en grille de **4 colonnes** : valeur en gras chiffres tabulaires, libellé, sous-ligne discrète.

### 9.5 Exemples de validation (à implémenter en tests unitaires)

1. Services : 1er `fault`, 2e `in`, 1er `ace`, 1er `net`, 2e `net` →
   1er srv = 1/3 = **33%**, 2e srv = 1/2 = **50%**, Aces = **1**, Dbl. faute = **1**.
2. Coups droits : `in`, `in`, `winner`, `net`, `fault`, `fault` → Succès 3/6 = **50%**, Winners 1/3 = **33%**, Filet 1/6 = **17%**, Out 2/6 = **33%**.
3. Revers : `net`, `fault` → Succès **0%**, Winners **—**, Filet **50%**, Out **50%**.
4. Volée `in`, Volée `fault`, Smash `winner` → Volée **50%** « 2 coups », Smash **100%** « 1 coup ».
5. Arrondi : 1 sur 8 = 12,5 → **13%** ; 2 sur 3 = 66,67 → **67%**.

---

## 10. Charte visuelle

- **Material 3**, thème clair et sombre (suit le système). **Pas de couleurs dynamiques (Material You)** en V1 : le codage couleur des résultats est sémantique et doit rester stable.
- Couleur primaire : indigo (≈ `#2E3A87`) ; couleur de marque/icône : violet `#5B21B6`.
- Couleurs sémantiques (clair : fond / contenu — sombre : fond / contenu) :

| Rôle | Clair | Sombre |
|---|---|---|
| En jeu (green) | `#DCFCE7` / `#15803D` | `#14532D`@40% / `#86EFAC` |
| Winner, Ace, Victoire (emerald) | `#D1FAE5` / `#047857` | `#064E3B`@40% / `#6EE7B7` |
| Out (orange), Amorti | `#FFEDD5` / `#C2410C` | `#7C2D12`@40% / `#FDBA74` |
| Filet, Défaite, Smash (red) | `#FEE2E2` / `#B91C1C` | `#7F1D1D`@40% / `#FCA5A5` |
| 1er service (violet) | `#EDE9FE` / `#6D28D9` | `#4C1D95`@40% / `#C4B5FD` |
| 2e service, Volée (amber) | `#FEF3C7` / `#B45309` | `#78350F`@40% / `#FCD34D` |
| Revers (cyan) | `#CFFAFE` / `#0E7490` | `#164E63`@40% / `#67E8F9` |
| Coup droit (blue) | `#DBEAFE` / `#1D4ED8` | `#1E3A8A`@40% / `#93C5FD` |
| Lob (teal) | `#CCFBF1` / `#0F766E` | `#134E4A`@40% / `#5EEAD4` |

- Icônes (Material Symbols, équivalents des icônes Lucide du web) :

| Élément | Icône |
|---|---|
| En jeu | `check_circle` |
| Winner | `star` |
| Ace / Service | `bolt` |
| Out | `cancel` |
| Filet | `fence` (ou `view_stream`) |
| Coup droit | `north_east` |
| Revers | `south_east` |
| Volée | `back_hand` |
| Smash | `local_fire_department` |
| Lob | `north` |
| Amorti | `south` |
| Victoire / Défaite | `emoji_events` / `thumb_down` |
| Annuler le coup / Supprimer | `undo` / `delete` |
| Nouveau match / Démarrer | `sports_tennis` / `play_arrow` |
| Nombre de coups / Date | `monitoring` / `schedule` |

- Icône d'application : balle de tennis stylisée sur fond violet `#5B21B6` (adaptive icon + monochrome pour les icônes à thème).

---

## 11. Écarts avec l'app web — récapitulatif

| Sujet | Web | Android V1 | Raison |
|---|---|---|---|
| Authentification | Google OAuth | Aucune | Beta locale |
| Stockage | SQLite / Turso | Room (local) | Beta locale |
| Historique | 5 derniers matchs | Tous, liste défilante | Choix produit |
| Supprimer un match terminé | Impossible | Menu ⋮ + confirmation | Choix produit |
| Feedback après un coup | Aucun (le bandeau décrit dans `CLAUDE.md` n'existe plus dans le code) | Haptique + bandeau 2 s | Choix produit |
| Fin de match | Panneau dépliable | Bouton top bar + ModalBottomSheet | Pattern Android |
| Confirmation de suppression | Double tap | AlertDialog | Pattern Android |
| Nouveau match si match en cours | Bouton grisé | FAB masqué + message | Pattern Material |
| « Annuler » sur un match terminé | Possible (bug) | Interdit | Correction |
| Affichage du niveau | Valeur brute | Libellé | Correction |
| Erreur d'enregistrement d'un coup | Ignorée silencieusement | Snackbar | Correction |
| Onglet initial d'un match terminé | Saisie | Statistiques | Ergonomie |
| Écran allumé, orientation | — | Écran maintenu allumé, portrait | Usage en bord de court |
