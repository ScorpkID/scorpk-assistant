
import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.ksp)
}

/**
 * Secretos leídos del archivo .env en la raíz del proyecto (formato CLAVE=valor).
 * Si una clave no está en .env se busca como variable de entorno del sistema.
 */
val dotEnv: Map<String, String> = rootProject.file(".env").let { file ->
    if (!file.exists()) {
        emptyMap()
    } else {
        file.readLines()
            .map { it.trim() }
            .filter { it.isNotEmpty() && !it.startsWith("#") && it.contains('=') }
            .associate { line ->
                val key = line.substringBefore('=').removePrefix("export ").trim()
                val value = line.substringAfter('=').trim().removeSurrounding("\"").removeSurrounding("'")
                key to value
            }
    }
}

fun env(name: String): String =
    dotEnv[name]?.takeIf { it.isNotBlank() }
        ?: providers.environmentVariable(name).orNull?.trim()
        ?: ""

fun String.asBuildConfigString(): String = "\"" + replace("\\", "\\\\").replace("\"", "\\\"") + "\""

val requiredSecrets = listOf("FIREWORKS_API", "SUPABASE_URL", "SUPABASE_ANON_KEY")
val missingSecrets = requiredSecrets.filter { env(it).isBlank() }
if (missingSecrets.isNotEmpty()) {
    logger.warn(
        "⚠ Scorpk: faltan variables en .env: ${missingSecrets.joinToString()}. " +
            "Los servicios que dependen de ellas (Fireworks AI / Supabase) quedarán deshabilitados en la app."
    )
}

android {
    namespace = "com.scorpk.assistant"
    compileSdk {
        version = release(37)
    }

    defaultConfig {
        applicationId = "com.scorpk.assistant"
        minSdk = 27
        targetSdk = 37
        // Al publicar una versión nueva: subir versionCode (entero) y versionName, y actualizar
        // public/android/latest.json en el proyecto web con los mismos valores.
        versionCode = 4
        versionName = "1.1.2"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"

        // Solo secretos que pueden ir dentro del APK. La clave de Fireworks NO: se define por tipo de build.
        (requiredSecrets - "FIREWORKS_API").forEach { name ->
            buildConfigField("String", name, env(name).asBuildConfigString())
        }
    }

    signingConfigs {
        create("release") {
            // keystore.properties (fuera de git) apunta a la llave de firma. Sin él, el release queda sin firmar.
            val props = Properties().apply {
                val file = rootProject.file("keystore.properties")
                if (file.exists()) file.inputStream().use { load(it) }
            }
            if (props.getProperty("storeFile") != null) {
                storeFile = file(props.getProperty("storeFile"))
                storePassword = props.getProperty("storePassword")
                keyAlias = props.getProperty("keyAlias")
                keyPassword = props.getProperty("keyPassword")
            }
        }
    }

    buildTypes {
        debug {
            // Solo desarrollo: la clave del .env permite llamar a Fireworks directo desde tu equipo.
            buildConfigField("String", "FIREWORKS_API", env("FIREWORKS_API").asBuildConfigString())
        }
        release {
            // Un APK público NUNCA lleva la clave: la IA pasa por el servidor de scorpk.tech.
            buildConfigField("String", "FIREWORKS_API", "\"\"")
            signingConfig = signingConfigs.getByName("release")
            optimization {
                enable = false
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

ksp {
    arg("room.generateKotlin", "true")
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
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.viewmodel.ktx)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.navigation.compose)
    implementation(libs.androidx.room.runtime)
    implementation(libs.androidx.room.ktx)
    ksp(libs.androidx.room.compiler)
    implementation(libs.androidx.datastore.preferences)
    implementation(libs.kotlinx.coroutines.android)
    implementation(libs.kotlinx.serialization.json)
    implementation(libs.androidx.browser)
    implementation(libs.okhttp)
    implementation(libs.vosk.android)
    implementation(platform(libs.supabase.bom))
    implementation(libs.supabase.auth)
    implementation(libs.supabase.postgrest)
    implementation(libs.ktor.client.okhttp)

    testImplementation(libs.junit)
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.compose.ui.test.junit4)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(libs.androidx.junit)
    debugImplementation(libs.androidx.compose.ui.test.manifest)
    debugImplementation(libs.androidx.compose.ui.tooling)
}
