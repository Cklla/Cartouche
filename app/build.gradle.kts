import java.io.FileInputStream
import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.ksp)
    alias(libs.plugins.hilt)
    alias(libs.plugins.androidx.room)
    // Lit google-services.json (non commité, voir .gitignore) et génère les ressources/config
    // nécessaires aux SDK Firebase (Auth, Firestore) à la compilation.
    alias(libs.plugins.google.services)
}

// La clé API RAWG est un secret personnel : elle vit uniquement dans
// local.properties (ignoré par git, voir .gitignore), jamais dans le code
// source. On l'expose au code Kotlin via un champ BuildConfig généré à la
// compilation.
val localProperties = Properties().apply {
    val localPropertiesFile = rootProject.file("local.properties")
    if (localPropertiesFile.exists()) {
        FileInputStream(localPropertiesFile).use { load(it) }
    }
}

// Identifiants du keystore de release : même principe que local.properties, jamais commités
// (voir .gitignore). Absent en configuration debug, donc chargé de façon optionnelle : un
// simple ./gradlew assembleDebug ne nécessite pas ce fichier.
val keystoreProperties = Properties().apply {
    val keystorePropertiesFile = rootProject.file("keystore.properties")
    if (keystorePropertiesFile.exists()) {
        FileInputStream(keystorePropertiesFile).use { load(it) }
    }
}

android {
    namespace = "fr.cklla.cartouche"
    compileSdk {
        version = release(37)
    }

    defaultConfig {
        applicationId = "fr.cklla.cartouche"
        minSdk = 24
        targetSdk = 37
        versionCode = 1
        versionName = "1.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"

        buildConfigField(
            "String",
            "RAWG_API_KEY",
            "\"${localProperties.getProperty("RAWG_API_KEY", "")}\"",
        )
        // Identifiants de l'app Twitch (console.twitch.tv/console/apps), utilisés pour le flow
        // OAuth2 client credentials qui authentifie les appels à l'API IGDB (voir
        // `data/remote/igdb/TwitchAuthApi.kt`). Mêmes garanties que RAWG_API_KEY : jamais en dur
        // dans le code, jamais commités.
        buildConfigField(
            "String",
            "IGDB_CLIENT_ID",
            "\"${localProperties.getProperty("IGDB_CLIENT_ID", "")}\"",
        )
        buildConfigField(
            "String",
            "IGDB_CLIENT_SECRET",
            "\"${localProperties.getProperty("IGDB_CLIENT_SECRET", "")}\"",
        )
    }

    signingConfigs {
        // Défini uniquement si keystore.properties existe (poste du développeur avec le
        // keystore de release) : permet à assembleRelease de fonctionner ailleurs (CI, autre
        // machine) sans configuration de signature, tant qu'on ne publie pas depuis là.
        if (keystoreProperties.isNotEmpty()) {
            create("release") {
                storeFile = rootProject.file(keystoreProperties.getProperty("storeFile"))
                storePassword = keystoreProperties.getProperty("storePassword")
                keyAlias = keystoreProperties.getProperty("keyAlias")
                keyPassword = keystoreProperties.getProperty("keyPassword")
            }
        }
    }

    buildTypes {
        release {
            optimization {
                enable = false
            }
            if (keystoreProperties.isNotEmpty()) {
                signingConfig = signingConfigs.getByName("release")
            }
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
    buildFeatures {
        compose = true
        buildConfig = true
    }
}

// Room génère le schéma de la base dans ce dossier à chaque compilation :
// permet de suivre les migrations de schéma dans le temps (utile dès qu'on
// changera la structure des tables après la v1).
room {
    schemaDirectory("$projectDir/schemas")
}

dependencies {
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.material.icons.extended)
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.viewmodel.ktx)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.kotlinx.coroutines.android)
    // Pont .await() entre les Task Google Play Services (FirebaseAuth.signInWithCredential...)
    // et les coroutines, pour éviter les callbacks imbriqués dans AuthRepositoryImpl.
    implementation(libs.kotlinx.coroutines.play.services)
    implementation(libs.androidx.navigation.compose)

    // Persistance locale (cache offline)
    implementation(libs.androidx.room.runtime)
    implementation(libs.androidx.room.ktx)
    ksp(libs.androidx.room.compiler)

    // Injection de dépendances
    implementation(libs.hilt.android)
    ksp(libs.hilt.android.compiler)
    implementation(libs.androidx.hilt.navigation.compose)

    // Réseau : recherche de jeux via l'API RAWG (seul usage de Retrofit/Moshi,
    // le backlog lui-même reste stocké via Room/Firestore, voir CLAUDE.md)
    implementation(libs.retrofit.core)
    implementation(libs.retrofit.converter.moshi)
    implementation(libs.moshi.kotlin)
    implementation(libs.okhttp.logging.interceptor)

    // Chargement des jaquettes réelles renvoyées par RAWG
    implementation(libs.coil.compose)

    // Firebase : Firestore (source de vérité distante du backlog) + Auth (identifie
    // l'utilisateur, nécessaire aux règles de sécurité Firestore). Le BoM aligne les versions
    // des différents modules Firebase entre eux, pas besoin de préciser de version sur chacun.
    implementation(platform(libs.firebase.bom))
    implementation(libs.firebase.auth)
    implementation(libs.firebase.firestore)

    // Connexion Google (Credential Manager, remplace l'ancien GoogleSignInClient déprécié)
    implementation(libs.androidx.credentials)
    implementation(libs.androidx.credentials.play.services.auth)
    implementation(libs.googleid)

    testImplementation(libs.junit)
    testImplementation(libs.kotlinx.coroutines.test)
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.compose.ui.test.junit4)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(libs.androidx.junit)
    debugImplementation(libs.androidx.compose.ui.test.manifest)
    debugImplementation(libs.androidx.compose.ui.tooling)
}