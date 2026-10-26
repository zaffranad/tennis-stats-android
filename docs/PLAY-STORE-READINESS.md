# Rapport de préparation Play Store

> Revue du 2026-10-26, version `0.1.0-beta` (commit `1ff631a`), via `/google-playstore-toolkit:review-app`.

**Type de projet :** Android natif (Kotlin, Jetpack Compose, Material 3)
**Agents exécutés :** 6 sur 6 applicables. Aucune bibliothèque de paiement ni de code React Native/Expo n'a été trouvé, les revues correspondantes n'ont donc pas été lancées.
**Évaluation globale :** **Not Ready**, à cause d'1 problème critique. Le code lui-même est sain ; ce qui bloque surtout, ce sont les éléments à fournir pour la publication : politique de confidentialité, signature, visuels.

---

## Problèmes critiques (1), qui entraîneront un refus

- **privacy-compliance-reviewer [90] : il n'y a aucune politique de confidentialité.** Play Console refuse toute publication sans URL de politique, y compris en test interne ou fermé. Rien n'existe dans `docs/`, le README ni `strings.xml`.
  - **Correction :** héberger une page publique en français, par exemple sur GitHub Pages. Elle doit indiquer :
    - les données stockées sur l'appareil : nom et niveau de l'adversaire, coups, dates ;
    - qu'aucune donnée n'est transmise, ni à vous ni à des tiers, et qu'il n'y a ni analytics ni publicité ;
    - comment supprimer les données : supprimer un match ou désinstaller l'application ;
    - la sauvegarde automatique Android (voir plus bas) ;
    - une adresse de contact.
  - Renseigner ensuite l'URL dans Play Console > Contenu de l'application.
  - **Règle Play :** User Data policy, section « Privacy Policy ».

## Problèmes importants (8), risque de refus ou de non-conformité

### Publication

1. **assets-metadata-reviewer [85] : la signature release n'est pas configurée.** Fichier : `app/build.gradle.kts:24-33`. `bundleRelease` produit un AAB non signé, que Play Console refuse.
   - **Correction :** créer une clé d'upload et ajouter `signingConfigs { create("release") }`. Les identifiants viennent de variables d'environnement ou d'un `keystore.properties` exclu de git. Ajouter ensuite `signingConfig` sur le build type `release` et s'inscrire à Play App Signing.
   - **Règle Play :** Play App Signing ; AAB obligatoire pour les nouvelles applications.
2. **assets-metadata-reviewer [75] : les visuels de la fiche Play Store manquent.** Il faut une icône 512×512 en PNG, une bannière 1024×500 et au moins 2 captures d'écran téléphone.
   - **Correction :** exporter l'icône depuis `ic_launcher_foreground.xml`, sur un fond plein `#5B21B6`. Les visuels peuvent vivre hors du dépôt, mais ils sont obligatoires.
   - **Règle Play :** Play Console Help, « Add preview assets to showcase your app ».

### Confidentialité

