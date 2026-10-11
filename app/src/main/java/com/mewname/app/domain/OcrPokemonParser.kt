package com.mewname.app.domain

import com.mewname.app.domain.OcrTextGeometry.normalizeDebugRect
import com.mewname.app.domain.OcrTextGeometry.orderedLines
import com.mewname.app.domain.OcrTextGeometry.normalizeText
import com.mewname.app.domain.OcrTextGeometry.repairMojibake
import com.mewname.app.domain.OcrTextGeometry.normalizedLinesInRegion
import com.mewname.app.domain.OcrTextGeometry.rawLinesInRegion
import com.mewname.app.domain.OcrTextGeometry.normalizedBitmapRect

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.Rect
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
import com.mewname.app.model.PvpLeagueRankInfo
import com.mewname.app.model.PvpSpeciesRankInfo
import com.mewname.app.model.SizeDebugInfo
import com.mewname.app.model.UniqueFormDebugInfo
import com.mewname.app.model.VivillonPattern
import com.mewname.app.ocr.OcrResult
import com.mewname.app.ocr.OcrTextLine
import org.json.JSONArray
import org.apache.commons.text.similarity.LevenshteinDistance
import org.json.JSONObject
import java.util.Locale
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min

class OcrPokemonParser {
    private val moveRegionTop = 0.52f
    private val moveRegionBottom = 0.98f

    private enum class EvolutionStageRank(val rank: Int) {
        BABY(0),
        BASIC(1),
        STAGE1(2),
        STAGE2(3)
    }

    private val ivCombinationRegex = Regex("""(\d{1,2})\s*[/|\\-]\s*(\d{1,2})\s*[/|\\-]\s*(\d{1,2})""")
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

    private val adventureKeywords = setOf("ADVENTURE EFFECT", "EFEITO AVENTURA")

    private val legacyKeywords = setOf(
        "LEGACY", "ANTIGO", "ELITE", "MOVIMENTO ANTIGO", "ATAQUE ANTIGO"
    )
    private val megaKeywords = setOf(
        "MEGA", "MEGAEVOLVE", "MEGA EVOLVE", "MEGAEVOLUIR", "MEGA EVOLUIR",
        "MEGA ENERGY", "MEGAENERGY", "MEGA ENERGIA", "MEGAENERGIA"
    )
    private val megaEligiblePokemon = setOf(
        "VENUSAUR", "CHARIZARD", "BLASTOISE", "BEEDRILL", "PIDGEOT", "ALAKAZAM",
        "SLOWBRO", "GENGAR", "KANGASKHAN", "PINSIR", "GYARADOS", "AERODACTYL",
        "AMPHAROS", "STEELIX", "SCIZOR", "HERACROSS", "HOUNDOOM", "TYRANITAR",
        "SCEPTILE", "BLAZIKEN", "SWAMPERT", "GARDEVOIR", "SABLEYE", "MAWILE",
        "AGGRON", "MEDICHAM", "MANECTRIC", "SHARPEDO", "CAMERUPT", "ALTARIA",
        "BANETTE", "ABSOL", "GLALIE", "SALAMENCE", "METAGROSS", "LATIAS",
        "LATIOS", "RAYQUAZA", "LOPUNNY", "GARCHOMP", "LUCARIO", "ABOMASNOW",
        "GALLADE", "AUDINO", "DIANCIE"
    )
    private val gigantamaxKeywords = setOf(
        "GIGANTAMAX", "GIGAMAX", "GIGAMA", "GIGAM",
        "GIGA MAX", "G-MAX", "G MAX"
    )
    private val dynamaxKeywords = setOf(
        "DYNAMAX", "DINAMAX", "DINA MAX", "D1NAMAX", "DYN4MAX", "DYNAMX", "DINAMX",
        "DYNAM", "DINAMA", "DINAM",
        "D-MAX", "D MAX", "MAX BATTLE", "BATALHA MAX"
    )

    private val xxlKeywords = setOf("XXL")
    private val xlKeywords = setOf("XL")
    private val xxsKeywords = setOf("XXS")
    private val xsKeywords = setOf("XS")
    private val regionalFormPrefixes = listOf("ALOLAN ", "GALARIAN ", "HISUIAN ", "PALDEAN ")
    private val regionalLegacyTypeHints = mapOf(
        "ALOLAN SANDSLASH" to setOf("ICE", "STEEL"),
        "ALOLAN MAROWAK" to setOf("FIRE", "GHOST"),
        "ALOLAN EXEGGUTOR" to setOf("GRASS", "DRAGON"),
        "GALARIAN RAPIDASH" to setOf("PSYCHIC", "FAIRY")
    )
    private var legacyMovesCache: Map<String, List<String>>? = null
    private var evolutionStageCache: Map<String, EvolutionStageRank>? = null
    private val fuzzyMatcher = LevenshteinDistance()
    private val rankCalculator = PvpRankCalculator()
    private val familySuggester = PokemonFamilySuggester()
    private val nameReader = OcrPokemonNameReader(familySuggester)
    private val ivBarReader = OcrIvBarReader()
    private val visualReader = OcrVisualAttributeReader()
    private val masterIvBadgeCatalog = MasterIvBadgeCatalog()
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
        var previousStep = "Preparando catálogos"
        var previousTime = android.os.SystemClock.elapsedRealtime()
        fun step(label: String) {
            if (Thread.currentThread().isInterrupted) throw java.util.concurrent.CancellationException("Reading cancelled")
            val now = android.os.SystemClock.elapsedRealtime()
            android.util.Log.d("MewNameReadTiming", "$previousStep: ${now - previousTime}ms")
            previousStep = label
            previousTime = now
            onAnalysisStep?.invoke(label)
        }
        IvCombinationTable.ensureLoaded(context)
        val referenceBounds = bitmap?.let { Rect(0, 0, it.width, it.height) }

        val normalizedRaw = rawText.replace("\n", " ")
        val normUpper = normalizeText(normalizedRaw)
        val hasIvContext = OcrNumericReader.hasIvContext(normUpper, orderedLines, referenceBounds)

        step("Identificando IVs e CP")

        val cp = OcrNumericReader.extractCp(normalizedRaw, orderedLines, referenceBounds)
        var ivPercent = OcrNumericReader.extractIvPercent(orderedLines, referenceBounds)
        val ivPercentFromOcr = ivPercent

        val ivComb = if (hasIvContext) ivCombinationRegex.find(normalizedRaw) else null
        val rawAtt = ivComb?.groupValues?.getOrNull(1)?.toIntOrNull()
        val rawDef = ivComb?.groupValues?.getOrNull(2)?.toIntOrNull()
        val rawSta = ivComb?.groupValues?.getOrNull(3)?.toIntOrNull()
        val hasValidIvCombination = listOf(rawAtt, rawDef, rawSta).all { value ->
            value != null && value in 0..15
        }
        var att = if (hasValidIvCombination) rawAtt else null
        var def = if (hasValidIvCombination) rawDef else null
        var sta = if (hasValidIvCombination) rawSta else null

