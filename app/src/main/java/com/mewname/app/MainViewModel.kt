package com.mewname.app

import android.content.Context
import android.graphics.Bitmap
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.mewname.app.BuildConfig
import com.mewname.app.domain.AppUpdateInfo
import com.mewname.app.domain.AppUpdateRepository
import com.mewname.app.domain.NameGenerator
import com.mewname.app.domain.OcrPokemonParser
import com.mewname.app.domain.PokemonReadSessionMerger
import com.mewname.app.domain.UniquePokemonCatalog
import com.mewname.app.model.NamingBlock
import com.mewname.app.model.NamingBlockType
import com.mewname.app.model.NamingConfig
import com.mewname.app.model.NamingField
import com.mewname.app.model.PokemonSize
import com.mewname.app.model.PokemonScreenData
import com.mewname.app.model.effectiveBlocks
import com.mewname.app.ocr.OcrEngine
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject

enum class AppScreen {
    HOME,
    PRESET_LIST,
    PRESET_EDIT,
    LEGACY_MOVES,
    ADVENTURE_EFFECTS,
    DONATION,
    APP_UPDATE,
    IV_VALIDATION
}

class MainViewModel : ViewModel() {
    private val parser = OcrPokemonParser()
    private val generator = NameGenerator()
    private val ocrEngine = OcrEngine()
    private val sessionMerger = PokemonReadSessionMerger()
    private val appUpdateRepository = AppUpdateRepository()
    private var lastCapturedData: PokemonScreenData? = null

    private val _uiState = MutableStateFlow(UiState())
    val uiState: StateFlow<UiState> = _uiState.asStateFlow()

    fun loadConfigs(context: Context) {
        val prefs = context.getSharedPreferences("mewname_prefs", Context.MODE_PRIVATE)
        val jsonString = prefs.getString("saved_presets", null)
        if (jsonString.isNullOrBlank()) return

        val loadedConfigs = runCatching {
            val jsonArray = JSONArray(jsonString)
            buildList {
                for (i in 0 until jsonArray.length()) {
                    add(jsonToNamingConfig(jsonArray.getJSONObject(i)))
                }
            }
        }.getOrElse { return }

        _uiState.update { state ->
            if (loadedConfigs.isEmpty()) state else state.copy(
                configs = loadedConfigs,
                generatedResults = state.parsedData?.let { generateAll(it, loadedConfigs) } ?: emptyList()
            )
        }
    }

    fun checkForAppUpdate(forceFeedback: Boolean = false) {
        if (_uiState.value.isCheckingForUpdate) return

        viewModelScope.launch {
            _uiState.update {
                it.copy(
                    isCheckingForUpdate = true,
                    appUpdateError = null,
                    appUpdateStatusMessage = if (forceFeedback) null else it.appUpdateStatusMessage
                )
            }

            runCatching {
                withContext(Dispatchers.IO) {
                    appUpdateRepository.fetchLatestRelease(BuildConfig.GITHUB_REPOSITORY)
                }
            }.onSuccess { latest ->
                val currentTag = BuildConfig.RELEASE_TAG.takeUnless { it.isBlank() || it == "dev" }
                val hasUpdate = currentTag == null || !sameReleaseTag(currentTag, latest.tagName)
                _uiState.update {
                    it.copy(
                        isCheckingForUpdate = false,
                        latestAppUpdate = if (hasUpdate) latest else null,
                        appUpdateStatusMessage = when {
                            hasUpdate && forceFeedback -> "Atualização disponível: ${latest.tagName}"
                            !hasUpdate && forceFeedback -> "Seu app já está na versão mais recente."
                            else -> null
                        },
                        appUpdateError = null
                    )
                }
            }.onFailure { error ->
                _uiState.update {
                    it.copy(
                        isCheckingForUpdate = false,
                        appUpdateError = error.message ?: "Falha ao verificar atualização.",
                        appUpdateStatusMessage = null
                    )
                }
            }
        }
    }

    fun clearAppUpdateStatus() {
        _uiState.update { it.copy(appUpdateStatusMessage = null, appUpdateError = null) }
    }

