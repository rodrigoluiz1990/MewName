import java.text.Normalizer
import groovy.json.JsonSlurper
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
val releaseVersionName = releaseTag
    .takeIf { it != "dev" }
    ?.removePrefix("v")
    ?: "1.0.23"
val hasReleaseSigning = releaseStoreFilePath != null &&
    releaseStorePassword != null &&
    releaseKeyAlias != null &&
    releaseKeyPassword != null

val validateCatalogJson by tasks.registering {
    group = "verification"
    description = "Validates every JSON catalog bundled in the app assets."
    val catalogFiles = fileTree("src/main/assets") { include("**/*.json") }
    inputs.files(catalogFiles)
    doLast {
        val errors = catalogFiles.files
            .sortedBy { it.invariantSeparatorsPath }
            .mapNotNull { file ->
                runCatching { JsonSlurper().parseText(file.readText(Charsets.UTF_8).removePrefix("\uFEFF")) }
                    .exceptionOrNull()
                    ?.let { error -> "${file.relativeTo(projectDir)}: ${error.message}" }
            }
        check(errors.isEmpty()) {
            "Invalid JSON catalog(s):\n${errors.joinToString("\n")}"
        }
    }
}

val validateCatalogSemantics by tasks.registering {
    group = "verification"
    description = "Validates required fields, identifiers, types and move references in bundled catalogs."
    dependsOn(validateCatalogJson)
    val assets = file("src/main/assets")
    inputs.dir(assets)
    doLast {
        fun readJson(path: String): Any = JsonSlurper().parseText(file("src/main/assets/$path").readText(Charsets.UTF_8).removePrefix("\uFEFF"))
        fun asEntries(value: Any, path: String): List<Map<*, *>> = (value as? List<*>)
            ?.mapIndexed { index, entry -> entry as? Map<*, *> ?: error("$path[$index] must be an object") }
            ?: error("$path must be an array")
        fun required(entry: Map<*, *>, path: String, field: String): Any =
            entry[field]?.takeIf { it !is String || it.isNotBlank() } ?: error("$path is missing '$field'")
        fun unique(values: List<String>, label: String) {
            val duplicates = values.groupingBy { it }.eachCount().filterValues { it > 1 }.keys
            check(duplicates.isEmpty()) { "$label has duplicate identifiers: ${duplicates.joinToString()}" }
        }

        val validTypes = setOf("Normal", "Fire", "Water", "Grass", "Electric", "Ice", "Fighting", "Poison", "Ground", "Flying", "Psychic", "Bug", "Rock", "Ghost", "Dragon", "Dark", "Steel", "Fairy")
        val names = asEntries(readJson("pokemon/names.json"), "pokemon/names.json")
        unique(names.mapIndexed { index, entry -> "${required(entry, "pokemon/names.json[$index]", "dex")}|${required(entry, "pokemon/names.json[$index]", "name").toString().uppercase()}" }, "pokemon/names.json dex and name")

        names.forEachIndexed { index, entry ->
            check((required(entry, "pokemon/names.json[$index]", "dex") as? Number)?.toInt()?.let { it > 0 } == true) { "pokemon/names.json[$index].dex must be positive" }
        }

        fun validateMoves(path: String): Set<String> {
            val moves = asEntries(readJson(path), path)
            unique(moves.mapIndexed { index, entry -> required(entry, "$path[$index]", "move_id").toString() }, "$path move_id")
            return moves.mapIndexed { index, entry ->
                val name = required(entry, "$path[$index]", "name").toString()
                val type = required(entry, "$path[$index]", "type").toString()
                check(type in validTypes) { "$path[$index].type is invalid: $type" }
                name
            }.toSet()
        }
        val knownMoves = validateMoves("catalogs/fast_moves.json") + validateMoves("catalogs/charged_moves.json")
        // These current moves are intentionally supported before their stat catalog entries are added.
        val knownUncataloguedMoves = setOf("Mystical Fire", "Wildbolt Storm")
        val currentMoves = asEntries(readJson("pokemon/current_moves.json"), "pokemon/current_moves.json")

        currentMoves.forEachIndexed { index, entry ->
            listOf("fastMoves", "chargedMoves").forEach { field ->
                val moves = required(entry, "pokemon/current_moves.json[$index]", field) as? List<*>
                    ?: error("pokemon/current_moves.json[$index].$field must be an array")
                moves.forEachIndexed { moveIndex, rawMove ->
                    val move = rawMove as? Map<*, *> ?: error("pokemon/current_moves.json[$index].$field[$moveIndex] must be an object")
                    val moveName = required(move, "pokemon/current_moves.json[$index].$field[$moveIndex]", "name").toString()
                    check(moveName in knownMoves || moveName in knownUncataloguedMoves) { "pokemon/current_moves.json[$index].$field[$moveIndex] references unknown move: $moveName" }
                }
            }
        }

        val stats = readJson("pokemon/stats.json") as? Map<*, *> ?: error("pokemon/stats.json must be an object")
        stats.forEach { (name, rawStats) ->
            val entry = rawStats as? Map<*, *> ?: error("pokemon/stats.json.$name must be an object")
            listOf("attack", "defense", "stamina").forEach { field ->
                check((required(entry, "pokemon/stats.json.$name", field) as? Number)?.toInt()?.let { it > 0 } == true) { "pokemon/stats.json.$name.$field must be positive" }
            }
        }

        fun normalizeCatalogName(value: String): String = Normalizer
            .normalize(value.trim(), Normalizer.Form.NFD)
            .replace(Regex("\\p{InCombiningDiacriticalMarks}+"), "")
            .uppercase()

        val canonicalNames = buildMap<String, String> {
            names.forEach { entry ->
                val name = entry["name"].toString()
                put(normalizeCatalogName(name), normalizeCatalogName(name))
                (entry["aliases"] as? List<*>)?.forEach { alias ->
                    put(normalizeCatalogName(alias.toString()), normalizeCatalogName(name))
                }
            }
        }
        // New family options can be selected before their stat catalogs are available.
        // Keep this explicit: unknown spellings must still fail validation.
        val pendingFamilyStats = setOf(
            "Arcanine (Hisui)", "Avalugg (Hisui)", "Basculegion", "Braviary (Hisui)",
            "Dialga (Origem)", "Electrode (Hisui)", "Giratina (Origem)", "Goodra (Hisui)",
            "Growlithe (Hisui)", "Hoopa (Unbound)", "Keldeo (Resolute)", "Landorus (Therian)",
            "Lilligant (Hisui)", "Meloetta (Aria)", "Meloetta (Pirouette)",
            "Necrozma (Asas da Alvorada)", "Necrozma (Juba do Crepúsculo)", "Necrozma (Ultra)",
            "Palkia (Origem)", "Qwilfish (Hisui)", "Samurott (Hisui)", "Shaymin (Céu)",
            "Sliggoo (Hisui)", "Sneasel (Hisui)", "Thundurus (Therian)", "Tornadus (Therian)",
            "Typhlosion (Hisui)", "Voltorb (Hisui)", "Zamazenta (Coroado)",
            "Zoroark (Hisui)", "Zorua (Hisui)"
        ).map(::normalizeCatalogName).toSet()
        val families = readJson("pokemon/families.json") as? Map<*, *>
            ?: error("pokemon/families.json must be an object")
        families.forEach { (rawKey, rawMembers) ->
            val key = rawKey as? String ?: error("pokemon/families.json has a non-string key")
            check(key.isNotBlank()) { "pokemon/families.json has a blank family key" }
            val members = when (rawMembers) {
                is String -> listOf(rawMembers)
                is List<*> -> rawMembers.mapIndexed { index, member ->
                    member as? String ?: error("pokemon/families.json.$key[$index] must be a string")
                }
                else -> error("pokemon/families.json.$key must be a string or array")
            }.map { it.trim() }
            check(members.isNotEmpty() && members.all { it.isNotBlank() }) {
                "pokemon/families.json.$key must contain at least one non-blank Pokemon name"
            }
            unique(members.map(::normalizeCatalogName), "pokemon/families.json.$key members")
            members.forEach { member ->
                val normalizedMember = normalizeCatalogName(member)
                val canonicalName = canonicalNames[normalizedMember]
                check(canonicalName != null || normalizedMember in pendingFamilyStats) {
                    "pokemon/families.json.$key references unknown Pokemon: $member"
                }
                check(normalizedMember in pendingFamilyStats || stats.containsKey(canonicalName)) {
                    "pokemon/families.json.$key references Pokemon without stats: $member"
                }
            }
        }
        asEntries(readJson("catalogs/pokemon_types.json"), "catalogs/pokemon_types.json").forEachIndexed { index, entry ->
            required(entry, "catalogs/pokemon_types.json[$index]", "pokemon_name")
            val types = required(entry, "catalogs/pokemon_types.json[$index]", "type") as? List<*>
                ?: error("catalogs/pokemon_types.json[$index].type must be an array")
            check(types.isNotEmpty() && types.all { it in validTypes }) { "catalogs/pokemon_types.json[$index] has invalid type values" }
        }
    }
}

tasks.named("preBuild") {
    dependsOn(validateCatalogSemantics)
}

android {
    namespace = "com.mewname.app"
    compileSdk = 34

    defaultConfig {
        applicationId = "com.mewname.app"
        minSdk = 26
        targetSdk = 34
        versionCode = autoVersionCode
        versionName = releaseVersionName
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
    testOptions {
        unitTests.isIncludeAndroidResources = true
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

    testImplementation("junit:junit:4.13.2")
    testImplementation("androidx.test:core:1.6.1")
    testImplementation("org.robolectric:robolectric:4.13")

    debugImplementation("androidx.compose.ui:ui-tooling")
    debugImplementation("androidx.compose.ui:ui-test-manifest")
}
