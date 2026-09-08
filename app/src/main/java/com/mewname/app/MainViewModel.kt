package com.mewname.app

import android.content.Context
import android.graphics.Bitmap
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.mewname.app.BuildConfig
import com.mewname.app.domain.AppLanguage
import com.mewname.app.domain.CatalogCache
import com.mewname.app.domain.CatalogLoadArea
import com.mewname.app.domain.CatalogLoadFeedback
import com.mewname.app.domain.AppUpdateInfo
import com.mewname.app.domain.AppUpdateRepository
import com.mewname.app.domain.GameTextRepository
import com.mewname.app.domain.NameGenerator
import com.mewname.app.domain.OcrPokemonParser
import com.mewname.app.domain.PokemonReadSessionMerger
import com.mewname.app.domain.UniquePokemonCatalog
import com.mewname.app.domain.ReviewPolicy
import com.mewname.app.model.NamingBlock
import com.mewname.app.model.NamingBlockType
import com.mewname.app.model.NamingConfig
import com.mewname.app.model.NamingField
import com.mewname.app.model.PokemonSize
import com.mewname.app.model.PokemonScreenData
import com.mewname.app.model.effectiveBlocks
import com.mewname.app.model.defaultNamingConfigs
import com.mewname.app.model.ensureBuiltInNamingConfigs
import com.mewname.app.ocr.OcrEngine
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import org.json.JSONArray
import org.json.JSONObject

enum class AppScreen {
    HOME,
    COLLECTIONS,
    PRESET_LIST,
    PRESET_EDIT,
    LEGACY_MOVES,
    ADVENTURE_EFFECTS,
    RAID_PLANNER,
    TYPE_CHART,
    MOVEDEX,
    POKEDEX,
    FILTER_BUILDER,
    TEST_MENU,
    HELP_MENU,
    DONATION,
    PRIVACY_POLICY,
    APP_UPDATE,
    IV_VALIDATION
}

