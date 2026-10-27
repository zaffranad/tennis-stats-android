package fr.zaffran.tennisstats.navigation

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import fr.zaffran.tennisstats.ui.home.HomeScreen
import fr.zaffran.tennisstats.ui.match.MatchScreen
import fr.zaffran.tennisstats.ui.newmatch.NewMatchScreen
import fr.zaffran.tennisstats.ui.stats.StatsScreen

@Composable
fun TennisStatsNavHost(modifier: Modifier = Modifier, navController: NavHostController = rememberNavController()) {
    NavHost(navController = navController, startDestination = Home, modifier = modifier) {
        composable<Home> {
            HomeScreen(
                onNewMatch = { navController.navigate(NewMatch) },
                onOpenStats = { navController.navigate(Stats) },
                onOpenMatch = { matchId, tab -> navController.navigate(Match(matchId, tab)) },
            )
        }
        composable<NewMatch> {
            NewMatchScreen(
                onBack = { navController.navigateUp() },
                // Used after creation and to redirect to an already active match: NewMatch leaves the back stack.
                onOpenMatch = { matchId ->
                    navController.navigate(Match(matchId, MatchTab.INPUT)) {
                        popUpTo<NewMatch> { inclusive = true }
                    }
                },
            )
        }
        composable<Stats> {
            StatsScreen(
                onBack = { navController.navigateUp() },
                onOpenMatch = { matchId, tab -> navController.navigate(Match(matchId, tab)) },
            )
        }
        composable<Match> {
            MatchScreen(
                onBack = { navController.navigateUp() },
                onMatchDeleted = { navController.popBackStack<Home>(inclusive = false) },
            )
        }
    }
}
