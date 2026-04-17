package com.mewname.app.domain

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Color
import android.graphics.Rect
import androidx.palette.graphics.Palette
import com.mewname.app.model.EvolutionFlag
import com.mewname.app.model.Gender
import com.mewname.app.model.GenderDebugInfo
import com.mewname.app.model.AdventureEffectDebugInfo
import com.mewname.app.model.AttributeDebugInfo
import com.mewname.app.model.BackgroundDebugInfo
import com.mewname.app.model.CandyDebugInfo
import com.mewname.app.model.EvolutionIconDebugInfo
import com.mewname.app.model.IvDebugInfo
import com.mewname.app.model.LegacyDebugInfo
import com.mewname.app.model.LevelDebugInfo
import com.mewname.app.model.MasterIvBadgeDebugInfo
import com.mewname.app.model.NormalizedDebugRect
import com.mewname.app.model.PokemonScreenData
import com.mewname.app.model.PvpLeague
import com.mewname.app.model.UniqueFormDebugInfo
import com.mewname.app.model.VivillonPattern
import com.mewname.app.ocr.OcrResult
import com.mewname.app.ocr.OcrTextLine
import org.json.JSONArray
import org.apache.commons.text.similarity.LevenshteinDistance
import org.json.JSONObject
import java.text.Normalizer
import java.util.Locale
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.roundToInt
import kotlin.math.sqrt

class OcrPokemonParser {
    @Volatile
    private var shadowTextureSignatures: List<IntArray>? = null

    private data class PokemonNameEntry(
        val dex: Int,
        val name: String,
        val normalizedName: String,
        val normalizedAliases: List<String>
    )

    private data class IvBarDetection(
        val attackRatio: Float?,
        val defenseRatio: Float?,
        val staminaRatio: Float?,
        val attack: Int?,
        val defense: Int?,
        val stamina: Int?,
        val attackDebug: String,
        val defenseDebug: String,
        val staminaDebug: String,
        val detectedBars: Int,
        val isReliable: Boolean,
        val appraisalPanelRect: Rect,
        val attackBarRect: Rect?,
        val defenseBarRect: Rect?,
        val staminaBarRect: Rect?
    )

    private data class DetectedBarBand(
        val top: Int,
        val bottom: Int,
        val ratio: Float?,
        val value: Int?,
        val likelyFullDark: Boolean = false,
        val rect: Rect,
        val debugSummary: String = ""
    )

    private data class BarMeasurement(
        val ratio: Float?,
        val value: Int? = null,
        val trackRect: Rect?,
        val debugSummary: String = ""
    )

    private data class LayoutBandCandidate(
        val measurement: BarMeasurement,
        val sourceRect: Rect,
        val score: Int
    )

    private data class BarColumnMetrics(
        val ratio: Float,
        val trackStart: Int,
        val trackEnd: Int
    )

    private val cpRegex = Regex("""(?:CP|PC|GP|OP|DP|P)\s*[:.-]?\s*(\d{2,5})""", RegexOption.IGNORE_CASE)
    private val ivPercentRegex = Regex("""(\d{1,3})\s*[%©]""")
    private val ivCombinationRegex = Regex("""(\d{1,2})\s*[/|\\-]\s*(\d{1,2})\s*[/|\\-]\s*(\d{1,2})""")
    private val levelRegex = Regex("""^(?:L(?:V(?:L)?)?|NIVEL|LEVEL|NIV|LEV)\s*[:.]?\s*(\d{1,2}(?:[.,]5)?)$""", RegexOption.IGNORE_CASE)
    private val footerPokemonRegex = Regex("""(?i)O\s+POK[ÉE]MON\s+([\p{L}' .-]+?)\s+FOI\s+(?:PEGO|CAPTURADO)\s+EM""")
    private val footerCaughtRegex = Regex("""(?i)(?:THIS|THE)\s+POK[ÉE]MON\s+([\p{L}' .-]+?)\s+WAS\s+CAUGHT""")

    private val pokemonTypes = setOf(
        "NORMAL", "FIRE", "WATER", "GRASS", "ELECTRIC", "ICE", "FIGHTING", "POISON",
        "GROUND", "FLYING", "PSYCHIC", "BUG", "ROCK", "GHOST", "DRAGON", "STEEL", "FAIRY", "DARK", "DRAGAO"
    )
    private val pokemonTypeAliases = mapOf(
        "NORMAL" to listOf("NORMAL"),
        "FIRE" to listOf("FIRE", "FOGO"),
        "WATER" to listOf("WATER", "AGUA"),
        "GRASS" to listOf("GRASS", "PLANTA"),
        "ELECTRIC" to listOf("ELECTRIC", "ELETRICO"),
        "ICE" to listOf("ICE", "GELO"),
        "FIGHTING" to listOf("FIGHTING", "LUTADOR"),
        "POISON" to listOf("POISON", "VENENOSO"),
        "GROUND" to listOf("GROUND", "TERRA"),
        "FLYING" to listOf("FLYING", "VOADOR"),
        "PSYCHIC" to listOf("PSYCHIC", "PSIQUICO"),
        "BUG" to listOf("BUG", "INSETO"),
        "ROCK" to listOf("ROCK", "PEDRA"),
        "GHOST" to listOf("GHOST", "FANTASMA"),
        "DRAGON" to listOf("DRAGON", "DRAGAO"),
        "STEEL" to listOf("STEEL", "ACO"),
        "FAIRY" to listOf("FAIRY", "FADA"),
        "DARK" to listOf("DARK", "SOMBRIO")
    )

    private val specialBackgroundKeywords = setOf(
        "LONDON", "LONDRES", "NEW YORK", "NOVA YORK", "OSAKA", "MADRID", "SENDAI",
        "INCHEON", "JEJU", "GUAYAQUIL", "JAKARTA", "BALI",
        "DELIGHTFUL", "TRANSFORMATION", "TALES", "GO FEST", "GOFEST", "SAFARI", "TOUR"
    )

    private val adventureKeywords = setOf("ADVENTURE EFFECT", "EFEITO AVENTURA")

    private val legacyKeywords = setOf(
        "LEGACY", "ANTIGO", "ELITE", "MOVIMENTO ANTIGO", "ATAQUE ANTIGO"
    )
    private val megaKeywords = setOf(
        "MEGA", "MEGAEVOLVE", "MEGA EVOLVE", "MEGAEVOLUIR", "MEGA EVOLUIR",
        "MEGA ENERGY", "MEGAENERGY", "MEGA ENERGIA", "MEGAENERGIA"
    )
    private val gigantamaxKeywords = setOf(
        "GIGANTAMAX", "GIGAMAX", "GIGAMA", "GIGAM",
        "GIGA MAX", "G-MAX", "G MAX"
    )
    private val dynamaxKeywords = setOf(
        "DYNAMAX", "DINAMAX", "DINA MAX", "D1NAMAX", "DYN4MAX", "DYNAMX", "DINAMX",
        "D-MAX", "D MAX", "MAX BATTLE", "BATALHA MAX"
    )

    private val xxlKeywords = setOf("XXL", "XL")
    private val xxsKeywords = setOf("XXS", "XS")
    private val ivContextKeywords = setOf(
        "APPRAISAL", "VALORACAO", "VALORAR", "ATTACK", "DEFENSE", "DEFESA", "HP", "ATAQUE"
    )

    private val generalBlacklist = setOf(
        "CP", "PC", "HP", "STARDUST", "WEIGHT", "HEIGHT", "ATAQUE", "DEFESA",
        "VALORAR", "APPRAISAL", "STATS", "POWER UP", "PURIFY", "EVOLVE", "NEW ATTACK"
    )

    private var legacyMovesCache: Map<String, List<String>>? = null
    private var pokemonNamesCache: List<PokemonNameEntry>? = null
    private val fuzzyMatcher = LevenshteinDistance()
    private val rankCalculator = PvpRankCalculator()
    private val familySuggester = PokemonFamilySuggester()
    private val masterIvBadgeCatalog = MasterIvBadgeCatalog()
    private val backgroundReferenceMatcher = BackgroundReferenceMatcher()
    private val uniquePokemonReferenceMatcher = UniquePokemonReferenceMatcher()
    private val vivillonIconMatcher = VivillonIconMatcher()

    private val genderlessPokemon = setOf(
        "MAGNEMITE", "MAGNETON", "MAGNEZONE",
        "VOLTORB", "ELECTRODE", "HISUIAN VOLTORB", "HISUIAN ELECTRODE",
        "STARYU", "STARMIE",
        "PORYGON", "PORYGON2", "PORYGON-Z",
        "BALTOY", "CLAYDOL",
        "BRONZOR", "BRONZONG",
        "ROTOM", "HEAT ROTOM", "WASH ROTOM", "FROST ROTOM", "FAN ROTOM", "MOW ROTOM",
        "BELDUM", "METANG", "METAGROSS",
        "KLINK", "KLANG", "KLINKLANG",
        "GOLETT", "GOLURK",
        "CRYOGONAL", "CARBINK",
        "DITTO", "UNKNOWN", "UNOWN",
        "ARTICUNO", "ZAPDOS", "MOLTRES", "MEWTWO", "MEW",
        "RAIKOU", "ENTEI", "SUICUNE", "LUGIA", "HO-OH", "CELEBI",
        "REGIROCK", "REGICE", "REGISTEEL", "LATIAS", "LATIOS", "KYOGRE", "GROUDON", "RAYQUAZA",
        "JIRACHI", "DEOXYS", "DEOXYS ATTACK", "DEOXYS DEFENSE", "DEOXYS SPEED",
        "UXIE", "MESPRIT", "AZELF", "DIALGA", "PALKIA", "HEATRAN", "REGIGIGAS", "GIRATINA", "GIRATINA ORIGIN",
        "CRESSELIA", "PHIONE", "MANAPHY", "DARKRAI", "SHAYMIN", "ARCEUS",
        "VICTINI", "KLINKLANG", "RESHIRAM", "ZEKROM", "KYUREM", "BLACK KYUREM", "WHITE KYUREM",
        "KELDEO", "MELOETTA", "GENESECT", "GENESECT BURN", "GENESECT CHILL", "GENESECT DOUSE", "GENESECT SHOCK",
        "XERNEAS", "YVELTAL", "ZYGARDE", "DIANCIE", "HOOPA", "HOOPA UNBOUND", "VOLCANION",
        "TYPE NULL", "SILVALLY", "MINIOR", "DHELMISE", "NIHILEGO", "BUZZWOLE", "PHEROMOSA", "XURKITREE",
        "CELESTEELA", "KARTANA", "GUZZLORD", "NECROZMA", "MAGEARNA", "MARSHADOW", "ZERAORA", "MELTAN", "MELMETAL",
        "ZACIAN", "ZAMAZENTA", "ETERNATUS", "KUBFU", "URSHIFU", "REGIELEKI", "REGIDRAGO", "GLASTRIER", "SPECTRIER",
        "CALYREX", "ENAMORUS", "ENAMORUS THERIAN", "WO-CHIEN", "CHIEN-PAO", "TING-LU", "CHI-YU",
        "ROARING MOON", "IRON VALIANT", "KORAIDON", "MIRAIDON", "GIMMIGHOUL", "GHOLDENGO", "PECHARUNT"
    )

    fun parse(
        context: Context,
        ocrResult: OcrResult,
        onAnalysisStep: ((String) -> Unit)? = null
    ): PokemonScreenData {
        val orderedLines = orderedLines(ocrResult)
        val rawText = orderedLines.joinToString("\n") { it.text }
        return parseInternal(context, rawText, orderedLines, ocrResult.bitmap, onAnalysisStep)
    }

    fun parse(
        context: Context,
        rawText: String,
        bitmap: Bitmap? = null,
        onAnalysisStep: ((String) -> Unit)? = null
    ): PokemonScreenData {
        val lines = rawText.split("\n").map { OcrTextLine(text = it, boundingBox = null) }
        return parseInternal(context, rawText, lines, bitmap, onAnalysisStep)
    }

