package fr.cklla.cartouche.ui.login

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import fr.cklla.cartouche.R
import fr.cklla.cartouche.ui.theme.AccentPurple
import fr.cklla.cartouche.ui.theme.AccentPurpleLight
import fr.cklla.cartouche.ui.theme.BackgroundDark
import fr.cklla.cartouche.ui.theme.CartoucheTextStyles
import fr.cklla.cartouche.ui.theme.CartoucheTheme
import fr.cklla.cartouche.ui.theme.ErrorCoral
import fr.cklla.cartouche.ui.theme.TextPrimary
import fr.cklla.cartouche.ui.theme.TextSecondary

/**
 * Écran de connexion : passage obligé au lancement de l'app tant que personne n'est connecté
 * (voir `CartoucheApp`, qui gate tout le `NavHost` derrière `AuthRepository.currentUser`).
 * Aucune maquette ne couvre cet écran (fonctionnalité postérieure au design handoff) : mise en
 * page dérivée par cohérence avec le reste de l'app (mêmes styles de texte, même palette) plutôt
 * qu'inventée de toutes pièces.
 */
@Composable
fun LoginScreen(viewModel: LoginViewModel = hiltViewModel()) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current
    LoginContent(
        uiState = uiState,
        onSignInClick = { viewModel.onSignInClicked(context) },
    )
}

@Composable
private fun LoginContent(uiState: LoginUiState, onSignInClick: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(BackgroundDark)
            .padding(horizontal = 32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Text(
            text = stringResource(R.string.login_kicker),
            style = CartoucheTextStyles.kicker,
            color = AccentPurpleLight,
        )
        Text(
            text = stringResource(R.string.login_title),
            style = CartoucheTextStyles.detailTitle,
            color = TextPrimary,
        )
        Text(
            text = stringResource(R.string.login_subtitle),
            style = CartoucheTextStyles.emptyMessage,
            color = TextSecondary,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(top = 12.dp, bottom = 32.dp),
        )
        if (uiState.isLoading) {
            CircularProgressIndicator(color = AccentPurple)
        } else {
            GoogleSignInButton(onClick = onSignInClick)
        }
        uiState.errorMessage?.let { message ->
            Text(
                text = message,
                style = CartoucheTextStyles.emptyMessage,
                color = ErrorCoral,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(top = 16.dp),
            )
        }
    }
}

@Composable
private fun GoogleSignInButton(onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 48.dp)
            .clip(RoundedCornerShape(24.dp))
            .background(AccentPurple)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = stringResource(R.string.login_google_button),
            style = CartoucheTextStyles.statusPillLabel,
            color = TextPrimary,
        )
    }
}

@Preview(showBackground = true, backgroundColor = 0xFF0A0812)
@Composable
private fun LoginContentPreview() {
    CartoucheTheme {
        LoginContent(uiState = LoginUiState(), onSignInClick = {})
    }
}

@Preview(showBackground = true, backgroundColor = 0xFF0A0812)
@Composable
private fun LoginContentErrorPreview() {
    CartoucheTheme {
        LoginContent(uiState = LoginUiState(errorMessage = "Connexion annulée."), onSignInClick = {})
    }
}
