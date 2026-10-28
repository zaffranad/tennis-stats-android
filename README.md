# Tennis Stats — Android (V1 Beta)

Application Android native qui permet à un observateur (parent, entraîneur) de saisir **en temps réel, en 1 tap**, chaque coup joué par **un** joueur de tennis pendant un match, puis de consulter les statistiques d'exécution du match : % de 1ers et 2es services, aces, doubles fautes, % de réussite, de winners, de fautes au filet et de fautes out par coup. Une page « Statistiques » cumule l'historique des matchs terminés, compare le match courant aux moyennes et montre l'évolution match après match. L'application ne compte pas le score.

C'est le portage de l'application web Tennis Stats (Next.js). Les documents de référence font foi :

- [`docs/SPEC-FONCTIONNELLE.md`](docs/SPEC-FONCTIONNELLE.md) — règles métier, écrans, libellés, machine à états de saisie, formules de stats ;
- [`docs/SPEC-TECHNIQUE.md`](docs/SPEC-TECHNIQUE.md) — stack, architecture, schéma Room, tests attendus ;
- [`docs/SPEC-STATISTIQUES.md`](docs/SPEC-STATISTIQUES.md) — étude et spécification de la page « Statistiques » (match courant, historique, évolution) ;
- [`CLAUDE.md`](CLAUDE.md) — documentation vivante de l'application (nomenclature des écrans, composants et boutons).

100 % hors ligne : pas de compte, pas de réseau, données stockées uniquement dans une base Room locale.

| | |
|---|---|
| `applicationId` | `fr.zaffran.tennisstats` |
| Version | `0.1.0-beta` (versionCode 1) |
| minSdk / targetSdk / compileSdk | 26 / 37 / 37 |
| Stack | Kotlin 2.4, Jetpack Compose + Material 3, Navigation Compose (routes typées), MVVM + UDF, Hilt, Room, Coroutines/Flow, KSP |

## Prérequis

- **JDK 17** (toolchain Gradle 17 ; le daemon peut tourner sur 17 ou plus).
- **Android SDK** avec la plateforme `android-37` et les build-tools 37 (Android Studio les installe ; en ligne de commande : `sdkmanager "platforms;android-37.0" "build-tools;37.0.0" "platform-tools"`).
- Un fichier `local.properties` (non commité) avec `sdk.dir=/chemin/vers/Android/Sdk`, ou la variable `ANDROID_HOME`.

Le wrapper Gradle (9.8.2) est commité : aucune installation de Gradle n'est nécessaire.

## Commandes

```bash
./gradlew assembleDebug              # APK debug : app/build/outputs/apk/debug/app-debug.apk
./gradlew installDebug               # installe sur l'appareil / émulateur connecté
./gradlew testDebugUnitTest          # tests unitaires JVM (domaine, ViewModels)
./gradlew connectedDebugAndroidTest  # tests instrumentés (Room in-memory, Compose UI) — appareil requis
./gradlew lint                       # Android Lint
./gradlew spotlessCheck              # format ktlint (spotlessApply pour corriger)

# Vérification complète (= CI)
./gradlew spotlessCheck lint testDebugUnitTest assembleDebug
```

La CI GitHub Actions ([`.github/workflows/ci.yml`](.github/workflows/ci.yml)) exécute la vérification complète à chaque push sur `main`/`master` et à chaque pull request, et publie l'APK debug en artefact.

### Tests instrumentés sous WSL avec un émulateur Windows

Sous WSL2 sans accès KVM, l'émulateur Linux ne démarre pas et Gradle (Linux) ne voit pas un émulateur Windows (réseau NAT). Les tests peuvent alors être lancés avec l'`adb.exe` Windows :

```bash
./gradlew assembleDebug assembleDebugAndroidTest
ADB=/mnt/c/Users/<user>/AppData/Local/Android/Sdk/platform-tools/adb.exe
$ADB install -r -t "$(wslpath -w app/build/outputs/apk/debug/app-debug.apk)"
$ADB install -r -t "$(wslpath -w app/build/outputs/apk/androidTest/debug/app-debug-androidTest.apk)"
$ADB shell am instrument -w fr.zaffran.tennisstats.test/androidx.test.runner.AndroidJUnitRunner
```

Pour utiliser directement `connectedDebugAndroidTest` : ajouter l'utilisateur au groupe `kvm` (`sudo usermod -aG kvm $USER`) et utiliser un émulateur Linux, ou activer `networkingMode=mirrored` dans `.wslconfig`. En émulateur sans fenêtre, `-gpu swiftshader_indirect` est nécessaire pour obtenir des captures d'écran non vides.

## Parcours manuel de validation

Ce parcours est couvert par le code et automatisé jusqu'à « Victoire » par `MatchScreenTest.manualPath_untilVictory` :