    private fun parseInternal(
        context: Context,
        rawText: String,
        orderedLines: List<OcrTextLine>,
        bitmap: Bitmap?,
        onAnalysisStep: ((String) -> Unit)?
    ): PokemonScreenData {
        IvCombinationTable.ensureLoaded(context)
        val referenceBounds = bitmap?.let { Rect(0, 0, it.width, it.height) }

        val normalizedRaw = rawText.replace("\n", " ")
        val normUpper = normalizeText(normalizedRaw)
        val hasIvContext = hasIvContext(normUpper, orderedLines, referenceBounds)

        onAnalysisStep?.invoke("Identificando IVs e CP")

        val cp = cpRegex.find(normalizedRaw)?.groupValues?.getOrNull(1)?.toIntOrNull()
        var ivPercent = extractIvPercent(normalizedRaw, orderedLines, referenceBounds)

        val ivComb = ivCombinationRegex.find(normalizedRaw)
        val rawAtt = ivComb?.groupValues?.getOrNull(1)?.toIntOrNull()
        val rawDef = ivComb?.groupValues?.getOrNull(2)?.toIntOrNull()
        val rawSta = ivComb?.groupValues?.getOrNull(3)?.toIntOrNull()
        val hasValidIvCombination = listOf(rawAtt, rawDef, rawSta).all { value ->
            value != null && value in 0..15
        }
        var att = if (hasValidIvCombination) rawAtt else null
        var def = if (hasValidIvCombination) rawDef else null
        var sta = if (hasValidIvCombination) rawSta else null

        val ivBarDetection = bitmap?.let { detectIvBars(it, orderedLines) }
        val canUseIvBars = hasIvContext && (ivBarDetection?.isReliable == true)
        if (canUseIvBars && (att == null || def == null || sta == null)) {
            val snapped = IvCombinationTable.nearestFromRatios(
                ivBarDetection?.attackRatio,
                ivBarDetection?.defenseRatio,
                ivBarDetection?.staminaRatio,
                ivPercentHint = ivPercent
            )
            att = att ?: ivBarDetection?.attack ?: snapped?.attack
            def = def ?: ivBarDetection?.defense ?: snapped?.defense
            sta = sta ?: ivBarDetection?.stamina ?: snapped?.stamina
        }

        if (canUseIvBars && ivPercent != null && listOf(att, def, sta).any { it == null }) {
            val percentAnchored = IvCombinationTable.nearestFromRatios(
                ivBarDetection?.attackRatio,
                ivBarDetection?.defenseRatio,
                ivBarDetection?.staminaRatio,
                ivPercentHint = ivPercent
            )
            if (!hasValidIvCombination && percentAnchored != null) {
                att = att ?: percentAnchored.attack
                def = def ?: percentAnchored.defense
                sta = sta ?: percentAnchored.stamina
            }
        }

        if (canUseIvBars && att != null && def != null && sta != null) {
            val barDerivedPercent = IvCombinationTable.fromValues(att, def, sta)?.ivPercent
                ?: ((att + def + sta) * 100 / 45)
            if (ivPercent == null) {
                ivPercent = barDerivedPercent
            }
        }

        if (!hasIvContext && !hasValidIvCombination) {
            ivPercent = null
        }

        if (ivPercent == null && att != null && def != null && sta != null) {
            ivPercent = IvCombinationTable.fromValues(att, def, sta)?.ivPercent ?: ((att + def + sta) * 100 / 45)
        }

        onAnalysisStep?.invoke("Reconhecendo Pokémon")

        val ocrLevel = extractOcrLevel(orderedLines, referenceBounds)
        val moves = extractMoves(orderedLines, referenceBounds)
        var name = inferPokemonName(context, orderedLines, moves, cp, referenceBounds)
        val (candyFamilyName, candyDebugInfo) = extractCandyFamilyName(context, orderedLines, referenceBounds)
        val hasUnownTitleSignal = detectUnownTitleSignal(orderedLines, referenceBounds)
        val hasUnownCandySignal = candyFamilyName.equals("Unown", ignoreCase = true)
        val hasUnownNameSignal = name.equals("Unown", ignoreCase = true)
        val provisionalUnownContext = hasUnownTitleSignal || hasUnownCandySignal || hasUnownNameSignal
        val unownLetter = if (provisionalUnownContext) detectUnownLetter(normUpper, orderedLines, referenceBounds) else null
        val shouldForceUnownName = hasUnownNameSignal ||
            (hasUnownTitleSignal && hasUnownCandySignal) ||
            (unownLetter != null && (hasUnownTitleSignal || hasUnownCandySignal))
        if (shouldForceUnownName) {
            name = "Unown"
        }

        onAnalysisStep?.invoke("Estimando nível")

        val curveLevel = if (name != null && cp != null && att != null && def != null && sta != null) {
            rankCalculator.estimateLevel(context, name, cp, att, def, sta)
        } else {
            null
        }
        val levelMismatch = if (ocrLevel != null && curveLevel != null) abs(ocrLevel - curveLevel) else null
        val level = when {
            ocrLevel != null && curveLevel != null && levelMismatch != null && levelMismatch > 2.5 -> curveLevel
            ocrLevel != null -> ocrLevel
            else -> curveLevel
        }
        val levelDebugInfo = LevelDebugInfo(
            source = when {
                ocrLevel != null && (curveLevel == null || (levelMismatch != null && levelMismatch <= 2.5)) -> "ocr"
                curveLevel != null -> "curva_cp"
                else -> "indisponivel"
            },
            ocrLevel = ocrLevel,
            curveLevel = curveLevel,
            finalLevel = level,
            pokemonName = name,
            cp = cp,
            attackIv = att,
            defenseIv = def,
            staminaIv = sta,
            notes = buildList {
                if (ocrLevel != null && (curveLevel == null || (levelMismatch != null && levelMismatch <= 2.5))) add("nível encontrado no texto OCR")
                if (curveLevel != null && (ocrLevel == null || (levelMismatch != null && levelMismatch > 2.5))) add("nível estimado pela curva de CP")
                if (ocrLevel != null && curveLevel != null && levelMismatch != null && levelMismatch > 2.5) {
                    add("nível OCR descartado por divergência com a curva de CP")
                }
                if (ocrLevel == null) add("nenhuma linha com nível explícito foi encontrada no OCR")
                if (curveLevel == null && cp == null) add("CP ausente para estimativa por curva")
                if (curveLevel == null && cp != null && listOf(att, def, sta).any { it == null }) {
                    add("estimativa pela curva de CP exige os três IVs; nesta captura eles não foram lidos")
                }
                if (ocrLevel == null && curveLevel == null) add("sem dados suficientes para estimar o nível")
            }.joinToString("; ")
        )

        onAnalysisStep?.invoke("Calculando PvP")

        val familyMembers = if (name != null && att != null && def != null && sta != null) {
            familySuggester.familyMembersFor(context, candyFamilyName, name)
        } else {
            emptyList()
        }
        val leagueRanks = if (familyMembers.isNotEmpty() && att != null && def != null && sta != null) {
            rankCalculator.calculateBestFamilyLeagueRanks(context, familyMembers, att, def, sta)
        } else {
            emptyList()
        }
        val familySpeciesRanks = if (familyMembers.isNotEmpty() && att != null && def != null && sta != null) {
            rankCalculator.calculateFamilySpeciesLeagueRanks(context, familyMembers, att, def, sta)
        } else {
            emptyList()
        }
        val masterIvBadgeResult = masterIvBadgeCatalog.resolve(
            context = context,
            familyMembers = familyMembers,
            ivPercent = ivPercent,
            attack = att,
            defense = def,
            stamina = sta
        )

        val bestFamilySpeciesRank = familySpeciesRanks
            .filter { it.eligible && it.rank != null }
            .minWithOrNull(
                compareBy<com.mewname.app.model.PvpSpeciesRankInfo> { it.rank ?: Int.MAX_VALUE }
                    .thenBy { it.league.ordinal }
            )
        var league = detectLeague(normalizedRaw)
        if (league == null) {
            league = bestFamilySpeciesRank?.league ?: leagueRanks
                .filter { it.eligible && it.rank != null }
                .minWithOrNull(compareBy<com.mewname.app.model.PvpLeagueRankInfo> { it.rank ?: Int.MAX_VALUE }.thenBy { it.league.ordinal })
                ?.league
        }
        if (league == null && cp != null) {
            league = when {
                cp <= 500 -> PvpLeague.LITTLE
                cp <= 1500 -> PvpLeague.GREAT
                cp <= 2500 -> PvpLeague.ULTRA
                else -> null
            }
        }

        val selectedSpeciesRankInfo = league?.let { selectedLeague ->
            familySpeciesRanks
                .filter { it.league == selectedLeague && it.eligible && it.rank != null }
                .minWithOrNull(
                    compareBy<com.mewname.app.model.PvpSpeciesRankInfo> { it.rank ?: Int.MAX_VALUE }
                        .thenBy { it.pokemonName }
                )
        }
        val selectedPvpRankInfo = league?.let { selectedLeague ->
            leagueRanks.firstOrNull { it.league == selectedLeague }
        }
        val rank = selectedSpeciesRankInfo?.rank ?: selectedPvpRankInfo?.rank
        val pvpPokemonName = selectedSpeciesRankInfo?.pokemonName ?: selectedPvpRankInfo?.pokemonName

        val typeDetection = detectPokemonTypes(orderedLines, normUpper, referenceBounds)
        val detectedTypes = typeDetection.first

        onAnalysisStep?.invoke("Validando fundo e marcadores")

        val backgroundDetection = detectSpecialBackground(context, normUpper, orderedLines, bitmap, referenceBounds, detectedTypes)
        val hasSpecialBackground = backgroundDetection.first
        val adventureDetection = detectAdventureEffect(context, name, moves, normUpper, orderedLines, referenceBounds)
        val hasAdventureEffect = adventureDetection.first
        val size = detectSize(normUpper, orderedLines, referenceBounds)
        val legacyDetection = detectLegacyMove(name, moves, orderedLines, loadLegacyMoves(context), referenceBounds)
        val hasLegacyMove = legacyDetection.first
        val genderDetection = detectGender(normalizedRaw, orderedLines, referenceBounds, name, bitmap)
        val isFavorite = bitmap?.let(::detectFavoriteStarFilled) ?: false
        val favoriteRatio = bitmap?.let(::measureFavoriteStarYellowRatio)
        val purifiedTextMatch = normalizedRaw.lowercase().let { it.contains("purified") || it.contains("purificado") }
        val vivillonDetection = detectVivillonPattern(context, bitmap, name)
        val uniqueFormDetection = detectUniqueForm(context, bitmap, name, detectedTypes, unownLetter)
        val evolutionDetection = detectEvolutionFlags(normUpper, orderedLines, name, referenceBounds, bitmap)

        onAnalysisStep?.invoke("Finalizando dados detectados")

        return PokemonScreenData(
            pokemonName = name,
            unownLetter = if (name.equals("Unown", ignoreCase = true)) {
                uniqueFormDetection.first?.takeIf { it.length == 1 || it == "!" || it == "?" } ?: unownLetter
            } else {
                null
            },
            uniqueForm = uniqueFormDetection.first,
            candyFamilyName = candyFamilyName,
            candyDebugInfo = candyDebugInfo,
            levelDebugInfo = levelDebugInfo,
            attributeDebugInfo = AttributeDebugInfo(
                typeRegionLines = typeDetection.second,
                detectedTypes = detectedTypes,
                favoriteFilledMatch = isFavorite,
                favoriteYellowRatio = favoriteRatio,
                purifiedTextMatch = purifiedTextMatch,
                notes = buildList {
                    if (detectedTypes.isEmpty()) add("nenhum tipo reconhecido na área central")
                    if (isFavorite) add("estrela superior direita preenchida em amarelo")
                    if (purifiedTextMatch) add("texto de purificado encontrado")
                }.joinToString("; ")
            ),
            legacyDebugInfo = legacyDetection.second,
            adventureEffectDebugInfo = adventureDetection.second,
            backgroundDebugInfo = backgroundDetection.second,
            uniqueFormDebugInfo = uniqueFormDetection.second,
            evolutionIconDebugInfo = evolutionDetection.second,
            vivillonDebugInfo = vivillonDetection.second,
            vivillonPattern = vivillonDetection.first,
            cp = cp,
            ivPercent = ivPercent,
            attIv = att,
            defIv = def,
            staIv = sta,
            ivDebugInfo = ivBarDetection?.let { detection ->
                IvDebugInfo(
                    attackRatio = detection.attackRatio,
                    defenseRatio = detection.defenseRatio,
                    staminaRatio = detection.staminaRatio,
                    attackDetected = detection.attack,
                    defenseDetected = detection.defense,
                    staminaDetected = detection.stamina,
                    attackMeasurementDebug = detection.attackDebug,
                    defenseMeasurementDebug = detection.defenseDebug,
                    staminaMeasurementDebug = detection.staminaDebug,
                    detectedBars = detection.detectedBars,
                    reliable = detection.isReliable,
                    appraisalDetected = hasIvContext,
                    percentFromOcr = extractIvPercent(normalizedRaw, orderedLines, referenceBounds),
                    percentFinal = ivPercent,
                    appraisalPanelRect = bitmap?.let { normalizeDebugRect(detection.appraisalPanelRect, it) },
                    attackBarRect = bitmap?.let { detection.attackBarRect?.let { rect -> normalizeDebugRect(rect, it) } },
                    defenseBarRect = bitmap?.let { detection.defenseBarRect?.let { rect -> normalizeDebugRect(rect, it) } },
                    staminaBarRect = bitmap?.let { detection.staminaBarRect?.let { rect -> normalizeDebugRect(rect, it) } },
                    notes = buildList {
                        if (!hasIvContext) add("contexto de appraisal nao encontrado")
                        if (!detection.isReliable) add("leitura das barras considerada pouco confiavel")
                        if (detection.detectedBars < 3) add("nem todas as barras foram encontradas")
                    }.joinToString("; ")
                )
            },
            level = level,
            gender = genderDetection.first,
            genderDebugInfo = genderDetection.second,
            type1 = detectedTypes.firstOrNull(),
            type2 = detectedTypes.getOrNull(1),
            isFavorite = isFavorite,
            isLucky = backgroundDetection.second.luckyTextMatch || backgroundDetection.second.luckyVisualMatch,
            isShiny = backgroundDetection.second.shinyParticleMatch,
            pvpLeague = league,
            pvpRank = rank,
            pvpPokemonName = pvpPokemonName,
            pvpLeagueRanks = leagueRanks,
            familyPvpRanks = familySpeciesRanks,
            masterIvBadgeMatch = masterIvBadgeResult.isBestMatch,
            masterIvBadgeDebugInfo = MasterIvBadgeDebugInfo(
                supportedIvPercent = ivPercent in setOf(67, 91, 93, 96, 98),
                familyMembers = familyMembers,
                expectedAttack = masterIvBadgeResult.expectedAttack,
                expectedDefense = masterIvBadgeResult.expectedDefense,
                expectedStamina = masterIvBadgeResult.expectedStamina,
                isBestMatch = masterIvBadgeResult.isBestMatch,
                notes = masterIvBadgeResult.notes
            ),
            evolutionFlags = evolutionDetection.first,
            hasLegacyMove = hasLegacyMove,
            isShadow = backgroundDetection.second.shadowTextMatch ||
                backgroundDetection.second.shadowParticleMatch ||
                backgroundDetection.second.shadowTextureMatch,
            isPurified = purifiedTextMatch,
            hasSpecialBackground = hasSpecialBackground,
            hasAdventureEffect = hasAdventureEffect,
            size = size
        )
    }

    private fun detectUniqueForm(
        context: Context,
        bitmap: Bitmap?,
        pokemonName: String?,
        detectedTypes: List<String>,
        detectedUnownLetter: String?
    ): Pair<String?, UniqueFormDebugInfo?> {
        if (pokemonName.equals("Unown", ignoreCase = true)) {
            if (!detectedUnownLetter.isNullOrBlank()) {
                return detectedUnownLetter to UniqueFormDebugInfo(
                    category = "unown",
                    bestLabel = detectedUnownLetter,
                    accepted = true,
                    notes = "decisao_final=ocr_unown"
                )
            }
            val matcherResult = uniquePokemonReferenceMatcher.detect(context, bitmap, "Unown")
            val matcherDebug = matcherResult.second
            val debug = matcherDebug?.copy(
                notes = buildString {
                    if (!matcherDebug.notes.isNullOrBlank()) {
                        append(matcherDebug.notes)
                        append("; ")
                    }
                    append("decisao_final=sprite_unown")
                }
            )
            return matcherResult.first to debug
        }
        val rotomFormByTypes = detectRotomFormByTypes(pokemonName, detectedTypes)
        val matcherResult = uniquePokemonReferenceMatcher.detect(
            context = context,
            bitmap = bitmap,
            pokemonName = pokemonName,
            allowedLabels = rotomFormByTypes?.let(::setOf).orEmpty()
        )
        if (rotomFormByTypes == null) return matcherResult

        val typeSummary = detectedTypes.joinToString("/")
        val baseDebug = matcherResult.second ?: UniqueFormDebugInfo(category = "rotom")
        return rotomFormByTypes to baseDebug.copy(
            accepted = true,
            notes = buildString {
                if (baseDebug.notes.isNotBlank()) {
                    append(baseDebug.notes)
                    append("; ")
                }
                append("decisao_final=tipos")
                if (typeSummary.isNotBlank()) {
                    append("; tipos=$typeSummary")
                }
            }
        )
    }

    private fun detectRotomFormByTypes(
        pokemonName: String?,
        detectedTypes: List<String>
    ): String? {
        if (!pokemonName.equals("Rotom", ignoreCase = true)) return null
        val typeSet = detectedTypes
            .map { it.trim().uppercase(Locale.US) }
            .filter { it.isNotEmpty() }
            .toSet()
        return when (typeSet) {
            setOf("ELECTRIC", "FIRE") -> "Calor"
            setOf("ELECTRIC", "WATER") -> "Lavagem"
            setOf("ELECTRIC", "ICE") -> "Congelante"
            setOf("ELECTRIC", "FLYING") -> "Ventilador"
            setOf("ELECTRIC", "GRASS") -> "Corte"
            setOf("ELECTRIC", "GHOST") -> "Normal"
            else -> null
        }
    }

    private fun detectUnownLetter(
        normalizedRaw: String,
        lines: List<OcrTextLine>,
        referenceBounds: Rect?
    ): String? {
        val relevant = normalizedLinesInRegion(lines, 0.16f, 0.84f, 0.18f, 0.40f, referenceBounds)
        val collapsed = relevant.joinToString(" ")
        val explicit = Regex("""UNOWN\s*([A-Z?])(?=$|[^A-Z])""").find(collapsed)?.groupValues?.getOrNull(1)
            ?: Regex("""UNOWN([A-Z?])(?=$|[^A-Z])""").find(collapsed)?.groupValues?.getOrNull(1)
        if (!explicit.isNullOrBlank()) return explicit
        return null
    }

    private fun detectUnownTitleSignal(
        lines: List<OcrTextLine>,
        referenceBounds: Rect?
    ): Boolean {
        val titleLines = normalizedLinesInRegion(lines, 0.14f, 0.86f, 0.18f, 0.36f, referenceBounds)
        return titleLines.any { line -> line.contains("UNOWN") }
    }

    private fun detectVivillonPattern(
        context: Context,
        bitmap: Bitmap?,
        pokemonName: String?
    ): Pair<VivillonPattern?, com.mewname.app.model.VivillonDebugInfo?> {
        if (bitmap == null || !isVivillonFamily(pokemonName)) return null to null
        val result = vivillonIconMatcher.detectPattern(context, bitmap, pokemonName)
        return result.pattern to result.debugInfo
    }

