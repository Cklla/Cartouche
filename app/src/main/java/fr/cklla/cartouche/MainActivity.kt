package fr.cklla.cartouche

import android.graphics.Color
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import dagger.hilt.android.AndroidEntryPoint
import fr.cklla.cartouche.ui.AppTab
import fr.cklla.cartouche.ui.bibliotheque.BibliothequeScreen
import fr.cklla.cartouche.ui.components.BottomNavBar
import fr.cklla.cartouche.ui.detail.DetailScreen
import fr.cklla.cartouche.ui.login.AuthGateViewModel
import fr.cklla.cartouche.ui.login.LoginScreen
import fr.cklla.cartouche.ui.navigation.CartoucheDestinations
import fr.cklla.cartouche.ui.navigation.route
import fr.cklla.cartouche.ui.recherche.RechercheScreen
import fr.cklla.cartouche.ui.stats.RecapGamesScreen
import fr.cklla.cartouche.ui.stats.RecapScreen
import fr.cklla.cartouche.ui.stats.StatsScreen
import fr.cklla.cartouche.ui.theme.BackgroundDark
import fr.cklla.cartouche.ui.theme.CartoucheTheme

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // L'app n'a qu'un thème sombre définitif : les barres système doivent toujours
        // afficher des icônes claires (visibles sur notre fond quasi-noir), qu'importe le
        // thème clair/sombre réglé sur l'appareil — d'où SystemBarStyle.dark() plutôt que
        // le auto() par défaut, qui suivrait le thème système.
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.dark(Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.dark(Color.TRANSPARENT),
        )
        setContent {
            CartoucheTheme {
                CartoucheApp()
            }
        }
    }
}

// Navigation Compose : les 3 onglets sont des destinations de premier niveau
// (une seule instance de chacune, état conservé via saveState/restoreState),
// Détail est poussé par-dessus depuis la Bibliothèque et n'affiche pas la
// barre de navigation basse (écran "empilé/push", voir maquette).
//
// Connexion Google obligatoire au lancement : tant que personne n'est connecté, on affiche `LoginScreen` à la
// place du `NavHost` — pas une destination de plus dans le graphe de navigation, un vrai "portail"
// en dehors de la pile. Dès que `AuthRepository.currentUser` devient non-null (connexion réussie),
// la recomposition bascule automatiquement sur le NavHost normal, qui démarre toujours sur la
// Bibliothèque.
@Composable
fun CartoucheApp(authGateViewModel: AuthGateViewModel = hiltViewModel()) {
    val currentUser by authGateViewModel.currentUser.collectAsStateWithLifecycle()
    if (currentUser == null) {
        LoginScreen()
        return
    }

    val navController = rememberNavController()
    val currentRoute = navController.currentBackStackEntryAsState().value?.destination?.route
    val selectedTab = AppTab.entries.find { it.route == currentRoute }

    Scaffold(
        containerColor = BackgroundDark,
        bottomBar = {
            // Pas de barre basse sur l'écran Détail : il n'est associé à aucun onglet.
            if (selectedTab != null) {
                BottomNavBar(
                    selectedTab = selectedTab,
                    onTabSelected = { tab ->
                        navController.navigate(tab.route) {
                            popUpTo(CartoucheDestinations.BIBLIOTHEQUE) { saveState = true }
                            launchSingleTop = true
                            restoreState = true
                        }
                    },
                )
            }
        },
    ) { innerPadding ->
        NavHost(
            navController = navController,
            startDestination = CartoucheDestinations.BIBLIOTHEQUE,
            modifier = Modifier.padding(innerPadding),
        ) {
            composable(CartoucheDestinations.BIBLIOTHEQUE) {
                BibliothequeScreen(
                    onGameClick = { gameId -> navController.navigate(CartoucheDestinations.detailRoute(gameId)) },
                )
            }
            composable(CartoucheDestinations.RECHERCHE) {
                RechercheScreen(
                    onResultClick = { result, backlogGameId ->
                        val route = backlogGameId?.let { CartoucheDestinations.detailRoute(it) }
                            ?: CartoucheDestinations.detailApercuRoute(result)
                        navController.navigate(route)
                    },
                )
            }
            composable(CartoucheDestinations.STATS) {
                StatsScreen(
                    onRecapClick = { year -> navController.navigate(CartoucheDestinations.recapRoute(year)) },
                )
            }
            composable(
                route = CartoucheDestinations.RECAP,
                arguments = listOf(navArgument(CartoucheDestinations.RECAP_ARG_YEAR) { type = NavType.IntType }),
            ) {
                RecapScreen(
                    onBackClick = { navController.popBackStack() },
                    onGamesClick = { year, filter ->
                        navController.navigate(CartoucheDestinations.recapGamesRoute(year, filter))
                    },
                )
            }
            composable(
                route = CartoucheDestinations.RECAP_GAMES,
                arguments = listOf(
                    navArgument(CartoucheDestinations.RECAP_ARG_YEAR) { type = NavType.IntType },
                    navArgument(CartoucheDestinations.RECAP_GAMES_ARG_FILTER) { type = NavType.StringType },
                ),
            ) {
                RecapGamesScreen(
                    onBackClick = { navController.popBackStack() },
                    onGameClick = { gameId -> navController.navigate(CartoucheDestinations.detailRoute(gameId)) },
                )
            }
            composable(
                route = CartoucheDestinations.DETAIL,
                arguments = listOf(navArgument(CartoucheDestinations.DETAIL_ARG_GAME_ID) { type = NavType.StringType }),
            ) {
                DetailScreen(onBackClick = { navController.popBackStack() })
            }
            composable(
                route = CartoucheDestinations.DETAIL_APERCU,
                arguments = listOf(
                    navArgument(CartoucheDestinations.DETAIL_APERCU_ARG_RAWG_ID) {
                        type = NavType.LongType
                        defaultValue = -1L
                    },
                    navArgument(CartoucheDestinations.DETAIL_APERCU_ARG_TITLE) {
                        type = NavType.StringType
                        defaultValue = ""
                    },
                    navArgument(CartoucheDestinations.DETAIL_APERCU_ARG_PLATFORM) {
                        type = NavType.StringType
                        defaultValue = ""
                    },
                    navArgument(CartoucheDestinations.DETAIL_APERCU_ARG_GENRE) {
                        type = NavType.StringType
                        defaultValue = ""
                    },
                    navArgument(CartoucheDestinations.DETAIL_APERCU_ARG_YEAR) {
                        type = NavType.StringType
                        defaultValue = ""
                    },
                    navArgument(CartoucheDestinations.DETAIL_APERCU_ARG_COVER_URL) {
                        type = NavType.StringType
                        defaultValue = ""
                    },
                ),
            ) {
                DetailScreen(onBackClick = { navController.popBackStack() })
            }
        }
    }
}
