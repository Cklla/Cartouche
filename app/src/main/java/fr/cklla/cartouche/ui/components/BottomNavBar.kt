package fr.cklla.cartouche.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.MenuBook
import androidx.compose.material.icons.outlined.BarChart
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import fr.cklla.cartouche.R
import fr.cklla.cartouche.ui.AppTab
import fr.cklla.cartouche.ui.theme.AccentPurpleLight
import fr.cklla.cartouche.ui.theme.BackgroundDark
import fr.cklla.cartouche.ui.theme.BorderHairline
import fr.cklla.cartouche.ui.theme.CartoucheTextStyles
import fr.cklla.cartouche.ui.theme.TextMuted

private data class TabSpec(val tab: AppTab, val icon: ImageVector, val labelRes: Int)

private val tabs = listOf(
    TabSpec(AppTab.BIBLIOTHEQUE, Icons.AutoMirrored.Outlined.MenuBook, R.string.bibliotheque_title),
    TabSpec(AppTab.RECHERCHE, Icons.Outlined.Search, R.string.nav_recherche),
    TabSpec(AppTab.STATS, Icons.Outlined.BarChart, R.string.nav_stats),
)

/** Barre de navigation basse fixe à 3 onglets (voir maquette, présente sur tous les écrans). */
@Composable
fun BottomNavBar(selectedTab: AppTab, onTabSelected: (AppTab) -> Unit, modifier: Modifier = Modifier) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .background(BackgroundDark)
            .border(0.5.dp, BorderHairline)
            .padding(vertical = 8.dp),
        horizontalArrangement = Arrangement.SpaceEvenly,
    ) {
        tabs.forEach { spec ->
            val selected = spec.tab == selectedTab
            val tint = if (selected) AccentPurpleLight else TextMuted
            Column(
                modifier = Modifier
                    .heightIn(min = 48.dp)
                    .clickable { onTabSelected(spec.tab) },
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
            ) {
                Icon(imageVector = spec.icon, contentDescription = null, tint = tint)
                Text(text = stringResource(spec.labelRes), style = CartoucheTextStyles.navLabel, color = tint)
            }
        }
    }
}
