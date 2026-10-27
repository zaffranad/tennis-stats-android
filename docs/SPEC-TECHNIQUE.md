# Tennis Stats Android — Spécification technique (V1 Beta)

Principe directeur : suivre les **recommandations officielles Google** (« Guide to app architecture », Now in Android) et la stack **la plus consensuelle de l'écosystème Android moderne**. Pas de bibliothèque exotique.

---

## 1. Stack

| Couche | Choix | Commentaire |
|---|---|---|
| Langage | **Kotlin** (2.x) | 100 % Kotlin, pas de Java. |
| UI | **Jetpack Compose** + **Material 3** | Pas de XML pour les écrans. BOM Compose. |
| Architecture | **MVVM + Unidirectional Data Flow** | UI layer / (domain) / data layer selon le guide Google. |
| Navigation | **Navigation Compose** avec **routes typées** (`@Serializable`) | Single-activity. |
| Injection de dépendances | **Hilt** | `hilt-navigation-compose` pour `hiltViewModel()`. |
| Asynchronisme | **Coroutines + Flow** | `StateFlow` exposé par les ViewModels. |
| Persistance | **Room** (SQLite) | Flows réactifs, schéma exporté. |
| Génération de code | **KSP** | Pour Room et Hilt (pas de kapt). |
| Sérialisation | **kotlinx.serialization** | Routes de navigation. |
| Cycle de vie | `lifecycle-viewmodel-compose`, `lifecycle-runtime-compose` | `collectAsStateWithLifecycle()`. |
| Build | **Gradle Kotlin DSL** + **Version Catalog** (`gradle/libs.versions.toml`) | Gradle wrapper commité. |
| Tests unitaires | **JUnit 4**, **kotlinx-coroutines-test**, **Turbine**, **Truth** | JUnit 4 reste le standard Android (instrumentation). |
| Tests instrumentés | AndroidX Test, **Room in-memory**, **Compose UI Test** | — |
| Qualité | **Android Lint**, **ktlint** (via Spotless ou plugin ktlint Gradle) | — |
| CI | **GitHub Actions** | build + tests unitaires + lint. |

**Versions** : utiliser les **dernières versions stables** au moment de l'initialisation (vérifier sur developer.android.com / Maven Google). Ne pas utiliser d'alpha/beta.

| Paramètre | Valeur |
|---|---|
| `applicationId` / namespace | `fr.zaffran.tennisstats` |
| `minSdk` | 26 (Android 8.0) |
| `targetSdk` / `compileSdk` | dernière API stable |
| JVM toolchain | 17 |
| `versionName` | `0.1.0-beta` |

