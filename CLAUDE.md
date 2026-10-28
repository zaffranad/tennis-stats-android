# CLAUDE.md — Tennis Stats Android

> **Ce fichier est la source de vérité de l'application. Après toute modification du code qui impacte une section (écran, composant, bouton, libellé, route, entité ou champ Room, dépendance), mettre CLAUDE.md à jour dans la même session de travail.**

Références fonctionnelles et techniques (elles font foi en cas de doute) : `docs/SPEC-FONCTIONNELLE.md`, `docs/SPEC-TECHNIQUE.md`, `docs/SPEC-STATISTIQUES.md` (page « Statistiques »). Décisions prises hors spec : section « Décisions » du `README.md`.

---

## 1. Vue d'ensemble

**Tennis Stats** (V1 Beta, `0.1.0-beta`) est le portage Android natif d'une application web Next.js. Un **observateur** (parent, entraîneur) saisit en temps réel, en **1 tap** (2 avec un modificateur), chaque coup joué par **un** joueur suivi, puis consulte les statistiques d'exécution du match. L'application **ne compte pas le score**.

- 100 % hors ligne : pas d'authentification, pas de réseau, pas de Firebase. Base **Room** locale uniquement.
- Interface **en français** uniquement ; tous les textes sont dans `app/src/main/res/values/strings.xml` (aucun texte en dur dans les composables). Code, identifiants et commits en anglais (Conventional Commits).
- Portrait uniquement, thème clair/sombre suivant le système, **sans dynamic color** (codage couleur sémantique stable).
- Les valeurs stockées sont **strictement identiques au web** (future synchro cloud sans conversion) — voir §3.

### Règles métier (appliquées dans `OfflineMatchRepository`, pas seulement dans l'UI)

| # | Règle |
|---|---|
| RG-01 | Un seul match `in_progress` à la fois (vérifié en transaction à la création). |
| RG-02 | Nom de l'adversaire obligatoire (`trim()`, non vide). |
| RG-03 | Ajout d'un coup uniquement sur un match `in_progress`. |
| RG-04 | Terminer : `status = completed`, `result = win|loss`, uniquement depuis `in_progress`, irréversible. |
| RG-05 | Annuler un match en cours = supprimer le match et ses coups (confirmation obligatoire). |
| RG-06 | « Annuler » le dernier coup, répétable jusqu'à 0, ou supprimer un coup précis (✕ du fil), uniquement sur un match en cours. |
| RG-07 | Supprimer un match terminé (confirmation obligatoire). |
| RG-08 | Chaque coup est persisté immédiatement. |
| RG-09 | Statistiques calculées à la volée depuis les coups, en direct pendant le match (y compris historique et évolution : aucune valeur agrégée stockée). |

---

## 2. Stack

| Couche | Choix | Version |
|---|---|---|
| Langage | Kotlin (Kotlin intégré à AGP 9, pas de plugin `kotlin-android`) | 2.4.21 |
| Build | Gradle Kotlin DSL + Version Catalog `gradle/libs.versions.toml`, wrapper commité | Gradle 9.8.2, AGP 9.4.1 |
| SDK | minSdk 26, targetSdk/compileSdk 37, JVM toolchain 17 | — |
| UI | Jetpack Compose + Material 3 (BOM) | BOM 2026.09.00 (Material 3 1.4.0) |
| Navigation | Navigation Compose, routes typées `@Serializable` | 2.10.2 |
| DI | Hilt (+ `hilt-navigation-compose`, `hiltViewModel()` de `androidx.hilt.lifecycle.viewmodel.compose`) | 2.60.1 / 1.4.0 |
| Persistance | Room (KSP, schéma exporté dans `app/schemas/`) | 2.8.5 |
| Génération de code | KSP (pas de kapt) | 2.3.12 |
| Asynchronisme | Coroutines + Flow (`kotlinx-coroutines-android` déclaré explicitement) | 1.11.0 |
| Sérialisation | kotlinx.serialization (routes) | 1.12.0 |
| Cycle de vie | `lifecycle-runtime-compose`, `lifecycle-viewmodel-compose` | 2.11.0 |
| Autres AndroidX | core-ktx 1.19.1, activity-compose 1.13.0, core-splashscreen 1.2.0 | — |
| Tests | JUnit 4.13.2, Truth 1.4.5, Turbine 1.2.1, coroutines-test 1.11.0, AndroidX Test (ext-junit 1.3.0, runner 1.7.0, espresso 3.7.0), Compose UI Test | — |
| Qualité | Android Lint, ktlint 1.8.0 via Spotless 8.10.4 (style `android_studio`, `.editorconfig`) | — |
| CI | GitHub Actions `.github/workflows/ci.yml` : `spotlessCheck lint testDebugUnitTest assembleDebug` | — |