    fun processImage(context: Context, uri: Uri) {
        viewModelScope.launch {
            _uiState.update {
                it.copy(
                    error = null,
                    isProcessing = true,
                    processingStatusMessage = "Extraindo texto da imagem"
                )
            }
            runCatching {
                ocrEngine.extract(context, uri)
            }.onSuccess { ocrResult ->
                val parsed = withContext(Dispatchers.Default) {
                    parser.parse(context, ocrResult) { step ->
                        _uiState.update { state -> state.copy(processingStatusMessage = step) }
                    }
                }
                val merged = sessionMerger.mergeIfSamePokemon(parsed, lastCapturedData)
                _uiState.update { state -> state.copy(processingStatusMessage = "Montando nomes sugeridos") }
                lastCapturedData = merged
                val reviewFields = reviewableFields(_uiState.value.configs)
                _uiState.update {
                    val needsReview = shouldOpenReview(merged, reviewFields)
                    it.copy(
                        rawText = ocrResult.fullText,
                        parsedData = merged,
                        generatedResults = if (needsReview) emptyList() else generateAll(merged, it.configs),
                        pendingReview = if (needsReview) ReviewState(merged, reviewFields, ocrResult.bitmap) else null,
                        isProcessing = false,
                        processingStatusMessage = null
                    )
                }
            }.onFailure { throwable ->
                _uiState.update {
                    it.copy(
                        error = throwable.message ?: "Falha no OCR",
                        isProcessing = false,
                        processingStatusMessage = null
                    )
                }
            }
        }
    }

    fun runDebugIvSampleValidation(context: Context) {
        viewModelScope.launch {
            _uiState.update {
                it.copy(
                    debugIvValidationRunning = true,
                    debugIvValidationError = null,
                    debugIvValidationResults = emptyList()
                )
            }

            runCatching {
                val files = context.assets.list("iv_samples")
                    ?.filter { it.endsWith(".png", true) || it.endsWith(".jpg", true) || it.endsWith(".jpeg", true) }
                    ?.sorted()
                    .orEmpty()

                files.map { fileName ->
                    val expected = parseExpectedIvFromFileName(fileName)
                    val bitmap = ocrEngine.loadBitmapFromAsset(context, "iv_samples/$fileName")
                    val ocrResult = bitmap?.let { ocrEngine.extract(it) }
                    val parsed = ocrResult?.let { parser.parse(context, it) }
                    DebugIvSampleResult(
                        fileName = fileName,
                        expectedAttack = expected?.first,
                        expectedDefense = expected?.second,
                        expectedStamina = expected?.third,
                        detectedAttack = parsed?.attIv,
                        detectedDefense = parsed?.defIv,
                        detectedStamina = parsed?.staIv,
                        detectedPercent = parsed?.ivPercent,
                        attackDebug = parsed?.ivDebugInfo?.attackMeasurementDebug.orEmpty(),
                        defenseDebug = parsed?.ivDebugInfo?.defenseMeasurementDebug.orEmpty(),
                        staminaDebug = parsed?.ivDebugInfo?.staminaMeasurementDebug.orEmpty(),
                        matched = expected != null && parsed != null &&
                            expected.first == parsed.attIv &&
                            expected.second == parsed.defIv &&
                            expected.third == parsed.staIv,
                        notes = buildString {
                            if (bitmap == null) append("bitmap nao carregado")
                            else if (ocrResult == null) append("ocr sem resultado")
                            else if (parsed == null) append("parser sem resultado")
                            else if (parsed.ivDebugInfo?.detectedBars == 0) append("barras detectadas: 0")
                            if (isBlank()) append("ok")
                        }
                    )
                }
            }.onSuccess { results ->
                _uiState.update {
                    it.copy(
                        debugIvValidationRunning = false,
                        debugIvValidationResults = results,
                        debugIvValidationError = null
                    )
                }
            }.onFailure { error ->
                _uiState.update {
                    it.copy(
                        debugIvValidationRunning = false,
                        debugIvValidationError = error.message ?: "Falha ao validar amostras",
                        debugIvValidationResults = emptyList()
                    )
                }
            }
        }
    }