3. **privacy-compliance-reviewer [75] : aucun lien vers la politique dans l'application.** L'application stocke le nom d'un tiers, qui peut être un mineur.
   - **Correction :** ajouter un menu ⋮ sur la barre du haut de l'Accueil, avec une entrée « Politique de confidentialité ». Elle ouvre l'URL avec `ACTION_VIEW`, ce qui ne demande pas la permission INTERNET.
   - **Règle Play :** User Data policy (politique disponible dans Play Console et dans l'application).
4. **privacy / manifest / security [72] : la sauvegarde est active sans règles déclarées.** Fichier : `app/src/main/AndroidManifest.xml:7`. Les trois agents ont relevé ce point. `tennis_stats.db` part dans la sauvegarde Google Drive et dans le transfert d'un appareil à l'autre, ce qui contredit une promesse « 100 % sur l'appareil ».
   - **Correction :** choisir l'une des deux options et aligner la politique de confidentialité dessus.
     - Garder la sauvegarde : ajouter `res/xml/data_extraction_rules.xml` et `res/xml/backup_rules.xml` (domaine `database`) et le mentionner dans la politique.
     - Exclure la base de la sauvegarde.
   - **Règle Play :** User Data policy (« Deceptive or undisclosed data handling ») ; exactitude du formulaire Data Safety.

### UX, accessibilité et grands écrans

5. **material-design-reviewer [80] : la barre de saisie a une hauteur fixe d'environ 438 dp et ne défile pas.** Fichiers : `ui/match/MatchScreen.kt:223-260`, `ui/match/input/GroundstrokeColumns.kt:49`, `ui/match/input/ServeSelector.kt:82` et `ui/match/input/ShotTracker.kt:62`. En écran partagé, où le verrou portrait ne s'applique pas, les boutons « Out » et « Filet » deviennent inaccessibles.
   - **Correction :** utiliser `BoxWithConstraints` et calculer la hauteur des boutons entre 48 et 64 dp selon l'espace disponible, avec un `verticalScroll` en dernier recours.
   - **Règle Play :** Large screen app quality guidelines (prise en charge du multi-fenêtre).
6. **material-design-reviewer [75] : les boutons de résultat ont des libellés en double pour TalkBack.** Fichier : `ui/match/input/GroundstrokeColumns.kt:138-221`. « En jeu », « Winner », « Out » et « Filet » sont annoncés à l'identique dans les deux colonnes, ce que le rapport de pré-lancement Play signale (`DuplicateSpeakableTextCheck`).
   - **Correction :** construire la `contentDescription` « {colonne} : {résultat} » à partir d'un format dans `strings.xml`, par exemple `<string name="outcome_button_description">%1$s : %2$s</string>`.
   - **Règle Play :** contrôles d'accessibilité du rapport de pré-lancement.
7. **material-design-reviewer [72] : du texte est coupé quand la taille de police est grande.** Des conteneurs de hauteur fixe contiennent un texte en `maxLines = 1` sans ellipse :
   - les boutons de résultat (`GroundstrokeColumns.kt:208-218`) et les modificateurs (`GroundstrokeColumns.kt:276-281`) ;
   - `ServeSelector.kt:82` (52 dp) ;
   - le bandeau de feedback (`ShotTracker.kt:62`, 44 dp) ;
   - `ui/components/StatTile.kt:28-34`.
   - **Correction :** remplacer `height` par `heightIn(min = …)` et ajouter `TextOverflow.Ellipsis` ou l'auto-size. Tester avec une police à 200 % et la taille d'affichage maximale.
   - **Règle Play :** Core app quality guidelines (mise à l'échelle du texte).
8. **material-design-reviewer [70] : le verrou portrait n'est pas accompagné d'une mise en page adaptative.** Fichier : `app/src/main/AndroidManifest.xml:18`. Avec targetSdk 37, Android 16+ ignore ce verrou sur les écrans d'au moins 600 dp, et la mise en page s'étire alors sur toute la largeur.
   - **Correction :** limiter la largeur du contenu avec `widthIn(max = 600.dp)`, centré, et tester sur les émulateurs Pixel Tablet et Pixel Fold. Autre option : restreindre la bêta aux téléphones.
   - **Règle Play :** Large screen app quality guidelines ; changement de comportement d'Android 16 sur l'orientation et le redimensionnement.

## Corrections rapides (moins de 30 min chacune)

- Libellés de boutons qui incluent la colonne — `ui/match/input/GroundstrokeColumns.kt`
- Garde `isRecording` dans `onUndo()` et bouton « Annuler » désactivé pendant l'enregistrement d'un coup. Sans cela, on peut supprimer le mauvais coup — `ui/match/MatchViewModel.kt:123`, `ui/match/feed/ShotFeed.kt:70`
- Fichiers de règles de sauvegarde et attributs correspondants dans le manifeste — `app/src/main/AndroidManifest.xml:7`
- `consumeWindowInsets(innerPadding)` avant `imePadding()` — `ui/newmatch/NewMatchScreen.kt:108`
- Ajout de `assembleRelease` ou `bundleRelease` à la CI pour détecter les régressions R8 — `.github/workflows/ci.yml`
- Configuration de la signature release — `app/build.gradle.kts`

## Points consultatifs

| # | Problème | Emplacement | Effort |
|---|---|---|---|
| 1 | Formulaire Data Safety : déclarer « Aucune donnée collectée ni partagée » | Play Console | Rapide |
| 2 | Public cible : adultes uniquement, pour éviter la politique Familles ; décision à noter dans la section « Décisions » du README | Play Console, README | Rapide |
| 3 | Les stats sont recalculées à chaque émission de l'état UI, bandeau compris ; les calculer seulement quand la liste des coups change | `ui/match/MatchViewModel.kt:65-87` | Rapide |
| 4 | StrictMode en build debug | `TennisStatsApplication.kt` | Rapide |
| 5 | En-tête « Revers » grisé : contraste d'environ 2,2:1 | `ui/match/input/GroundstrokeColumns.kt` | Rapide |
| 6 | Le padding horizontal de l'Accueil ignore les insets | `ui/home/HomeScreen.kt:105-111` | Rapide |
| 7 | Remplacer `Role.Button` + `selected` par `toggleable` ou `selectable` sur le service et les modificateurs | `ServeSelector.kt`, `GroundstrokeColumns.kt` | Rapide |
| 8 | Le point pulsant ignore le réglage « réduire les animations » | `ui/components/StatusBadge.kt` | Rapide |
| 9 | Le `versionName` « 0.1.0-beta » est visible des utilisateurs ; passer par les pistes de test plutôt que par le suffixe | `app/build.gradle.kts:19` | Rapide |
| 10 | `gradle-daemon-jvm.properties` (JDK 25), non suivi par git, contredit le README (JDK 17) : à commiter avec le README mis à jour, ou à supprimer | `gradle/` | Rapide |
| 11 | Prévoir les migrations Room (auto-migrations et `MigrationTestHelper`) avant toute v2 du schéma | `data/di/DatabaseModule.kt` | Modéré |
| 12 | La future synchro cloud imposera de refaire Data Safety et d'ajouter consentement et suppression de compte | — | Important |

## Ce qui est déjà conforme

- **Manifeste :** aucune permission, pas d'INTERNET, seule l'activité de lancement est exportée, pas de deep links. targetSdk 37 dépasse le minimum exigé par Play.
- **Sécurité :** aucun secret dans le code ni dans l'historique git. `local.properties` et les keystores sont ignorés par git. R8 et le shrink des ressources sont activés. Toutes les requêtes Room sont paramétrées. Aucun log, aucune WebView.
- **Confidentialité :** aucun SDK tiers, aucun identifiant d'appareil, pas d'AAID ni d'analytics, pas de compte. On peut supprimer ses données depuis l'application (RG-05, RG-07).
- **Stabilité :** Room n'est appelé qu'en `suspend` ou via `Flow`, sans `GlobalScope` ni `runBlocking`. Les `CancellationException` sont bien relancées. `SavedStateHandle` couvre la mort du processus. Le maintien de l'écran allumé est relâché proprement. Il n'y a aucun travail en arrière-plan.
- **Material et accessibilité :** edge-to-edge, Material 3 complet, cibles tactiles d'au moins 48 dp, `heading` / `liveRegion` / `error` dans les semantics, bon contraste des couleurs sémantiques, retour prédictif géré.
- **Icône :** icône adaptative complète (premier plan, fond, monochrome), dans la zone de sécurité. Le nom « Tennis Stats » ne contient aucun terme interdit.

## Action recommandée

1. **Critique :** publier la politique de confidentialité et renseigner son URL dans Play Console.
2. **Importants, bloquants pour l'upload :** configurer la signature release, produire les visuels de la fiche, ajouter le lien vers la politique dans l'application, trancher sur la sauvegarde.
3. **Importants, qualité :** barre de saisie adaptative, libellés TalkBack, texte coupé, grands écrans. À défaut de traiter les grands écrans, restreindre la bêta aux téléphones.
4. Faire les corrections rapides, en premier la garde de `onUndo()`, qui corrige un vrai bug sur les données.
5. Relancer `/google-playstore-toolkit:review-app`.