Dépendances explicitement **exclues** en V1 : réseau (Retrofit/Ktor), Firebase, authentification, DataStore (aucune préférence utilisateur à stocker), bibliothèques de graphiques (le graphique d'évolution est dessiné avec `Canvas`).

---

## 2. Architecture

```
┌──────────────────────────── UI layer ────────────────────────────┐
│  Composables (Screen + composants)  ◄── UiState (StateFlow)       │
│          │ events                          ▲                      │
│          ▼                                 │                      │
│      ViewModel (Hilt) ─────────────────────┘                      │
└──────────┬───────────────────────────────────────────────────────┘
           │ suspend fun / Flow
┌──────────▼────────── Domain layer (Kotlin pur) ──────────────────┐
│  Modèles · ShotInputReducer (machine à états) · StatsCalculator   │
│  deriveServeNumber() · UseCases simples si utiles                 │
└──────────┬───────────────────────────────────────────────────────┘
           │
┌──────────▼──────────── Data layer ───────────────────────────────┐
│  MatchRepository (interface) ─► OfflineMatchRepository            │
│                                    └─► Room : MatchDao, ShotDao   │
└──────────────────────────────────────────────────────────────────┘
```

Règles :

- **Single source of truth** = Room. L'UI observe des `Flow` ; aucune copie mutable de la liste des coups en mémoire.
- Les ViewModels exposent **un seul `StateFlow<XxxUiState>`** (data class immuable) et des fonctions d'événements (`onShot(...)`, `onUndo()`…). Les événements ponctuels (navigation après création, Snackbar, feedback) passent par un `Channel`/`SharedFlow` d'effets, ou par un champ consommable de l'état.
- Le **domaine ne dépend pas d'Android** : `ShotInputReducer` et `StatsCalculator` sont des fonctions pures, testées unitairement.
- Les modèles de domaine sont distincts des entités Room (mappers `toDomain()` / `toEntity()`).
- Le `MatchRepository` est une **interface** : une future implémentation synchronisée (API / Turso) pourra la remplacer sans toucher à l'UI.

### Modularisation

**Module unique `:app`** pour la Beta, avec un découpage en packages qui préfigure une modularisation future (`:core:data`, `:core:domain`, `:feature:*`) :

```
fr.zaffran.tennisstats
├── TennisStatsApplication.kt        (@HiltAndroidApp)
├── MainActivity.kt                  (@AndroidEntryPoint, enableEdgeToEdge, setContent)
├── navigation/
│   ├── Routes.kt                    (@Serializable Home, NewMatch, Match(matchId, initialTab))
│   └── TennisStatsNavHost.kt
├── domain/
│   ├── model/                       (Match, Shot, ShotType, ShotOutcome, MatchStatus, MatchResult, OpponentLevel)
│   ├── input/                       (ShotInputState, ShotInputReducer, deriveServeNumber)
│   └── stats/                       (MatchStats, StatsCalculator, ShotTally, StatMetric/MetricCalculator, HistoryCalculator, TrendCalculator)
├── data/
│   ├── local/                       (TennisStatsDatabase, MatchEntity, ShotEntity, MatchDao, ShotDao, Converters)
│   ├── repository/                  (MatchRepository, OfflineMatchRepository)
│   └── di/                          (DatabaseModule, RepositoryModule)
└── ui/
    ├── theme/                       (Color.kt, Theme.kt, Type.kt, SemanticColors.kt)
    ├── components/                  (StatusBadge, StatTile, SectionHeader…)
    ├── home/                        (HomeScreen, HomeViewModel, HomeUiState)
    ├── newmatch/                    (NewMatchScreen, NewMatchViewModel)
    └── match/
        ├── MatchScreen.kt, MatchViewModel.kt, MatchUiState.kt
        ├── input/                   (ShotTracker, ServeSelector, GroundstrokeColumns, FeedbackBanner)
        ├── feed/                    (ShotFeed)
        ├── finish/                  (FinishMatchSheet)
        └── stats/                   (MatchStatsContent, matchStatsSections)
    └── stats/                       (StatsScreen, StatsViewModel, onglets Match / Historique / Évolution, TrendChart)
```

---

## 3. Domaine

```kotlin
enum class ShotType(val value: String) { SERVE("serve"), FOREHAND("forehand"), BACKHAND("backhand"),
    VOLLEY("volley"), SMASH("smash"), LOB("lob"), DROP_SHOT("drop_shot") }

enum class ShotOutcome(val value: String) { IN("in"), WINNER("winner"), ACE("ace"),
    FAULT("fault"), NET("net"), UNFORCED_ERROR("unforced_error") }

enum class MatchStatus(val value: String) { IN_PROGRESS("in_progress"), COMPLETED("completed") }
enum class MatchResult(val value: String) { WIN("win"), LOSS("loss") }
```

> Les `value` sont **identiques au web** et sont celles stockées en base (compatibilité de synchro future).

### 3.1 Machine à états de saisie

```kotlin
data class ShotInputState(
    val activeMode: ShotType? = null,   // null, SERVE, VOLLEY, SMASH, LOB, DROP_SHOT
    val serveNumber: Int = 1,           // 1 ou 2
)

sealed interface InputAction {
    data class ToggleServe(val number: Int) : InputAction
    data class ToggleModifier(val type: ShotType) : InputAction
    data class TapOutcome(val column: Column, val outcome: ShotOutcome) : InputAction
}
enum class Column { LEFT, RIGHT }

data class ShotToRecord(val type: ShotType, val outcome: ShotOutcome, val isSecondServe: Boolean)

// Fonction pure : (état, action) -> (nouvel état, coup à enregistrer ?)
fun reduce(state: ShotInputState, action: InputAction): Pair<ShotInputState, ShotToRecord?>

// Dérivation depuis la base : 2 si le dernier service est un 1er service fault/net, sinon 1
fun deriveServeNumber(lastServe: Shot?): Int
```

Les règles exactes sont dans `01-SPEC-FONCTIONNELLE.md` §8.3. Points d'attention :
- en mode `SERVE`, un tap sur la colonne gauche est **impossible** (désactivée) ; le reducer doit l'ignorer ;
- en mode `SERVE`, le résultat `WINNER` de la colonne droite est remplacé par `ACE` côté UI ;
- le nouvel état n'est **appliqué qu'après le succès** de l'écriture en base.

### 3.2 StatsCalculator

`fun computeStats(shots: List<Shot>): MatchStats` — fonction pure. Surcharge `computeStats(tally: ShotTally)` : un `ShotTally` compte les coups par (type, résultat, 1er/2e service) ; les tallies de plusieurs matchs s'additionnent pour les statistiques poolées de l'historique (`SPEC-STATISTIQUES.md`). Les sections sont nullables lorsqu'elles ne doivent pas être affichées. Pourcentages en `Int?` (`null` → « — »). Arrondi : `(a * 100.0 / b).roundToInt()`, équivalent au `Math.round` JavaScript pour des valeurs positives. Formules et cas de test : `01-SPEC-FONCTIONNELLE.md` §9.

---

## 4. Données (Room)

```kotlin
@Entity(tableName = "matches", indices = [Index("status"), Index("playedAt")])
data class MatchEntity(
    @PrimaryKey val id: String,            // UUID
    val opponentName: String,
    val opponentLevel: String?,
    val result: String?,                   // "win" | "loss"
    val status: String,                    // "in_progress" | "completed"
    val playedAt: Long,                    // epoch millis
    val createdAt: Long,
    val updatedAt: Long,
)

@Entity(
    tableName = "shots",
    foreignKeys = [ForeignKey(entity = MatchEntity::class, parentColumns = ["id"],
        childColumns = ["matchId"], onDelete = ForeignKey.CASCADE)],
    indices = [Index(value = ["matchId", "createdAt"])],
)
data class ShotEntity(
    @PrimaryKey val id: String,            // UUID
    val matchId: String,
    val type: String,
    val outcome: String,
    val isSecondServe: Boolean,
    val createdAt: Long,
)
```

- Base `tennis_stats.db`, `version = 1`, `exportSchema = true` (dossier `app/schemas/` commité).
- Tri des coups : `ORDER BY createdAt DESC, rowid DESC` (stable même si deux coups partagent la même milliseconde).
- Requêtes clés (DAO) :
  - `observeActiveMatch(): Flow<MatchWithShotCount?>` ;
  - `observeCompletedMatches(): Flow<List<MatchWithShotCount>>` (tri `playedAt DESC`) ;
  - `observeMatch(id): Flow<MatchEntity?>` ;
  - `observeShots(matchId): Flow<List<ShotEntity>>` (alimente le fil, le compteur, les stats et la dérivation du n° de service) ;
  - `observeCompletedShotCounts(): Flow<List<ShotCountRow>>` (comptage `GROUP BY matchId, type, outcome, isSecondServe` des matchs terminés : alimente l'historique et l'évolution sans charger tous les coups) ;
  - `insertShot`, `deleteLastShot(matchId)`, `finishMatch(id, result)`, `deleteMatch(id)`.
- **Règles métier appliquées dans le repository** (et pas seulement dans l'UI) : RG-01 dans une transaction `@Transaction` (vérifier l'absence de match en cours puis insérer), RG-03, RG-04, RG-06 (refus si le match est terminé).
- Le repository renvoie un `Result`/une `sealed class` d'erreurs métier (`MatchAlreadyInProgress`, `OpponentNameRequired`, `MatchNotInProgress`) que les ViewModels traduisent en messages (`strings.xml`).
- Les horodatages passent par une abstraction `Clock`/`TimeProvider` injectée, et les ids par `IdGenerator` (tests déterministes).

---

## 5. UI — directives Android

- **Edge-to-edge** (`enableEdgeToEdge()`), `Scaffold` avec gestion des `WindowInsets` ; la barre de saisie respecte `navigationBars` + `ime`.
- **Écran allumé** : `Modifier.keepScreenOn()` (ou `view.keepScreenOn = true` via un `DisposableEffect`) sur l'écran Match lorsque le match est en cours.
- **Haptique** : `LocalHapticFeedback.current.performHapticFeedback(HapticFeedbackType.Confirm)` (ou `LongPress` en repli sur les anciennes API) après chaque coup enregistré.
- **Orientation** : `android:screenOrientation="portrait"` sur `MainActivity` (V1).
- **Thème** : `MaterialTheme` clair/sombre, **sans dynamic color** ; couleurs sémantiques exposées via un `CompositionLocal` (`LocalSemanticColors`) avec variantes claire et sombre (palette dans la spec fonctionnelle §10).
- **Icônes** : Material Symbols importés en vector drawables (`res/drawable`), plutôt que la dépendance `material-icons-extended`, lourde.
- **Textes** : tous dans `res/values/strings.xml` (français dans `values/`, langue par défaut), avec `plurals` pour « coup/coups ». Aucun texte en dur dans les composables.
- **Accessibilité** : `contentDescription` sur chaque bouton iconographique ; boutons de résultat ≥ 64 dp ; état sélectionné exposé (`Modifier.semantics { selected = … }`) sur le sélecteur de service et les modificateurs ; contrastes AA.
- **Previews** : `@Preview` clair/sombre pour chaque composant principal (ShotTracker dans les modes normal / 1er service / 2e service / modificateur, ShotFeed, tuiles de stats).
- Les états transitoires de saisie (`ShotInputState`) sont stockés dans le `SavedStateHandle` du `MatchViewModel`.
- **Splash screen** : API `core-splashscreen`.
- **Format de date** : `dd/MM/yyyy`, locale `fr-FR`.

---

## 6. Tests — minimum attendu pour la V1

| Cible | Type | Cas |
|---|---|---|
| `ShotInputReducer` | Unitaire | Toutes les lignes des tableaux §8.3 de la spec fonctionnelle (toggle 1er/2e, toggle modificateur, enchaînement 1re faute → 2e → double faute → retour à l'état normal, ace, coup spécial qui réinitialise le mode, colonne gauche ignorée en mode service). |
| `deriveServeNumber` | Unitaire | aucun service ; dernier = 1er `fault` ; dernier = 1er `net` ; dernier = 1er `in` ; dernier = 2e `fault`. |
| `StatsCalculator` | Unitaire | Les 5 exemples du §9.5 + match vide + sections absentes. |
| `MetricCalculator`, `HistoryCalculator`, `TrendCalculator` | Unitaire | Les exemples du §7 de `SPEC-STATISTIQUES.md`. |
| ViewModels | Unitaire (fake repository, Turbine) | création OK / nom vide / match déjà en cours ; enregistrement d'un coup et transition d'état ; « Annuler » et re-dérivation du n° de service ; fin de match. |
| DAO / Repository | Instrumenté (Room in-memory) | cascade de suppression, ordre des coups, `deleteLastShot`, refus d'un 2e match en cours. |
| ShotTracker | Compose UI test | séquence 1er service Out → l'en-tête affiche « 2e service » → Filet → bandeau « Double faute » et retour en mode normal. |

---

## 7. Livrables du dépôt

```
.
├── CLAUDE.md                     # doc vivante de l'app (nomenclature des écrans/composants/boutons)
├── README.md                     # présentation, prérequis, build, tests
├── docs/
│   ├── SPEC-FONCTIONNELLE.md
│   └── SPEC-TECHNIQUE.md
├── .github/workflows/ci.yml      # ./gradlew lint testDebugUnitTest assembleDebug
├── .editorconfig                 # règles ktlint
├── .gitignore                    # standard Android Studio
├── gradle/libs.versions.toml
├── gradle/wrapper/…              # wrapper commité
├── settings.gradle.kts, build.gradle.kts, gradle.properties
└── app/
    ├── build.gradle.kts
    ├── schemas/                  # schémas Room exportés
    └── src/{main,test,androidTest}/…
```
