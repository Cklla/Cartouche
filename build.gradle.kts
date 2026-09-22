// Top-level build file where you can add configuration options common to all sub-projects/modules.

buildscript {
    configurations.classpath {
        resolutionStrategy {
            // dependency-check 13 utilise org.apache.commons.lang3.Strings, classe
            // apparue en commons-lang3 3.18.0. L'AGP, lui, amène 3.16.0
            // (sdklib -> repository -> commons-compress). Sans ce force, rien ne
            // garantit à long terme laquelle des deux l'emporte sur le classpath.
            force("org.apache.commons:commons-lang3:3.20.0")
        }
    }
}

plugins {
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.kotlin.compose) apply false
    alias(libs.plugins.ksp) apply false
    alias(libs.plugins.hilt) apply false
    alias(libs.plugins.androidx.room) apply false
    alias(libs.plugins.google.services) apply false
    // Déclaré ici (et pas seulement dans :app) pour une raison de classloader Gradle :
    // un plugin déclaré uniquement dans un sous-projet est chargé dans un classloader
    // enfant, séparé de celui du projet racine où vit l'AGP. Les deux copies de
    // commons-lang3 se retrouvaient alors dans deux classloaders distincts, et le scan
    // échouait sur un IllegalAccessError (Strings ne pouvant plus accéder à
    // CharSequenceUtils, chargée par l'autre classloader).
    // Déclaré ici, tout vit dans un seul classloader et une seule résolution.
    alias(libs.plugins.owasp.dependencycheck) apply false
}