    private fun normalizeDebugRect(rect: Rect, bitmap: Bitmap): NormalizedDebugRect {
        return NormalizedDebugRect(
            left = rect.left.toFloat() / bitmap.width.toFloat(),
            top = rect.top.toFloat() / bitmap.height.toFloat(),
            right = rect.right.toFloat() / bitmap.width.toFloat(),
            bottom = rect.bottom.toFloat() / bitmap.height.toFloat()
        )
    }

    private fun extractCandyFamilyName(
        context: Context,
        lines: List<OcrTextLine>,
        referenceBounds: Rect?
    ): Pair<String?, CandyDebugInfo> {
        val regionLines = rawLinesInRegion(lines, 0.26f, 0.82f, 0.50f, 0.74f, referenceBounds)
            .sortedBy { it.boundingBox?.top ?: Int.MAX_VALUE }
        val candyIndex = regionLines.indexOfFirst { line ->
            val normalized = normalizeText(line.text)
            normalized.contains("CANDY") || normalized.contains("DOCES DE")
        }
        val candyEntry = regionLines.getOrNull(candyIndex)
        val candyLine = candyEntry?.text

        if (candyLine == null) {
            return null to CandyDebugInfo(
                regionLineCount = regionLines.size,
                regionLines = regionLines.map { it.text },
                notes = "nenhuma linha com DOCES DE/CANDY encontrada"
            )
        }

        val candyRect = candyEntry.boundingBox
        val familyLine = if (candyRect != null) {
            regionLines
                .drop(candyIndex + 1)
                .filter { candidate ->
                    val rect = candidate.boundingBox ?: return@filter false
                    val verticalGap = rect.top - candyRect.bottom
                    val centerDiff = abs(rect.exactCenterX() - candyRect.exactCenterX())
                    verticalGap >= 0 &&
                        verticalGap <= candyRect.height() * 4 &&
                        centerDiff <= candyRect.width() * 0.8f
                }
                .firstOrNull { candidate ->
                    val normalized = normalizeText(candidate.text)
                    normalized.isNotBlank() &&
                        !normalized.contains("DOCES DE") &&
                        !normalized.contains("CANDY") &&
                        !normalized.contains("DOCES GG") &&
                        !normalized.contains("POEIRA") &&
                        !normalized.matches(Regex("""[\d.,]+"""))
                }
                ?.text
        } else {
            null
        }

        val combinedLine = listOfNotNull(candyLine, familyLine)
            .joinToString(" ")
            .replace(Regex("\\s+"), " ")
            .trim()

        val rawFamily = Regex("(?i)(?:doces de|candy)\\s+([A-Za-zÀ-ÖØ-öø-ÿ' .-]+)")
            .find(combinedLine)
            ?.groupValues
            ?.getOrNull(1)
            ?.replace(Regex("\\s+"), " ")
            ?.trim()
            ?.takeIf { it.isNotBlank() }
        if (rawFamily == null) {
            return null to CandyDebugInfo(
                regionLineCount = regionLines.size,
                regionLines = regionLines.map { it.text },
                matchedLine = combinedLine,
                notes = "linha encontrada, mas a familia nao foi extraida pela regex"
            )
        }

        val normalizedFamily = normalizeText(rawFamily)
        val pokemonNames = loadPokemonNames(context)
        val resolved = pokemonNames.firstOrNull { entry ->
            entry.normalizedName == normalizedFamily || normalizedFamily in entry.normalizedAliases
        }?.name ?: pokemonNames
            .asSequence()
            .map { entry ->
                val score = entry.normalizedAliases.minOf { alias ->
                    when {
                        alias.startsWith(normalizedFamily) || normalizedFamily.startsWith(alias) -> 0
                        alias.contains(normalizedFamily) || normalizedFamily.contains(alias) -> 1
                        else -> fuzzyMatcher.apply(normalizedFamily, alias)
                    }
                }
                entry to score
            }
            .filter { (_, score) -> score <= 2 || normalizedFamily.length >= 4 }
            .minByOrNull { (_, score) -> score }
            ?.first
            ?.name

        return resolved to CandyDebugInfo(
            regionLineCount = regionLines.size,
            regionLines = regionLines.map { it.text },
            matchedLine = combinedLine,
            extractedFamilyRaw = rawFamily,
            resolvedFamilyName = resolved,
            notes = if (resolved == null) "familia extraida, mas nao encontrada na base local" else ""
        )
    }
    private fun isVivillonFamily(pokemonName: String?): Boolean {
        val normalized = pokemonName?.let(::normalizeText) ?: return false
        return normalized == "SCATTERBUG" || normalized == "SPEWPA" || normalized == "VIVILLON"
    }

    private fun detectIvBars(bitmap: Bitmap, lines: List<OcrTextLine>): IvBarDetection {
        val width = bitmap.width
        val height = bitmap.height

        val appraisalPanel = detectIvAppraisalPanel(bitmap) ?: Rect(
            (width * 0.05f).roundToInt(),
            (height * 0.70f).roundToInt(),
            (width * 0.48f).roundToInt(),
            (height * 0.91f).roundToInt()
        )

        val panelWidth = appraisalPanel.width().coerceAtLeast(1)
        val panelHeight = appraisalPanel.height().coerceAtLeast(1)
        val regionLeft = (appraisalPanel.left + panelWidth * 0.06f).roundToInt()
        val labelRects = detectIvLabelRects(lines, appraisalPanel)

        val expectedBarRects = buildExpectedIvBarRects(appraisalPanel, labelRects)
        val layoutBands = detectIvBarsFromExpectedLayout(
            bitmap = bitmap,
            appraisalPanel = appraisalPanel,
            expectedBarRects = expectedBarRects
        )
        val labelAnchoredBands = detectIvBarsFromLabels(
            bitmap = bitmap,
            appraisalPanel = appraisalPanel,
            labelRects = labelRects,
            expectedBarRects = expectedBarRects
        )
        val fallbackScanRect = Rect(
            regionLeft,
            (appraisalPanel.top + panelHeight * 0.48f).roundToInt(),
            expectedBarRects.maxOfOrNull { it.right } ?: appraisalPanel.right,
            (appraisalPanel.top + panelHeight * 0.96f).roundToInt().coerceAtMost(height)
        )

        val scanBands = detectIvBarBands(bitmap, fallbackScanRect)
        val bands = mergeDetectedBands(expectedBarRects, labelAnchoredBands, layoutBands, scanBands)
        val attackBand = bands.getOrNull(0)
        val defenseBand = bands.getOrNull(1)
        val staminaBand = bands.getOrNull(2)

        val attackRatio = attackBand?.ratio
        val defenseRatio = defenseBand?.ratio
        val staminaRatio = staminaBand?.ratio
        var attack = resolveDetectedBandValue(attackBand, IvBarType.ATTACK)
        var defense = resolveDetectedBandValue(defenseBand, IvBarType.DEFENSE)
        var stamina = resolveDetectedBandValue(staminaBand, IvBarType.STAMINA)
        val ratios = listOf(attackRatio, defenseRatio, staminaRatio)
        val detectedBars = ratios.count { it != null }
        val validSpread = ratios.filterNotNull().let { nonNull ->
            nonNull.size >= 2 && (nonNull.maxOrNull()!! - nonNull.minOrNull()!!) >= 0.025f
        }
        val hasEnoughFill = ratios.filterNotNull().average().toFloat() >= 0.18f
        val allZeroBars = attack == 0 && defense == 0 && stamina == 0
        val reliable = detectedBars == 3 && (validSpread || hasEnoughFill || allZeroBars)

        return IvBarDetection(
            attackRatio = attackRatio,
            defenseRatio = defenseRatio,
            staminaRatio = staminaRatio,
            attack = attack,
            defense = defense,
            stamina = stamina,
            attackDebug = attackBand?.debugSummary.orEmpty(),
            defenseDebug = defenseBand?.debugSummary.orEmpty(),
            staminaDebug = staminaBand?.debugSummary.orEmpty(),
            detectedBars = detectedBars,
            isReliable = reliable,
            appraisalPanelRect = appraisalPanel,
            attackBarRect = attackBand?.rect,
            defenseBarRect = defenseBand?.rect,
            staminaBarRect = staminaBand?.rect
        )
    }

    private fun detectIvBarsFromLabels(
        bitmap: Bitmap,
        appraisalPanel: Rect,
        labelRects: Map<String, Rect>,
        expectedBarRects: List<Rect>
    ): List<DetectedBarBand> {
        return listOf("ATTACK", "DEFENSE", "HP").mapIndexedNotNull { index, key ->
            val labelRect = labelRects[key] ?: return@mapIndexedNotNull null
            val expectedRect = expectedBarRects.getOrNull(index) ?: return@mapIndexedNotNull null
            val nextLabelRect = listOf("ATTACK", "DEFENSE", "HP")
                .getOrNull(index + 1)
                ?.let(labelRects::get)
            val searchTop = labelRect.bottom.coerceIn(appraisalPanel.top, appraisalPanel.bottom - 2)
            val fallbackBottom = (searchTop + expectedRect.height() + appraisalPanel.height() * 0.08f)
                .roundToInt()
                .coerceAtMost(appraisalPanel.bottom)
            val searchBottom = nextLabelRect
                ?.top
                ?.coerceAtMost(appraisalPanel.bottom)
                ?.takeIf { it > searchTop + 6 }
                ?: fallbackBottom
            val searchRect = Rect(
                expectedRect.left,
                searchTop,
                expectedRect.right,
                searchBottom
            )
            val localScan = detectIvBarBands(bitmap, searchRect)
                .filter { it.ratio != null }
                .filter { isBandGeometryCompatible(it, expectedRect) }
                .minByOrNull { abs(it.rect.centerY() - expectedRect.centerY()) }
            if (localScan != null) {
                return@mapIndexedNotNull localScan.copy(
                    value = localScan.value ?: nearestValueForLabel(key, localScan.ratio),
                    debugSummary = "label-scan ${localScan.debugSummary}"
                )
            }

            val anchoredTop = searchTop
                .coerceIn(appraisalPanel.top, appraisalPanel.bottom - expectedRect.height())
            val anchoredRect = Rect(
                expectedRect.left,
                anchoredTop,
                expectedRect.right,
                (anchoredTop + expectedRect.height()).coerceAtMost(appraisalPanel.bottom)
            )
            val measurement = findBestMeasurementNearExpectedRect(bitmap, appraisalPanel, anchoredRect)
            if (measurement != null) {
                val trackRect = measurement.measurement.trackRect ?: measurement.sourceRect
                return@mapIndexedNotNull DetectedBarBand(
                    top = trackRect.top,
                    bottom = trackRect.bottom,
                    ratio = measurement.measurement.ratio,
                    value = measurement.measurement.value ?: nearestValueForLabel(key, measurement.measurement.ratio),
                    likelyFullDark = measurement.measurement.ratio != null &&
                        isDarkFullBar(bitmap, measurement.sourceRect, measurement.measurement.ratio),
                    rect = trackRect,
                    debugSummary = "label-direct ${measurement.measurement.debugSummary}"
                )
            }

            DetectedBarBand(
                top = anchoredRect.top,
                bottom = anchoredRect.bottom,
                ratio = null,
                value = null,
                likelyFullDark = false,
                rect = anchoredRect,
                debugSummary = "label: sem medicao"
            )
        }
    }

    private fun detectIvLabelRects(
        lines: List<OcrTextLine>,
        appraisalPanel: Rect
    ): Map<String, Rect> {
        val relevantLines = lines
            .mapNotNull { line -> line.boundingBox?.let { rect -> line to rect } }
            .filter { (_, rect) -> rect.centerY() in appraisalPanel.top..appraisalPanel.bottom }

        val labelMap = linkedMapOf(
            "ATTACK" to null as Rect?,
            "DEFENSE" to null as Rect?,
            "HP" to null as Rect?
        ).toMutableMap()

        relevantLines.forEach { (line, rect) ->
            val normalized = normalizeText(line.text)
            when {
                normalized.contains("ATTACK") || normalized.contains("ATAQUE") -> labelMap["ATTACK"] = rect
                normalized.contains("DEFENSE") || normalized.contains("DEFESA") -> labelMap["DEFENSE"] = rect
                normalized == "HP" || normalized == "PS" || normalized.contains(" PS") -> labelMap["HP"] = rect
            }
        }

        return labelMap.mapNotNull { (key, rect) -> rect?.let { key to it } }.toMap()
    }

    private fun mergeDetectedBands(
        expectedBarRects: List<Rect>,
        vararg bandSources: List<DetectedBarBand>
    ): List<DetectedBarBand> {
        val merged = mutableListOf<DetectedBarBand>()
        for (index in 0 until 3) {
            val candidates = bandSources.mapNotNull { it.getOrNull(index) }
            val expectedRect = expectedBarRects.getOrNull(index)
            val geometryMatched = expectedRect?.let { rect ->
                candidates.filter { candidate ->
                    candidate.ratio != null && isBandGeometryCompatible(candidate, rect)
                }
            }.orEmpty()
            val chosen = geometryMatched.firstOrNull()
                ?: expectedRect?.let { rect ->
                    candidates.firstOrNull { isBandGeometryCompatible(it, rect) }
                }
                ?: candidates.firstOrNull()
                ?: continue
            merged += chosen
        }
        return merged
    }

    private fun detectIvBarsFromExpectedLayout(
        bitmap: Bitmap,
        appraisalPanel: Rect,
        expectedBarRects: List<Rect>
    ): List<DetectedBarBand> {
        return expectedBarRects.mapIndexed { index, rect ->
            val labelKey = when (index) {
                0 -> "ATTACK"
                1 -> "DEFENSE"
                else -> "HP"
            }
            detectExpectedLayoutBand(
                bitmap = bitmap,
                appraisalPanel = appraisalPanel,
                expectedRect = rect,
                labelKey = labelKey
            )
        }
    }

    private fun buildExpectedIvBarRects(
        appraisalPanel: Rect,
        labelRects: Map<String, Rect> = emptyMap()
    ): List<Rect> {
        val commonBarStartRatio = 128f / 654f
        val commonBarWidthRatio = 375f / 654f
        val barHeightRatio = 51f / 842f
        val topRatios = listOf(
            "ATTACK" to 369f / 842f,
            "DEFENSE" to 472f / 842f,
            "HP" to 577f / 842f
        )

        val barLeft = appraisalPanel.left + (appraisalPanel.width() * commonBarStartRatio).roundToInt()
        val barRight = (barLeft + appraisalPanel.width() * commonBarWidthRatio)
            .roundToInt()
            .coerceAtMost(appraisalPanel.right)
        val barHeight = (appraisalPanel.height() * barHeightRatio).roundToInt().coerceAtLeast(18)
        val labelDrivenOffset = (appraisalPanel.height() * 0.012f).roundToInt().coerceAtLeast(10)

        return topRatios.map { (key, topRatio) ->
            val layoutTop = (appraisalPanel.top + appraisalPanel.height() * topRatio)
                .roundToInt()
                .coerceIn(appraisalPanel.top, appraisalPanel.bottom - 2)
            val labelAnchoredTop = labelRects[key]
                ?.let { rect -> rect.bottom + labelDrivenOffset }
                ?.coerceIn(appraisalPanel.top, appraisalPanel.bottom - 2)
            val barTop = labelAnchoredTop?.let { min(layoutTop, it) } ?: layoutTop
            val barBottom = (barTop + barHeight).coerceAtMost(appraisalPanel.bottom)
            Rect(barLeft, barTop, barRight, barBottom)
        }
    }

