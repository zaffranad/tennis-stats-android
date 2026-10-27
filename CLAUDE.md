# CLAUDE.md — Tennis Stats Android

> **Ce fichier est la source de vérité de l'application. Après toute modification du code qui impacte une section (écran, composant, bouton, libellé, route, entité ou champ Room, dépendance), mettre CLAUDE.md à jour dans la même session de travail.**

Références fonctionnelles et techniques (elles font foi en cas de doute) : `docs/SPEC-FONCTIONNELLE.md`, `docs/SPEC-TECHNIQUE.md`. Décisions prises hors spec : section « Décisions » du `README.md`.

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
| RG-06 | « Annuler » le dernier coup, répétable jusqu'à 0, uniquement sur un match en cours. |
| RG-07 | Supprimer un match terminé (confirmation obligatoire). |
| RG-08 | Chaque coup est persisté immédiatement. |
| RG-09 | Statistiques calculées à la volée depuis les coups, en direct pendant le match. |

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

Exclus en V1 : réseau (Retrofit/Ktor), Firebase, authentification, DataStore, bibliothèques de graphiques, `material-icons-extended` (icônes en vector drawables).

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

**DAO**

| DAO | Méthode | Rôle |
|---|---|---|
| `MatchDao` | `observeActiveMatch(): Flow<MatchWithShotCount?>` | match `in_progress` + nombre de coups |
| | `observeCompletedMatches(): Flow<List<MatchWithShotCount>>` | matchs `completed`, `playedAt DESC` |
| | `observeMatch(id): Flow<MatchEntity?>`, `getMatch(id)`, `getActiveMatch()` | lecture |
| | `insert(match)`, `finishMatch(id, result, updatedAt): Int`, `deleteMatch(id): Int` | écriture (`finishMatch` ne touche qu'un match `in_progress`) |
| `ShotDao` | `observeShots(matchId): Flow<List<ShotEntity>>` | alimente fil, compteur, stats, dérivation du n° de service |
| | `getLastShot(matchId)`, `insertShot(shot)`, `deleteLastShot(matchId): Int` | écriture |

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

- `MatchRepository` (interface) → `OfflineMatchRepository` (Room). Méthodes : `observeActiveMatch`, `observeCompletedMatches`, `observeMatch`, `observeShots`, `createMatch(opponentName, opponentLevel)`, `addShot(matchId, ShotToRecord)`, `deleteLastShot(matchId)` (renvoie le coup supprimé ou `null`), `finishMatch(matchId, MatchResult)`, `deleteMatch(matchId)` (RG-05 et RG-07).
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
| Match | `Match(matchId: String, initialTab: MatchTab)` | carte « en cours » (`INPUT`) ou ligne d'historique (`STATS`) | retour ; match supprimé ou introuvable → `popBackStack<Home>` |

`MatchTab` (`@Keep @Serializable enum`) : `INPUT` = onglet « Saisie », `STATS` = onglet « Statistiques ».

Architecture UI : un ViewModel Hilt par écran exposant **un** `StateFlow<XxxUiState>` ; événements ponctuels (navigation, Snackbar) via `Channel` exposé en `Flow`.

---

## 5. Nomenclature des écrans, zones, composants et boutons

Les libellés entre « » sont les textes **exacts** affichés (ressource `strings.xml` entre parenthèses quand utile).

### 5.1 Accueil — `ui/home/HomeScreen.kt` (`HomeScreen` → `HomeContent`), `HomeViewModel`, `HomeUiState`

| Zone / composant | Contenu affiché | Comportement |
|---|---|---|
| **Top app bar** (`TopAppBar`) | « Tennis Stats » (`home_title`) | pas de menu |
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

**Onglet « Saisie »** (`InputTab`) : fil du dernier coup (zone défilante) + barre de saisie fixée en bas (au-dessus de la barre de navigation système), **uniquement si le match est en cours**. Écran maintenu allumé (`KeepScreenOn`) tant que le match est en cours.

#### ShotFeed — `ui/match/feed/ShotFeed.kt`

| Élément | Contenu | Comportement |
|---|---|---|
| Libellé | « Dernier coup ({N} total) » | — |
| Bouton texte **« Annuler »** (icône `undo`, couleur d'erreur) | « Annuler » | visible si match en cours et N > 0, désactivé pendant l'opération → RG-06, puis re-dérivation du n° de service (le mode actif est conservé) |
| Carte du dernier coup (`LastShotCard`, tag `last_shot`) | point coloré + « {Type} → {Résultat} » ; service : « Service (1er) → … » / « Service (2e) → … » ; 2e service `fault`/`net` : « Double faute » (rouge foncé = `colorScheme.error`) | teintée selon le résultat |
| État vide (`EmptyFeed`) | « Aucun coup enregistré. Commencez à saisir ! » | cadre en pointillés |

#### ShotTracker (barre de saisie) — `ui/match/input/`

Disposition, de haut en bas : **bandeau de feedback** → **sélecteur de service** → **colonnes**.

| Composant (fichier) | Élément | Libellé / contenu | Comportement |
|---|---|---|---|
| `ShotTracker` (`ShotTracker.kt`, tag `shot_tracker`) | conteneur | `Surface` arrondie en haut, ombre | désactivée et à 50 % d'opacité pendant l'écriture d'un coup ; indicateur de progression en haut à droite ; taps ignorés (anti double-tap) |
| `FeedbackBanner` (`FeedbackBanner.kt`, tag `feedback_banner`) | bandeau animé (bande réservée de 44 dp, ne masque aucun bouton) | « Coup droit → Winner », « Volée → Filet », « 1er service → Ace », « 2e service → En jeu », « 1er service → Out », « Double faute » | visible **2 s** (`MatchViewModel.FEEDBACK_DURATION_MILLIS`), remplacé à chaque coup ; couleur du résultat ; `liveRegion` ; vibration à chaque coup via l'API `Vibrator` (`ShotVibrator` : `EFFECT_HEAVY_CLICK`, impulsion de 40 ms avant API 29 ; permission `VIBRATE`), indépendante du réglage système « vibration au toucher » |
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

`deriveServeNumber(lastServe)` : 2 si le dernier service est un 1er service `fault`/`net`, sinon 1 ; `lastServeOf(shotsNewestFirst)`. Re-dérivé à l'ouverture de l'écran et après « Annuler ». `ShotInputState` et l'onglet sont sauvegardés dans le `SavedStateHandle` (prioritaires sur la re-dérivation après une mort de processus).

**`domain/stats/`** — `StatsCalculator.computeStats(shots): MatchStats(totalShots, serve: ServeStats?, groundstrokes: GroundstrokeStats?(forehand, backhand), specialShots: List<SpecialShotStats>?)`. Section `null` = non affichée ; pourcentage `null` = « — ». `percentage(a, b) = round(a × 100 / b)` (demi vers le haut), `null` si `b = 0`.

---

## 7. Charte visuelle

- `ui/theme/Theme.kt` : `TennisStatsTheme` (clair/sombre, **pas** de dynamic color), primaire indigo `#2E3A87`, tertiaire = violet de marque `#5B21B6`. `TennisStatsTheme.semanticColors` lit `LocalSemanticColors`.
- `ui/theme/SemanticColors.kt` : `SemanticColor(container, content)` ; `SemanticColors` : `green` (En jeu), `emerald` (Winner, Ace, Victoire), `orange` (Out, Amorti), `red` (Filet, Défaite, Smash), `violet` (1er service), `amber` (2e service, Volée), `cyan` (Revers), `blue` (Coup droit), `teal` (Lob). Conteneurs sombres à 40 % d'opacité.
- `ui/match/ShotPresentation.kt` : correspondances type/résultat → libellé (`shotTypeLabel`, `outcomeLabel`, `serveLabel`), icône (`shotTypeIcon`, `outcomeIcon`), couleur (`shotTypeColor`, `outcomeColor`, `recordedShotColor`) et textes (`shotFeedText` pour le fil, `shotFeedbackText` pour le bandeau).
- Icônes : Material Symbols Outlined convertis en vector drawables `res/drawable/ic_*.xml` (`arrow_back`, `back_hand`, `bolt`, `cancel`, `check_circle`, `delete`, `emoji_events`, `fence`, `local_fire_department`, `monitoring`, `more_vert`, `north`, `north_east`, `play_arrow`, `schedule`, `south`, `south_east`, `sports_tennis`, `star`, `thumb_down`, `undo`).
- Icône d'application : `mipmap-anydpi/ic_launcher(_round).xml` — fond `@color/brand_violet` (`#5B21B6`), premier plan `ic_launcher_foreground` (balle `#D4F53C`, coutures blanches), variante `ic_launcher_monochrome`. Splash : `Theme.TennisStats.Starting` (`res/values/themes.xml`).

---

## 8. Composants et fichiers source

```
app/src/main/java/fr/zaffran/tennisstats/
├── TennisStatsApplication.kt          @HiltAndroidApp
├── MainActivity.kt                    splash, edge-to-edge, TennisStatsNavHost
├── navigation/  Routes.kt (Home, NewMatch, Match, MatchTab) · TennisStatsNavHost.kt
├── domain/
│   ├── model/   ShotType · ShotOutcome · MatchStatus · MatchResult · OpponentLevel · Match · Shot · MatchSummary
│   ├── input/   ShotInputState.kt (ShotInputState, Column, InputAction, ShotToRecord) · ShotInputReducer.kt (+ deriveServeNumber, lastServeOf)
│   └── stats/   MatchStats.kt (MatchStats, ServeStats, GroundstrokeStats, StrokeStats, SpecialShotStats) · StatsCalculator.kt
├── data/
│   ├── local/   TennisStatsDatabase · MatchEntity · ShotEntity · MatchWithShotCount · MatchDao · ShotDao · Converters · Mappers
│   ├── repository/  MatchRepository · OfflineMatchRepository · MatchError.kt (MatchError, RepositoryResult)
│   ├── util/    Clock.kt (Clock, SystemClock) · IdGenerator.kt (IdGenerator, UuidIdGenerator)
│   └── di/      DatabaseModule · RepositoryModule
└── ui/
    ├── theme/       Color · Theme · Type · SemanticColors
    ├── components/  MatchStatusBadge + PulsingDot (StatusBadge.kt) · StatTile · SectionHeader · Labels.kt (opponentLevelLabel, shotCountText, formatDate) · ThemePreviews
    ├── home/        HomeScreen (HomeContent, ActiveMatchCard, HistoryRow, EmptyHistory) · HomeViewModel · HomeUiState
    ├── newmatch/    NewMatchScreen (NewMatchContent, OpponentNameField, OpponentLevelField) · NewMatchViewModel (NewMatchUiState, NewMatchError, NewMatchEvent)
    └── match/
        ├── MatchScreen.kt (MatchScreen, MatchContent, InputTab, MatchTitle, MoreMenu, KeepScreenOn) · MatchViewModel.kt · MatchUiState.kt (MatchUiState, ShotFeedback, MatchEvent, MatchMessage) · ShotPresentation.kt · ShotVibrator.kt (ShotVibrator, rememberShotVibrator)
        ├── input/   ShotTracker · ServeSelector · GroundstrokeColumns (colonnes + modificateurs) · FeedbackBanner
        ├── feed/    ShotFeed
        ├── finish/  FinishMatchSheet.kt (FinishMatchSheet, DeleteMatchDialog)
        └── stats/   MatchStatsContent
```

Tests :

- `app/src/test/` (JVM) : `ShotInputReducerTest`, `DeriveServeNumberTest`, `StatsCalculatorTest`, `EnumValuesTest`, `HomeViewModelTest`, `NewMatchViewModelTest`, `MatchViewModelTest` ; utilitaires `testing/FakeMatchRepository`, `MainDispatcherRule`, `ShotFactory`.
- `app/src/androidTest/` (instrumentés) : `TennisStatsDaoTest`, `OfflineMatchRepositoryTest` (Room in-memory), `HomeAndNewMatchContentTest`, `MatchScreenTest` (séquence « double faute » et parcours manuel jusqu'à « Victoire »).
- Previews `@ThemePreviews` (clair + sombre) : ShotTracker (normal, 1er service, 2e service, modificateur, enregistrement), ShotFeed, statistiques, Accueil, Nouveau match, badges.

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
| `ShotFeed` | fil du dernier coup (« Dernier coup (N total) ») |
| `ShotTracker` | barre de saisie |
| `ServeSelector` | sélecteur « 1er service » / « 2e service » |
| `GroundstrokeColumns` | colonnes Revers / modificateurs / Coup droit-Service |
| colonne gauche / droite (`Column.LEFT` / `RIGHT`) | Revers / Coup droit (ou Service) |
| modificateur (`ShotType.SPECIAL_SHOTS`) | « Volée », « Smash », « Lob », « Amorti » |
| mode service (`activeMode = SERVE`) | la colonne droite enregistre des services |
| `serveNumber` | 1er (1) ou 2e (2) service |
| `FeedbackBanner` / `ShotFeedback` | bandeau de feedback de 2 s |
| `FinishMatchSheet` | feuille « Terminer le match » |
| `DeleteMatchDialog` | dialogue « Supprimer le match ? » |
| `MatchStatsContent` | contenu de l'onglet « Statistiques » |
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
