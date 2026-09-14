package fr.cklla.cartouche

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import dagger.hilt.android.AndroidEntryPoint
import fr.cklla.cartouche.ui.AppTab
import fr.cklla.cartouche.ui.bibliotheque.BibliothequeScreen
import fr.cklla.cartouche.ui.components.BottomNavBar
import fr.cklla.cartouche.ui.components.PlaceholderScreen
import fr.cklla.cartouche.ui.theme.BackgroundDark
import fr.cklla.cartouche.ui.theme.CartoucheTheme

// @AndroidEntryPoint permet d'injecter des ViewModels Hilt (hiltViewModel())
// depuis les écrans Compose affichés par cette Activity.
@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            CartoucheTheme {
                CartoucheApp()
            }
        }
    }
}

// Navigation par onglets simple (pas de back stack à gérer) : seule la
// Bibliothèque est implémentée pour l'instant, Recherche et Stats arrivent
// dans de prochaines étapes (voir WORK.md).
@Composable
fun CartoucheApp() {
    var selectedTab by rememberSaveable { mutableStateOf(AppTab.BIBLIOTHEQUE) }

    Scaffold(
        containerColor = BackgroundDark,
        bottomBar = {
            BottomNavBar(selectedTab = selectedTab, onTabSelected = { selectedTab = it })
        },
    ) { innerPadding ->
        when (selectedTab) {
            AppTab.BIBLIOTHEQUE -> BibliothequeScreen(modifier = Modifier.padding(innerPadding))
            AppTab.RECHERCHE -> PlaceholderScreen(
                label = stringResource(R.string.placeholder_bientot_disponible),
                modifier = Modifier.padding(innerPadding),
            )
            AppTab.STATS -> PlaceholderScreen(
                label = stringResource(R.string.placeholder_bientot_disponible),
                modifier = Modifier.padding(innerPadding),
            )
        }
    }
}