    fun runDebugIvUriValidation(context: Context, uris: List<Uri>) {
        viewModelScope.launch {
            _uiState.update {
                it.copy(
                    debugIvValidationRunning = true,
                    debugIvValidationError = null,
                    debugIvValidationResults = emptyList()
                )
            }

            runCatching {
                uris.map { uri ->
                    val fileName = resolveDisplayName(context, uri)
                    val expected = parseExpectedIvFromFileName(fileName)
                    val ocrResult = ocrEngine.extract(context, uri)
                    val parsed = parser.parse(context, ocrResult)
                    DebugIvSampleResult(
                        fileName = fileName,
                        expectedAttack = expected?.first,
                        expectedDefense = expected?.second,
                        expectedStamina = expected?.third,
                        detectedAttack = parsed.attIv,
                        detectedDefense = parsed.defIv,
                        detectedStamina = parsed.staIv,
                        detectedPercent = parsed.ivPercent,
                        attackDebug = parsed.ivDebugInfo?.attackMeasurementDebug.orEmpty(),
                        defenseDebug = parsed.ivDebugInfo?.defenseMeasurementDebug.orEmpty(),
                        staminaDebug = parsed.ivDebugInfo?.staminaMeasurementDebug.orEmpty(),
                        matched = expected != null &&
                            expected.first == parsed.attIv &&
                            expected.second == parsed.defIv &&
                            expected.third == parsed.staIv,
                        comparable = expected != null,
                        notes = buildString {
                            if (expected == null) append("sem IV esperado no nome do arquivo")
                            if (parsed.ivDebugInfo?.detectedBars == 0) {
                                if (isNotBlank()) append(" | ")
                                append("barras detectadas: 0")
                            }
                            if (isBlank()) append("ok")
                        }
                    )
                }
            }.onSuccess { results ->
                _uiState.update {
                    it.copy(
                        debugIvValidationRunning = false,
                        debugIvValidationResults = results,
                        debugIvValidationError = null
                    )
                }
            }.onFailure { error ->
                _uiState.update {
                    it.copy(
                        debugIvValidationRunning = false,
                        debugIvValidationError = error.message ?: "Falha ao analisar prints",
                        debugIvValidationResults = emptyList()
                    )
                }
            }
        }
    }

    private fun generateAll(data: PokemonScreenData, configs: List<NamingConfig>): List<GeneratedNameResult> {
        return configs.mapNotNull { config ->
            val generatedName = generator.generate(data, config).trim()
            generatedName.takeIf { it.isNotEmpty() }?.let {
                GeneratedNameResult(
                    configId = config.id,
                    configName = config.name,
                    generatedName = it
                )
            }
        }
    }

    fun clearResults() {
        lastCapturedData = null
        _uiState.update { 
            it.copy(
                rawText = null, 
                parsedData = null, 
                generatedResults = emptyList(), 
                error = null,
                pendingReview = null,
                isProcessing = false
            ) 
        }
    }

    fun dismissReview() {
        _uiState.update { it.copy(pendingReview = null, isProcessing = false) }
    }

    fun applyReview(data: PokemonScreenData) {
        lastCapturedData = data
        _uiState.update { state ->
            state.copy(
                parsedData = data,
                generatedResults = generateAll(data, state.configs),
                pendingReview = null,
                isProcessing = false
            )
        }
    }

    fun navigateTo(screen: AppScreen, editingConfigId: String? = null) {
        _uiState.update { it.copy(currentScreen = screen, editingConfigId = editingConfigId) }
    }

    fun setBubbleOptionVisible(visible: Boolean) {
        _uiState.update { it.copy(showBubbleOption = visible) }
    }

    fun addConfig(context: Context, name: String) {
        val newConfig = NamingConfig(name = name)
        _uiState.update { state ->
            val updatedConfigs = state.configs + newConfig
            saveConfigs(context, updatedConfigs)
            state.copy(
                configs = updatedConfigs,
                generatedResults = state.parsedData?.let { generateAll(it, updatedConfigs) } ?: emptyList()
            )
        }
        navigateTo(AppScreen.PRESET_EDIT, newConfig.id)
    }

    fun removeConfig(context: Context, id: String) {
        _uiState.update { state ->
            val updatedConfigs = state.configs.filter { it.id != id }
            saveConfigs(context, updatedConfigs)
            state.copy(
                configs = updatedConfigs,
                generatedResults = state.parsedData?.let { generateAll(it, updatedConfigs) } ?: emptyList()
            )
        }
    }

    fun updateConfig(context: Context, config: NamingConfig) {
        _uiState.update { state ->
            val updatedConfigs = state.configs.map { if (it.id == config.id) config else it }
            saveConfigs(context, updatedConfigs)
            state.copy(
                configs = updatedConfigs,
                generatedResults = state.parsedData?.let { generateAll(it, updatedConfigs) } ?: emptyList()
            )
        }
    }