        val ivBarDetection = if (hasIvContext) bitmap?.let { ivBarReader.detectIvBars(it, orderedLines) } else null
        val canUseIvBars = hasIvContext && (ivBarDetection?.isReliable == true)
        val barSnappedIv = if (canUseIvBars) {
            IvCombinationTable.nearestFromRatios(
                ivBarDetection?.attackRatio,
                ivBarDetection?.defenseRatio,
                ivBarDetection?.staminaRatio,
                ivPercentHint = ivPercent
            )
        } else {
            null
        }
        if (canUseIvBars && (att == null || def == null || sta == null)) {
            val snapped = barSnappedIv
            att = att ?: ivBarDetection?.attack ?: snapped?.attack
            def = def ?: ivBarDetection?.defense ?: snapped?.defense
            sta = sta ?: ivBarDetection?.stamina ?: snapped?.stamina
        }

        if (canUseIvBars && ivPercent != null && listOf(att, def, sta).any { it == null }) {
            val percentAnchored = barSnappedIv
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

        if (!hasIvContext) {
            att = null
            def = null
            sta = null
            ivPercent = null
        }

        if (ivPercent == null && att != null && def != null && sta != null) {
            ivPercent = IvCombinationTable.fromValues(att, def, sta)?.ivPercent ?: ((att + def + sta) * 100 / 45)
        }

        step("Reconhecendo Pokémon")

        val ocrLevel = OcrNumericReader.extractOcrLevel(orderedLines, referenceBounds)
        val moves = extractMoves(orderedLines, referenceBounds)
        val detailCandy = if (!hasIvContext) nameReader.extractCandyFamilyName(context, orderedLines, referenceBounds, null).first else null
        var name = if (!hasIvContext && referenceBounds != null)
            nameReader.inferDetailPokemonName(context, orderedLines, referenceBounds, detailCandy)
        else nameReader.inferPokemonName(context, orderedLines, moves, cp, referenceBounds)
        step("Identificando tipos")
        val typeDetection = detectPokemonTypes(orderedLines, normUpper, referenceBounds)
        val ocrDetectedTypes = typeDetection.first
        val detectedTypes = ocrDetectedTypes.ifEmpty { lookupPokemonTypesByName(context, name) }
        val typeFallbackUsed = ocrDetectedTypes.isEmpty() && detectedTypes.isNotEmpty()
        name = resolveRegionalMeowthName(name, detectedTypes)
        if (ocrDetectedTypes.isNotEmpty()) {
            val dex = name?.let { nameReader.findPokemonNameEntry(context, it)?.dex }
            if (dex != null) {
                val typeForms = nameReader.loadPokemonNames(context).filter { it.dex == dex }.filter {
                    lookupPokemonTypesByName(context, it.name).toSet() == ocrDetectedTypes.toSet()
                }
                typeForms.singleOrNull()?.let { name = it.name }
            }
        }
        step("Identificando família pelos doces")
        var (candyFamilyName, candyDebugInfo) = nameReader.extractCandyFamilyName(context, orderedLines, referenceBounds, name)
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

        step("Identificando gênero")
        val genderDetection = detectGender(normalizedRaw, orderedLines, referenceBounds, name, bitmap)
        resolveGenderedNidoranIdentity(name, candyFamilyName, genderDetection.first).let { (resolvedName, resolvedFamily) ->
            name = resolvedName
            candyFamilyName = resolvedFamily
        }

        var familyMembers = if (name != null || candyFamilyName != null) {
            familySuggester.familyMembersFor(context, candyFamilyName, name)
        } else {
            emptyList()
        }

        step("Estimando nível")

        val displayedMaxHp = OcrNumericReader.extractDisplayedMaxHp(orderedLines, referenceBounds)
        val evidenceName = name
        val cpIvEvidence = if (evidenceName != null && cp != null && (displayedMaxHp != null || ocrLevel != null || listOf(att, def, sta).count { it != null } >= 2) &&
            listOf(att, def, sta).any { it == null }) {
            rankCalculator.resolveIvEvidence(context, evidenceName, cp, displayedMaxHp, att, def, sta, ocrLevel)
        } else null
        if (cpIvEvidence != null && cpIvEvidence.matches > 0) {
            // Only unanimous attributes are supplied. Keep any confirmed bar values.
            att = att ?: cpIvEvidence.attack
            def = def ?: cpIvEvidence.defense
            sta = sta ?: cpIvEvidence.stamina
            if (att != null && def != null && sta != null) {
                ivPercent = IvCombinationTable.fromValues(att, def, sta)?.ivPercent ?: ((att + def + sta) * 100 / 45)
            }
        }
        val cpIvNote = cpIvEvidence?.let {
            when {
                it.matches == 0 -> "PC/HP sem combinação exata compatível; IVs não inferidos"
                it.attack != null && it.defense != null && it.stamina != null -> "IVs confirmados por PC/HP: ${it.matches} combinações de IV/nível compatíveis"
                else -> "PC/HP ambíguos: ${it.matches} combinações de IV/nível; somente atributos unânimes preenchidos"
            }
        }
        val curveLevelCandidates = nameReader.curveLevelCandidatesFor(context, name, familyMembers).let { candidates ->
            if (ocrDetectedTypes.isEmpty()) candidates else candidates.filter {
                lookupPokemonTypesByName(context, it).toSet() == ocrDetectedTypes.toSet()
            }.ifEmpty { candidates }
        }
        val curveEstimate = if (name != null && cp != null && att != null && def != null && sta != null) {
            rankCalculator.estimateLevelForCandidates(
                context = context,
                pokemonNames = curveLevelCandidates,
                cp = cp,
                atk = att,
                def = def,
                sta = sta
            )
        } else {
            null
        }
        val hpEstimate = if (name != null && displayedMaxHp != null && sta != null) {
            rankCalculator.estimateLevelFromHpForCandidates(
                context = context,
                pokemonNames = curveLevelCandidates,
                observedHp = displayedMaxHp,
                sta = sta
            )
        } else {
            null
        }
        val curveAdjustedPokemonName = curveEstimate?.pokemonName
            ?.takeIf { estimatedName -> name != null && !estimatedName.equals(name, ignoreCase = true) }
        if (curveAdjustedPokemonName != null) {
            name = curveAdjustedPokemonName
            familyMembers = familySuggester.familyMembersFor(context, candyFamilyName, name)
        }
        val curveLevel = curveEstimate?.level
        val hpLevel = hpEstimate?.level
        val curveCandidateDistance = curveEstimate?.cpDistance
        val hpCandidateDistance = hpEstimate?.hpDistance
        val curveHpMismatch = if (curveLevel != null && hpLevel != null) abs(curveLevel - hpLevel) else null
        val suspiciousLowCpReading = cp != null && cp < 1000
        val preferHpOverCurve = hpLevel != null &&
            hpCandidateDistance == 0 &&
            suspiciousLowCpReading &&
            curveHpMismatch != null &&
            curveHpMismatch >= 8.0
        val supportingLevel = when {
            cpIvEvidence?.level != null -> cpIvEvidence.level
            preferHpOverCurve -> hpLevel
            curveLevel != null -> curveLevel
            else -> hpLevel
        }
        val levelMismatch = if (ocrLevel != null && supportingLevel != null) abs(ocrLevel - supportingLevel) else null
        val level = when {
            ocrLevel != null && supportingLevel != null && levelMismatch != null && levelMismatch > 2.5 -> supportingLevel
            ocrLevel != null -> ocrLevel
            else -> supportingLevel
        }
        val levelDebugInfo = LevelDebugInfo(
            source = when {
                cpIvEvidence?.level != null -> "pc_hp_exatos"
                ocrLevel != null && (supportingLevel == null || (levelMismatch != null && levelMismatch <= 2.5)) -> "ocr"
                preferHpOverCurve -> "hp"
                curveLevel != null -> "curva_cp"
                hpLevel != null -> "hp"
                else -> "indisponivel"
            },
            ocrLevel = ocrLevel,
            curveLevel = curveLevel,
            hpLevel = hpLevel,
            finalLevel = level,
            pokemonName = name,
            cp = cp,
            maxHp = displayedMaxHp,
            attackIv = att,
            defenseIv = def,
            staminaIv = sta,
            notes = buildList {
                cpIvNote?.let { add(it) }
                if (ocrLevel != null && (supportingLevel == null || (levelMismatch != null && levelMismatch <= 2.5))) add("nível encontrado no texto OCR")
                if (curveLevel != null && (ocrLevel == null || (levelMismatch != null && levelMismatch > 2.5))) add("nível estimado pela curva de CP")
                if (hpLevel != null && curveLevel == null && (ocrLevel == null || (levelMismatch != null && levelMismatch > 2.5))) add("nível estimado pelo HP máximo")
                if (preferHpOverCurve) add("estimativa por HP priorizada porque o CP lido parece truncado e divergiu fortemente da curva de HP")
                if (curveAdjustedPokemonName != null) {
                    add("forma ajustada pela curva de CP: $curveAdjustedPokemonName")
                }
                if (curveCandidateDistance != null && curveCandidateDistance > 0) {
                    add("curva de CP com diferença de $curveCandidateDistance ponto(s)")
                }
                if (hpCandidateDistance != null && hpCandidateDistance > 0) {
                    add("HP com diferença de $hpCandidateDistance ponto(s)")
                }
                if (curveHpMismatch != null && curveHpMismatch >= 8.0) {
                    add("CP e HP apontaram níveis incompatíveis (${curveLevel ?: "-"} vs ${hpLevel ?: "-"})")
                }
                if (ocrLevel != null && supportingLevel != null && levelMismatch != null && levelMismatch > 2.5) {
                    add("nível OCR descartado por divergência com a estimativa complementar")
                }
                if (ocrLevel == null) add("nenhuma linha com nível explícito foi encontrada no OCR")
                if (curveLevel == null && cp == null) add("CP ausente para estimativa por curva")
                if (curveLevel == null && cp != null && listOf(att, def, sta).any { it == null }) {
                    add("estimativa pela curva de CP exige os três IVs; nesta captura eles não foram lidos")
                }
                if (curveLevel == null && cp != null && name != null && listOf(att, def, sta).all { it != null }) {
                    add("nenhuma espécie/forma candidata bateu com o CP observado")
                }
                if (hpLevel == null && displayedMaxHp == null) add("HP ausente para estimativa por HP")
                if (hpLevel == null && displayedMaxHp != null && (name == null || sta == null)) {
                    add("estimativa por HP exige espécie reconhecida e IV de PS")
                }
                if (ocrLevel == null && curveLevel == null && hpLevel == null) add("sem dados suficientes para estimar o nível")
            }.joinToString("; ")
        )

        step("Calculando PvP")
        val familySpeciesRanks = if (familyMembers.isNotEmpty() && att != null && def != null && sta != null) {
            rankCalculator.calculateFamilySpeciesLeagueRanks(
                context, familyMembers, att, def, sta,
                currentPokemonName = name, currentLevel = level, currentCp = cp
            )
        } else {
            emptyList()
        }
        val masterIvBadgeResult = masterIvBadgeCatalog.resolve(
            context = context,
            familyMembers = familyMembers,
            ivPercent = ivPercent,
            attack = att,
            defense = def,
            stamina = sta,
            selectedPokemonName = name
        )

        val selectableFamilySpeciesRanks = familySpeciesRanks
        val selectableLeagueRanks = bestLeagueRanksFromSelectableSpecies(selectableFamilySpeciesRanks, name)
        val bestFamilySpeciesRank = selectableFamilySpeciesRanks
            .filter { it.eligible && it.rank != null }
            .minWithOrNull(
                compareBy<com.mewname.app.model.PvpSpeciesRankInfo> { it.rank ?: Int.MAX_VALUE }
                    .thenBy { it.league.ordinal }
            )
        var league = detectLeague(normalizedRaw)
        if (league != null && selectableFamilySpeciesRanks.none { it.league == league && it.eligible && it.rank != null } &&
            selectableLeagueRanks.none { it.league == league && it.eligible && it.rank != null }
        ) {
            league = null
        }
        if (league == null) {
            league = bestFamilySpeciesRank?.league ?: selectableLeagueRanks
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
            selectableFamilySpeciesRanks
                .filter { it.league == selectedLeague && it.eligible && it.rank != null }
                .minWithOrNull(
                    pvpRankComparator(name, { it.rank }, { it.pokemonName })
                )
        }
        val selectedPvpRankInfo = league?.let { selectedLeague ->
            selectableLeagueRanks.firstOrNull { it.league == selectedLeague }
        }
        val rank = selectedSpeciesRankInfo?.rank ?: selectedPvpRankInfo?.rank
        val pvpPokemonName = selectedSpeciesRankInfo?.pokemonName ?: selectedPvpRankInfo?.pokemonName

        step("Validando fundo e marcadores")

        val backgroundDetection = visualReader.detectSpecialBackground(context, normUpper, orderedLines, bitmap, referenceBounds, detectedTypes)
        val hasSpecialBackground = backgroundDetection.first
        val adventureDetection = detectAdventureEffect(context, name, moves, normUpper, orderedLines, referenceBounds)
        val hasAdventureEffect = adventureDetection.first
        val sizeDetection = detectSize(normUpper, orderedLines, referenceBounds, bitmap)
        val size = sizeDetection.first
        val legacyDetection = detectLegacyMove(context, name, familyMembers, moves, orderedLines, loadLegacyMoves(context), detectedTypes, referenceBounds)
        val hasLegacyMove = legacyDetection.first

        val favoriteRatio = bitmap?.let(visualReader::measureFavoriteStarYellowRatio)
        val isFavorite = favoriteRatio?.let { it >= 0.16 } ?: false
        val purifiedTextMatch = normalizedRaw.lowercase().let { it.contains("purified") || it.contains("purificado") }
        val vivillonDetection = detectVivillonPattern(context, bitmap, name)
        val uniqueFormDetection = detectUniqueForm(context, bitmap, name, detectedTypes, unownLetter)
        val evolutionDetection = detectEvolutionFlags(
            normalizedRaw = normUpper,
            lines = orderedLines,
            pokemonName = name,
            referenceBounds = referenceBounds,
            bitmap = bitmap,
            masterIvBestMatch = masterIvBadgeResult.isBestMatch == true
        )

        val recognizedMoves = try {
            PokemonMoveRecognition.read(context, name,
                rawLinesInRegion(orderedLines, 0.0f, 1.0f, moveRegionTop, moveRegionBottom, referenceBounds)
                    .map { it.text })
        } catch (exception: java.util.concurrent.CancellationException) {
            throw exception
        } catch (exception: Exception) {
            android.util.Log.w("OcrPokemonParser", "Unable to resolve recognized moves", exception)
            RecognizedPokemonMoves()
        }

        step("Finalizando dados detectados")

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
                    if (typeFallbackUsed) add("tipo preenchido pela base local a partir do nome")
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
                    percentFromOcr = ivPercentFromOcr,
                    percentFinal = ivPercent,
                    appraisalPanelRect = bitmap?.let { normalizeDebugRect(detection.appraisalPanelRect, it) },
                    attackBarRect = bitmap?.let { detection.attackBarRect?.let { rect -> normalizeDebugRect(rect, it) } },
                    defenseBarRect = bitmap?.let { detection.defenseBarRect?.let { rect -> normalizeDebugRect(rect, it) } },
                    staminaBarRect = bitmap?.let { detection.staminaBarRect?.let { rect -> normalizeDebugRect(rect, it) } },
                    notes = buildList {
                        cpIvNote?.let { add(it) }
                        if (!hasIvContext) add("contexto de appraisal nao encontrado")
                        if (!detection.isReliable) add("leitura das barras considerada pouco confiavel")
                        if (detection.detectedBars < 3) add("nem todas as barras foram encontradas")
                    }.joinToString("; ")
                )
            } ?: cpIvNote?.let { IvDebugInfo(appraisalDetected = hasIvContext, percentFinal = ivPercent, notes = it) },
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
            pvpLeagueRanks = selectableLeagueRanks,
            familyPvpRanks = selectableFamilySpeciesRanks,
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
            selectedFastMove = recognizedMoves.fast,
            selectedChargedMove = recognizedMoves.charged,
            hasLegacyMove = hasLegacyMove,
            isShadow = backgroundDetection.second.shadowTextMatch ||
                backgroundDetection.second.shadowParticleMatch ||
                backgroundDetection.second.shadowTextureMatch,
            isPurified = purifiedTextMatch,
            hasSpecialBackground = hasSpecialBackground,
            specialBackgroundType = backgroundDetection.second.detectedSpecialType,
            hasAdventureEffect = hasAdventureEffect,
            size = size,
            sizeDebugInfo = sizeDetection.second
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

    private fun isVivillonFamily(pokemonName: String?): Boolean {
        val normalized = pokemonName?.let(::normalizeText) ?: return false
        return normalized == "SCATTERBUG" || normalized == "SPEWPA" || normalized == "VIVILLON"
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
        val normalizedPokemonName = pokemonName?.let(::normalizeText)
        val genericKeyword = sequenceOf(normalizedRaw)
            .plus(moveAreaLines.asSequence())
            .plus(upperLeftBadgeLines.asSequence())
            .firstNotNullOfOrNull { normalized ->
                adventureKeywords.firstOrNull { keyword -> normalized.contains(keyword) }
            }

        val matchedEntry = catalog.firstOrNull { entry ->
            val pokemonMatch = normalizedPokemonName?.let { normalizedName ->
                entry.normalizedPokemonAliases.any { alias ->
                    normalizedName == alias
                }
            } ?: false

            val matchedMove = pokemonMatch && moves.any { extractedMove ->
                entry.normalizedMoveAliases.any { moveAlias -> isStrongAdventureMoveMatch(extractedMove, moveAlias) }
            }

            val matchedText = pokemonMatch && (moveAreaLines + upperLeftBadgeLines).any { normalized ->
                entry.normalizedMoveAliases.any { moveAlias -> normalized.contains(moveAlias) } ||
                    entry.normalizedEffectAliases.any { effectAlias -> normalized.contains(effectAlias) }
            }

            matchedMove || matchedText || (pokemonMatch && genericKeyword != null)
        }

        val matchedMove = matchedEntry?.let { entry ->
            moves.firstOrNull { extractedMove ->
                entry.normalizedMoveAliases.any { moveAlias ->
                    isStrongAdventureMoveMatch(extractedMove, moveAlias)
                }
            } ?: entry.move
        }

        val detected = matchedEntry != null
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
                genericKeyword != null -> "keyword de efeito de aventura encontrada, mas pokemon/golpe nao bateram com a base local"
                else -> "nenhum efeito de aventura bateu com a base local"
            }
        )
    }

    private fun isStrongAdventureMoveMatch(extractedMove: String, moveAlias: String): Boolean {
        val extracted = normalizeText(extractedMove)
        val alias = normalizeText(moveAlias)
        if (extracted.length < 4 || alias.length < 4) return false
        if (extracted == alias) return true
        if (extracted.length >= 6 && alias.contains(extracted)) return true
        if (alias.length >= 6 && extracted.contains(alias)) return true

        val distance = fuzzyMatcher.apply(extracted, alias)
        val maxDistance = if (alias.length <= 8 || extracted.length <= 8) 1 else 2
        return distance <= maxDistance
    }

    private fun detectSize(
        normalizedRaw: String,
        lines: List<OcrTextLine>,
        referenceBounds: Rect?,
        bitmap: Bitmap?
    ): Pair<com.mewname.app.model.PokemonSize, SizeDebugInfo> {
        val statsBandLines = normalizedLinesInRegion(lines, 0.08f, 0.96f, 0.32f, 0.60f, referenceBounds)
        val nameToStatsBridgeLines = normalizedLinesInRegion(lines, 0.10f, 0.92f, 0.22f, 0.40f, referenceBounds)

        val fallbackRawLines = if (lines.none { it.boundingBox != null }) listOf(normalizedRaw) else emptyList()
        val relevantLines = (nameToStatsBridgeLines + statsBandLines + fallbackRawLines).filterNot {
            it.contains("XL CANDY") || it.contains("CANDY XL") ||
                it.contains("XL DOCES") || it.contains("DOCES XL") ||
                it.contains("STARDUST") || it.contains("POEIRA") ||
                it.contains("POWER UP") || it.contains("FORTALECER") ||
                it.contains("EVOLVE") || it.contains("EVOLUIR")
        }

        val textDetection = relevantLines
            .firstNotNullOfOrNull { line -> detectSizeFromTextLine(line)?.let { it to line } }
        // XXS and XXL share the same golden badge: color alone cannot distinguish them.
        val detected = textDetection?.first ?: com.mewname.app.model.PokemonSize.NORMAL
        return detected to SizeDebugInfo(
            detectedSize = detected,
            candidateLines = relevantLines.distinct().take(12),
            textMatch = textDetection?.second,
            notes = if (textDetection != null) "texto ${textDetection.first.name} encontrado"
                else "nenhum marcador especial confirmado por texto"
        )
    }
    private fun detectSizeFromTextLine(line: String): com.mewname.app.model.PokemonSize? {
        fun hasToken(keyword: String): Boolean {
            return Regex("""(^|[^A-Z])${Regex.escape(keyword)}([^A-Z]|$)""").containsMatchIn(line)
        }
        return when {
            xxlKeywords.any(::hasToken) -> com.mewname.app.model.PokemonSize.XXL
            xxsKeywords.any(::hasToken) -> com.mewname.app.model.PokemonSize.XXS
            xlKeywords.any(::hasToken) -> com.mewname.app.model.PokemonSize.XL
            xsKeywords.any(::hasToken) -> com.mewname.app.model.PokemonSize.XS
            else -> null
        }
    }

    private data class FamilyLegacySuggestion(
        val pokemon: String,
        val move: String
    )

    private data class LegacyMoveMatch(
        val matched: Boolean,
        val move: String? = null,
        val pokemon: String? = null,
        val sourceText: String? = null
    )

    private fun detectLegacyMove(
        context: Context,
        pokemonName: String?,
        familyMembers: List<String>,
        moves: List<String>,
        lines: List<OcrTextLine>,
        legacyData: Map<String, List<String>>,
        detectedTypes: List<String>,
        referenceBounds: Rect?
    ): Pair<Boolean, LegacyDebugInfo> {
        val moveAreaLines = normalizedLinesInRegion(lines, 0.0f, 1.0f, moveRegionTop, moveRegionBottom, referenceBounds)
        val matchedKeyword = moveAreaLines.firstNotNullOfOrNull { normalized ->
            legacyKeywords.firstOrNull { normalized.contains(it) }
        }
        val familySuggestion = findFamilyLegacyMoveSuggestion(context, pokemonName, familyMembers)
        val checked = checkLegacyMoves(pokemonName, familyMembers, moves, moveAreaLines, legacyData, detectedTypes)
        if (checked.matched) {
            return true to LegacyDebugInfo(
                moveRegionLines = moveAreaLines,
                extractedMoves = moves,
                matchedKeyword = matchedKeyword,
                matchedLegacyMove = findLegacyDisplayMove(context, checked.pokemon, checked.move) ?: checked.move,
                matchedAgainstPokemon = checked.pokemon,
                notes = buildList {
                    add("golpe legado bateu com a base local")
                    checked.sourceText?.let { add("leitura usada: $it") }
                    if (matchedKeyword != null) add("keyword legado encontrado na area de movimentos")
                }.joinToString("; ")
            )
        }

        if (familySuggestion != null) {
            return false to LegacyDebugInfo(
                moveRegionLines = moveAreaLines,
                extractedMoves = moves,
                matchedKeyword = matchedKeyword,
                matchedLegacyMove = familySuggestion.move,
                matchedAgainstPokemon = familySuggestion.pokemon,
                notes = buildList {
                    add("golpe legado sugerido pela familia, mas nao encontrado nos golpes da tela")
                    if (matchedKeyword != null) add("keyword legado ignorado para nao marcar sem golpe detectado")
                }.joinToString("; ")
            )
        }

        return false to LegacyDebugInfo(
            moveRegionLines = moveAreaLines,
            extractedMoves = moves,
            matchedKeyword = matchedKeyword,
            matchedAgainstPokemon = pokemonName,
            notes = if (matchedKeyword != null) {
                "keyword legado ignorado para nao marcar sem golpe detectado"
            } else {
                "nenhum golpe legado bateu com a base local"
            }
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

    private fun lookupPokemonTypesByName(context: Context, name: String?): List<String> {
        val normalizedName = name?.let(::normalizeText) ?: return emptyList()
        if (normalizedName.isBlank()) return emptyList()
        return GameInfoRepository.loadBattlePokemonIndex(context)
            .firstOrNull { entry -> normalizedName in entry.normalizedNames }
            ?.types
            ?.take(2)
            .orEmpty()
    }

    private fun resolveRegionalMeowthName(name: String?, detectedTypes: List<String>): String? {
        val normalizedName = name?.let(::normalizeText) ?: return name
        val typeSet = detectedTypes.toSet()
        return when (normalizedName) {
            "MEOWTH" -> when {
                "STEEL" in typeSet -> "Meowth (Galar)"
                "DARK" in typeSet -> "Meowth (Alola)"
                else -> name
            }
            "PERSIAN" -> when {
                "DARK" in typeSet -> "Persian (Alola)"
                else -> name
            }
            "PERRSERKER GALARIAN" -> "Perrserker"
            else -> name
        }
    }

    private fun checkLegacyMoves(
        pokemonName: String?,
        familyMembers: List<String>,
        moves: List<String>,
        moveAreaLines: List<String>,
        legacyData: Map<String, List<String>>,
        detectedTypes: List<String>
    ): LegacyMoveMatch {
        val normalizedTargets = buildList {
            pokemonName?.let { add(it) }
            addAll(familyMembers)
        }
            .map(::normalizeText)
            .filter { it.isNotBlank() }
            .distinct()
        if (normalizedTargets.isEmpty()) return LegacyMoveMatch(matched = false)

        val matchedPokemonKeys = expandLegacyPokemonTargetsForRegionalForms(normalizedTargets, legacyData.keys, detectedTypes)
        if (matchedPokemonKeys.isEmpty()) return LegacyMoveMatch(matched = false)

        val screenCandidates = (moves + moveAreaLines)
            .map(::normalizeLegacyMoveCandidate)
            .filter { it.isNotBlank() && !isIgnoredMoveCandidate(it) }
            .distinct()

        matchedPokemonKeys.forEach { matchedPokemon ->
            val pokemonLegacyMoves = legacyData[matchedPokemon].orEmpty()
            screenCandidates.forEach { candidate ->
                val matchedMove = pokemonLegacyMoves.firstOrNull { legacyMove ->
                    isStrongLegacyMoveMatch(candidate, legacyMove)
                }
                if (matchedMove != null) {
                    return LegacyMoveMatch(
                        matched = true,
                        move = matchedMove,
                        pokemon = matchedPokemon,
                        sourceText = candidate
                    )
                }
            }
        }

        return LegacyMoveMatch(matched = false, pokemon = matchedPokemonKeys.firstOrNull())
    }

    private fun expandLegacyPokemonTargetsForRegionalForms(
        normalizedTargets: List<String>,
        legacyKeys: Set<String>,
        detectedTypes: List<String>
    ): List<String> {
        val exactMatches = normalizedTargets.mapNotNull { target ->
            legacyKeys.firstOrNull { it == target }
                ?: legacyKeys.firstOrNull { fuzzyMatcher.apply(target, it) <= 1 }
        }
        val targetBaseNames = normalizedTargets
            .map(::stripRegionalPrefix)
            .filter { it.isNotBlank() }
            .toSet()
        val regionalMatches = legacyKeys.filter { key ->
            val baseKey = stripRegionalPrefix(key)
            key != baseKey && baseKey in targetBaseNames
        }
        return prioritizeRegionalLegacyTargetsByTypes((exactMatches + regionalMatches).distinct(), detectedTypes)
    }

    private fun prioritizeRegionalLegacyTargetsByTypes(
        targets: List<String>,
        detectedTypes: List<String>
    ): List<String> {
        val typeSet = detectedTypes
            .map(::normalizeText)
            .filter { it.isNotBlank() }
            .toSet()
        if (typeSet.isEmpty()) return targets
        return targets.sortedBy { target ->
            val hints = regionalLegacyTypeHints.entries.firstOrNull { normalizeText(pokemonDisplayName(it.key)) == normalizeText(pokemonDisplayName(target)) }?.value.orEmpty()
            if (hints.any { it in typeSet }) 0 else 1
        }
    }

    private fun stripRegionalPrefix(name: String): String {
        val normalized = normalizeText(pokemonDisplayName(name)).replace(Regex("""\s*\((ALOLA|GALAR|HISUI|PALDEA)\)$"""), "")
        return regionalFormPrefixes.firstNotNullOfOrNull { prefix ->
            normalized.removePrefix(prefix).takeIf { it != normalized }
        } ?: normalized
    }

    private fun normalizeLegacyMoveCandidate(text: String): String {
        return normalizeText(text)
            .replace("BONUS CLIMATICO", " ")
            .replace("NOVO ATAQUE", " ")
            .replace(Regex("""\d+(\+\d+)?"""), " ")
            .replace(Regex("""[^A-Z0-9 ]"""), " ")
            .replace(Regex("""\s+"""), " ")
            .trim()
    }

    private fun isIgnoredMoveCandidate(candidate: String): Boolean {
        if (candidate.length < 3) return true
        return candidate.contains("DOCES") ||
            candidate.contains("CANDY") ||
            candidate.contains("POEIRA") ||
            candidate.contains("FORTALECER") ||
            candidate.contains("TREINADOR") ||
            candidate.contains("GINASIOS") ||
            candidate.contains("BATALHAS") ||
            candidate.contains("BONUS CLIMATICO") ||
            candidate.contains("NOVO ATAQUE")
    }

    private fun isStrongLegacyMoveMatch(screenCandidate: String, legacyMove: String): Boolean {
        val candidate = normalizeLegacyMoveCandidate(screenCandidate)
        val legacy = normalizeLegacyMoveCandidate(legacyMove)
        if (candidate.isBlank() || legacy.isBlank()) return false
        if (candidate == legacy) return true
        if (candidate.contains(legacy) && legacy.length >= 6) return true
        if (legacy.length >= 6 && legacy.contains(candidate) && candidate.length >= min(8, legacy.length)) return true

        val compactCandidate = candidate.replace(" ", "")
        val compactLegacy = legacy.replace(" ", "")
        if (compactCandidate == compactLegacy) return true
        if (compactCandidate.contains(compactLegacy) && compactLegacy.length >= 6) return true
        if (compactLegacy.length >= 6 && compactLegacy.startsWith(compactCandidate) && compactCandidate.length >= min(8, compactLegacy.length)) return true
        if (compactLegacy.length >= 6 && compactLegacy.contains(compactCandidate) && compactCandidate.length >= 10) return true

        val distance = fuzzyMatcher.apply(candidate, legacy)
        val maxDistance = when {
            legacy.length <= 8 -> 1
            legacy.length <= 14 -> 2
            else -> 3
        }
        return distance <= maxDistance
    }

    private fun findFamilyLegacyMoveSuggestion(
        context: Context,
        pokemonName: String?,
        familyMembers: List<String>
    ): FamilyLegacySuggestion? {
        val normalizedFamily = buildList {
            pokemonName?.let { add(it) }
            addAll(familyMembers)
        }
            .map(::normalizeText)
            .filter { it.isNotBlank() }
            .distinct()
        if (normalizedFamily.isEmpty()) return null

        val catalog = GameCatalogRepository.loadLegacyMoveCatalog(context)
        return normalizedFamily.firstNotNullOfOrNull { member ->
            catalog.firstOrNull { entry -> normalizeText(entry.pokemon) == member }
                ?.let { entry ->
                    entry.moves.firstOrNull { it.isNotBlank() }
                        ?.let { move -> FamilyLegacySuggestion(entry.pokemon, move) }
                }
        }
    }

    private fun findLegacyDisplayMove(
        context: Context,
        matchedPokemon: String?,
        matchedMove: String?
    ): String? {
        val normalizedPokemon = matchedPokemon?.let(::normalizeText)?.takeIf { it.isNotBlank() } ?: return null
        val normalizedMove = matchedMove?.let(::normalizeText)?.takeIf { it.isNotBlank() }
        val entry = GameCatalogRepository.loadLegacyMoveCatalog(context)
            .firstOrNull { normalizeText(it.pokemon) == normalizedPokemon }
            ?: return null
        if (normalizedMove == null) {
            return entry.moves.firstOrNull { it.isNotBlank() }
        }
        return entry.moves.firstOrNull { displayMove ->
            val normalizedDisplayMove = normalizeText(displayMove)
            normalizedDisplayMove == normalizedMove ||
                normalizedDisplayMove.contains(normalizedMove) ||
                normalizedMove.contains(normalizedDisplayMove) ||
                fuzzyMatcher.apply(normalizedDisplayMove, normalizedMove) <= 2
        } ?: entry.moves.firstOrNull { it.isNotBlank() }
    }

    private fun extractMoves(lines: List<OcrTextLine>, referenceBounds: Rect?): List<String> {
        val moveRegex = Regex("""^([\p{L}' .-]+)\s+(\d{1,3})$""", RegexOption.IGNORE_CASE)
        val damageOnlyRegex = Regex("""^\d{1,3}$""")
        val moveLines = rawLinesInRegion(lines, 0.0f, 1.0f, moveRegionTop, moveRegionBottom, referenceBounds)
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

    internal fun resolveGenderedNidoranIdentity(
        pokemonName: String?,
        candyFamilyName: String?,
        gender: Gender
    ): Pair<String?, String?> {
        if (gender != Gender.MALE && gender != Gender.FEMALE) {
            return pokemonName to candyFamilyName
        }
        val nidoranBaseNames = setOf("NIDORAN", "NIDORAN♀", "NIDORAN♂", "NIDORANF", "NIDORANM")
        val isNidoranBase = listOfNotNull(pokemonName, candyFamilyName)
            .map(::normalizeText)
            .any { it in nidoranBaseNames }
        if (!isNidoranBase) return pokemonName to candyFamilyName

        val canonicalName = if (gender == Gender.FEMALE) "Nidoran♀" else "Nidoran♂"
        return canonicalName to canonicalName
    }

    internal fun detectGender(
        text: String,
        lines: List<OcrTextLine>,
        referenceBounds: Rect?,
        pokemonName: String?,
        bitmap: Bitmap?
    ): Pair<Gender, GenderDebugInfo?> {
        val regions = listOf(
            NormalizedDebugRect(0.18f, 0.16f, 0.82f, 0.42f),
            NormalizedDebugRect(0.76f, 0.30f, 0.98f, 0.60f)
        )
        val candidates = regions.flatMap { region ->
            rawLinesInRegion(lines, region.left, region.right, region.top, region.bottom, referenceBounds)
                .map { it.text }
        }.distinct()
        val standaloneSymbol = lines.isEmpty() && text.trim() in setOf("♂", "♀")
        val evidence = if (standaloneSymbol) text.trim() else candidates.joinToString(" ")
        val normalized = normalizeText(evidence)
        val male = evidence.contains("♂") || Regex("\\b(MACHO|MALE|MASCULINO)\\b").containsMatchIn(normalized)
        val female = evidence.contains("♀") || Regex("\\b(FEMEA|FEMALE|HEMBRA|FEMENINO)\\b").containsMatchIn(normalized)
        val genderless = isGenderlessPokemon(pokemonName)
        val visual = if (!genderless && bitmap != null) GenderIconDetector.detect(bitmap, lines) else null
        val visualConflict = (male && visual?.gender == Gender.FEMALE) || (female && visual?.gender == Gender.MALE)
        val result = when {
            genderless -> Gender.GENDERLESS
            (male && female) || visualConflict -> Gender.UNKNOWN
            male -> Gender.MALE
            female -> Gender.FEMALE
            else -> visual?.gender ?: Gender.UNKNOWN
        }
        val source = when {
            genderless -> "species_catalog"
            visualConflict -> "conflicting_visual_ocr"
            male && female -> "conflicting_regional_ocr"
            standaloneSymbol -> "standalone_symbol_ocr"
            male || female -> "regional_ocr"
            visual?.gender in listOf(Gender.MALE, Gender.FEMALE) -> "visual_icon"
            else -> "unrecognized"
        }
        return result to GenderDebugInfo(
            detectedGender = result, source = source, pokemonName = pokemonName,
            candidateLines = candidates, examinedRegions = regions + listOfNotNull(visual?.region),
            rawMaleSymbol = text.contains("♂"), rawFemaleSymbol = text.contains("♀"),
            bitmapAvailable = bitmap != null,
            visualComparisonPerformed = visual != null,
            iconRect = visual?.rect,
            notes = when {
                genderless -> "Especie sem genero na base local; simbolos OCR nao substituem esta regra."
                visualConflict -> "OCR e desenho do icone discordam; selecao neutra."
                male && female -> "Sinais masculino e feminino conflitantes; selecao neutra."
                male || female -> "Sinal encontrado nas regioes de nome/icone."
                source == "visual_icon" -> "Genero identificado pelo desenho do icone ao lado da barra de PS."
                else -> "Sem evidencia regional suficiente; UNKNOWN, nao GENDERLESS."
            } + " Visual: ${visual?.notes ?: "not required/unavailable"}"
        )
    }
    private fun isGenderlessPokemon(pokemonName: String?): Boolean {
        val normalized = normalizeText(pokemonName.orEmpty()).trim()
        val baseName = normalized.substringBefore(" (")
        return normalized.isNotBlank() && (normalized in genderlessPokemon || baseName in genderlessPokemon ||
            normalized in setOf("ZACIAN HERO", "ZACIAN CROWNED", "ZACIAN CROWNED SWORD", "ZAMAZENTA HERO", "ZAMAZENTA CROWNED", "ZAMAZENTA CROWNED SHIELD"))
    }

    private fun detectEvolutionFlags(
        normalizedRaw: String,
        lines: List<OcrTextLine>,
        pokemonName: String?,
        referenceBounds: Rect?,
        bitmap: Bitmap?,
        masterIvBestMatch: Boolean
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
        val megaEligibleMatch = pokemonName?.let { normalizeText(it) in megaEligiblePokemon } == true
        val megaAutomaticMatch = megaKeyword != null ||
            pokemonName?.startsWith("Mega ", ignoreCase = true) == true ||
            megaEligibleMatch
        val megaDebugMatch = when {
            !masterIvBestMatch && megaAutomaticMatch -> "MEGA_REQUIRES_BEST_IV"
            megaKeyword != null -> megaKeyword
            megaEligibleMatch -> "MEGA_ELIGIBLE_SPECIES"
            else -> null
        }
        val hasMega = megaAutomaticMatch && masterIvBestMatch
        val hasGigantamax = gigantamaxKeyword != null
        // Colour alone cannot distinguish the Max badge from backgrounds or the team leader.
        val hasDynamax = dynamaxKeyword != null
        val dynamaxDebugMatch = dynamaxKeyword

        if (hasMega) flags += EvolutionFlag.MEGA
        if (hasGigantamax) flags += EvolutionFlag.GIGANTAMAX
        if (hasDynamax) flags += EvolutionFlag.DYNAMAX
        val debugInfo = EvolutionIconDebugInfo(
            badgeLines = badgeLines,
            titleLines = titleLines,
            centerLines = (centeredEvolutionLines + dynamaxStatusLines).distinct(),
            actionLines = lowerActionLines,
            megaKeyword = megaDebugMatch,
            gigantamaxKeyword = gigantamaxKeyword,
            dynamaxKeyword = dynamaxDebugMatch,
            detectedFlags = flags.map { it.name },
            notes = buildList {
                if (pokemonName?.startsWith("Mega ", ignoreCase = true) == true) {
                    add("o nome do Pokémon já veio com prefixo Mega")
                }
                if (megaEligibleMatch) add("espécie cadastrada como apta a Mega")
                if (megaAutomaticMatch && !masterIvBestMatch) {
                    add("marcação automática de Mega bloqueada porque os IVs não são a melhor combinação")
                }
                if (maxBadgeVisualMatch) add("cores compatíveis com selo Max, mas sem reconhecimento de formato; cor isolada não confirma Dynamax/Gigamax")

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
            normalizedBitmapRect(bitmap, 0.28f, 0.48f, 0.56f, 0.68f),
            normalizedBitmapRect(bitmap, 0.26f, 0.57f, 0.49f, 0.66f),
            normalizedBitmapRect(bitmap, 0.20f, 0.63f, 0.53f, 0.72f)
        )
        val hsv = FloatArray(3)
        return regions.any { rect ->
            var maxBadgePixels = 0
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
                        if (hsv[0] in 235f..345f) {
                            maxBadgePixels++
                        }
                    }
                    total++
                    x += stepX
                }
                y += stepY
            }

            val maxBadgeRatio = maxBadgePixels / total.coerceAtLeast(1).toDouble()
            val saturatedRatio = saturatedPixels / total.coerceAtLeast(1).toDouble()
            maxBadgeRatio >= 0.032 && saturatedRatio >= 0.075
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

    private fun bestLeagueRanksFromSelectableSpecies(speciesRanks: List<PvpSpeciesRankInfo>, currentPokemonName: String? = null): List<PvpLeagueRankInfo> {
        return listOf(PvpLeague.LITTLE, PvpLeague.GREAT, PvpLeague.ULTRA, PvpLeague.MASTER).mapNotNull { league ->
            speciesRanks
                .filter { it.league == league && it.eligible && it.rank != null }
                .minWithOrNull(
                    pvpRankComparator(currentPokemonName, { it.rank }, { it.pokemonName })
                )
                ?.let { info ->
                    PvpLeagueRankInfo(
                        league = info.league,
                        pokemonName = info.pokemonName,
                        eligible = info.eligible,
                        rank = info.rank,
                        bestCp = info.bestCp,
                        bestLevel = info.bestLevel,
                        bestStatProduct = info.bestStatProduct,
                        stadiumUrl = info.stadiumUrl,
                        description = info.description
                    )
                }
        }
    }

    private fun isPvpSuggestionAllowed(
        context: Context,
        candidatePokemonName: String?,
        currentPokemonName: String?,
        familyMembers: List<String>,
        league: PvpLeague,
        currentCp: Int?,
        currentLevel: Double?,
        attack: Int?,
        defense: Int?,
        stamina: Int?
    ): Boolean {
        if (league == PvpLeague.MASTER) return true
        val candidate = candidatePokemonName?.takeIf { it.isNotBlank() } ?: return false
        val cap = pvpLeagueCap(league)
        if (!isCandidateReachableFromCurrentPokemon(context, candidate, currentPokemonName, familyMembers)) return false

        if (currentLevel != null && attack != null && defense != null && stamina != null) {
            val candidateCp = rankCalculator.estimateCpAtLevel(
                context = context,
                pokemonName = candidate,
                atk = attack,
                def = defense,
                sta = stamina,
                level = currentLevel
            )
            if (candidateCp != null) return candidateCp <= cap
        }

        return currentPokemonName?.let { normalizeText(it) == normalizeText(candidate) } == true &&
            currentCp != null &&
            currentCp <= cap
    }

    private fun isCandidateReachableFromCurrentPokemon(
        context: Context,
        candidatePokemonName: String,
        currentPokemonName: String?,
        familyMembers: List<String>
    ): Boolean {
        val current = currentPokemonName?.takeIf { it.isNotBlank() } ?: return true
        val normalizedCandidate = normalizeText(candidatePokemonName)
        val normalizedCurrent = normalizeText(current)
        if (normalizedCandidate == normalizedCurrent) return true

        val normalizedFamily = familyMembers.map(::normalizeText)
        val currentIndex = normalizedFamily.indexOf(normalizedCurrent)
        val candidateIndex = normalizedFamily.indexOf(normalizedCandidate)
        if (currentIndex == -1 || candidateIndex == -1) return true
        if (candidateIndex < currentIndex) return false

        val stages = loadEvolutionStages(context)
        val currentStage = stages[normalizedCurrent]
        val candidateStage = stages[normalizedCandidate]
        return if (currentStage != null && candidateStage != null) {
            candidateStage.rank > currentStage.rank
        } else {
            candidateIndex > currentIndex
        }
    }

    private fun loadEvolutionStages(context: Context): Map<String, EvolutionStageRank> {
        evolutionStageCache?.let { return it }
        return try {
            val jsonString = context.assets.open(AssetPaths.POKEMON_EVOLUTION_STAGES).bufferedReader().use { it.readText() }
            val jsonArray = JSONArray(jsonString)
            val map = mutableMapOf<String, EvolutionStageRank>()
            for (index in 0 until jsonArray.length()) {
                val obj = jsonArray.getJSONObject(index)
                val name = normalizeText(obj.optString("name"))
                val stage = runCatching {
                    EvolutionStageRank.valueOf(obj.optString("stage").uppercase(Locale.US))
                }.getOrNull()
                if (name.isNotBlank() && stage != null) {
                    map[name] = stage
                }
            }
            evolutionStageCache = map
            map
        } catch (_: Exception) {
            emptyMap()
        }
    }

    private fun pvpLeagueCap(league: PvpLeague): Int {
        return when (league) {
            PvpLeague.LITTLE -> 500
            PvpLeague.GREAT -> 1500
            PvpLeague.ULTRA -> 2500
            PvpLeague.MASTER -> 10000
        }
    }

}