    private fun detectExpectedLayoutBand(
        bitmap: Bitmap,
        appraisalPanel: Rect,
        expectedRect: Rect,
        labelKey: String
    ): DetectedBarBand {
        val bestDirect = findBestMeasurementNearExpectedRect(bitmap, appraisalPanel, expectedRect)
        if (bestDirect != null) {
            val measurement = bestDirect.measurement
            return DetectedBarBand(
                top = measurement.trackRect?.top ?: bestDirect.sourceRect.top,
                bottom = measurement.trackRect?.bottom ?: bestDirect.sourceRect.bottom,
                ratio = measurement.ratio,
                value = measurement.value ?: nearestValueForLabel(labelKey, measurement.ratio),
                likelyFullDark = measurement.ratio != null && isDarkFullBar(bitmap, bestDirect.sourceRect, measurement.ratio),
                rect = measurement.trackRect ?: bestDirect.sourceRect,
                debugSummary = "layout-direct ${measurement.debugSummary}"
            )
        }

        val searchPadding = (appraisalPanel.height() * 0.05f).roundToInt().coerceAtLeast(18)
        val searchRect = Rect(
            expectedRect.left,
            (expectedRect.top - searchPadding).coerceAtLeast(appraisalPanel.top),
            expectedRect.right,
            (expectedRect.bottom + searchPadding).coerceAtMost(appraisalPanel.bottom)
        )

        val localScan = detectIvBarBands(bitmap, searchRect)
            .filter { it.ratio != null }
            .filter { isBandGeometryCompatible(it, expectedRect) }
            .minByOrNull { abs(it.rect.centerY() - expectedRect.centerY()) }
        if (localScan != null) {
            return localScan.copy(
                value = localScan.value ?: nearestValueForLabel(labelKey, localScan.ratio),
                debugSummary = "layout-scan ${localScan.debugSummary}"
            )
        }

        return DetectedBarBand(
            top = expectedRect.top,
            bottom = expectedRect.bottom,
            ratio = null,
            value = null,
            likelyFullDark = false,
            rect = expectedRect,
            debugSummary = "layout: sem medicao"
        )
    }

    private fun findBestMeasurementNearExpectedRect(
        bitmap: Bitmap,
        appraisalPanel: Rect,
        expectedRect: Rect
    ): LayoutBandCandidate? {
        val offsetStep = max(2, expectedRect.height() / 8)
        val maxOffset = max(12, expectedRect.height() / 2)
        val candidates = mutableListOf<LayoutBandCandidate>()

        for (offset in -maxOffset..maxOffset step offsetStep) {
            val shiftedTop = (expectedRect.top + offset).coerceIn(appraisalPanel.top, appraisalPanel.bottom - 2)
            val shiftedBottom = (shiftedTop + expectedRect.height()).coerceAtMost(appraisalPanel.bottom)
            if (shiftedBottom - shiftedTop < 8) continue
            val shiftedRect = Rect(expectedRect.left, shiftedTop, expectedRect.right, shiftedBottom)
            val measurement = detectBarMeasurement(bitmap, shiftedRect) ?: continue
            val trackRect = measurement.trackRect ?: shiftedRect
            val widthDelta = abs(trackRect.width() - expectedRect.width())
            val centerDelta = abs(trackRect.centerY() - expectedRect.centerY())
            val heightPenalty = if (trackRect.height() < max(8, expectedRect.height() / 5)) 120 else 0
            val score = widthDelta + (centerDelta * 3) + heightPenalty
            candidates += LayoutBandCandidate(
                measurement = measurement,
                sourceRect = shiftedRect,
                score = score
            )
        }

        return candidates
            .filter { candidate ->
                val band = DetectedBarBand(
                    top = candidate.measurement.trackRect?.top ?: candidate.sourceRect.top,
                    bottom = candidate.measurement.trackRect?.bottom ?: candidate.sourceRect.bottom,
                    ratio = candidate.measurement.ratio,
                    value = candidate.measurement.value,
                    rect = candidate.measurement.trackRect ?: candidate.sourceRect
                )
                isBandGeometryCompatible(band, expectedRect)
            }
            .minByOrNull { it.score }
    }

    private fun isBandGeometryCompatible(
        band: DetectedBarBand,
        expectedRect: Rect
    ): Boolean {
        val candidateRect = band.rect
        val widthRatio = candidateRect.width().toFloat() / expectedRect.width().toFloat().coerceAtLeast(1f)
        val leftDelta = abs(candidateRect.left - expectedRect.left)
        val rightDelta = abs(candidateRect.right - expectedRect.right)
        val centerDelta = abs(candidateRect.centerY() - expectedRect.centerY())
        val minHeight = max(10, expectedRect.height() / 5)

        return widthRatio in 0.82f..1.18f &&
            leftDelta <= max(18, expectedRect.width() / 10) &&
            rightDelta <= max(18, expectedRect.width() / 10) &&
            centerDelta <= max(34, expectedRect.height()) &&
            candidateRect.height() >= minHeight
    }

    private fun detectIvBarBands(bitmap: Bitmap, scanRect: Rect): List<DetectedBarBand> {
        val candidateRows = mutableListOf<Int>()
        for (y in scanRect.top until scanRect.bottom) {
            val ratio = detectBarFillRatioForRow(bitmap, scanRect, y)
            if (ratio != null) {
                candidateRows += y
            }
        }

        if (candidateRows.isEmpty()) return emptyList()

        val groups = mutableListOf<MutableList<Int>>()
        candidateRows.forEach { row ->
            val current = groups.lastOrNull()
            if (current == null || row - current.last() > 6) {
                groups += mutableListOf(row)
            } else {
                current += row
            }
        }

        return groups
            .filter { it.size >= 2 }
            .map { rows ->
                val top = rows.first().coerceAtLeast(scanRect.top)
                val bottom = (rows.last() + 1).coerceAtMost(scanRect.bottom)
                val rect = Rect(scanRect.left, top, scanRect.right, bottom)
                val measurement = detectBarMeasurement(bitmap, rect)
                val ratio = measurement?.ratio
                DetectedBarBand(
                    top = measurement?.trackRect?.top ?: top,
                    bottom = measurement?.trackRect?.bottom ?: bottom,
                    ratio = ratio,
                    value = measurement?.value,
                    likelyFullDark = ratio != null && isDarkFullBar(bitmap, rect, ratio),
                    rect = measurement?.trackRect ?: rect,
                    debugSummary = measurement?.debugSummary ?: "scan: sem medicao"
                )
            }
            .sortedBy { it.top }
            .take(3)
    }

    private fun detectIvAppraisalPanel(bitmap: Bitmap): Rect? {
        val searchRect = normalizedBitmapRect(
            bitmap,
            minX = 0.03f,
            maxX = 0.58f,
            minY = 0.62f,
            maxY = 0.95f
        )

        var minX = Int.MAX_VALUE
        var minY = Int.MAX_VALUE
        var maxX = Int.MIN_VALUE
        var maxY = Int.MIN_VALUE
        var hitCount = 0

        for (y in searchRect.top until searchRect.bottom) {
            for (x in searchRect.left until searchRect.right) {
                if (isAppraisalPanelPixel(bitmap.getPixel(x, y))) {
                    hitCount++
                    if (x < minX) minX = x
                    if (y < minY) minY = y
                    if (x > maxX) maxX = x
                    if (y > maxY) maxY = y
                }
            }
        }

        if (hitCount < 350 || minX == Int.MAX_VALUE) return null

        val rect = Rect(
            (minX - bitmap.width * 0.01f).roundToInt().coerceAtLeast(0),
            (minY - bitmap.height * 0.01f).roundToInt().coerceAtLeast(0),
            (maxX + bitmap.width * 0.01f).roundToInt().coerceAtMost(bitmap.width),
            (maxY + bitmap.height * 0.01f).roundToInt().coerceAtMost(bitmap.height)
        )

        return if (rect.width() >= bitmap.width * 0.18f && rect.height() >= bitmap.height * 0.10f) rect else null
    }

    private fun detectBarFillRatio(bitmap: Bitmap, rect: Rect): Float? {
        return detectBarMeasurement(bitmap, rect)?.ratio
    }

    private fun detectBarMeasurement(bitmap: Bitmap, rect: Rect): BarMeasurement? {
        if (rect.width() <= 1 || rect.height() <= 1) return null
        val safeTop = rect.top.coerceIn(0, bitmap.height - 1)
        val safeBottom = rect.bottom.coerceIn(safeTop + 1, bitmap.height)
        val safeLeft = rect.left.coerceIn(0, bitmap.width - 1)
        val safeRight = rect.right.coerceIn(safeLeft + 1, bitmap.width)
        if (safeBottom <= safeTop || safeRight <= safeLeft) return null

        val safeRect = Rect(safeLeft, safeTop, safeRight, safeBottom)
        detectDiscreteIvMeasurement(bitmap, safeRect)?.let { discrete ->
            return discrete
        }

        val sampleRows = listOf(
            safeRect.top + safeRect.height() / 4,
            safeRect.top + safeRect.height() / 2 - safeRect.height() / 8,
            safeRect.top + safeRect.height() / 2,
            safeRect.top + safeRect.height() / 3,
            safeRect.top + (safeRect.height() * 2 / 3),
            safeRect.top + safeRect.height() * 3 / 4
        ).map { it.coerceIn(safeRect.top, safeRect.bottom - 1) }

        val aggregatedMetrics = detectBarColumnMetricsByColumns(bitmap, safeRect)
        val aggregatedRatio = aggregatedMetrics?.ratio
        val rowRatios = sampleRows.mapNotNull { y ->
            detectBarFillRatioForRow(bitmap, safeRect, y)
        }

        if (aggregatedRatio == null && rowRatios.isEmpty()) return null
        if (rowRatios.isEmpty()) {
            val tightenedTrackRect = aggregatedMetrics?.toTrackRect(safeRect)?.let { trackRect ->
                tightenBarTrackRect(bitmap, trackRect)
            } ?: safeRect
            return BarMeasurement(
                ratio = aggregatedRatio?.let(::calibrateObservedIvRatio),
                value = null,
                trackRect = tightenedTrackRect,
                debugSummary = "fallback aggRatio=${aggregatedRatio?.formatDebug() ?: "-"} trackW=${tightenedTrackRect.width()} trackH=${tightenedTrackRect.height()}"
            )
        }
        val sorted = rowRatios.sorted()
        val median = sorted[sorted.size / 2]
        val upperQuartile = sorted[(sorted.size * 3) / 4]
        val maxRatio = sorted.last()

        val observedRatio = when {
            aggregatedRatio != null -> {
                val clampedAggregate = min(aggregatedRatio, maxRatio * 1.02f)
                maxOf(
                    clampedAggregate,
                    median * 0.98f,
                    upperQuartile * 0.96f
                )
            }
            else -> maxOf(median, upperQuartile * 0.97f)
        }.coerceIn(0f, 1f)
        val tightenedTrackRect = aggregatedMetrics?.toTrackRect(safeRect)?.let { trackRect ->
            tightenBarTrackRect(bitmap, trackRect)
        } ?: safeRect
        return BarMeasurement(
            ratio = calibrateObservedIvRatio(observedRatio),
            value = null,
            trackRect = tightenedTrackRect,
            debugSummary = "fallback rows med=${median.formatDebug()} uq=${upperQuartile.formatDebug()} max=${maxRatio.formatDebug()} agg=${aggregatedRatio?.formatDebug() ?: "-"} obs=${observedRatio.formatDebug()} trackW=${tightenedTrackRect.width()} trackH=${tightenedTrackRect.height()}"
        )
    }

    private fun detectDiscreteIvMeasurement(bitmap: Bitmap, rect: Rect): BarMeasurement? {
        if (rect.width() < 30 || rect.height() < 6) return null

        val bandTop = (rect.top + rect.height() * 0.24f).roundToInt().coerceIn(rect.top, rect.bottom - 2)
        val bandBottom = (rect.bottom - rect.height() * 0.24f).roundToInt().coerceIn(bandTop + 2, rect.bottom)
        val measurementRect = Rect(rect.left, bandTop, rect.right, bandBottom)
        val minDarkHits = max(1, measurementRect.height() / 7)
        val darkColumns = BooleanArray(measurementRect.width())
        val softFillColumns = BooleanArray(measurementRect.width())
        val fillColumns = BooleanArray(measurementRect.width())
        val separatorColumns = BooleanArray(measurementRect.width())
        val trackColumns = BooleanArray(measurementRect.width())

        for (column in 0 until measurementRect.width()) {
            val x = measurementRect.left + column
            var darkHits = 0
            var warmHits = 0
            var separatorHits = 0
            var trackHits = 0
            for (y in measurementRect.top until measurementRect.bottom) {
                val color = bitmap.getPixel(x, y)
                if (isIvBarMeasuredFillPixel(color)) {
                    darkHits++
                    trackHits++
                } else if (isIvBarWarmTailPixel(color)) {
                    warmHits++
                    trackHits++
                } else if (isIvBarSeparatorPixel(color)) {
                    separatorHits++
                    trackHits++
                } else if (isIvBarTrackPixel(color)) {
                    trackHits++
                }
            }
            if (darkHits >= minDarkHits) {
                darkColumns[column] = true
            }
            if (warmHits >= max(1, measurementRect.height() / 5)) {
                softFillColumns[column] = true
            }
            if (darkColumns[column] || softFillColumns[column]) {
                fillColumns[column] = true
            }
            if (separatorHits >= 1) {
                separatorColumns[column] = true
            }
            if (trackHits >= max(1, measurementRect.height() / 3)) {
                trackColumns[column] = true
            }
        }

        if (darkColumns.count { it } < max(2, measurementRect.width() / 80)) {
            val sampleRows = listOf(
                measurementRect.top + measurementRect.height() / 4,
                measurementRect.top + measurementRect.height() / 2,
                measurementRect.top + measurementRect.height() * 3 / 4
            ).map { it.coerceIn(measurementRect.top, measurementRect.bottom - 1) }
            for (column in 0 until measurementRect.width()) {
                val x = measurementRect.left + column
                if (sampleRows.any { y ->
                        val color = bitmap.getPixel(x, y)
                        isIvBarMeasuredFillPixel(color) || isIvBarWarmTailPixel(color)
                    }) {
                    darkColumns[column] = true
                    fillColumns[column] = true
                }
            }
        }

        val darkCount = darkColumns.count { it }
        val softCount = softFillColumns.count { it }
        val fillCount = fillColumns.count { it }
        val trackCount = trackColumns.count { it }
        if (darkCount < max(2, measurementRect.width() / 80)) {
            return if (trackCount >= max(12, measurementRect.width() / 3) && softCount <= max(4, measurementRect.width() / 20)) {
                BarMeasurement(
                    ratio = 0f,
                    value = 0,
                    trackRect = measurementRect,
                    debugSummary = "discrete-zero trackWidthPx=${measurementRect.width()} trackColumns=$trackCount darkColumns=$darkCount softColumns=$softCount bandH=${measurementRect.height()}"
                )
            } else {
                null
            }
        }

        var lastDarkColumn = -1
        var lastFillColumn = -1
        var started = false
        var separatorBudget = max(4, measurementRect.width() / 60)
        var emptyBudget = max(3, measurementRect.width() / 45)

        for (column in 0 until measurementRect.width()) {
            if (fillColumns[column]) {
                if (darkColumns[column]) {
                    lastDarkColumn = column
                }
                lastFillColumn = column
                separatorBudget = max(4, measurementRect.width() / 60)
                emptyBudget = max(3, measurementRect.width() / 45)
                started = true
            } else if (darkColumns[column]) {
                lastDarkColumn = column
                lastFillColumn = column
                separatorBudget = max(4, measurementRect.width() / 60)
                emptyBudget = max(3, measurementRect.width() / 45)
                started = true
            } else if (started && softFillColumns[column]) {
                lastFillColumn = column
                separatorBudget = max(4, measurementRect.width() / 60)
                emptyBudget = max(3, measurementRect.width() / 45)
            } else if (started && separatorColumns[column] && separatorBudget > 0) {
                separatorBudget--
            } else if (started && emptyBudget > 0) {
                emptyBudget--
            } else if (started) {
                break
            }
        }

        if (lastFillColumn < 0) return null

        val fillWidthPx = (lastFillColumn + 1).coerceIn(0, measurementRect.width())
        val ratio = (fillWidthPx.toFloat() / measurementRect.width().toFloat()).coerceIn(0f, 1f)
        val stepWidth = measurementRect.width().toFloat() / 15f
        val rawIv = if (stepWidth > 0f) fillWidthPx.toFloat() / stepWidth else 0f
        if (measurementRect.height() <= 4 && rawIv <= 5.6f) {
            return BarMeasurement(
                ratio = 0f,
                value = 0,
                trackRect = measurementRect,
                debugSummary = "discrete-zero thinBand fillWidthPx=$fillWidthPx trackWidthPx=${measurementRect.width()} darkColumns=$darkCount softColumns=$softCount rawIv=${rawIv.formatDebug()} bandH=${measurementRect.height()}"
            )
        }
        val value = when {
            isDarkFullBar(bitmap, measurementRect, ratio) -> 15
            stepWidth <= 0f -> null
            else -> (rawIv + 0.25f).roundToInt().coerceIn(0, 15)
        }

        return BarMeasurement(
            ratio = ratio,
            value = value,
            trackRect = measurementRect,
            debugSummary = "discrete fillWidthPx=$fillWidthPx trackWidthPx=${measurementRect.width()} darkColumns=$darkCount softColumns=$softCount fillColumns=$fillCount lastDarkColumn=$lastDarkColumn lastFillColumn=$lastFillColumn rawIv=${rawIv.formatDebug()} iv=${value ?: "-"} bandH=${measurementRect.height()}"
        )
    }