Exclus en V1 : réseau (Retrofit/Ktor), Firebase, authentification, DataStore, bibliothèques de graphiques (graphique d'évolution en `Canvas`), `material-icons-extended` (icônes en vector drawables).

---

## 3. Modèle de données

### 3.1 Room — base `tennis_stats.db`, version 1 (`data/local/TennisStatsDatabase.kt`)

**Table `matches`** — `MatchEntity` (index : `status`, `playedAt`)

| Champ | Type Kotlin → SQLite | Règles |
|---|---|---|
| `id` | `String` (PK) | UUID v4 (`UuidIdGenerator`) |
| `opponentName` | `String` | obligatoire, trimé |
| `opponentLevel` | `String?` | **valeur** du niveau (`debutant`, `15/4`…), voir §3.3 |
| `result` | `String?` | `win` \| `loss`, `null` tant que le match est en cours |
| `status` | `String` | `in_progress` \| `completed` |
| `playedAt` | `Instant` → INTEGER (epoch millis) | = date de création |
| `createdAt`, `updatedAt` | `Instant` → INTEGER (epoch millis) | techniques |

**Table `shots`** — `ShotEntity` (FK `matchId` → `matches.id`, **ON DELETE CASCADE** ; index `(matchId, createdAt)`)

| Champ | Type | Règles |
|---|---|---|
| `id` | `String` (PK) | UUID v4 |
| `matchId` | `String` | FK |
| `type` | `String` | `serve`, `forehand`, `backhand`, `volley`, `smash`, `lob`, `drop_shot` |
| `outcome` | `String` | `in`, `winner`, `ace`, `fault`, `net`, `unforced_error` (jamais produit par la saisie) |
| `isSecondServe` | `Boolean` | `true` uniquement pour un 2e service |
| `createdAt` | `Instant` → INTEGER (epoch millis) | tri chronologique |

- Tri des coups : `ORDER BY createdAt DESC, rowid DESC` (le plus récent d'abord, stable dans la même milliseconde).
- `Converters` : `Instant` ↔ epoch millis. `Mappers.kt` : `toDomain()` / `toEntity()`.
- `MatchWithShotCount` : `@Embedded MatchEntity` + `shotCount` (sous-requête `COUNT(*)`).
- `ShotCountRow(matchId, type, outcome, isSecondServe, count)` : comptage groupé (pas une table) ; `List<ShotCountRow>.toTallies()` (`Mappers.kt`) → `Map<matchId, ShotTally>`.

**DAO**

| DAO | Méthode | Rôle |
|---|---|---|
| `MatchDao` | `observeActiveMatch(): Flow<MatchWithShotCount?>` | match `in_progress` + nombre de coups |
| | `observeCompletedMatches(): Flow<List<MatchWithShotCount>>` | matchs `completed`, `playedAt DESC` |
| | `observeMatch(id): Flow<MatchEntity?>`, `getMatch(id)`, `getActiveMatch()` | lecture |
| | `insert(match)`, `finishMatch(id, result, updatedAt): Int`, `deleteMatch(id): Int` | écriture (`finishMatch` ne touche qu'un match `in_progress`) |
| `ShotDao` | `observeShots(matchId): Flow<List<ShotEntity>>` | alimente fil, compteur, stats, dérivation du n° de service |
| | `observeCompletedShotCounts(): Flow<List<ShotCountRow>>` | `GROUP BY matchId, type, outcome, isSecondServe` des matchs `completed` (historique, évolution) |
| | `getLastShot(matchId)`, `getShot(matchId, shotId)`, `insertShot(shot)`, `deleteLastShot(matchId): Int`, `deleteShot(matchId, shotId): Int` | lecture / écriture |

### 3.2 Domaine (`domain/model/`, Kotlin pur)

| Type | Valeurs (`value` = valeur stockée) |
|---|---|
| `ShotType` | `SERVE("serve")`, `FOREHAND("forehand")`, `BACKHAND("backhand")`, `VOLLEY("volley")`, `SMASH("smash")`, `LOB("lob")`, `DROP_SHOT("drop_shot")` ; `SPECIAL_SHOTS` = Volée, Smash, Lob, Amorti (ordre d'affichage) ; `isSpecial` |
| `ShotOutcome` | `IN("in")`, `WINNER("winner")`, `ACE("ace")`, `FAULT("fault")`, `NET("net")`, `UNFORCED_ERROR("unforced_error")` ; `isMiss` = `FAULT` ou `NET` |
| `MatchStatus` | `IN_PROGRESS("in_progress")`, `COMPLETED("completed")` |
| `MatchResult` | `WIN("win")`, `LOSS("loss")` |
| `OpponentLevel` | 29 niveaux du §3.3, `fromValue()` renvoie `null` pour une valeur inconnue |
| `Match` | `id`, `opponentName`, `opponentLevel: String?`, `result`, `status`, `playedAt`, `createdAt`, `updatedAt` ; `isInProgress` |
| `Shot` | `id`, `matchId`, `type`, `outcome`, `isSecondServe`, `createdAt` ; `isDoubleFault` = 2e service `fault`/`net` |
| `MatchSummary` | `match` + `shotCount` (Accueil) |

### 3.3 Niveaux de l'adversaire (ordre d'affichage)

`debutant` « Débutant », `intermediaire` « Intermédiaire », `avance` « Avancé », `expert` « Expert », `nc` « Non classé », puis les classements affichés tels quels : `40/2`, `40/1`, `30/5`, `30/4`, `30/3`, `30/2`, `30/1`, `30`, `15/5`, `15/4`, `15/3`, `15/2`, `15/1`, `15`, `5/6`, `4/6`, `3/6`, `2/6`, `1/6`, `0`, `-2/6`, `-4/6`, `-15`, `-30`. Le **libellé** est toujours affiché (`opponentLevelLabel()`), une valeur inconnue s'affiche telle quelle.

### 3.4 Repository (`data/repository/`)

- `MatchRepository` (interface) → `OfflineMatchRepository` (Room). Méthodes : `observeActiveMatch`, `observeCompletedMatches`, `observeCompletedMatchTallies` (`List<MatchTally>`, `playedAt DESC`), `observeMatch`, `observeShots`, `createMatch(opponentName, opponentLevel)`, `addShot(matchId, ShotToRecord)`, `deleteLastShot(matchId)`, `deleteShot(matchId, shotId)` (renvoient le coup supprimé ou `null`), `finishMatch(matchId, MatchResult)`, `deleteMatch(matchId)` (RG-05 et RG-07).
- Résultat : `RepositoryResult.Success(value)` / `RepositoryResult.Failure(MatchError)`. `MatchError` : `MatchAlreadyInProgress`, `OpponentNameRequired`, `MatchNotInProgress`, `MatchNotFound`. Les erreurs de stockage sont des exceptions (traduites en Snackbar/message par les ViewModels).
- Horodatages via `Clock` (`SystemClock`), identifiants via `IdGenerator` (`UuidIdGenerator`), injectés par Hilt (`data/di/RepositoryModule.kt`, `DatabaseModule.kt`).

---

## 4. Navigation et écrans

Permission déclarée : `VIBRATE` uniquement (normale, accordée à l'installation).

Single-activity (`MainActivity` : splash screen `core-splashscreen`, `enableEdgeToEdge()`, portrait). `navigation/Routes.kt` + `navigation/TennisStatsNavHost.kt`.

| Écran | Route | Entrée | Sortie |
|---|---|---|---|
| Accueil | `Home` (data object) | démarrage | — |
| Nouveau match | `NewMatch` (data object) | FAB « Nouveau match » | « Démarrer » → `Match(id, INPUT)` en **retirant `NewMatch`** de la pile ; redirection identique si un match est déjà en cours à l'ouverture |
| Match | `Match(matchId: String, initialTab: MatchTab)` | carte « en cours » (`INPUT`) ou ligne d'historique (`STATS`) ; depuis Statistiques : « Ouvrir le match », ligne « Détail par match » | retour ; match supprimé ou introuvable → `popBackStack<Home>` |
| Statistiques | `Stats` (data object) | icône « Statistiques » de la top app bar de l'Accueil | retour |

`MatchTab` (`@Keep @Serializable enum`) : `INPUT` = onglet « Saisie », `STATS` = onglet « Statistiques ».

Architecture UI : un ViewModel Hilt par écran exposant **un** `StateFlow<XxxUiState>` ; événements ponctuels (navigation, Snackbar) via `Channel` exposé en `Flow`.

---

## 5. Nomenclature des écrans, zones, composants et boutons

Les libellés entre « » sont les textes **exacts** affichés (ressource `strings.xml` entre parenthèses quand utile).

### 5.1 Accueil — `ui/home/HomeScreen.kt` (`HomeScreen` → `HomeContent`), `HomeViewModel`, `HomeUiState`

| Zone / composant | Contenu affiché | Comportement |
|---|---|---|
| **Top app bar** (`TopAppBar`) | « Tennis Stats » (`home_title`) ; action icône `bar_chart` (contentDescription « Statistiques », `home_stats`) | pas de menu ; l'icône ouvre `Stats` (toujours visible) |
| **Carte match en cours** (`ActiveMatchCard`) | badge « En cours » avec point pulsant (`MatchStatusBadge`), « vs {adversaire} », « Niveau : {libellé} » (si renseigné), date `dd/MM/yyyy` (icône `schedule`), « {n} coup(s) » (icône `monitoring`), « Reprendre le match » | visible si un match `in_progress` existe ; fond/contour vert ; toute la carte est cliquable → `Match(id, INPUT)` |
| **Message d'information** | « Terminez le match en cours avant d'en démarrer un nouveau. » | visible si un match est en cours |
| **Section « Derniers matchs »** (titre `heading`) | **tous** les matchs terminés, `playedAt` décroissant (`LazyColumn`) | — |
| **Ligne d'historique** (`HistoryRow`, `OutlinedCard`) | « vs {adversaire} », date, « {n} coup(s) », badge « Victoire » (émeraude) / « Défaite » (rouge) | clic → `Match(id, STATS)` |
| **État vide** (`EmptyHistory`) | icône `sports_tennis`, « Aucun match terminé pour le moment. », « Commencez par créer un nouveau match ! » | si aucun match terminé |
| **ExtendedFAB « Nouveau match »** | icône `sports_tennis` + « Nouveau match » | visible uniquement s'il n'y a **aucun** match en cours (`HomeUiState.canCreateMatch`) → `NewMatch` |

### 5.2 Nouveau match — `ui/newmatch/NewMatchScreen.kt` (`NewMatchScreen` → `NewMatchContent`), `NewMatchViewModel`

| Élément | Contenu affiché | Comportement |
|---|---|---|
| **Top app bar** | « Nouveau match », flèche retour (contentDescription « Retour ») | `navigateUp` |
| **Champ « Nom de l'adversaire »** (`OpponentNameField`, `OutlinedTextField`) | placeholder « Ex : Jean Dupont » | focus + clavier automatiques à la première ouverture, majuscule à chaque mot, action IME « Terminé » = soumettre ; erreur RG-02 sous le champ : « Le nom de l'adversaire est requis. » |
| **Champ « Niveau (classement) »** (`OpponentLevelField`, `ExposedDropdownMenuBox`) | placeholder « Sélectionner un niveau » (libellé toujours au-dessus) ; menu : « Aucun » puis les 29 niveaux | optionnel ; « Aucun » efface le choix |
| **Message d'erreur du formulaire** (`FormError`) | RG-01 : « Un match est déjà en cours. Terminez-le avant d'en créer un nouveau. » ; stockage : « Une erreur est survenue. Réessayez. » | sous les champs, au-dessus du bouton |
| **Bouton « Démarrer »** (`Button`, icône `play_arrow`) | pendant la création : indicateur + « Création... », désactivé | succès → `Match(id, INPUT)` |

Le formulaire (nom, niveau) est conservé dans le `SavedStateHandle`.

### 5.3 Match — `ui/match/MatchScreen.kt` (`MatchScreen` → `MatchContent`), `MatchViewModel`, `MatchUiState`

**Top app bar**

| Élément | Contenu | Comportement |
|---|---|---|
| Bouton retour | icône `arrow_back`, « Retour » | `navigateUp` |
| Titre (`MatchTitle`) | « vs {adversaire} » ; sous-titre : libellé du niveau (si renseigné) + `MatchStatusBadge` (« En cours » / « Victoire » / « Défaite ») | — |
| Bouton **« Terminer »** (`TextButton`) | « Terminer » | visible si le match est en cours → ouvre la **feuille de fin de match** |
| Menu **⋮** (`MoreMenu`, contentDescription « Plus d'options ») | item « Supprimer le match » (icône `delete`) | visible si le match est terminé → **dialogue de suppression** |

**Onglets** (`PrimaryTabRow`) : « Saisie » / « Statistiques ». Onglet initial = `initialTab` de la route ; après « Victoire »/« Défaite » → « Statistiques ». Onglet courant sauvegardé (`SavedStateHandle`).

**Onglet « Saisie »** (`InputTab`) : fil des derniers coups (zone défilante) + barre de saisie fixée en bas (au-dessus de la barre de navigation système), **uniquement si le match est en cours**. Écran maintenu allumé (`KeepScreenOn`) tant que le match est en cours.

#### ShotFeed — `ui/match/feed/ShotFeed.kt`

| Élément | Contenu | Comportement |
|---|---|---|
| Libellé | « Derniers coups ({N} total) » (`feed_recent_shots`) | — |
| Liste (`Surface` arrondie 12 dp, lignes séparées par un trait) | les **5** derniers coups (`MatchUiState.recentShots`, `MatchViewModel.RECENT_SHOT_COUNT`), le plus récent en haut | — |
| Ligne de coup (`ShotRow`, 48 dp ; tag `last_shot` pour le plus récent, `shot_row` sinon) | numéro du coup dans le match (N, N−1…), point coloré, « {Type} → {Résultat} » ; service : « Service (1er) → … » / « Service (2e) → … » ; 2e service `fault`/`net` : « Double faute » (rouge foncé = `colorScheme.error`) | la plus récente est teintée selon le résultat et en gras |
| Bouton **✕** de la ligne (`IconButton`, icône `close`, tag `delete_shot_{n}`, contentDescription « Supprimer le coup {n} ({texte}) ») | — | affiché si le match est en cours, désactivé si `!canDeleteShots` → supprime ce coup sans confirmation (RG-06), bandeau « Annulé : … », re-dérivation du n° de service (le mode actif est conservé) |
| État vide (`EmptyFeed`) | « Aucun coup enregistré. Commencez à saisir ! » | cadre en pointillés |

#### ShotTracker (barre de saisie) — `ui/match/input/`

Disposition, de haut en bas : **bandeau de feedback + « Annuler »** → **sélecteur de service** → **colonnes**.

| Composant (fichier) | Élément | Libellé / contenu | Comportement |
|---|---|---|---|
| `ShotTracker` (`ShotTracker.kt`, tag `shot_tracker`) | conteneur | `Surface` arrondie en haut, ombre | désactivée et à 50 % d'opacité pendant l'écriture d'un coup ; indicateur de progression en haut à gauche ; taps ignorés pendant l'écriture ou la suppression d'un coup (anti double-tap) |
| | bouton **« Annuler »** (`UndoButton`, `FilledTonalButton` `errorContainer`, icône `undo`, tag `undo_last_shot`, contentDescription « Annuler le dernier coup ») | « Annuler » (`tracker_undo`) | à droite du bandeau, toujours affiché ; désactivé si 0 coup, pendant l'écriture ou la suppression d'un coup → RG-06 (`MatchViewModel.onUndo`), bandeau « Annulé : … », re-dérivation du n° de service |
| `FeedbackBanner` (`FeedbackBanner.kt`, tag `feedback_banner`) | bandeau animé (bande réservée de 48 dp partagée avec « Annuler », ne masque aucun bouton ; indicateur d'enregistrement à gauche) | « Coup droit → Winner », « Volée → Filet », « 1er service → Ace », « 2e service → En jeu », « 1er service → Out », « Double faute » ; après une suppression (`ShotFeedback.isUndone`) : « Annulé : {coup} » (couleurs `inverseSurface`, icône `undo`) | visible **2 s** (`MatchViewModel.FEEDBACK_DURATION_MILLIS`), remplacé à chaque coup ; couleur du résultat ; `liveRegion` ; vibration à chaque coup via l'API `Vibrator` (`ShotVibrator` : `EFFECT_HEAVY_CLICK`, impulsion de 40 ms avant API 29 ; permission `VIBRATE`), indépendante du réglage système « vibration au toucher » |
| `ServeSelector` (`ServeSelector.kt`) | bouton **« 1er service »** (tag `serve_1`, pastille « 1 ») | actif : violet | tap : si (`SERVE`, 1) → désactive le mode service ; sinon → `SERVE`, n° 1 |
| | bouton **« 2e service »** (tag `serve_2`, pastille « 2 ») | actif : ambre | tap : si (`SERVE`, 2) → désactive ; sinon → `SERVE`, n° 2 |
| | contour du sélecteur | prend la couleur active | `semantics { selected }` sur chaque bouton |
| `GroundstrokeColumns` (`GroundstrokeColumns.kt`) | **colonne gauche** (collée au bord gauche, coins arrondis côté centre) | en-tête (tag `header_left`) « Revers » (cyan, icône `south_east`) ou « {modificateur} » | grisée et désactivée en mode service |
| | **colonne droite** (collée au bord droit) | en-tête (tag `header_right`) « Coup droit » (bleu, `north_east`), « 1er service » (violet) / « 2e service » (ambre) avec icône `bolt`, ou « {modificateur} » | — |
| | **boutons de résultat** (64 dp, icône + libellé gras ; tags `outcome_left_{value}` / `outcome_right_{value}`) | « En jeu » (vert, `check_circle`, pleine largeur), « Winner » (émeraude, `star`) — remplacé par « Ace » (émeraude, `bolt`) en colonne droite en mode service —, « Out » (orange, `cancel`), « Filet » (rouge, `fence`) | Winner/Ace, Out, Filet en retrait de 20 dp côté bord extérieur ; tap → enregistre le coup (§6) |
| | **colonne centrale** (~20 %) : modificateurs (tags `modifier_{value}`) | « Volée » (ambre, `back_hand`), « Smash » (rouge, `local_fire_department`), « Lob » (sarcelle, `north`), « Amorti » (orange, `south`) | tap : sélectionne (remplace le mode service ou un autre modificateur) / désélectionne ; sélectionné = couleur + anneau + élévation ; `semantics { selected }` |

#### Fin de match — `ui/match/finish/FinishMatchSheet.kt`

| Composant | Élément | Comportement |
|---|---|---|
| `FinishMatchSheet` (`ModalBottomSheet`) | titre « Terminer le match » (+ indicateur de progression pendant l'opération) | ouverte par « Terminer » |
| | bouton **« Victoire »** (émeraude, `emoji_events`) | RG-04 `win` → feuille fermée, badge « Victoire », onglet « Statistiques » |
| | bouton **« Défaite »** (rouge, `thumb_down`) | RG-04 `loss`, même suite |
| | séparateur, bouton texte **« Annuler et supprimer le match »** (couleur d'erreur, `delete`) | ouvre le dialogue de suppression |
| `DeleteMatchDialog` (`AlertDialog`, icône `delete`) | titre « Supprimer le match ? », texte « Tous les coups saisis seront définitivement supprimés. », actions « Annuler » / **« Supprimer »** (couleur d'erreur) | confirmation → RG-05 (match en cours) ou RG-07 (match terminé) → retour à l'Accueil |

Boutons désactivés pendant l'opération. Échec d'enregistrement d'un coup → Snackbar « Impossible d'enregistrer le coup » ; autres échecs → « Une erreur est survenue. Réessayez. ».

#### Onglet « Statistiques » — `ui/match/stats/MatchStatsContent.kt`

Mise à jour en direct. Aucun coup : « Aucune statistique disponible pour l'instant. ». Sinon, cartes (`OutlinedCard`) avec `SectionHeader` (« — TITRE — », petites capitales) et rangées de 4 `StatTile` (valeur en gras, chiffres tabulaires ; libellé ; sous-ligne). Pourcentage « {pct}% » ou « — » si non calculable.

| Section (affichée si…) | Tuiles : libellé — valeur — sous-ligne — couleur |
|---|---|
| « Service » (≥ 1 service) | « 1er srv » — `pct(S1_in, S1)` — « {S1_in}/{S1} » — vert ; « 2e srv » — `pct(S2_in, S2)` — « {S2_in}/{S2} » — ambre ; « Aces » — nombre — émeraude ; « Dbl. faute » — nombre — rouge |
| « Coup droit » / « Revers » (une carte commune, sous-sections séparées par un trait ; chacune si ≥ 1 coup du type) | « Succès » — `pct(R, T)` — « sur {T} » — vert ; « Winners » — `pct(W, R)` — « sur succès » — émeraude ; « Filet » — `pct(N, T)` — rouge ; « Out » — `pct(O, T)` — orange |
| « Coups spéciaux » (≥ 1 coup spécial) | une tuile par type joué, ordre Volée, Smash, Lob, Amorti : « {Type} » — `pct(R, T)` — « {T} coup(s) » — vert |


### 5.4 Statistiques — `ui/stats/` (`StatsScreen` → `StatsContent`), `StatsViewModel`, `StatsUiState` (spec : `docs/SPEC-STATISTIQUES.md`)

Top app bar : flèche retour (« Retour »), titre « Statistiques ». Onglets (`PrimaryTabRow`, `StatsTab`) : « Match » (`MATCH`, initial) / « Historique » (`HISTORY`) / « Évolution » (`EVOLUTION`). Onglet, période et indicateur sauvegardés (`SavedStateHandle`). Tout est observé en direct.

**Onglet « Match »** (`FocusMatchTab.kt`) — match de référence (`FocusMatch`) = match en cours, sinon dernier match terminé.

| Élément | Contenu | Comportement |
|---|---|---|
| `FocusMatchCard` | « Match en cours » / « Dernier match terminé », « vs {adversaire} » + `MatchStatusBadge`, « Niveau : … », « {date} · {n} coup(s) », bouton texte « Ouvrir le match » | → `Match(id, INPUT)` si en cours, sinon `Match(id, STATS)` |
| sections du match | `matchStatsSections` (mêmes cartes que §5.3) ; aucun coup : « Aucune statistique disponible pour l'instant. » | — |
| `ComparisonCard` | « — COMPARAISON — », « Par rapport à la moyenne de vos {n} autres matchs terminés » (1 : « Par rapport à votre 1 autre match terminé ») ; lignes : indicateur, valeur, « moy. {x} », écart « +5 pts » / « −0,5 » / « = » (vert = mieux, rouge = moins bien ; doubles fautes : plus bas = mieux) | si ≥ 1 indicateur comparable avec les **autres** matchs terminés (période ignorée) |
| État vide | « Aucun match pour le moment. » / « Créez un match pour suivre ses statistiques. » (`StatsEmptyState`, icône `bar_chart`) | — |

**Sélecteur de période** (`PeriodSelector`, `SingleChoiceSegmentedButtonRow`, onglets Historique et Évolution) : « Tous » / « 10 derniers » / « 5 derniers » (`StatsPeriod`), puis « {n} match(s) terminé(s) ». Seuls les matchs **terminés** comptent. Aucun : « Aucun match terminé pour le moment. » / « Les statistiques cumulées apparaîtront à la fin de votre premier match. »

**Onglet « Historique »** (`HistoryTab.kt`)

| Carte | Contenu |
|---|---|
| « — BILAN — » (`SummaryCard`) | « Matchs » ; « Victoires » (émeraude, sous-ligne « {pct}% ») ; « Défaites » (rouge) ; « Coups / match » (sous-ligne « {total} au total ») |
| Service, Coup droit / Revers, Coups spéciaux | `matchStatsSections` sur tous les coups de la période (taux poolés) ; tuiles service « Aces / match » et « DF / match » (moyenne sur les matchs avec services, « 2,3 », sous-ligne « {total} au total ») |
| « — VICTOIRES / DÉFAITES — » (`ResultSplitCard`) | tableau indicateur × « Victoires » / « Défaites » ; si ≥ 1 victoire et ≥ 1 défaite |

**Onglet « Évolution »** (`EvolutionTab.kt`, `TrendChart.kt`)

| Élément | Contenu | Comportement |
|---|---|---|
| `MetricSelector` (`FilterChip` défilants) | « 1er service », « 2e service », « Coup droit », « Revers », « Aces », « Doubles fautes », « Winners » (`StatMetric`) | un seul sélectionné (initial « 1er service ») |
| `TrendChartCard` | titre indicateur + « en % » / « par match » ; encart « {date} · vs {adversaire} : {valeur} » ; `TrendChart` (courbe 2 dp couleur de l'indicateur, points colorés par résultat, moyenne en pointillés, grille 0 / milieu / max, dates premier/dernier) ; `TrendLegend` « Victoire » / « Défaite » / « Moyenne » | tap → point le plus proche sélectionné (défaut : dernier match) ; contentDescription « {indicateur} sur {n} matchs, du … au …, moyenne … » |
| `TrendSummary` | « Moyenne », « Meilleur » (date), « Dernier » (date), « Tendance » (« 3 derniers » ; moyenne des 3 derniers − moyenne des précédents, « — » si < 4 points) | — |
| < 2 points | « Au moins 2 matchs terminés avec cet indicateur sont nécessaires pour afficher une évolution. » | — |
| « Détail par match » (`TrendMatchRow`) | « vs {adversaire} », date, valeur (+ « a/b » pour un taux), badge | plus récent en premier ; tap → `Match(id, STATS)` |

Formatage commun (`StatsFormat.kt`) : `metricLabel`, `metricColor` (1er service violet, 2e service ambre, Coup droit bleu, Revers cyan, Aces émeraude, Doubles fautes rouge, Winners sarcelle), `periodLabel`, `metricValueText`, `aggregateText`, `decimalText`, `deltaText`, `deltaColor`, `completedMatchCountText`.

---

## 6. Machine à états de saisie et statistiques (domaine)

**`domain/input/`** — `ShotInputState(activeMode: ShotType? = null, serveNumber: Int = 1)` ; `InputAction` = `ToggleServe(number)`, `ToggleModifier(type)`, `TapOutcome(column: Column, outcome)` ; `Column` = `LEFT`, `RIGHT` ; `ShotToRecord(type, outcome, isSecondServe)`.

`ShotInputReducer.reduce(state, action): Pair<ShotInputState, ShotToRecord?>` (fonction pure) :

| Mode | Tap résultat | Coup enregistré | État après |
|---|---|---|---|
| `null` | gauche / droite | `backhand` / `forehand` | inchangé |
| modificateur X | l'une ou l'autre colonne | `X` | `activeMode = null` |
| `SERVE`, n° 1 | `in` / `ace` | `serve`, `isSecondServe = false` | normal, n° 1 |
| `SERVE`, n° 1 | `fault` / `net` | `serve`, `isSecondServe = false` | reste en `SERVE`, n° 2 |
| `SERVE`, n° 2 | tout résultat | `serve`, `isSecondServe = true` | normal, n° 1 |

Ignorés (état inchangé) : colonne gauche en mode service, Winner en mode service, Ace hors mode service, `unforced_error`. Le nouvel état n'est appliqué par `MatchViewModel` **qu'après le succès** de l'écriture.

`deriveServeNumber(lastServe)` : 2 si le dernier service est un 1er service `fault`/`net`, sinon 1 ; `lastServeOf(shotsNewestFirst)`. Re-dérivé à l'ouverture de l'écran et après chaque suppression de coup (« Annuler » ou ✕ du fil). `ShotInputState` et l'onglet sont sauvegardés dans le `SavedStateHandle` (prioritaires sur la re-dérivation après une mort de processus).

**`domain/stats/`** — `StatsCalculator.computeStats(shots | tally: ShotTally): MatchStats(totalShots, serve: ServeStats?, groundstrokes: GroundstrokeStats?(forehand, backhand), specialShots: List<SpecialShotStats>?)`. Section `null` = non affichée ; pourcentage `null` = « — ». `percentage(a, b) = round(a × 100 / b)` (demi vers le haut), `null` si `b = 0`.

Multi-matchs (`docs/SPEC-STATISTIQUES.md`) : `ShotTally` (compte par `ShotKey(type, outcome, isSecondServe)`, additionnable, `ShotTally.of(shots)`, `Iterable<ShotTally>.sum()`) ; `MatchTally(match, tally)`. `StatMetric` (`isRate`, `higherIsBetter`) ; `MetricCalculator.valueOf(metric, tally): MetricValue?` (`part`, `total` null pour un volume ; null si la famille de coups n'a pas été saisie), `aggregate` (taux poolé arrondi à l'entier, volume = moyenne par match à 1 décimale), `compare` → `MetricComparison(metric, value, reference, delta)`, `isImprovement`. `StatsPeriod` (`ALL`, `LAST_10`, `LAST_5`, `select`). `HistoryCalculator.computeHistory(matches): HistoryStats?` (bilan, `pooled: MatchStats`, `acesPerMatch`, `doubleFaultsPerMatch`, `resultSplit: List<ResultSplitRow>?`). `TrendCalculator.computeTrend(metric, matchesNewestFirst): Trend(points chronologiques, average, best, last, change)` ; `Trend.canBeDrawn` = ≥ 2 points.

---

## 7. Charte visuelle

- `ui/theme/Theme.kt` : `TennisStatsTheme` (clair/sombre, **pas** de dynamic color), primaire indigo `#2E3A87`, tertiaire = violet de marque `#5B21B6`. `TennisStatsTheme.semanticColors` lit `LocalSemanticColors`.
- `ui/theme/SemanticColors.kt` : `SemanticColor(container, content)` ; `SemanticColors` : `green` (En jeu), `emerald` (Winner, Ace, Victoire), `orange` (Out, Amorti), `red` (Filet, Défaite, Smash), `violet` (1er service), `amber` (2e service, Volée), `cyan` (Revers), `blue` (Coup droit), `teal` (Lob). Conteneurs sombres à 40 % d'opacité.
- `ui/match/ShotPresentation.kt` : correspondances type/résultat → libellé (`shotTypeLabel`, `outcomeLabel`, `serveLabel`), icône (`shotTypeIcon`, `outcomeIcon`), couleur (`shotTypeColor`, `outcomeColor`, `recordedShotColor`) et textes (`shotFeedText` pour le fil, `shotFeedbackText` pour le bandeau).
- Icônes : Material Symbols Outlined convertis en vector drawables `res/drawable/ic_*.xml` (`arrow_back`, `back_hand`, `bar_chart`, `bolt`, `cancel`, `check_circle`, `close`, `delete`, `emoji_events`, `fence`, `local_fire_department`, `monitoring`, `more_vert`, `north`, `north_east`, `play_arrow`, `schedule`, `south`, `south_east`, `sports_tennis`, `star`, `thumb_down`, `undo`).
- Icône d'application : `mipmap-anydpi/ic_launcher(_round).xml` — fond `@color/brand_violet` (`#5B21B6`), premier plan `ic_launcher_foreground` (balle `#D4F53C`, coutures blanches), variante `ic_launcher_monochrome`. Splash : `Theme.TennisStats.Starting` (`res/values/themes.xml`).

---

## 8. Composants et fichiers source

```
app/src/main/java/fr/zaffran/tennisstats/
├── TennisStatsApplication.kt          @HiltAndroidApp
├── MainActivity.kt                    splash, edge-to-edge, TennisStatsNavHost
├── navigation/  Routes.kt (Home, NewMatch, Stats, Match, MatchTab) · TennisStatsNavHost.kt
├── domain/
│   ├── model/   ShotType · ShotOutcome · MatchStatus · MatchResult · OpponentLevel · Match · Shot · MatchSummary
│   ├── input/   ShotInputState.kt (ShotInputState, Column, InputAction, ShotToRecord) · ShotInputReducer.kt (+ deriveServeNumber, lastServeOf)
│   └── stats/   MatchStats.kt (MatchStats, ServeStats, GroundstrokeStats, StrokeStats, SpecialShotStats) · StatsCalculator.kt · ShotTally.kt (ShotKey, ShotTally, MatchTally) · StatMetric.kt (StatMetric, MetricValue, MetricCalculator, MetricComparison) · HistoryStats.kt (StatsPeriod, HistoryStats, ResultSplitRow, HistoryCalculator) · Trend.kt (TrendPoint, Trend, TrendCalculator)
├── data/
│   ├── local/   TennisStatsDatabase · MatchEntity · ShotEntity · MatchWithShotCount · ShotCountRow · MatchDao · ShotDao · Converters · Mappers
│   ├── repository/  MatchRepository · OfflineMatchRepository · MatchError.kt (MatchError, RepositoryResult)
│   ├── util/    Clock.kt (Clock, SystemClock) · IdGenerator.kt (IdGenerator, UuidIdGenerator)
│   └── di/      DatabaseModule · RepositoryModule
└── ui/
    ├── theme/       Color · Theme · Type · SemanticColors
    ├── components/  MatchStatusBadge + PulsingDot (StatusBadge.kt) · StatTile · SectionHeader · StatsCard.kt (StatsCard, StatTileRow, percentText) · Labels.kt (opponentLevelLabel, shotCountText, formatDate) · ThemePreviews
    ├── home/        HomeScreen (HomeContent, ActiveMatchCard, HistoryRow, EmptyHistory) · HomeViewModel · HomeUiState
    ├── newmatch/    NewMatchScreen (NewMatchContent, OpponentNameField, OpponentLevelField) · NewMatchViewModel (NewMatchUiState, NewMatchError, NewMatchEvent)
    └── match/
        ├── MatchScreen.kt (MatchScreen, MatchContent, InputTab, MatchTitle, MoreMenu, KeepScreenOn) · MatchViewModel.kt · MatchUiState.kt (MatchUiState, ShotFeedback, MatchEvent, MatchMessage) · ShotPresentation.kt · ShotVibrator.kt (ShotVibrator, rememberShotVibrator)
        ├── input/   ShotTracker (+ UndoButton) · ServeSelector · GroundstrokeColumns (colonnes + modificateurs) · FeedbackBanner
        ├── feed/    ShotFeed (+ ShotRow)
        ├── finish/  FinishMatchSheet.kt (FinishMatchSheet, DeleteMatchDialog)
        └── stats/   MatchStatsContent (+ matchStatsSections, réutilisé par la page Statistiques)
    └── stats/       StatsScreen (StatsContent, PeriodSelector, StatsEmptyState) · StatsViewModel · StatsUiState (StatsTab, FocusMatch) · FocusMatchTab (FocusMatchCard, ComparisonCard) · HistoryTab (SummaryCard, ResultSplitCard) · EvolutionTab (MetricSelector, TrendChartCard, TrendSummary, TrendMatchRow) · TrendChart (TrendChart, TrendLegend) · StatsFormat
```

Tests :

- `app/src/test/` (JVM) : `ShotInputReducerTest`, `DeriveServeNumberTest`, `StatsCalculatorTest`, `MetricCalculatorTest`, `HistoryAndTrendTest`, `EnumValuesTest`, `HomeViewModelTest`, `NewMatchViewModelTest`, `MatchViewModelTest`, `StatsViewModelTest` ; utilitaires `testing/FakeMatchRepository`, `MainDispatcherRule`, `ShotFactory`.
- `app/src/androidTest/` (instrumentés) : `TennisStatsDaoTest` (dont `observeCompletedShotCounts`), `OfflineMatchRepositoryTest` (Room in-memory), `HomeAndNewMatchContentTest` (dont l'icône « Statistiques »), `MatchScreenTest` (séquence « double faute », parcours manuel jusqu'à « Victoire », suppression d'un coup depuis le fil), `StatsScreenTest` (onglets Match / Historique / Évolution sur Room in-memory).
- Previews `@ThemePreviews` (clair + sombre) : ShotTracker (normal, 1er service, 2e service, modificateur, enregistrement, annulation), ShotFeed, statistiques, page Statistiques (3 onglets), Accueil, Nouveau match, badges.

---

## 9. Glossaire des noms canoniques

| Nom canonique (code) | Libellé affiché / sens |
|---|---|
| Home / `HomeScreen` | écran « Accueil » (top bar « Tennis Stats ») |
| NewMatch / `NewMatchScreen` | écran « Nouveau match » |
| Match / `MatchScreen` | écran du match (« vs {adversaire} ») |
| `MatchTab.INPUT` / `MatchTab.STATS` | onglets « Saisie » / « Statistiques » |
| `ActiveMatchCard` | carte « match en cours » de l'Accueil (« Reprendre le match ») |
| `HistoryRow` | ligne de la section « Derniers matchs » |
| `ShotFeed` | fil des 5 derniers coups (« Derniers coups (N total) »), chacun supprimable |
| `UndoButton` | bouton « Annuler » (dernier coup) de la barre de saisie |
| `ShotTracker` | barre de saisie |
| `ServeSelector` | sélecteur « 1er service » / « 2e service » |
| `GroundstrokeColumns` | colonnes Revers / modificateurs / Coup droit-Service |
| colonne gauche / droite (`Column.LEFT` / `RIGHT`) | Revers / Coup droit (ou Service) |
| modificateur (`ShotType.SPECIAL_SHOTS`) | « Volée », « Smash », « Lob », « Amorti » |
| mode service (`activeMode = SERVE`) | la colonne droite enregistre des services |
| `serveNumber` | 1er (1) ou 2e (2) service |
| `FeedbackBanner` / `ShotFeedback` | bandeau de feedback de 2 s (coup enregistré, ou « Annulé : … » si `isUndone`) |
| `FinishMatchSheet` | feuille « Terminer le match » |
| `DeleteMatchDialog` | dialogue « Supprimer le match ? » |
| `MatchStatsContent` | contenu de l'onglet « Statistiques » de l'écran Match |
| Stats / `StatsScreen` | page « Statistiques » (onglets « Match », « Historique », « Évolution ») |
| match de référence (`FocusMatch`) | match de l'onglet « Match » : en cours, sinon dernier terminé |
| indicateur (`StatMetric`) | 1er service, 2e service, Coup droit, Revers (taux) ; Aces, Doubles fautes, Winners (volumes par match) |
| période (`StatsPeriod`) | « Tous » / « 10 derniers » / « 5 derniers » matchs terminés |
| poolé | taux cumulé `pct(Σ réussis, Σ total)` (et non moyenne des pourcentages) |
| tendance (`Trend.change`) | moyenne des 3 derniers matchs − moyenne des précédents |
| `StatTile` / `SectionHeader` | tuile de statistique / titre de section « — TITRE — » |
| `MatchStatusBadge` | badge « En cours » / « Victoire » / « Défaite » |
| joueur suivi | joueur dont on saisit les coups (non nommé en V1) |
| double faute (`Shot.isDoubleFault`) | 2e service `fault` ou `net` |
| `fault` | « Out » ; `net` = « Filet » ; `in` = « En jeu » |

---

## 10. Commandes Gradle utiles

Prérequis : JDK 17 (`JAVA_HOME`), SDK Android (`local.properties` → `sdk.dir`).

```bash
./gradlew assembleDebug                 # APK debug
./gradlew installDebug                  # installer sur l'appareil connecté
./gradlew testDebugUnitTest             # tests unitaires JVM
./gradlew connectedDebugAndroidTest     # tests instrumentés (appareil/émulateur requis)
./gradlew lint                          # Android Lint (rapport : app/build/reports/lint-results-debug.html)
./gradlew spotlessCheck                 # vérifier le format ktlint
./gradlew spotlessApply                 # corriger le format
./gradlew assembleRelease               # build release minifiée (R8)
./gradlew spotlessCheck lint testDebugUnitTest assembleDebug   # vérification complète = CI
```

- Toute modification du schéma Room : incrémenter `version` dans `TennisStatsDatabase`, ajouter une migration, commiter le nouveau JSON de `app/schemas/`.
- Tests instrumentés sous WSL avec un émulateur Windows : voir le README (installation des APK et `am instrument` via `adb.exe`).
