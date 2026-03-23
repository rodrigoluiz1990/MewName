import java.time.ZoneId
import java.time.ZonedDateTime

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

val buildMoment = ZonedDateTime.now(ZoneId.of("America/Sao_Paulo"))
val autoVersionCode = ((buildMoment.year - 2000) * 10_000_000) +
    (buildMoment.dayOfYear * 100_000) +
    (buildMoment.hour * 3_600) +
    (buildMoment.minute * 60) +
    buildMoment.second
val releaseStoreFilePath = System.getenv("MEWNAME_UPLOAD_STORE_FILE")?.takeIf { it.isNotBlank() }
val releaseStorePassword = System.getenv("MEWNAME_UPLOAD_STORE_PASSWORD")?.takeIf { it.isNotBlank() }
val releaseKeyAlias = System.getenv("MEWNAME_UPLOAD_KEY_ALIAS")?.takeIf { it.isNotBlank() }
val releaseKeyPassword = System.getenv("MEWNAME_UPLOAD_KEY_PASSWORD")?.takeIf { it.isNotBlank() }
val releaseTag = System.getenv("MEWNAME_RELEASE_TAG")?.takeIf { it.isNotBlank() } ?: "dev"
val hasReleaseSigning = releaseStoreFilePath != null &&
    releaseStorePassword != null &&
    releaseKeyAlias != null &&
    releaseKeyPassword != null

android {
    namespace = "com.mewname.app"
    compileSdk = 34

    defaultConfig {
        applicationId = "com.mewname.app"
        minSdk = 26
        targetSdk = 34
        versionCode = autoVersionCode
        versionName = "1.0.$autoVersionCode"
        buildConfigField("String", "RELEASE_TAG", "\"$releaseTag\"")
        buildConfigField("String", "GITHUB_REPOSITORY", "\"rodrigoluiz1990/MewName\"")

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        vectorDrawables {
            useSupportLibrary = true
        }
    }

    signingConfigs {
        if (hasReleaseSigning) {
            create("release") {
                storeFile = file(releaseStoreFilePath!!)
                storePassword = releaseStorePassword
                keyAlias = releaseKeyAlias
                keyPassword = releaseKeyPassword
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
            if (hasReleaseSigning) {
                signingConfig = signingConfigs.getByName("release")
            }
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions {
        jvmTarget = "17"
    }
    buildFeatures {
        compose = true
        buildConfig = true
    }
    composeOptions {
        kotlinCompilerExtensionVersion = "1.5.14"
    }
    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
        }
    }
}

dependencies {
    val composeBom = platform("androidx.compose:compose-bom:2024.06.00")

    implementation("androidx.core:core-ktx:1.13.1")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.8.3")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.8.3")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.8.3")
    implementation("androidx.savedstate:savedstate-ktx:1.2.1")
    implementation("androidx.activity:activity-compose:1.9.1")
    implementation(composeBom)
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.compose.material3:material3")

    implementation("com.google.mlkit:text-recognition:16.0.1")
    implementation("com.google.mlkit:language-id:17.0.6")
    implementation("org.apache.commons:commons-text:1.12.0")
    implementation("androidx.palette:palette-ktx:1.0.0")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.8.1")

    debugImplementation("androidx.compose.ui:ui-tooling")
    debugImplementation("androidx.compose.ui:ui-test-manifest")
}
