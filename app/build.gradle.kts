import java.io.FileInputStream
import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.ksp)
    alias(libs.plugins.hilt)
    alias(libs.plugins.androidx.room)
    // Lit google-services.json et génère les ressources/config
    // nécessaires aux SDK Firebase (Auth, Firestore) à la compilation.
    alias(libs.plugins.google.services)
    // Scan de sécurité
    alias(libs.plugins.owasp.dependencycheck)
}

// La clé API RAWG vit uniquement dans local.properties,
// jamais dans le code source. On l'expose au code Kotlin via un champ
// BuildConfig généré à la compilation.
val localProperties = Properties().apply {
    val localPropertiesFile = rootProject.file("local.properties")
    if (localPropertiesFile.exists()) {
        FileInputStream(localPropertiesFile).use { load(it) }
    }
}

// Identifiants du keystore de release : absent en configuration debug, donc chargé de façon optionnelle : un
// simple ./gradlew assembleDebug ne nécessite pas ce fichier.
val keystoreProperties = Properties().apply {
    val keystorePropertiesFile = rootProject.file("keystore.properties")
    if (keystorePropertiesFile.exists()) {
        FileInputStream(keystorePropertiesFile).use { load(it) }
    }
}

dependencyCheck {
    data.directory = "$rootDir/.dependency-check-data"
    formats = listOf("HTML")
    nvd {
        apiKey = System.getenv("NVD_API_KEY")
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
        versionCode = 10
        versionName = "1.1.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"

        buildConfigField(
            "String",
            "RAWG_API_KEY",
            "\"${localProperties.getProperty("RAWG_API_KEY", "")}\"",
        )
        // Identifiants de l'app Twitch (console.twitch.tv/console/apps), utilisés pour le flow
        // OAuth2 client credentials qui authentifie les appels à l'API IGDB (voir
        // `data/remote/igdb/TwitchAuthApi.kt`).
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
        // Défini uniquement si keystore.properties existe: permet à assembleRelease de fonctionner ailleurs (CI, autre
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
            // R8 actif : le code est réduit et obfusqué, et les ressources inutilisées retirées.
            // Ça ne rend pas secret ce qui est embarqué dans l'APK (une constante reste lisible),
            // mais ça complique nettement la rétro-ingénierie et allège le binaire.
            optimization {
                enable = true
            }
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro",
            )
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

    // Notification locale du récap annuel : job périodique (voir RecapNotificationWorker),
    // sans lien avec Firebase Cloud Messaging (condition purement locale, basée sur la date).
    implementation(libs.androidx.work.runtime.ktx)
    implementation(libs.androidx.hilt.work)
    ksp(libs.androidx.hilt.compiler)

    // Réseau : recherche de jeux via l'API RAWG (seul usage de Retrofit/Moshi,
    // le backlog lui-même reste stocké via Room/Firestore).
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
    // App Check : atteste que les appels à Firestore/Auth viennent bien de cette app installée
    // depuis le Play Store, et pas d'un script ou d'une app reconstruite à partir du binaire.
    implementation(libs.firebase.appcheck.playintegrity)
    // Le fournisseur Play Integrity ne peut rien attester sur un émulateur ou un build local :
    // en debug, App Check s'appuie sur un jeton à déclarer dans la console Firebase.
    debugImplementation(libs.firebase.appcheck.debug)

    // Connexion Google (Credential Manager)
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

    constraints {
        // org.owasp.dependencycheck a besoin d'au moins cette version de jackson —
        // d'autres plugins tirent une version plus ancienne
        add("implementation", "com.fasterxml.jackson:jackson-bom:2.21.2")
    }
}