1. **Accueil** → ExtendedFAB **« Nouveau match »** → saisir le nom de l'adversaire (le clavier s'ouvre seul), choisir éventuellement un niveau → **« Démarrer »**.
2. Écran **Match**, onglet **Saisie** : tap **« 1er service »** → **Out** (colonne droite). Le bandeau affiche « 1er service → Out », la barre passe en **2e service** (ambre).
3. **En jeu** (colonne droite) → bandeau « 2e service → En jeu », retour au mode normal.
4. **Winner** (colonne droite, « Coup droit ») → « Coup droit → Winner ».
5. Modificateur **« Volée »** → **Filet** (n'importe quelle colonne) → « Volée → Filet ».
6. **« Annuler »** (en haut à droite de la barre de saisie) → bandeau « Annulé : Volée → Filet », le fil affiche « Derniers coups (3 total) » avec « Coup droit → Winner » en tête.
7. Onglet **Statistiques** : Service 1er srv **0%** (0/1), 2e srv **100%** (1/1), Aces 0, Dbl. faute 0 ; Coup droit Succès **100%** (sur 1), Winners **100%** ; pas de section « Coups spéciaux ».
8. **« Terminer »** (top app bar) → feuille « Terminer le match » → **« Victoire »** : badge « Victoire », onglet Statistiques, plus de barre de saisie.
9. Retour → l'Accueil affiche le match dans **« Derniers matchs »** (« 3 coups », badge Victoire), le FAB « Nouveau match » est de nouveau visible.
9 bis. Icône **« Statistiques »** (top app bar de l'Accueil) → onglet **Match** : « Dernier match terminé », vs {adversaire}, mêmes sections qu'à l'étape 7 (pas de carte « Comparaison » avec un seul match) ; onglet **Historique** : « 1 match terminé », Bilan 1 / 1 victoire ; onglet **Évolution** : « Au moins 2 matchs terminés… » et une ligne dans « Détail par match ». Retour.
10. Tap sur la ligne → match (onglet Statistiques) → menu **⋮** → **« Supprimer le match »** → dialogue « Supprimer le match ? » → **« Supprimer »** → retour à l'Accueil, historique vide.

## Architecture

Module unique `:app`, découpé en packages qui préfigurent une modularisation (`domain`, `data`, `ui`, `navigation`) :

- `domain/` — Kotlin pur, sans dépendance Android : modèles, `ShotInputReducer` (machine à états de saisie), `deriveServeNumber`, `StatsCalculator`, indicateurs multi-matchs (`ShotTally`, `MetricCalculator`, `HistoryCalculator`, `TrendCalculator`).
- `data/` — Room (`TennisStatsDatabase`, entités, DAO), `MatchRepository` / `OfflineMatchRepository` (règles métier RG-01 à RG-07 appliquées en transaction), `Clock`, `IdGenerator`, modules Hilt.
- `ui/` — écrans Compose, un `ViewModel` par écran exposant un `StateFlow<UiState>` ; événements ponctuels (navigation, Snackbar) via `Channel`.
- `navigation/` — routes `@Serializable` (`Home`, `NewMatch`, `Match(matchId, initialTab)`) et `NavHost`.

Les valeurs stockées (`serve`, `forehand`, … / `in`, `winner`, `ace`, `fault`, `net`, `unforced_error` / `in_progress`, `completed` / `win`, `loss`) sont strictement identiques à celles de l'application web.

## Décisions

Choix pris là où les spécifications ne tranchaient pas, ou écarts techniques assumés.

### Build et outillage

- **AGP 9 avec Kotlin intégré** : le plugin `org.jetbrains.kotlin.android` n'est plus appliqué (nouveau standard AGP 9) ; seuls les plugins Compose compiler et kotlinx.serialization sont déclarés.
- **compileSdk / targetSdk 37** (API majeure). L'API 37.2 existe en version mineure ; la version majeure reste la référence la plus courante.
- **ktlint via Spotless**, style `android_studio`, nommage des fonctions `@Composable` en PascalCase autorisé (`.editorconfig`).
- **kotlinx-coroutines-android 1.11.0 déclaré explicitement** : sans cela l'app embarque la 1.9 (transitive) alors que `coroutines-test` 1.11 attend la 1.11, ce qui fait échouer les tests instrumentés (`NoSuchMethodError`).
- **Vérifications d'accessibilité automatiques de Compose (ATF) non intégrées** : `ui-test-junit4-accessibility` apporte Guava, en conflit de version avec `listenablefuture` côté application. L'accessibilité a été vérifiée à la main (voir plus bas).
- Gradle affiche un avertissement « `Configuration.setVisible` deprecated » : il provient d'un plugin tiers, pas du build du projet.

### Domaine

- **Actions de saisie invalides ignorées** par le reducer, état inchangé : colonne gauche en mode service (comme la spec), Winner en mode service, Ace hors mode service, `unforced_error`.
- **Désactiver le mode service** (« 1er service » ou « 2e service » re-tapé) ne modifie pas `serveNumber` (lecture littérale du tableau §8.3).
- **`unforced_error`** (jamais produit par la saisie) compte dans le total d'un coup, mais ni dans les réussis, ni dans Filet, ni dans Out.
- **Restauration de la saisie** : à l'ouverture de l'écran, `serveNumber` est dérivé de la base ; après une mort de processus, l'état sauvegardé (`SavedStateHandle`) est prioritaire (rotation et mort de processus préservent exactement l'état de la barre).

### Données

- Les horodatages des entités sont des `Instant` côté Kotlin, convertis par `Converters` en **epoch millis (INTEGER)** : le schéma SQLite est celui de la spec.
- Le repository renvoie une erreur supplémentaire **`MatchNotFound`** (en plus de `MatchAlreadyInProgress`, `OpponentNameRequired`, `MatchNotInProgress`).
- `deleteLastShot` et `deleteShot` renvoient le coup supprimé, ou `null` s'il n'y en avait pas (ce n'est pas une erreur). `deleteMatch` sert à la fois à **RG-05** (annuler un match en cours) et à **RG-07** (supprimer un match terminé).

### Interface

- **« 0 coups »** : en français, Android range 0 dans la catégorie de pluriel `one`. Un utilitaire (`shotCountText`) force la forme plurielle pour 0, conformément à la règle web (« s » si n ≠ 1).
- **Espaces insécables** avant « : » et « ! » (« Niveau : », « Ex : », « match ! ») : rendu identique, mais pas de ponctuation isolée en début de ligne.
- **Erreur RG-01** affichée sous le formulaire (au-dessus de « Démarrer ») : elle ne concerne aucun champ précis.
- **Message générique** « Une erreur est survenue. Réessayez. » pour les échecs de stockage hors enregistrement d'un coup (création, Annuler, Terminer, Supprimer).
- **ExtendedFAB « Nouveau match »** avec la variante à contenu libre : avec Material 3 1.4, la variante `text`/`icon` masque son libellé aux services d'accessibilité.
- **Champ « Niveau (classement) »** : libellé toujours placé au-dessus du champ, pour que le placeholder « Sélectionner un niveau » soit visible sans focus.
- **Bandeau de feedback** : une bande de 48 dp lui est réservée en haut de la barre de saisie, pour qu'il ne masque jamais un bouton ; l'indicateur de progression d'enregistrement s'y affiche à gauche, le bouton « Annuler » à droite.
- **« Annuler » dans la barre de saisie** plutôt qu'en haut du fil : c'est là que se trouvent le pouce et le regard au moment de l'erreur. Toujours affiché (désactivé à 0 coup) pour garder une position fixe, mémorisable sans regarder.
- **Suppression d'un coup du fil sans confirmation** : comme « Annuler », elle est annoncée par le bandeau « Annulé : … » et une vibration ; une confirmation ralentirait la correction pendant le jeu.
- **Fil des derniers coups** : « Service (1er) → Out » (format §8.2) ; **bandeau** : « 1er service → Out » (format §8.3).
- **Double faute** : couleur `colorScheme.error` (rouge foncé en clair, rose en sombre).
- **Carte « Coup droit » / « Revers »** : pas de titre global ; chaque sous-section a son en-tête « — COUP DROIT — » / « — REVERS — », séparées par un trait.
- **Navigation après suppression** : l'écran Match revient à l'Accueil dès que le match n'existe plus en base (couvre la suppression et un identifiant invalide).
- **Dates** affichées `dd/MM/yyyy` dans le fuseau horaire de l'appareil.
- **Orientation portrait** : respectée sur téléphone. À partir d'Android 16, elle est ignorée sur les grands écrans (≥ 600 dp), hors périmètre de la Beta ; les alertes lint correspondantes sont neutralisées dans le manifeste.
- **`MatchViewModel` à deux constructeurs** : celui injecté par Hilt décode la route avec `toRoute()` (qui a besoin d'Android) ; les tests JVM passent directement la route.

### Statistiques multi-matchs

Les décisions de fond sont dans `docs/SPEC-STATISTIQUES.md` §8. Côté implémentation :

- **Comptage en SQL** (`ShotDao.observeCompletedShotCounts`, `GROUP BY`) puis agrégation dans le domaine via `ShotTally` : les statistiques poolées de l'historique réutilisent exactement `StatsCalculator`.
- **Arrondi avant comparaison** : moyennes et écarts sont arrondis comme affichés (entier pour un taux, 1 décimale pour un volume), pour que « 70% » face à « moy. 60% » donne toujours « +10 pts ».
- **Graphique en `Canvas`** (pas de bibliothèque) : point sélectionné par défaut = dernier match ; la liste « Détail par match » sert d'alternative accessible.
- **Icône d'accès** : `bar_chart` dans la top app bar de l'Accueil, toujours visible (les onglets gèrent leurs états vides).

### Accessibilité

- Chaque bouton iconographique a une `contentDescription` (« Retour », « Plus d'options ») ; les boutons à texte n'ont pas d'icône décrite en double.
- Sélecteur de service et modificateurs : `semantics { selected = … }`. Compose l'expose aux services d'accessibilité comme état « coché » (rôle autre que Tab).
- Cibles tactiles ≥ 48 dp (boutons de résultat 64 dp, modificateurs ≈ 70 dp), vérifiées sur émulateur.
- Bandeau de feedback en `liveRegion` (annoncé par TalkBack) ; titres de section marqués `heading()` ; tuiles de statistiques fusionnées pour une lecture en un bloc.