    private fun detectBarColumnMetricsByColumns(bitmap: Bitmap, rect: Rect): BarColumnMetrics? {
        if (rect.width() <= 1 || rect.height() <= 1) return null

        val minFilledPixels = max(1, rect.height() / 6)
        val minTrackPixels = max(2, rect.height() / 4)
        val isFilledColumn = BooleanArray(rect.width())
        val isSeparatorColumn = BooleanArray(rect.width())
        val isTrackColumn = BooleanArray(rect.width())

        for (column in 0 until rect.width()) {
            val x = rect.left + column
            var filledHits = 0
            var separatorHits = 0
            var trackHits = 0

            for (scanY in rect.top until rect.bottom) {
                val color = bitmap.getPixel(x, scanY)
                when {
                    isIvBarCoreFillPixel(color) -> {
                        filledHits++
                        trackHits++
                    }
                    isIvBarSeparatorPixel(color) -> {
                        separatorHits++
                        trackHits++
                    }
                    isIvBarTrackPixel(color) || isIvBarWarmTailPixel(color) -> {
                        trackHits++
                    }
                }
            }

            if (filledHits >= minFilledPixels) {
                isFilledColumn[column] = true
            }
            if (separatorHits >= 1) {
                isSeparatorColumn[column] = true
            }
            if (trackHits >= minTrackPixels) {
                isTrackColumn[column] = true
            }
        }

        return analyzeBarColumns(rect.width(), isFilledColumn, isSeparatorColumn, isTrackColumn)
    }

    private fun nearestValueForLabel(labelKey: String, ratio: Float?): Int? {
        val barType = when (labelKey) {
            "ATTACK" -> IvBarType.ATTACK
            "DEFENSE" -> IvBarType.DEFENSE
            else -> IvBarType.STAMINA
        }
        return IvCombinationTable.nearestValueFromRatio(ratio, barType)
    }

    private fun resolveDetectedBandValue(band: DetectedBarBand?, barType: IvBarType): Int? {
        if (band == null) return null
        if (band.likelyFullDark && (band.ratio ?: 0f) >= 0.84f) {
            return 15
        }
        return band.value ?: IvCombinationTable.nearestValueFromRatio(band.ratio, barType)
    }

    private fun calibrateObservedIvRatio(observedRatio: Float): Float {
        val clamped = observedRatio.coerceIn(0f, 1f)
        return when {
            clamped >= 0.93f -> min(1f, clamped + 0.04f)
            clamped >= 0.60f -> (clamped * 0.97f).coerceIn(0f, 1f)
            clamped >= 0.18f -> (clamped * 0.94f - 0.005f).coerceIn(0f, 1f)
            else -> clamped
        }
    }

    private fun detectBarFillRatioForRow(bitmap: Bitmap, rect: Rect, y: Int): Float? {
        val isFilledColumn = BooleanArray(rect.width())
        val isSeparatorColumn = BooleanArray(rect.width())
        val isTrackColumn = BooleanArray(rect.width())

        for (column in 0 until rect.width()) {
            val x = rect.left + column
            val color = bitmap.getPixel(x, y)
            if (isIvBarCoreFillPixel(color)) {
                isFilledColumn[column] = true
            } else if (isIvBarSeparatorPixel(color)) {
                isSeparatorColumn[column] = true
                isTrackColumn[column] = true
            } else if (isIvBarTrackPixel(color) || isIvBarWarmTailPixel(color)) {
                isTrackColumn[column] = true
            }
        }

        return analyzeBarColumns(rect.width(), isFilledColumn, isSeparatorColumn, isTrackColumn)?.ratio
    }

    private fun analyzeBarColumns(
        rectWidth: Int,
        isFilledColumn: BooleanArray,
        isSeparatorColumn: BooleanArray,
        isTrackColumn: BooleanArray
    ): BarColumnMetrics? {
        val filledCount = isFilledColumn.count { it }
        if (filledCount < max(1, rectWidth / 24)) return null

        val trackGroups = mutableListOf<IntRange>()
        val maxGap = max(2, rectWidth / 42)
        var start = -1
        var lastSeen = -1
        var gap = 0

        for (column in 0 until isTrackColumn.size) {
            val isTrackLike = isTrackColumn[column] || isFilledColumn[column]
            when {
                isTrackLike && start == -1 -> {
                    start = column
                    lastSeen = column
                    gap = 0
                }
                isTrackLike -> {
                    lastSeen = column
                    gap = 0
                }
                start != -1 && gap < maxGap -> {
                    gap++
                }
                start != -1 -> {
                    trackGroups += (start..lastSeen)
                    start = -1
                    lastSeen = -1
                    gap = 0
                }
            }
        }
        if (start != -1 && lastSeen != -1) {
            trackGroups += (start..lastSeen)
        }
        if (trackGroups.isEmpty()) return null

        val selectedGroup = trackGroups.maxByOrNull { group ->
            val width = group.last - group.first + 1
            val filledInside = (group.first..group.last).count { isFilledColumn[it] }
            (filledInside * 3) + width
        } ?: return null

        val firstFilled = (selectedGroup.first..selectedGroup.last).firstOrNull { isFilledColumn[it] } ?: return null
        var lastContinuousFilled = firstFilled
        var separatorBudget = max(2, (selectedGroup.last - selectedGroup.first + 1) / 24)
        var emptyBudget = max(1, (selectedGroup.last - selectedGroup.first + 1) / 40)
        var started = false

        for (column in firstFilled..selectedGroup.last) {
            if (isFilledColumn[column]) {
                lastContinuousFilled = column
                separatorBudget = max(2, (selectedGroup.last - selectedGroup.first + 1) / 24)
                emptyBudget = max(1, (selectedGroup.last - selectedGroup.first + 1) / 40)
                started = true
            } else if (started && isSeparatorColumn[column] && separatorBudget > 0) {
                separatorBudget--
            } else if (started && emptyBudget > 0) {
                emptyBudget--
            } else if (started) {
                break
            }
        }

        if (lastContinuousFilled <= firstFilled) return null

        val detectedTrackWidth = (selectedGroup.last - selectedGroup.first + 1).toFloat().coerceAtLeast(1f)
        val effectiveWidth = (lastContinuousFilled - firstFilled + 1).toFloat()
        val ratio = (effectiveWidth / detectedTrackWidth).coerceIn(0f, 1f)
        if (ratio < 0.03f) return null

        return BarColumnMetrics(
            ratio = ratio,
            trackStart = selectedGroup.first,
            trackEnd = selectedGroup.last
        )
    }

    private fun BarColumnMetrics.toTrackRect(baseRect: Rect): Rect {
        val left = (baseRect.left + trackStart).coerceIn(baseRect.left, baseRect.right - 1)
        val right = (baseRect.left + trackEnd + 1).coerceIn(left + 1, baseRect.right)
        return Rect(left, baseRect.top, right, baseRect.bottom)
    }

    private fun tightenBarTrackRect(bitmap: Bitmap, rect: Rect): Rect {
        if (rect.width() <= 1 || rect.height() <= 1) return rect

        val minTrackPixels = max(4, rect.width() / 7)
        val candidateRows = mutableListOf<Int>()

        for (y in rect.top until rect.bottom) {
            var trackHits = 0
            for (x in rect.left until rect.right) {
                val color = bitmap.getPixel(x, y)
                if (
                    isIvBarCoreFillPixel(color) ||
                    isIvBarSeparatorPixel(color) ||
                    isIvBarTrackPixel(color) ||
                    isIvBarWarmTailPixel(color)
                ) {
                    trackHits++
                }
            }
            if (trackHits >= minTrackPixels) {
                candidateRows += y
            }
        }

        if (candidateRows.isEmpty()) return rect

        val groups = mutableListOf<MutableList<Int>>()
        candidateRows.forEach { row ->
            val current = groups.lastOrNull()
            if (current == null || row - current.last() > 1) {
                groups += mutableListOf(row)
            } else {
                current += row
            }
        }

        val selectedGroup = groups.maxByOrNull { group -> group.size } ?: return rect
        val top = selectedGroup.first().coerceIn(rect.top, rect.bottom - 1)
        val bottom = (selectedGroup.last() + 1).coerceIn(top + 1, rect.bottom)
        return Rect(rect.left, top, rect.right, bottom)
    }

    private fun isIvBarCoreFillPixel(colorInt: Int): Boolean {
        val hsv = FloatArray(3)
        Color.colorToHSV(colorInt, hsv)
        val hue = hsv[0]
        val saturation = hsv[1]
        val value = hsv[2]

        val isOrangeRed = (hue in 0f..45f) || (hue in 340f..360f)
        return isOrangeRed && saturation >= 0.22f && value in 0.24f..0.88f
    }

    private fun isIvBarMeasuredFillPixel(colorInt: Int): Boolean {
        val hsv = FloatArray(3)
        Color.colorToHSV(colorInt, hsv)
        val hue = hsv[0]
        val saturation = hsv[1]
        val value = hsv[2]

        val isOrangeRed = (hue in 0f..45f) || (hue in 340f..360f)
        return isOrangeRed && saturation >= 0.18f && value in 0.22f..0.88f
    }

    private fun isIvBarWarmTailPixel(colorInt: Int): Boolean {
        val hsv = FloatArray(3)
        Color.colorToHSV(colorInt, hsv)
        val hue = hsv[0]
        val saturation = hsv[1]
        val value = hsv[2]

        val isOrangeRed = (hue in 0f..45f) || (hue in 340f..360f)
        return isOrangeRed && saturation >= 0.18f && value > 0.86f
    }

    private fun isIvBarSeparatorPixel(colorInt: Int): Boolean {
        val hsv = FloatArray(3)
        Color.colorToHSV(colorInt, hsv)
        return hsv[1] <= 0.18f && hsv[2] >= 0.70f
    }

    private fun isIvBarTrackPixel(colorInt: Int): Boolean {
        val hsv = FloatArray(3)
        Color.colorToHSV(colorInt, hsv)
        return hsv[1] <= 0.18f && hsv[2] in 0.45f..0.88f
    }

    private fun isDarkFullBar(bitmap: Bitmap, rect: Rect, ratio: Float): Boolean {
        if (ratio < 0.82f || rect.width() < 8 || rect.height() < 2) return false
        val startX = (rect.left + rect.width() * 0.72f).roundToInt().coerceIn(rect.left, rect.right - 1)
        val endX = (rect.left + rect.width() * 0.96f).roundToInt().coerceIn(startX + 1, rect.right)
        val sampleY = ((rect.top + rect.bottom) / 2).coerceIn(0, bitmap.height - 1)
        var darkHits = 0
        var totalHits = 0
        for (x in startX until endX) {
            val hsv = FloatArray(3)
            Color.colorToHSV(bitmap.getPixel(x, sampleY), hsv)
            val isBarRed = (hsv[0] in 340f..360f || hsv[0] in 0f..25f) && hsv[1] >= 0.22f
            if (!isBarRed) continue
            totalHits++
            if (hsv[2] <= 0.88f) {
                darkHits++
            }
        }
        return totalHits >= 4 && darkHits >= max(3, (totalHits * 0.55f).roundToInt())
    }

    private fun Float.formatDebug(): String = String.format(Locale.US, "%.3f", this)

    private fun isAppraisalPanelPixel(colorInt: Int): Boolean {
        val hsv = FloatArray(3)
        Color.colorToHSV(colorInt, hsv)
        return hsv[1] <= 0.10f && hsv[2] >= 0.93f
    }

    private fun orderedLines(ocrResult: OcrResult): List<OcrTextLine> {
        return ocrResult.blocks
            .sortedWith(compareBy({ it.boundingBox?.top ?: Int.MAX_VALUE }, { it.boundingBox?.left ?: Int.MAX_VALUE }))
            .flatMap { block ->
                block.lines.sortedWith(compareBy({ it.boundingBox?.top ?: Int.MAX_VALUE }, { it.boundingBox?.left ?: Int.MAX_VALUE }))
            }
    }

    private fun hasIvContext(normalizedRaw: String, lines: List<OcrTextLine>, referenceBounds: Rect?): Boolean {
        if (ivContextKeywords.any { normalizedRaw.contains(it) }) return true

        val lowerLines = normalizedLinesInRegion(lines, 0.0f, 1.0f, 0.55f, 0.95f, referenceBounds)
        val keywordHits = lowerLines.count { line ->
            ivContextKeywords.any { keyword -> line.contains(keyword) }
        }

        return keywordHits >= 2
    }

    private fun extractIvPercent(normalizedRaw: String, lines: List<OcrTextLine>, referenceBounds: Rect?): Int? {
        val appraisalLines = normalizedLinesInRegion(lines, 0.0f, 0.55f, 0.58f, 0.95f, referenceBounds)
        val fromAppraisal = appraisalLines
            .asSequence()
            .mapNotNull { text ->
                ivPercentRegex.find(text)?.groupValues?.getOrNull(1)?.toIntOrNull()
            }
            .firstOrNull()
            ?.coerceIn(0, 100)

        return fromAppraisal
    }

    private fun normalizeText(text: String): String {
        return Normalizer.normalize(text, Normalizer.Form.NFD)
            .replace(Regex("\\p{InCombiningDiacriticalMarks}+"), "")
            .uppercase(Locale.US)
            .trim()
    }

    private fun repairMojibake(text: String): String {
        if (!text.any { it == 'Ã' || it == 'Â' || it == 'â' }) return text
        return runCatching {
            String(text.toByteArray(Charsets.ISO_8859_1), Charsets.UTF_8)
        }.getOrDefault(text)
    }

    private fun loadLegacyMoves(context: Context): Map<String, List<String>> {
        if (legacyMovesCache != null) return legacyMovesCache!!
        return try {
            val jsonString = context.assets.open(AssetPaths.GAME_CATALOGS).bufferedReader().use { it.readText() }
            val jsonObject = JSONObject(jsonString).getJSONObject("legacyMoves")
            val map = mutableMapOf<String, List<String>>()
            jsonObject.keys().forEach { key ->
                val array = jsonObject.getJSONArray(key)
                val list = mutableListOf<String>()
                for (i in 0 until array.length()) {
                    list.add(normalizeText(repairMojibake(array.getString(i))))
                }
                map[normalizeText(repairMojibake(key))] = list
            }
            legacyMovesCache = map
            map
        } catch (_: Exception) {
            emptyMap()
        }
    }