    private fun saveConfigs(context: Context, configs: List<NamingConfig>) {
        val prefs = context.getSharedPreferences("mewname_prefs", Context.MODE_PRIVATE)
        val jsonArray = JSONArray()
        configs.forEach { config ->
            val obj = JSONObject().apply {
                put("id", config.id)
                put("name", config.name)
                put("maxLength", config.maxLength)
                put("customSeparator", config.customSeparator)
                val blocksArray = JSONArray()
                config.blocks.forEach { block ->
                    blocksArray.put(
                        JSONObject().apply {
                            put("id", block.id)
                            put("type", block.type.name)
                            put("field", block.field?.name)
                            put("fixedText", block.fixedText)
                        }
                    )
                }
                put("blocks", blocksArray)
                val fieldsArray = JSONArray()
                config.fields.forEach { fieldsArray.put(it.name) }
                put("fields", fieldsArray)
                val symbolsObj = JSONObject()
                config.symbols.forEach { (k, v) -> symbolsObj.put(k, v) }
                put("symbols", symbolsObj)
            }
            jsonArray.put(obj)
        }
        prefs.edit().putString("saved_presets", jsonArray.toString()).apply()
    }

    private fun resolveDisplayName(context: Context, uri: Uri): String {
        val resolver = context.contentResolver
        val fromCursor = runCatching {
            resolver.query(uri, arrayOf(android.provider.OpenableColumns.DISPLAY_NAME), null, null, null)
                ?.use { cursor ->
                    if (cursor.moveToFirst()) cursor.getString(0) else null
                }
        }.getOrNull()
        return fromCursor?.takeIf { it.isNotBlank() }
            ?: uri.lastPathSegment?.substringAfterLast('/')
            ?: "imagem"
    }
}

data class UiState(
    val currentScreen: AppScreen = AppScreen.HOME,
    val editingConfigId: String? = null,
    val showBubbleOption: Boolean = true,
    val rawText: String? = null,
    val parsedData: PokemonScreenData? = null,
    val configs: List<NamingConfig> = listOf(NamingConfig(name = "Padrão")),
    val generatedResults: List<GeneratedNameResult> = emptyList(),
    val error: String? = null,
    val pendingReview: ReviewState? = null,
    val isProcessing: Boolean = false,
    val processingStatusMessage: String? = null,
    val debugIvValidationRunning: Boolean = false,
    val debugIvValidationResults: List<DebugIvSampleResult> = emptyList(),
    val debugIvValidationError: String? = null,
    val isCheckingForUpdate: Boolean = false,
    val latestAppUpdate: AppUpdateInfo? = null,
    val appUpdateStatusMessage: String? = null,
    val appUpdateError: String? = null
)

data class GeneratedNameResult(
    val configId: String,
    val configName: String,
    val generatedName: String
)

data class ReviewState(
    val data: PokemonScreenData,
    val fields: List<NamingField>,
    val bitmap: Bitmap? = null
)

data class DebugIvSampleResult(
    val fileName: String,
    val expectedAttack: Int? = null,
    val expectedDefense: Int? = null,
    val expectedStamina: Int? = null,
    val detectedAttack: Int? = null,
    val detectedDefense: Int? = null,
    val detectedStamina: Int? = null,
    val detectedPercent: Int? = null,
    val attackDebug: String = "",
    val defenseDebug: String = "",
    val staminaDebug: String = "",
    val matched: Boolean = false,
    val comparable: Boolean = false,
    val notes: String = ""
)

private fun reviewableFields(configs: List<NamingConfig>): List<NamingField> {
    return configs.flatMap { config ->
        config.effectiveBlocks()
            .filter { block -> block.type == NamingBlockType.VARIABLE }
            .mapNotNull { block -> block.field }
    }.distinct()
}

private fun shouldOpenReview(data: PokemonScreenData, fields: List<NamingField>): Boolean {
    return fields.any { field ->
        when (field) {
            NamingField.POKEMON_NAME -> data.pokemonName.isNullOrBlank()
            NamingField.UNOWN_LETTER -> false
            NamingField.UNIQUE_FORM -> UniquePokemonCatalog.optionsFor(data.pokemonName).isNotEmpty() && data.uniqueForm.isNullOrBlank()
            NamingField.VIVILLON_PATTERN -> isVivillonFamily(data.pokemonName) && data.vivillonPattern == null
            NamingField.CP -> data.cp == null
            NamingField.IV_PERCENT -> data.ivPercent == null
            NamingField.IV_COMBINATION -> data.attIv == null || data.defIv == null || data.staIv == null
            NamingField.LEVEL -> data.level == null
            NamingField.GENDER -> data.gender == com.mewname.app.model.Gender.UNKNOWN
            NamingField.SIZE -> data.size == PokemonSize.NORMAL
            NamingField.MASTER_IV_BADGE -> false
            NamingField.PVP_LEAGUE -> data.pvpLeague == null
            NamingField.PVP_RANK -> data.pvpRank == null
            NamingField.EVOLUTION_TYPE -> data.evolutionFlags.isEmpty()
            else -> false
        }
    }
}

