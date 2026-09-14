package fr.cklla.cartouche

import android.app.Application
import dagger.hilt.android.HiltAndroidApp

/**
 * Point d'entrée de l'injection de dépendances Hilt : cette classe déclenche
 * la génération du graphe de dépendances au démarrage de l'application.
 */
@HiltAndroidApp
class CartoucheApplication : Application()