    private fun loadPokemonNames(context: Context): List<PokemonNameEntry> {
        if (pokemonNamesCache != null) return pokemonNamesCache!!
        return try {
            val list = mutableListOf<PokemonNameEntry>()

            val jsonString = context.assets.open(AssetPaths.POKEMON_NAMES).bufferedReader().use { it.readText() }
            val jsonArray = JSONArray(jsonString)
            for (i in 0 until jsonArray.length()) {
                val obj = jsonArray.getJSONObject(i)
                val aliasesJson = obj.getJSONArray("aliases")
                val aliases = mutableListOf<String>()
                for (j in 0 until aliasesJson.length()) {
                    aliases += normalizeText(aliasesJson.getString(j))
                }
                val canonicalName = obj.getString("name")
                list += PokemonNameEntry(
                    dex = obj.getInt("dex"),
                    name = canonicalName,
                    normalizedName = normalizeText(canonicalName),
                    normalizedAliases = (aliases + normalizeText(canonicalName)).distinct()
                )
            }

            val deduped = list.distinctBy { it.normalizedName }
            pokemonNamesCache = deduped
            deduped
        } catch (_: Exception) {
            emptyList()
        }
    }

    private fun analyzeColorsForSpecialBackground(bitmap: Bitmap): Boolean {
        return try {
            // Sample the visible battle/background area behind the Pokemon and
            // avoid UI chrome like the white favorite panel in the top-right.
            val region = normalizedBitmapRect(
                bitmap,
                minX = 0.05f,
                maxX = 0.80f,
                minY = 0.05f,
                maxY = 0.40f
            )
            val part = Bitmap.createBitmap(bitmap, region.left, region.top, region.width(), region.height())
            val palette = Palette.from(part).generate()
            val standardColors = listOf(
                Color.parseColor("#76c84d"),
                Color.parseColor("#509bf2"),
                Color.parseColor("#2c3e50")
            )
            val dominantColor = palette.getDominantColor(Color.TRANSPARENT)
            if (dominantColor == Color.TRANSPARENT) return false
            val hsv = FloatArray(3)
            Color.colorToHSV(dominantColor, hsv)
            val isSpecialPalette = hsv[1] >= 0.30f && hsv[2] <= 0.75f
            isSpecialPalette && standardColors.none { color -> calculateColorDistance(dominantColor, color) < 32.0 }
        } catch (_: Exception) {
            false
        }
    }

    private fun detectSpecialBackground(
        context: Context,
        normalizedRaw: String,
        lines: List<OcrTextLine>,
        bitmap: Bitmap?,
        referenceBounds: Rect?,
        detectedTypes: List<String>
    ): Pair<Boolean, BackgroundDebugInfo> {
        val topVisualLines = normalizedLinesInRegion(lines, 0.0f, 1.0f, 0.00f, 0.22f, referenceBounds)
        val bottomMetaLines = normalizedLinesInRegion(lines, 0.0f, 1.0f, 0.86f, 1.00f, referenceBounds)
        val midInfoLines = normalizedLinesInRegion(lines, 0.18f, 0.82f, 0.30f, 0.58f, referenceBounds)

        val textMatch = specialBackgroundKeywords.any { normalizedRaw.contains(it) }
        val topRegionMatch = (topVisualLines + bottomMetaLines)
            .any { normalized -> specialBackgroundKeywords.any { normalized.contains(it) } }
        val eventBadgeVisualMatch = bitmap?.let(::detectEventBadgeVisual) ?: false
        val shadowTextMatch = sequenceOf(normalizedRaw)
            .plus(midInfoLines.asSequence())
            .plus(bottomMetaLines.asSequence())
            .any { normalized ->
                normalized.contains("SHADOW") ||
                    normalized.contains("SOMBROSO")
            }
        val luckyTextMatch = sequenceOf(normalizedRaw)
            .plus(midInfoLines.asSequence())
            .plus(bottomMetaLines.asSequence())
            .any { normalized ->
                normalized.contains("SORTUDO") ||
                    normalized.contains("POKEMON SORTUDO") ||
                    normalized.contains("LUCKY POKEMON") ||
                    normalized.contains("LUCKY")
            }
        val luckyVisualMatch = bitmap?.let(::detectLuckyBackgroundVisual) ?: false
        val luckyMatch = luckyTextMatch || luckyVisualMatch
        val shinyParticleMatch = bitmap?.let(::detectShinyParticles) ?: false
        val shadowParticleMatch = bitmap?.let(::detectShadowParticles) ?: false
        val shadowTextureMatch = bitmap?.let { detectShadowTexture(context, it) } ?: false
        val referenceDebug = bitmap?.let { backgroundReferenceMatcher.debugSpecialBackground(context, it) }
        val referenceMatch = referenceDebug?.isSpecial
        val bestNormalDistance = referenceDebug?.bestNormalDistance ?: Double.MAX_VALUE
        val typeNormalReferenceMatch = referenceDebug?.bestNormalReferenceName
            ?.let { name -> matchesDetectedTypeBackground(name, detectedTypes) }
            ?: false
        val typeNormalReferenceStrongEnough = typeNormalReferenceMatch &&
            bestNormalDistance.isFinite() &&
            bestNormalDistance <= 5.80
        val normalReferenceClearlyBetter = referenceMatch == false &&
            bestNormalDistance.isFinite() &&
            bestNormalDistance <= 2.60
        val normalReferenceCompetitive = referenceMatch == false &&
            bestNormalDistance.isFinite() &&
            bestNormalDistance <= 2.78
        val strongNormalReference = referenceMatch == false &&
            normalReferenceClearlyBetter
        val colorMatch = bitmap?.let { analyzeColorsForSpecialBackground(it) } ?: false
        val referenceUnknownOrSpecial = referenceMatch != false
        val referenceVeryDifferentFromNormal = bestNormalDistance.isFinite() &&
            bestNormalDistance >= 4.25
        val referenceSpecialMatch = referenceMatch == true && !typeNormalReferenceStrongEnough
        val colorOverrideSpecial = colorMatch &&
            referenceUnknownOrSpecial &&
            referenceVeryDifferentFromNormal &&
            !typeNormalReferenceStrongEnough
        val normalReferenceBlocksWeakVisual = typeNormalReferenceStrongEnough || normalReferenceClearlyBetter
        val eventBadgeSpecialMatch = eventBadgeVisualMatch && !normalReferenceBlocksWeakVisual
        val detected = (
            !luckyMatch &&
                (textMatch || topRegionMatch || eventBadgeSpecialMatch || referenceSpecialMatch || colorOverrideSpecial)
            )
        return detected to BackgroundDebugInfo(
            textMatch = textMatch,
            topRegionMatch = topRegionMatch,
            eventBadgeVisualMatch = eventBadgeVisualMatch,
            luckyTextMatch = luckyTextMatch,
            luckyVisualMatch = luckyVisualMatch,
            shinyParticleMatch = shinyParticleMatch,
            shadowTextMatch = shadowTextMatch,
            shadowParticleMatch = shadowParticleMatch,
            shadowTextureMatch = shadowTextureMatch,
            referenceDecision = referenceMatch,
            referenceName = referenceDebug?.bestReferenceName,
            referenceDistance = referenceDebug?.bestDistance,
            specialReferenceName = referenceDebug?.bestSpecialReferenceName,
            specialReferenceDistance = referenceDebug?.bestSpecialDistance,
            colorFallbackMatch = colorMatch,
            topRegionLines = topVisualLines,
            bottomRegionLines = bottomMetaLines,
            notes = buildString {
                append("decisao final: ${if (detected) "especial" else "normal"}")
                append("; ")
                if (referenceDebug?.referenceCount == 0) append("nenhuma referencia normal carregada; ")
                if (textMatch) append("texto especial encontrado; ")
                if (topRegionMatch) append("topo/meta com marcador especial; ")
                if (eventBadgeVisualMatch) append("selo visual de evento encontrado; ")
                if (referenceMatch == true) append("matcher de referencia marcou especial; ")
                if (referenceMatch == false) append("matcher de referencia marcou normal; ")
                if (referenceMatch == null && referenceDebug?.bestDistance != null) append("matcher de referencia ficou inconclusivo; ")
                if (!referenceDebug?.bestSpecialReferenceName.isNullOrBlank()) append("melhor special=${referenceDebug?.bestSpecialReferenceName}; ")
                if (colorMatch) append("fallback de cor marcou especial; ")
                if (colorOverrideSpecial) append("fallback de cor sobrepos referencia normal; ")
                if (eventBadgeVisualMatch && normalReferenceBlocksWeakVisual) append("referencia normal bloqueou selo visual; ")
                if (typeNormalReferenceStrongEnough) append("referencia normal do tipo detectado bloqueou especial; ")
                if (luckyTextMatch) append("texto de sortudo encontrado; ")
                if (luckyVisualMatch) append("visual dourado de sortudo encontrado; ")
                if (luckyMatch) append("sortudo tratado como fundo normal; ")
                if (shinyParticleMatch) append("particulas brilhantes de shiny encontradas; ")
                if (shadowTextMatch) append("texto de sombrio encontrado; ")
                if (shadowParticleMatch) append("particulas de sombra encontradas; ")
                if (shadowTextureMatch) append("textura de sombra encontrada; ")
                if (normalReferenceClearlyBetter) append("referencia normal forte encontrada; ")
                if (normalReferenceCompetitive && !normalReferenceClearlyBetter) append("referencia normal competitiva encontrada; ")
                if (strongNormalReference) append("referencia normal forte bloqueou o fallback de cor; ")
                if (referenceMatch == null && bitmap == null) append("sem bitmap para matcher de referencia; ")
            }.trim()
        )
    }

    private fun detectEventBadgeVisual(bitmap: Bitmap): Boolean {
        return try {
            val region = normalizedBitmapRect(bitmap, 0.02f, 0.34f, 0.03f, 0.17f)
            val hsv = FloatArray(3)
            var sampled = 0
            var saturated = 0
            var darkOutline = 0
            val hueBuckets = mutableSetOf<Int>()
            var y = region.top
            while (y < region.bottom) {
                var x = region.left
                while (x < region.right) {
                    val color = bitmap.getPixel(x, y)
                    Color.colorToHSV(color, hsv)
                    val sat = hsv[1]
                    val value = hsv[2]
                    val brightness = (Color.red(color) + Color.green(color) + Color.blue(color)) / 3f
                    sampled++
                    if (sat >= 0.22f && value in 0.18f..0.95f) {
                        saturated++
                        hueBuckets += (hsv[0] / 24f).toInt()
                    }
                    if (brightness <= 96f && value <= 0.50f) {
                        darkOutline++
                    }
                    x += 2
                }
                y += 2
            }
            if (sampled == 0) return false
            val saturatedRatio = saturated.toDouble() / sampled.toDouble()
            saturatedRatio >= 0.16 &&
                darkOutline >= 45 &&
                hueBuckets.size >= 4
        } catch (_: Exception) {
            false
        }
    }

    private fun matchesDetectedTypeBackground(referenceName: String, detectedTypes: List<String>): Boolean {
        if (detectedTypes.isEmpty()) return false
        val normalizedName = referenceName.lowercase()
        return detectedTypes.any { type ->
            val normalizedType = type.lowercase()
            normalizedName.contains("details_type_bg_$normalizedType") ||
                normalizedName.contains("_$normalizedType.")
        }
    }

    private fun detectLuckyBackgroundVisual(bitmap: Bitmap): Boolean {
        return try {
            val region = normalizedBitmapRect(bitmap, 0.08f, 0.82f, 0.03f, 0.34f)
            val hsv = FloatArray(3)
            var goldenBright = 0
            var sampled = 0
            var y = region.top
            while (y < region.bottom) {
                var x = region.left
                while (x < region.right) {
                    val color = bitmap.getPixel(x, y)
                    Color.colorToHSV(color, hsv)
                    sampled++
                    val hue = hsv[0]
                    val sat = hsv[1]
                    val value = hsv[2]
                    val warmGold = ((hue in 28f..62f) || hue < 16f) && sat >= 0.22f && value >= 0.72f
                    if (warmGold) goldenBright++
                    x += 6
                }
                y += 6
            }
            sampled > 0 && (goldenBright.toDouble() / sampled.toDouble()) >= 0.20
        } catch (_: Exception) {
            false
        }
    }

    private fun detectShinyParticles(bitmap: Bitmap): Boolean {
        return try {
            val region = normalizedBitmapRect(bitmap, 0.06f, 0.90f, 0.03f, 0.34f)
            val hsv = FloatArray(3)
            var brightWhite = 0
            var sampled = 0
            var y = region.top
            while (y < region.bottom) {
                var x = region.left
                while (x < region.right) {
                    if (x < bitmap.width * 0.86f || y > bitmap.height * 0.10f) {
                        val color = bitmap.getPixel(x, y)
                        Color.colorToHSV(color, hsv)
                        sampled++
                        val nearWhite = hsv[1] <= 0.12f && hsv[2] >= 0.92f
                        if (nearWhite) brightWhite++
                    }
                    x += 8
                }
                y += 8
            }
            sampled > 0 && brightWhite in 12..180
        } catch (_: Exception) {
            false
        }
    }

    private fun detectShadowParticles(bitmap: Bitmap): Boolean {
        return try {
            val candidateRegions = listOf(
                normalizedBitmapRect(bitmap, 0.06f, 0.90f, 0.03f, 0.38f),
                normalizedBitmapRect(bitmap, 0.18f, 0.82f, 0.05f, 0.34f),
                normalizedBitmapRect(bitmap, 0.22f, 0.78f, 0.08f, 0.30f)
            )
            val hsv = FloatArray(3)
            candidateRegions.any { region ->
                var purpleDark = 0
                var sampled = 0
                var y = region.top
                while (y < region.bottom) {
                    var x = region.left
                    while (x < region.right) {
                        val color = bitmap.getPixel(x, y)
                        Color.colorToHSV(color, hsv)
                        sampled++
                        val hue = hsv[0]
                        val sat = hsv[1]
                        val value = hsv[2]
                        val shadowLike = (hue in 250f..330f) && sat >= 0.18f && value in 0.14f..0.78f
                        if (shadowLike) purpleDark++
                        x += 5
                    }
                    y += 5
                }
                sampled > 0 && (purpleDark.toDouble() / sampled.toDouble()) >= 0.07
            }
        } catch (_: Exception) {
            false
        }
    }

    private fun detectShadowTexture(context: Context, bitmap: Bitmap): Boolean {
        return try {
            val textureSignatures = loadShadowTextureSignatures(context)
            if (textureSignatures.isEmpty()) return false
            val candidates = listOf(
                normalizedBitmapRect(bitmap, 0.06f, 0.90f, 0.03f, 0.38f),
                normalizedBitmapRect(bitmap, 0.10f, 0.86f, 0.05f, 0.34f),
                normalizedBitmapRect(bitmap, 0.12f, 0.82f, 0.07f, 0.32f),
                normalizedBitmapRect(bitmap, 0.18f, 0.82f, 0.08f, 0.30f),
                normalizedBitmapRect(bitmap, 0.22f, 0.78f, 0.10f, 0.28f)
            )
            val bestDistance = candidates.minOfOrNull { rect ->
                val crop = Bitmap.createBitmap(bitmap, rect.left, rect.top, rect.width(), rect.height())
                try {
                    val cropSignature = createMiniSignature(crop)
                    textureSignatures.minOfOrNull { signatureDistance(cropSignature, it) } ?: Double.MAX_VALUE
                } finally {
                    if (!crop.isRecycled) crop.recycle()
                }
            } ?: Double.MAX_VALUE
            bestDistance <= 2.95
        } catch (_: Exception) {
            false
        }
    }