private fun isVivillonFamily(name: String?): Boolean {
    return when (name?.trim()?.uppercase()) {
        "SCATTERBUG", "SPEWPA", "VIVILLON" -> true
        else -> false
    }
}

private fun parseExpectedIvFromFileName(fileName: String): Triple<Int, Int, Int>? {
    val match = Regex("""(\d{1,2})-(\d{1,2})-(\d{1,2})""").find(fileName) ?: return null
    val values = match.groupValues.drop(1).mapNotNull { it.toIntOrNull() }
    if (values.size != 3) return null
    return Triple(
        values[0].coerceIn(0, 15),
        values[1].coerceIn(0, 15),
        values[2].coerceIn(0, 15)
    )
}

private fun sameReleaseTag(currentTag: String, latestTag: String): Boolean {
    fun normalize(tag: String): String {
        return tag.trim().removePrefix("refs/tags/").removePrefix("v").uppercase()
    }
    return normalize(currentTag) == normalize(latestTag)
}

fun jsonToNamingConfig(obj: JSONObject): NamingConfig {
    val blocks = mutableListOf<NamingBlock>()
    if (obj.has("blocks")) {
        val blocksArray = obj.getJSONArray("blocks")
        for (i in 0 until blocksArray.length()) {
            val blockObj = blocksArray.getJSONObject(i)
            blocks += NamingBlock(
                id = blockObj.optString("id").ifBlank { java.util.UUID.randomUUID().toString() },
                type = NamingBlockType.valueOf(blockObj.optString("type", NamingBlockType.VARIABLE.name)),
                field = blockObj.optString("field")
                    .takeIf { it.isNotBlank() && it != "null" }
                    ?.let { NamingField.valueOf(it) },
                fixedText = blockObj.optString("fixedText", "")
            )
        }
    }

    val fields = mutableListOf<NamingField>()
    if (obj.has("fields")) {
        val fieldsArray = obj.getJSONArray("fields")
        for (j in 0 until fieldsArray.length()) {
            fields += NamingField.valueOf(fieldsArray.getString(j))
        }
    }

    val symbols = mutableMapOf<String, String>()
    if (obj.has("symbols")) {
        val symbolsObj = obj.getJSONObject("symbols")
        symbolsObj.keys().forEach { k -> symbols[k] = symbolsObj.getString(k) }
    }

    val defaultSymbols = com.mewname.app.model.defaultSymbols()
    val migratedSymbols = symbols.mapValues { (key, value) ->
        val legacyVivillonValue = legacyVivillonDefaultSymbols[key]
        if (legacyVivillonValue != null && value == legacyVivillonValue) {
            defaultSymbols[key].orEmpty()
        } else {
            value
        }
    }
    val mergedSymbols = defaultSymbols.toMutableMap().apply {
        putAll(migratedSymbols)
    }

    return NamingConfig(
        id = obj.getString("id"),
        name = obj.getString("name"),
        maxLength = obj.getInt("maxLength"),
        customSeparator = obj.optString("customSeparator", ""),
        blocks = blocks,
        fields = fields,
        symbols = mergedSymbols
    )
}

private val legacyVivillonDefaultSymbols = mapOf(
    "VIVILLON_ARCHIPELAGO" to "ARC",
    "VIVILLON_CONTINENTAL" to "CON",
    "VIVILLON_ELEGANT" to "ELE",
    "VIVILLON_GARDEN" to "GAR",
    "VIVILLON_HIGH_PLAINS" to "HPL",
    "VIVILLON_ICY_SNOW" to "ISN",
    "VIVILLON_JUNGLE" to "JUN",
    "VIVILLON_MARINE" to "MAR",
    "VIVILLON_MEADOW" to "MEA",
    "VIVILLON_MODERN" to "MOD",
    "VIVILLON_MONSOON" to "MON",
    "VIVILLON_OCEAN" to "OCE",
    "VIVILLON_POLAR" to "POL",
    "VIVILLON_RIVER" to "RIV",
    "VIVILLON_SANDSTORM" to "SAN",
    "VIVILLON_SAVANNA" to "SAV",
    "VIVILLON_SUN" to "SUN",
    "VIVILLON_TUNDRA" to "TUN"
)