class MainViewModel(
    private val reviewOcrPipeline: ReviewOcrPipeline = AndroidReviewOcrPipeline()
) : ViewModel() {
    private val parser = OcrPokemonParser()
    private val generator = NameGenerator()
    private val ocrEngine = OcrEngine()
    private val sessionMerger = PokemonReadSessionMerger()
    private val appUpdateRepository = AppUpdateRepository()
    private var lastCapturedData: PokemonScreenData? = null
    private var imageProcessingJob: Job? = null
    private var ivValidationJob: Job? = null

    private val _uiState = MutableStateFlow(UiState())
    val uiState: StateFlow<UiState> = _uiState.asStateFlow()

    fun loadConfigs(context: Context) {
        val prefs = context.getSharedPreferences("mewname_prefs", Context.MODE_PRIVATE)
        if (_uiState.value.configsLoaded) return
        val savedLanguage = prefs.getString("app_language", null)
            ?.let { runCatching { AppLanguage.valueOf(it) }.getOrNull() }
            ?: GameTextRepository.resolveLanguage()
        val jsonString = prefs.getString("saved_presets", null)
        if (jsonString.isNullOrBlank()) {
            _uiState.update { it.copy(appLanguage = savedLanguage, configsLoaded = true) }
            return
        }

        val payload = runCatching { decodeSavedPresets(jsonString) }.getOrElse { error ->
            CatalogLoadFeedback.reportFailure(CatalogLoadArea.PRESETS, error)
            val fallbackConfigs = defaultNamingConfigs()
            _uiState.update { state ->
                state.copy(
                    appLanguage = savedLanguage,
                    configsLoaded = true,
                    configs = fallbackConfigs,
                    generatedResults = state.parsedData?.let { generateAll(it, fallbackConfigs) } ?: emptyList(),
                    error = CatalogLoadFeedback.message(CatalogLoadArea.PRESETS, savedLanguage)
                )
            }
            return
        }
        val loadedConfigs = payload.configs
        val migratedConfigs = ensureBuiltInNamingConfigs(loadedConfigs)
        if (payload.requiresMigration || migratedConfigs != loadedConfigs) {
            saveConfigs(context, migratedConfigs, backupSource = jsonString)
        }
        _uiState.update { state ->
            state.copy(
                appLanguage = savedLanguage,
                configsLoaded = true,
                configs = migratedConfigs.ifEmpty { defaultNamingConfigs() },
                generatedResults = state.parsedData?.let { generateAll(it, migratedConfigs.ifEmpty { defaultNamingConfigs() }) } ?: emptyList()
            )
        }
    }

    fun setAppLanguage(context: Context, language: AppLanguage) {
        if (_uiState.value.appLanguage == language) return
        CatalogCache.invalidateLanguage(language)
        context.getSharedPreferences("mewname_prefs", Context.MODE_PRIVATE)
            .edit()
            .putString("app_language", language.name)
            .apply()
        _uiState.update { it.copy(appLanguage = language) }
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
        if (imageProcessingJob?.isActive == true) return
        imageProcessingJob = viewModelScope.launch {
            _uiState.update {
                it.copy(
                    error = null,
                    isProcessing = true,
                    processingStatusMessage = "Extraindo texto da imagem"
                )
            }
            try {
                val ocrResult = reviewOcrPipeline.process(context, uri) { step ->
                    _uiState.update { state -> state.copy(processingStatusMessage = step) }
                }
                _uiState.update { state -> state.copy(processingStatusMessage = "Montando nomes sugeridos") }
                val configs = _uiState.value.configs
                val processed = withTimeout(12_000L) {
                    withContext(Dispatchers.Default) {
                        val merged = sessionMerger.mergeIfSamePokemon(ocrResult.data, lastCapturedData)
                        val reviewFields = ReviewPolicy.reviewableFields(configs)
                        val generatedResults = generateAll(merged, configs)
                        ProcessedImageData(merged, reviewFields, generatedResults)
                    }
                }
                val merged = processed.data
                val reviewFields = processed.reviewFields
                val generatedResults = processed.generatedResults
                lastCapturedData = merged
                _uiState.update {
                    val needsReview = ReviewPolicy.shouldOpenReview(merged, reviewFields)
                    // Keep the generated-name preview and editable fields together after every successful scan.
                    val showReview = needsReview || generatedResults.isNotEmpty()
                    it.copy(
                        rawText = ocrResult.rawText,
                        parsedData = merged,
                        generatedResults = generatedResults,
                        pendingReview = if (showReview) ReviewState(merged, reviewFields, ocrResult.bitmap) else null,
                        isProcessing = false,
                        processingStatusMessage = null
                    )
                }
            } catch (_: CancellationException) {
                _uiState.update {
                    it.copy(
                        isProcessing = false,
                        processingStatusMessage = null
                    )
                }
            } catch (throwable: Throwable) {
                CatalogLoadFeedback.reportFailure(CatalogLoadArea.OCR, throwable)
                _uiState.update {
                    it.copy(
                        error = CatalogLoadFeedback.message(CatalogLoadArea.OCR, it.appLanguage),
                        isProcessing = false,
                        processingStatusMessage = null
                    )
                }
            } finally {
                imageProcessingJob = null
            }
        }
    }

    fun cancelImageProcessing() {
        imageProcessingJob?.cancel()
        imageProcessingJob = null
        _uiState.update {
            it.copy(
                isProcessing = false,
                processingStatusMessage = null,
                error = "Processo cancelado."
            )
        }
    }

    fun runDebugIvSampleValidation(context: Context) {
        if (ivValidationJob?.isActive == true) return
        ivValidationJob = viewModelScope.launch {
            _uiState.update {
                it.copy(
                    debugIvValidationRunning = true,
                    debugIvValidationError = null,
                    debugIvValidationResults = emptyList()
                )
            }

            try {
                val results = context.assets.list("iv_samples")
                    ?.filter { it.endsWith(".png", true) || it.endsWith(".jpg", true) || it.endsWith(".jpeg", true) }
                    ?.sorted()
                    .orEmpty()
                    .map { fileName ->
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
                _uiState.update {
                    it.copy(
                        debugIvValidationRunning = false,
                        debugIvValidationResults = results,
                        debugIvValidationError = null
                    )
                }
            } catch (_: CancellationException) {
                _uiState.update {
                    it.copy(
                        debugIvValidationRunning = false,
                        debugIvValidationError = "Validacao cancelada.",
                        debugIvValidationResults = emptyList()
                    )
                }
            } catch (error: Throwable) {
                _uiState.update {
                    it.copy(
                        debugIvValidationRunning = false,
                        debugIvValidationError = error.message ?: "Falha ao validar amostras",
                        debugIvValidationResults = emptyList()
                    )
                }
            } finally {
                ivValidationJob = null
            }
        }
    }

    fun runDebugIvUriValidation(context: Context, uris: List<Uri>) {
        if (ivValidationJob?.isActive == true) return
        ivValidationJob = viewModelScope.launch {
            _uiState.update {
                it.copy(
                    debugIvValidationRunning = true,
                    debugIvValidationError = null,
                    debugIvValidationResults = emptyList()
                )
            }

            try {
                val results = uris.map { uri ->
                    val fileName = resolveDisplayName(context, uri)
                    val expected = parseExpectedIvFromFileName(fileName)
                    val ocrResult = withTimeout(20_000L) {
                        ocrEngine.extract(context, uri)
                    }
                    val parsed = withTimeout(25_000L) {
                        withContext(Dispatchers.Default) {
                            parser.parse(context, ocrResult)
                        }
                    }
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
                _uiState.update {
                    it.copy(
                        debugIvValidationRunning = false,
                        debugIvValidationResults = results,
                        debugIvValidationError = null
                    )
                }
            } catch (_: CancellationException) {
                _uiState.update {
                    it.copy(
                        debugIvValidationRunning = false,
                        debugIvValidationError = "Validacao cancelada.",
                        debugIvValidationResults = emptyList()
                    )
                }
            } catch (error: Throwable) {
                _uiState.update {
                    it.copy(
                        debugIvValidationRunning = false,
                        debugIvValidationError = error.message ?: "Falha ao analisar prints",
                        debugIvValidationResults = emptyList()
                    )
                }
            } finally {
                ivValidationJob = null
            }
        }
    }

    fun cancelIvValidation() {
        ivValidationJob?.cancel()
        ivValidationJob = null
        _uiState.update {
            it.copy(
                debugIvValidationRunning = false,
                debugIvValidationError = "Validacao cancelada.",
                debugIvValidationResults = emptyList()
            )
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
            val updatedConfigs = if (state.configs.any { it.id == config.id }) {
                state.configs.map { if (it.id == config.id) config else it }
            } else {
                state.configs + config
            }
            saveConfigs(context, updatedConfigs)
            state.copy(
                configs = updatedConfigs,
                generatedResults = state.parsedData?.let { generateAll(it, updatedConfigs) } ?: emptyList()
            )
        }
    }

    private fun saveConfigs(
        context: Context,
        configs: List<NamingConfig>,
        backupSource: String? = null
    ) {
        val prefs = context.getSharedPreferences("mewname_prefs", Context.MODE_PRIVATE)
        val serialized = encodeSavedPresets(configs)
        prefs.edit().apply {
            if (backupSource != null && backupSource != serialized) {
                putString(SAVED_PRESETS_BACKUP_KEY, backupSource)
            }
            putString(SAVED_PRESETS_KEY, serialized)
        }.apply()
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
    val appLanguage: AppLanguage = GameTextRepository.resolveLanguage(),
    val configsLoaded: Boolean = false,
    val showBubbleOption: Boolean = true,
    val rawText: String? = null,
    val parsedData: PokemonScreenData? = null,
    val configs: List<NamingConfig> = defaultNamingConfigs(),
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

private data class ProcessedImageData(
    val data: PokemonScreenData,
    val reviewFields: List<NamingField>,
    val generatedResults: List<GeneratedNameResult>
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
        if ((legacyVivillonValue != null && value == legacyVivillonValue) ||
            (key == "MASTER_IV_MATCH" && value == "tm")) {
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