    private fun loadShadowTextureSignatures(context: Context): List<IntArray> {
        shadowTextureSignatures?.let { return it }
        synchronized(this) {
            shadowTextureSignatures?.let { return it }
            val loaded = buildList {
                listOf("tx_sombroso.png", "tx_sombroso_alt.png").forEach { assetName ->
                    runCatching {
                        context.assets.open(assetName).use { input ->
                            val bitmap = BitmapFactory.decodeStream(input) ?: return@use
                            try {
                                add(createMiniSignature(bitmap))
                            } finally {
                                if (!bitmap.isRecycled) bitmap.recycle()
                            }
                        }
                    }.getOrNull()
                }
            }
            shadowTextureSignatures = loaded
            return loaded
        }
    }

    private fun createMiniSignature(bitmap: Bitmap): IntArray {
        val size = 8
        val scaled = Bitmap.createScaledBitmap(bitmap, size, size, true)
        val signature = IntArray(size * size * 3)
        var index = 0
        for (y in 0 until size) {
            for (x in 0 until size) {
                val color = scaled.getPixel(x, y)
                signature[index++] = ((Color.red(color) / 16f).roundToInt()).coerceIn(0, 15)
                signature[index++] = ((Color.green(color) / 16f).roundToInt()).coerceIn(0, 15)
                signature[index++] = ((Color.blue(color) / 16f).roundToInt()).coerceIn(0, 15)
            }
        }
        if (scaled != bitmap && !scaled.isRecycled) {
            scaled.recycle()
        }
        return signature
    }

    private fun signatureDistance(a: IntArray, b: IntArray): Double {
        if (a.size != b.size) return Double.MAX_VALUE
        var total = 0.0
        for (i in a.indices) {
            total += abs(a[i] - b[i]).toDouble()
        }
        return total / a.size.toDouble()
    }

    private fun detectAdventureEffect(
        context: Context,
        pokemonName: String?,
        moves: List<String>,
        normalizedRaw: String,
        lines: List<OcrTextLine>,
        referenceBounds: Rect?
    ): Pair<Boolean, AdventureEffectDebugInfo> {
        val moveAreaLines = normalizedLinesInRegion(lines, 0.0f, 1.0f, 0.62f, 0.90f, referenceBounds)
        val upperLeftBadgeLines = normalizedLinesInRegion(lines, 0.0f, 0.28f, 0.24f, 0.50f, referenceBounds)
        val catalog = GameCatalogRepository.loadAdventureEffectCatalog(context)
        val genericKeyword = sequenceOf(normalizedRaw)
            .plus(moveAreaLines.asSequence())
            .plus(upperLeftBadgeLines.asSequence())
            .firstNotNullOfOrNull { normalized ->
                adventureKeywords.firstOrNull { keyword -> normalized.contains(keyword) }
            }

        val matchedEntry = catalog.firstOrNull { entry ->
            val pokemonMatch = pokemonName?.let { name ->
                val normalizedName = normalizeText(name)
                entry.normalizedPokemonAliases.any { alias ->
                    normalizedName == alias || normalizedName.contains(alias) || alias.contains(normalizedName)
                }
            } ?: false

            val matchedMove = moves.any { extractedMove ->
                entry.normalizedMoveAliases.any { moveAlias ->
                    val distance = fuzzyMatcher.apply(extractedMove, moveAlias)
                    distance <= 2 || extractedMove.contains(moveAlias) || moveAlias.contains(extractedMove)
                }
            }

            val matchedText = (moveAreaLines + upperLeftBadgeLines).any { normalized ->
                entry.normalizedMoveAliases.any { moveAlias -> normalized.contains(moveAlias) } ||
                    entry.normalizedEffectAliases.any { effectAlias -> normalized.contains(effectAlias) }
            }

            matchedMove || matchedText || (pokemonMatch && genericKeyword != null)
        }

        val matchedMove = matchedEntry?.let { entry ->
            moves.firstOrNull { extractedMove ->
                entry.normalizedMoveAliases.any { moveAlias ->
                    val distance = fuzzyMatcher.apply(extractedMove, moveAlias)
                    distance <= 2 || extractedMove.contains(moveAlias) || moveAlias.contains(extractedMove)
                }
            } ?: entry.move
        }

        val detected = matchedEntry != null || genericKeyword != null
        return detected to AdventureEffectDebugInfo(
            moveRegionLines = moveAreaLines,
            upperBadgeLines = upperLeftBadgeLines,
            extractedMoves = moves,
            matchedKeyword = genericKeyword,
            matchedPokemon = matchedEntry?.pokemon,
            matchedMove = matchedMove,
            matchedEffectName = matchedEntry?.effectName,
            notes = when {
                matchedEntry != null -> "efeito de aventura bateu com a base local"
                genericKeyword != null -> "keyword de efeito de aventura encontrada na tela"
                else -> "nenhum efeito de aventura bateu com a base local"
            }
        )
    }

    private fun detectSize(
        normalizedRaw: String,
        lines: List<OcrTextLine>,
        referenceBounds: Rect?
    ): com.mewname.app.model.PokemonSize {
        val statsBandLines = normalizedLinesInRegion(lines, 0.12f, 0.88f, 0.34f, 0.54f, referenceBounds)
        val nameToStatsBridgeLines = normalizedLinesInRegion(lines, 0.12f, 0.88f, 0.22f, 0.38f, referenceBounds)

        val relevantLines = (nameToStatsBridgeLines + statsBandLines).filterNot {
            it.contains("XL CANDY") || it.contains("CANDY XL") ||
                it.contains("XL DOCES") || it.contains("DOCES XL") ||
                it.contains("STARDUST") || it.contains("POEIRA") ||
                it.contains("POWER UP") || it.contains("FORTALECER") ||
                it.contains("EVOLVE") || it.contains("EVOLUIR")
        }

        val hasXxl = relevantLines.any { line -> xxlKeywords.any { keyword -> line.contains(keyword) } }
        val hasXxs = relevantLines.any { line -> xxsKeywords.any { keyword -> line.contains(keyword) } }

        return when {
            hasXxl && !hasXxs -> com.mewname.app.model.PokemonSize.XXL
            hasXxs && !hasXxl -> com.mewname.app.model.PokemonSize.XXS
            else -> com.mewname.app.model.PokemonSize.NORMAL
        }
    }

    private fun detectLegacyMove(
        pokemonName: String?,
        moves: List<String>,
        lines: List<OcrTextLine>,
        legacyData: Map<String, List<String>>,
        referenceBounds: Rect?
    ): Pair<Boolean, LegacyDebugInfo> {
        val moveAreaLines = normalizedLinesInRegion(lines, 0.0f, 1.0f, 0.60f, 0.90f, referenceBounds)
        val matchedKeyword = moveAreaLines.firstNotNullOfOrNull { normalized ->
            legacyKeywords.firstOrNull { normalized.contains(it) }
        }
        if (matchedKeyword != null) {
            return true to LegacyDebugInfo(
                moveRegionLines = moveAreaLines,
                extractedMoves = moves,
                matchedKeyword = matchedKeyword,
                matchedAgainstPokemon = pokemonName,
                notes = "keyword legado encontrado na area de movimentos"
            )
        }
        val checked = checkLegacyMoves(pokemonName, moves, legacyData)
        return checked.first to LegacyDebugInfo(
            moveRegionLines = moveAreaLines,
            extractedMoves = moves,
            matchedLegacyMove = checked.second,
            matchedAgainstPokemon = checked.third,
            notes = if (checked.first) "golpe legado bateu com a base local" else "nenhum golpe legado bateu com a base local"
        )
    }

    private fun detectPokemonTypes(
        lines: List<OcrTextLine>,
        normalizedRaw: String,
        referenceBounds: Rect?
    ): Pair<List<String>, List<String>> {
        val centerStatsLines = normalizedLinesInRegion(lines, 0.22f, 0.78f, 0.34f, 0.58f, referenceBounds)
        val candidateTexts = buildList {
            addAll(centerStatsLines)
            add(normalizedRaw)
        }

        val detected = linkedSetOf<String>()
        candidateTexts.forEach { text ->
            pokemonTypeAliases.forEach { (canonical, aliases) ->
                if (aliases.any { alias -> text.contains(alias) }) {
                    detected += canonical
                }
            }
        }

        return detected.take(2) to centerStatsLines
    }

    private fun detectFavoriteStarFilled(bitmap: Bitmap): Boolean {
        val ratio = measureFavoriteStarYellowRatio(bitmap) ?: return false
        return ratio >= 0.16
    }

    private fun measureFavoriteStarYellowRatio(bitmap: Bitmap): Double? {
        return try {
            val candidateRects = listOf(
                normalizedBitmapRect(bitmap, 0.84f, 0.96f, 0.03f, 0.13f),
                normalizedBitmapRect(bitmap, 0.85f, 0.97f, 0.05f, 0.16f),
                normalizedBitmapRect(bitmap, 0.86f, 0.98f, 0.06f, 0.18f),
                normalizedBitmapRect(bitmap, 0.82f, 0.98f, 0.00f, 0.18f)
            )
            candidateRects.maxOfOrNull { rect ->
                measureFavoriteYellowRatio(bitmap, rect)
            }
        } catch (_: Exception) {
            null
        }
    }

    private fun measureFavoriteYellowRatio(bitmap: Bitmap, rect: Rect): Double {
        val hsv = FloatArray(3)
        var yellow = 0
        var chromaSampled = 0
        var sampled = 0
        var y = rect.top
        while (y < rect.bottom) {
            var x = rect.left
            while (x < rect.right) {
                val color = bitmap.getPixel(x, y)
                Color.colorToHSV(color, hsv)
                sampled++
                val hue = hsv[0]
                val sat = hsv[1]
                val value = hsv[2]
                val isFilledYellow = hue in 35f..66f && sat >= 0.45f && value >= 0.70f
                val isChromaPixel = sat >= 0.14f || value <= 0.72f
                if (isFilledYellow) yellow++
                if (isChromaPixel) chromaSampled++
                x += 4
            }
            y += 4
        }
        if (sampled == 0) return 0.0
        val ratioAll = yellow.toDouble() / sampled.toDouble()
        val ratioChroma = if (chromaSampled == 0) 0.0 else yellow.toDouble() / chromaSampled.toDouble()
        return max(ratioAll, ratioChroma)
    }

    private fun normalizedLinesInRegion(
        lines: List<OcrTextLine>,
        minX: Float,
        maxX: Float,
        minY: Float,
        maxY: Float,
        referenceBounds: Rect? = null
    ): List<String> {
        val screenBounds = referenceBounds ?: ocrBounds(lines) ?: return emptyList()
        return lines
            .filter { line ->
                line.boundingBox?.let { rect ->
                    rect.inNormalizedRegion(screenBounds, minX, maxX, minY, maxY)
                } ?: false
            }
            .map { normalizeText(it.text) }
    }

    private fun rawLinesInRegion(
        lines: List<OcrTextLine>,
        minX: Float,
        maxX: Float,
        minY: Float,
        maxY: Float,
        referenceBounds: Rect? = null
    ): List<OcrTextLine> {
        val screenBounds = referenceBounds ?: ocrBounds(lines) ?: return emptyList()
        return lines.filter { line ->
            line.boundingBox?.let { rect ->
                rect.inNormalizedRegion(screenBounds, minX, maxX, minY, maxY)
            } ?: false
        }
    }

    private fun ocrBounds(lines: List<OcrTextLine>): Rect? {
        val rects = lines.mapNotNull { it.boundingBox }
        if (rects.isEmpty()) return null
        val left = rects.minOf { it.left }
        val top = rects.minOf { it.top }
        val right = rects.maxOf { it.right }
        val bottom = rects.maxOf { it.bottom }
        return Rect(left, top, right, bottom)
    }

    private fun Rect.inNormalizedRegion(
        screenBounds: Rect,
        minX: Float,
        maxX: Float,
        minY: Float,
        maxY: Float
    ): Boolean {
        val width = (screenBounds.width()).coerceAtLeast(1)
        val height = (screenBounds.height()).coerceAtLeast(1)
        val centerX = (exactCenterX() - screenBounds.left).toFloat() / width.toFloat()
        val centerY = (exactCenterY() - screenBounds.top).toFloat() / height.toFloat()
        return centerX in minX..maxX && centerY in minY..maxY
    }

    private fun calculateColorDistance(c1: Int, c2: Int): Double {
        val r = (Color.red(c1) - Color.red(c2)).toDouble().pow(2.0)
        val g = (Color.green(c1) - Color.green(c2)).toDouble().pow(2.0)
        val b = (Color.blue(c1) - Color.blue(c2)).toDouble().pow(2.0)
        return sqrt(r + g + b)
    }

    private fun normalizedBitmapRect(
        bitmap: Bitmap,
        minX: Float,
        maxX: Float,
        minY: Float,
        maxY: Float
    ): Rect {
        val left = (bitmap.width * minX).roundToInt().coerceIn(0, bitmap.width - 1)
        val top = (bitmap.height * minY).roundToInt().coerceIn(0, bitmap.height - 1)
        val right = (bitmap.width * maxX).roundToInt().coerceIn(left + 1, bitmap.width)
        val bottom = (bitmap.height * maxY).roundToInt().coerceIn(top + 1, bitmap.height)
        return Rect(left, top, min(right, bitmap.width), min(bottom, bitmap.height))
    }

    private fun checkLegacyMoves(
        pokemonName: String?,
        moves: List<String>,
        legacyData: Map<String, List<String>>
    ): Triple<Boolean, String?, String?> {
        val normName = pokemonName?.let { normalizeText(it) } ?: return Triple(false, null, null)
        val matchedPokemon = legacyData.keys.firstOrNull { it == normName } ?: legacyData.keys.firstOrNull {
            fuzzyMatcher.apply(normName, it) <= 1
        } ?: return Triple(false, null, null)
        val pokemonLegacyMoves = legacyData[matchedPokemon] ?: return Triple(false, null, matchedPokemon)

        val matchedMove = moves.firstOrNull { extractedMove ->
            pokemonLegacyMoves.any { legacyMove ->
                val distance = fuzzyMatcher.apply(extractedMove, legacyMove)
                distance <= 2 || extractedMove.contains(legacyMove) || legacyMove.contains(extractedMove)
            }
        }
        return Triple(matchedMove != null, matchedMove, matchedPokemon)
    }

    private fun extractMoves(lines: List<OcrTextLine>, referenceBounds: Rect?): List<String> {
        val moveRegex = Regex("""^([\p{L}' .-]+)\s+(\d{1,3})$""", RegexOption.IGNORE_CASE)
        val damageOnlyRegex = Regex("""^\d{1,3}$""")
        val moveLines = rawLinesInRegion(lines, 0.0f, 1.0f, 0.60f, 0.92f, referenceBounds)
            .map { it.text }

        val extracted = mutableListOf<String>()

        moveLines.forEachIndexed { index, line ->
            if (line.startsWith("CP", true) || line.startsWith("PC", true)) return@forEachIndexed

            val sameLineMove = moveRegex.find(line)?.groupValues?.getOrNull(1)?.let { normalizeText(it) }
            if (sameLineMove != null) {
                extracted += sameLineMove
                return@forEachIndexed
            }

            val normalizedLine = normalizeText(line)
            if (
                normalizedLine.isBlank() ||
                normalizedLine.contains("DOCES") ||
                normalizedLine.contains("CANDY") ||
                normalizedLine.contains("POEIRA") ||
                normalizedLine.contains("FORTALECER") ||
                normalizedLine.contains("TREINADOR") ||
                normalizedLine.contains("GINASIOS") ||
                normalizedLine.contains("BATALHAS")
            ) {
                return@forEachIndexed
            }

            val nextLine = moveLines.getOrNull(index + 1)?.trim().orEmpty()
            val isSplitMoveWithDamage = damageOnlyRegex.matches(nextLine)
            if (isSplitMoveWithDamage) {
                extracted += normalizedLine
            }
        }

        return extracted.distinct()
    }

    private fun extractOcrLevel(lines: List<OcrTextLine>, referenceBounds: Rect?): Double? {
        val candidateLines = rawLinesInRegion(lines, 0.0f, 1.0f, 0.0f, 0.82f, referenceBounds)
            .map { normalizeText(it.text).trim() }

        return candidateLines.firstNotNullOfOrNull { line ->
            levelRegex.matchEntire(line)?.groupValues?.getOrNull(1)?.replace(",", ".")?.toDoubleOrNull()
        }
    }

    private fun detectGender(
        text: String,
        lines: List<OcrTextLine>,
        referenceBounds: Rect?,
        pokemonName: String?,
        bitmap: Bitmap?
    ): Pair<Gender, GenderDebugInfo?> {
        if (isGenderlessPokemon(pokemonName)) {
            return Gender.GENDERLESS to GenderDebugInfo(
                detectedGender = Gender.GENDERLESS,
                notes = "pokémon sem gênero definido na base local"
            )
        }
        when {
            text.contains("♂") -> return Gender.MALE to GenderDebugInfo(detectedGender = Gender.MALE, notes = "símbolo masculino encontrado no OCR bruto")
            text.contains("♀") -> return Gender.FEMALE to GenderDebugInfo(detectedGender = Gender.FEMALE, notes = "símbolo feminino encontrado no OCR bruto")
        }

        val candidateLines = buildList {
            addAll(rawLinesInRegion(lines, 0.18f, 0.82f, 0.16f, 0.42f, referenceBounds).map { it.text })
            addAll(rawLinesInRegion(lines, 0.76f, 0.98f, 0.30f, 0.60f, referenceBounds).map { it.text })
        }.distinct()

        val normalizedCandidates = candidateLines.map(::normalizeText)
        if (candidateLines.any { it.contains("♂") } || normalizedCandidates.any { it.contains(" MACHO") || it == "MACHO" || it.endsWith(" MACHO") }) {
            return Gender.MALE to GenderDebugInfo(detectedGender = Gender.MALE, notes = "símbolo/texto masculino encontrado na área do ícone")
        }
        if (candidateLines.any { it.contains("♀") } || normalizedCandidates.any { it.contains(" FEMEA") || it == "FEMEA" || it.endsWith(" FEMEA") }) {
            return Gender.FEMALE to GenderDebugInfo(detectedGender = Gender.FEMALE, notes = "símbolo/texto feminino encontrado na área do ícone")
        }

        return bitmap?.let(::detectGenderFromIcon) ?: (Gender.UNKNOWN to null)
    }

    private fun isGenderlessPokemon(pokemonName: String?): Boolean {
        val normalized = normalizeText(pokemonName.orEmpty()).trim()
        return normalized.isNotBlank() && normalized in genderlessPokemon
    }

    private fun detectGenderFromIcon(bitmap: Bitmap): Pair<Gender, GenderDebugInfo> {
        val iconRect = normalizedBitmapRect(bitmap, 0.84f, 0.95f, 0.34f, 0.46f)
        var maleHueScore = 0.0
        var femaleHueScore = 0.0
        var upperRightInk = 0.0
        var lowerCenterInk = 0.0
        var lowerBarInk = 0.0
        val hsv = FloatArray(3)

        var y = iconRect.top
        while (y < iconRect.bottom) {
            var x = iconRect.left
            while (x < iconRect.right) {
                val color = bitmap.getPixel(x, y)
                Color.colorToHSV(color, hsv)
                val hue = hsv[0]
                val saturation = hsv[1]
                val value = hsv[2]
                val nx = (x - iconRect.left).toFloat() / iconRect.width().coerceAtLeast(1)
                val ny = (y - iconRect.top).toFloat() / iconRect.height().coerceAtLeast(1)

                if (saturation >= 0.18f && value >= 0.35f) {
                    val weight = saturation.toDouble() * value.toDouble()
                    if (hue in 175f..245f) {
                        maleHueScore += weight
                    }
                    if (hue >= 300f || hue <= 15f) {
                        femaleHueScore += weight
                    }
                }

                val brightness = (Color.red(color) + Color.green(color) + Color.blue(color)) / 3f
                val isInk = brightness <= 238f && value <= 0.96f
                if (isInk) {
                    if (nx >= 0.68f && ny <= 0.34f) {
                        upperRightInk += 1.0
                    }
                    if (nx in 0.38f..0.62f && ny >= 0.56f) {
                        lowerCenterInk += 1.0
                    }
                    if (nx in 0.26f..0.74f && ny >= 0.78f) {
                        lowerBarInk += 1.0
                    }
                }
                x += 2
            }
            y += 2
        }

        val femaleStructure = lowerCenterInk + (lowerBarInk * 1.6)
        val detected = when {
            lowerBarInk >= 3.0 && lowerCenterInk >= 3.0 && femaleStructure > upperRightInk -> Gender.FEMALE
            femaleStructure >= 7.0 && femaleStructure > upperRightInk * 1.05 -> Gender.FEMALE
            upperRightInk >= 9.0 && upperRightInk > femaleStructure * 1.8 -> Gender.MALE
            femaleHueScore >= 10.0 && femaleHueScore > maleHueScore * 1.35 -> Gender.FEMALE
            maleHueScore >= 10.0 && maleHueScore > femaleHueScore * 1.35 -> Gender.MALE
            else -> Gender.UNKNOWN
        }
        return detected to GenderDebugInfo(
            detectedGender = detected,
            iconRect = normalizeDebugRect(iconRect, bitmap),
            notes = "maleHue=${"%.2f".format(maleHueScore)} femaleHue=${"%.2f".format(femaleHueScore)} upperRightInk=${"%.1f".format(upperRightInk)} lowerCenterInk=${"%.1f".format(lowerCenterInk)} lowerBarInk=${"%.1f".format(lowerBarInk)}"
        )
    }

    private fun detectEvolutionFlags(
        normalizedRaw: String,
        lines: List<OcrTextLine>,
        pokemonName: String?,
        referenceBounds: Rect?,
        bitmap: Bitmap?
    ): Pair<Set<EvolutionFlag>, EvolutionIconDebugInfo> {
        val flags = mutableSetOf<EvolutionFlag>()

        val badgeLines = normalizedLinesInRegion(lines, 0.20f, 0.80f, 0.32f, 0.48f, referenceBounds)
        val titleLines = normalizedLinesInRegion(lines, 0.16f, 0.84f, 0.06f, 0.28f, referenceBounds)
        val centeredEvolutionLines = normalizedLinesInRegion(lines, 0.18f, 0.82f, 0.50f, 0.68f, referenceBounds)
        val dynamaxStatusLines = normalizedLinesInRegion(lines, 0.18f, 0.82f, 0.54f, 0.70f, referenceBounds)
        val lowerActionLines = normalizedLinesInRegion(lines, 0.0f, 1.0f, 0.72f, 0.98f, referenceBounds)
        val relevantText = buildList {
            add(normalizedRaw)
            addAll(titleLines)
            addAll(badgeLines)
            addAll(centeredEvolutionLines)
            addAll(dynamaxStatusLines)
            addAll(lowerActionLines)
            pokemonName?.let { add(normalizeText(it)) }
        }
        val collapsedText = relevantText.map(::collapseForKeywordMatch)

        val megaKeyword = findKeywordMatch(relevantText, collapsedText, megaKeywords)
        val gigantamaxKeyword = findKeywordMatch(relevantText, collapsedText, gigantamaxKeywords)
        val dynamaxKeyword = findKeywordMatch(relevantText, collapsedText, dynamaxKeywords)
        val maxBadgeVisualMatch = bitmap?.let(::detectMaxBadgeVisual) == true
        val hasMega = megaKeyword != null || pokemonName?.startsWith("Mega ", ignoreCase = true) == true
        val hasGigantamax = gigantamaxKeyword != null
        val hasDynamax = dynamaxKeyword != null || (maxBadgeVisualMatch && !hasGigantamax)

        if (hasMega) flags += EvolutionFlag.MEGA
        if (hasGigantamax) flags += EvolutionFlag.GIGANTAMAX
        if (hasDynamax) flags += EvolutionFlag.DYNAMAX
        val debugInfo = EvolutionIconDebugInfo(
            badgeLines = badgeLines,
            titleLines = titleLines,
            centerLines = (centeredEvolutionLines + dynamaxStatusLines).distinct(),
            actionLines = lowerActionLines,
            megaKeyword = megaKeyword,
            gigantamaxKeyword = gigantamaxKeyword,
            dynamaxKeyword = dynamaxKeyword,
            detectedFlags = flags.map { it.name },
            notes = buildList {
                if (pokemonName?.startsWith("Mega ", ignoreCase = true) == true) {
                    add("o nome do Pokémon já veio com prefixo Mega")
                }
                if (maxBadgeVisualMatch) add("selo visual de Dynamax/Gigamax encontrado")
                if (flags.isEmpty()) add("nenhum indicador textual de mega, gigantamax ou dynamax foi encontrado")
                if (badgeLines.isEmpty() && titleLines.isEmpty() && centeredEvolutionLines.isEmpty()) {
                    add("OCR não encontrou linhas úteis nas áreas de ícone/badge")
                }
            }.joinToString("; ")
        )
        return flags to debugInfo
    }

    private fun findKeywordMatch(
        texts: List<String>,
        collapsedTexts: List<String>,
        keywords: Set<String>
    ): String? {
        keywords.forEach { keyword ->
            val normalizedKeyword = normalizeText(keyword)
            val collapsedKeyword = collapseForKeywordMatch(normalizedKeyword)
            if (texts.any { it.contains(normalizedKeyword) } || collapsedTexts.any { it.contains(collapsedKeyword) }) {
                return keyword
            }
        }
        return null
    }

    private fun detectMaxBadgeVisual(bitmap: Bitmap): Boolean {
        val regions = listOf(
            normalizedBitmapRect(bitmap, 0.26f, 0.57f, 0.49f, 0.66f),
            normalizedBitmapRect(bitmap, 0.20f, 0.63f, 0.53f, 0.72f)
        )
        val hsv = FloatArray(3)
        return regions.any { rect ->
            var magentaPixels = 0
            var saturatedPixels = 0
            val stepX = (rect.width() / 80).coerceAtLeast(1)
            val stepY = (rect.height() / 80).coerceAtLeast(1)
            var total = 0

            var y = rect.top
            while (y < rect.bottom) {
                var x = rect.left
                while (x < rect.right) {
                    val color = bitmap.getPixel(x, y)
                    Color.colorToHSV(color, hsv)
                    if (hsv[1] >= 0.25f && hsv[2] >= 0.22f) {
                        saturatedPixels++
                        if (hsv[0] in 285f..345f) {
                            magentaPixels++
                        }
                    }
                    total++
                    x += stepX
                }
                y += stepY
            }

            val magentaRatio = magentaPixels / total.coerceAtLeast(1).toDouble()
            val saturatedRatio = saturatedPixels / total.coerceAtLeast(1).toDouble()
            magentaRatio >= 0.032 && saturatedRatio >= 0.075
        }
    }

    private fun collapseForKeywordMatch(text: String): String {
        return normalizeText(text).replace(" ", "").replace("-", "")
    }

    private fun detectLeague(text: String): PvpLeague? {
        val normalized = normalizeText(text)
        return when {
            normalized.contains("GREAT") || normalized.contains("GRANDE") -> PvpLeague.GREAT
            normalized.contains("ULTRA") -> PvpLeague.ULTRA
            normalized.contains("LITTLE") || normalized.contains("COPINHA") || normalized.contains("PEQUENA") -> PvpLeague.LITTLE
            else -> null
        }
    }

    private fun inferPokemonName(
        context: Context,
        lines: List<OcrTextLine>,
        extractedMoves: List<String>,
        cp: Int?,
        referenceBounds: Rect?
    ): String? {
        val footerName = inferPokemonNameFromFooter(context, lines, referenceBounds)
        if (footerName != null) return footerName

        val pokemonNames = loadPokemonNames(context)
        val candidateLines = prioritizeTopNameCandidates(lines, cp)

        for (line in candidateLines) {
            val candidate = sanitizeNameCandidate(line.text) ?: continue
            val normalizedCandidate = normalizeText(candidate)
            if (shouldSkipNameCandidate(normalizedCandidate, extractedMoves)) continue

            val exactMatch = pokemonNames.firstOrNull { entry ->
                normalizedCandidate == entry.normalizedName || normalizedCandidate in entry.normalizedAliases
            }
            if (exactMatch != null) return exactMatch.name
        }

        for (line in candidateLines) {
            val candidate = sanitizeNameCandidate(line.text) ?: continue
            val normalizedCandidate = normalizeText(candidate)
            if (shouldSkipNameCandidate(normalizedCandidate, extractedMoves)) continue

            val fuzzyMatch = pokemonNames
                .asSequence()
                .map { entry ->
                    val bestDistance = entry.normalizedAliases.minOf { alias ->
                        fuzzyMatcher.apply(normalizedCandidate, alias)
                    }
                    entry to bestDistance
                }
                .filter { (_, distance) -> distance <= 2 }
                .minByOrNull { (_, distance) -> distance }
                ?.first

            if (fuzzyMatch != null) return fuzzyMatch.name
        }

        return candidateLines
            .mapNotNull { sanitizeNameCandidate(it.text) }
            .firstOrNull()
    }

    private fun inferPokemonNameFromFooter(
        context: Context,
        lines: List<OcrTextLine>,
        referenceBounds: Rect?
    ): String? {
        val footerLines = rawLinesInRegion(lines, 0.0f, 1.0f, 0.84f, 1.0f, referenceBounds)
            .map { it.text }
        val footerText = footerLines.joinToString(" ").replace(Regex("\\s+"), " ").trim()
        val rawCandidate = footerPokemonRegex.find(footerText)?.groupValues?.getOrNull(1)
            ?: footerCaughtRegex.find(footerText)?.groupValues?.getOrNull(1)
            ?: return null

        val candidate = sanitizeNameCandidate(rawCandidate) ?: return null
        val normalizedCandidate = normalizeText(candidate)
        val pokemonNames = loadPokemonNames(context)

        return pokemonNames.firstOrNull { entry ->
            normalizedCandidate == entry.normalizedName || normalizedCandidate in entry.normalizedAliases
        }?.name ?: pokemonNames
            .asSequence()
            .map { entry ->
                val bestDistance = entry.normalizedAliases.minOf { alias ->
                    fuzzyMatcher.apply(normalizedCandidate, alias)
                }
                entry to bestDistance
            }
            .filter { (_, distance) -> distance <= 2 }
            .minByOrNull { (_, distance) -> distance }
            ?.first
            ?.name
    }

    private fun prioritizeTopNameCandidates(lines: List<OcrTextLine>, cp: Int?): List<OcrTextLine> {
        val cpLineTop = lines.firstOrNull { cp != null && cpRegex.containsMatchIn(it.text) }?.boundingBox?.top
        return lines
            .sortedWith(
                compareBy<OcrTextLine>(
                    { line ->
                        when {
                            cpLineTop != null && line.boundingBox != null -> abs(line.boundingBox.top - cpLineTop)
                            else -> Int.MAX_VALUE
                        }
                    },
                    { it.boundingBox?.top ?: Int.MAX_VALUE },
                    { it.boundingBox?.left ?: Int.MAX_VALUE }
                )
            )
            .take(12)
    }

    private fun sanitizeNameCandidate(text: String): String? {
        val cleaned = text
            .replace(Regex("[^\\p{L}' -]"), " ")
            .replace(Regex("\\s+"), " ")
            .trim()
        if (cleaned.length !in 3..18) return null
        return cleaned
    }

    private fun shouldSkipNameCandidate(normalizedCandidate: String, extractedMoves: List<String>): Boolean {
        return generalBlacklist.any { normalizedCandidate == it || normalizedCandidate.contains(it) } ||
            pokemonTypes.contains(normalizedCandidate) ||
            extractedMoves.any { it == normalizedCandidate }
    }
}
