package com.mewname.app

import androidx.compose.ui.draw.rotate

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.clickable
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import coil.request.CachePolicy
import coil.request.ImageRequest
import com.mewname.app.domain.*
import kotlinx.coroutines.*
import org.json.JSONArray
import java.text.DateFormat
import java.util.Date

@OptIn(ExperimentalLayoutApi::class, ExperimentalMaterial3Api::class)
@Composable
internal fun RaidDetailsScreen(bossName: String, initialTier: String = "RAID_LEVEL_5", bubble: Boolean = false, raidScreenText: String = "", detectedRaidLevel: Int? = null, shielded: Boolean = false, ocrCandidates: List<RaidChoice> = emptyList(), onBack: () -> Unit) {
    val context = LocalContext.current
    val navigationContentPadding = LocalNavigationContentPadding.current
    val language = appLanguage()
    fun tr(pt: String,en: String,es: String) = lt(language,pt,en,es)
    val repository = remember { RaidCounterRepository(context.applicationContext) }
    var meta by remember { mutableStateOf<RaidMetadata?>(null) }
    var pokemonNames by remember(language) { mutableStateOf<Map<Int,String>>(emptyMap()) }
    var moveNames by remember(language) { mutableStateOf<Map<String,String>>(emptyMap()) }
    var level by rememberSaveable { mutableIntStateOf(repository.preferredLevel()) }
    var tier by rememberSaveable(bossName) { mutableStateOf(initialTier) }
    var inferredTier by remember(bossName) { mutableStateOf(false) }
    var selectedId by rememberSaveable(bossName) { mutableStateOf<String?>(bossName) }
    var formChoices by remember(bossName) { mutableStateOf<List<RaidChoice>>(emptyList()) }
    var refresh by remember { mutableIntStateOf(0) }
    val scope = rememberCoroutineScope()
    var identityLoading by remember { mutableStateOf(true) }
    var catalogRefreshing by remember { mutableStateOf(false) }
    var catalogRefreshFailed by remember { mutableStateOf(false) }
    var report by remember(selectedId,tier,level) { mutableStateOf<RaidReport?>(null) }
    var trainerReport by remember(selectedId, tier) { mutableStateOf<RaidReport?>(null) }
    var trainersLoading by remember(selectedId, tier) { mutableStateOf(false) }
    var trainersFailed by remember(selectedId, tier) { mutableStateOf(false) }
    var loading by remember { mutableStateOf(true) }
    var failed by remember { mutableStateOf(false) }
    var weakness by remember { mutableStateOf<List<TypeMatchupEntry>>(emptyList()) }
    val boss = remember(meta,selectedId) { selectedId?.let { meta?.resolve(it) } }
    val clipboard = LocalClipboardManager.current
    var copied by remember(report,language) { mutableStateOf(false) }
    // A service overlay has no Activity back dispatcher, even for a disabled handler.
    if (!bubble) BackHandler(onBack = onBack)
    LaunchedEffect(language, bossName, raidScreenText, detectedRaidLevel) {
        identityLoading = true
        try { withContext(Dispatchers.IO) {
            val identityStarted = android.os.SystemClock.elapsedRealtime()
            val loaded = repository.metadata()
            val identity = if (bubble && ocrCandidates.isNotEmpty()) {
                val selected = ocrCandidates.singleOrNull() ?: ocrCandidates.singleOrNull { it.id == bossName }
                RaidBossResolver.Result(selected, ocrCandidates, false)
            } else if (bubble) repository.resolveBoss(bossName, raidScreenText, detectedRaidLevel) else null
            val startingChoice = if (bubble) identity?.selected else loaded.resolve(bossName)?.let { RaidChoice(it.id, initialTier) }
            val startingLevel = startingChoice?.let { repository.initialLevel(it.id, it.tier) }
            android.util.Log.d("RaidReadTiming", "identity boss=$bossName selected=$startingChoice: ${android.os.SystemClock.elapsedRealtime() - identityStarted}ms")
            withContext(Dispatchers.Main) {
                if (!inferredTier && startingLevel != null) level=startingLevel
                if (bubble && !inferredTier) {
                    selectedId=identity?.selected?.id
                    tier=identity?.selected?.tier ?: initialTier
                    formChoices=identity?.alternatives.orEmpty()
                    inferredTier=true
                }
                inferredTier=true
                meta=loaded
            }
            val names = GameTextRepository.pokemonTranslations(context.applicationContext,language)
            val localized = GameTextRepository.moveTranslations(context.applicationContext,language)
            val labels = listOf(AssetPaths.FAST_MOVES,AssetPaths.CHARGED_MOVES).flatMap { path ->
                val a=JSONArray(context.assets.open(path).bufferedReader().use { it.readText() })
                (0 until a.length()).map { i -> val m=a.getJSONObject(i)
                    raidKey(m.getString("name")) to (localized[m.getInt("move_id")] ?: m.getString("name")) }
            }.toMap()
            withContext(Dispatchers.Main) { moveNames=labels; pokemonNames=names }
        } } catch(e: CancellationException) { throw e } catch(_: Exception) { failed=true;loading=false }
        finally { identityLoading = false }
    }
    LaunchedEffect(boss?.id,tier,level,refresh) {
        val selected=boss ?: return@LaunchedEffect
        loading=true; failed=false
        try {
            // Load saved suggestions first; auxiliary type data must not prevent their display.
            report=withContext(Dispatchers.IO) {
                val started = android.os.SystemClock.elapsedRealtime()
                repository.cached(selected.id,tier,level).also {
                    android.util.Log.d("RaidReadTiming", "cache boss=${selected.id} tier=$tier level=$level hit=${it != null}: ${android.os.SystemClock.elapsedRealtime() - started}ms")
                }
            }
            weakness=withContext(Dispatchers.Default) { runCatching {
                GameInfoRepository.matchupAgainst(context.applicationContext,selected.types).filter { it.multiplier > 1 }
            }.getOrDefault(emptyList()) }
            if ((!bubble && screenCacheExpired(report?.fetchedAt, BATTLE_DATA_MAX_AGE)) || refresh > 0) report=withContext(Dispatchers.IO) { repository.refresh(selected.id,tier,level) }
        } catch(e: CancellationException) { throw e }
        catch(_: Exception) { failed=true }
        finally { loading=false }
    }
    // Group-size estimates have their own level-20 cache, independent of the counter selector.
    LaunchedEffect(boss?.id, tier, refresh, shielded) {
        val selected = boss ?: return@LaunchedEffect
        if (shielded || raidHasShields(tier)) return@LaunchedEffect
        trainersLoading = true
        trainersFailed = false
        try {
            trainerReport = withContext(Dispatchers.IO) { repository.cached(selected.id, tier, 20) }
            if (trainerReport == null || refresh > 0 || (!bubble && screenCacheExpired(trainerReport?.fetchedAt, BATTLE_DATA_MAX_AGE))) {
                trainerReport = withContext(Dispatchers.IO) { repository.refresh(selected.id, tier, 20) }
            }
        } catch (cancelled: CancellationException) { throw cancelled }
        catch (_: Exception) { trainersFailed = true }
        finally { trainersLoading = false }
    }
    fun name(id: String): String {
        val base = pokemonNames[meta?.pokemon?.get(id)?.dex] ?: return pokemonDisplayName(raidName(id))
        val suffix = raidKey(id).removePrefix(raidKey(base)).trim()
        if(suffix.isEmpty()) return base
        val form = when(suffix) {
            "hero" -> tr("Herói","Hero","Héroe")
            "crowned sword" -> tr("Espada Coroada","Crowned Sword","Espada Suprema")
            "crowned shield" -> tr("Escudo Coroado","Crowned Shield","Escudo Supremo")
            "shadow" -> tr("Sombroso","Shadow","Oscuro")
            "primal" -> tr("Primal","Primal","Primigenio")
            else -> raidName(suffix.replace(' ','_'))
        }
        return if(suffix.startsWith("mega")) "$form $base" else "$base ($form)"
    }
    fun raidLabel(choice: RaidChoice): String {
        val titleTier = if (shielded || raidHasShields(choice.tier)) "RAID_LEVEL_4_MEGA_ENHANCED" else choice.tier
        val category = raidCategoryTabTitle(RaidHistoryCategory(titleTier, "", "", "", emptyList()), language)
        val variant=if(choice.id.endsWith("_MEGA_X") || choice.id.endsWith("_MEGA_Y")) " ${choice.id.last()}" else ""
        return "$category$variant"
    }
    val variantOptions = raidVariantOptions(formChoices, ::raidLabel, ::name)
    fun variantLabel(choice: RaidChoice) = variantOptions.firstOrNull { it.choice == choice }?.label ?: raidLabel(choice)
    fun moveLabel(id: String) = moveNames[raidKey(id)] ?: raidName(id.removeSuffix("_FAST"))
    val capture = boss?.let { b -> raidCaptureId(b.id)?.let { meta?.pokemon?.get(it) } }
    val background=LocalAppAppearance.current.background
    Column(Modifier.fillMaxSize().background(Brush.verticalGradient(background))) {
        if(bubble) Box(Modifier.fillMaxWidth().height(30.dp).pointerInput(onBack) {
            var down=0f
            detectVerticalDragGestures(onDragStart={down=0f},onVerticalDrag={change,amount->change.consume();down+=amount},onDragEnd={if(down>60.dp.toPx())onBack()})
        }, contentAlignment=Alignment.Center) { Box(Modifier.size(42.dp,4.dp).background(MaterialTheme.colorScheme.outline.copy(alpha=.4f),RoundedCornerShape(4.dp))) }
        AppTopBar(title={Text(tr("Detalhes da raid","Raid details","Detalles de la incursión"))}, navigationIcon={
            IconButton(onClick=onBack) { Icon(if(bubble) AppIcons.Close else AppIcons.Back,tr("Fechar","Close","Cerrar")) }
        }, actions = {
            IconButton(
                enabled = !identityLoading && !catalogRefreshing && (boss == null || (!loading && !trainersLoading)),
                onClick = {
                    if (boss != null) {
                        refresh++
                    } else {
                        catalogRefreshing = true
                        catalogRefreshFailed = false
                        val previousSelection = selectedId
                        scope.launch {
                            try {
                                val (loaded, identity) = withContext(Dispatchers.IO) {
                                    val metadata = repository.metadata()
                                    repository.catalog(refresh = true)
                                    metadata to if (ocrCandidates.isNotEmpty()) RaidBossResolver.Result(
                                        ocrCandidates.singleOrNull(), ocrCandidates, false)
                                    else repository.resolveBoss(bossName, raidScreenText, detectedRaidLevel)
                                }
                                meta = loaded
                                inferredTier = true
                                failed = false
                                // Preserve a selection made while the catalog request was running.
                                if (selectedId == previousSelection) {
                                    formChoices = identity.alternatives
                                    selectedId = identity.selected?.id
                                    tier = identity.selected?.tier ?: initialTier
                                }
                                // This explicit refresh also downloads counters and portraits
                                // when the refreshed catalog identifies the boss in the overlay.
                                refresh++
                            } catch (cancelled: CancellationException) {
                                throw cancelled
                            } catch (_: Exception) {
                                catalogRefreshFailed = true
                            } finally {
                                catalogRefreshing = false
                            }
                        }
                    }
                }
            ) {
                Icon(AppIcons.Refresh, if (boss == null)
                    tr("Atualizar raids disponíveis", "Refresh available raids", "Actualizar incursiones disponibles")
                else tr("Atualizar", "Refresh", "Actualizar"))
            }
        })
        LazyColumn(Modifier.weight(1f),contentPadding=PaddingValues(start=16.dp, top=16.dp, end=16.dp, bottom=16.dp + navigationContentPadding),verticalArrangement=Arrangement.spacedBy(12.dp)) {
            if (catalogRefreshing) item { LinearProgressIndicator(Modifier.fillMaxWidth()) }
            if(meta==null && !failed) item { LinearProgressIndicator(Modifier.fillMaxWidth()) }
            if(meta==null && failed) item { Text(tr("Não foi possível carregar os dados da raid.","Could not load raid data.","No se pudieron cargar los datos.")) }
            if(meta!=null && boss==null) item { Text(if(formChoices.isNotEmpty()) tr("Chefe não identificado. Escolha um dos candidatos deste nível.","Boss not identified. Choose a candidate at this level.","Jefe no identificado. Elige un candidato de este nivel.") else tr("Não foi possível identificar o chefe nem encontrar raids atuais deste nível.","The boss could not be identified and no current raids were found at this level.","No se pudo identificar al jefe ni encontrar incursiones actuales de este nivel.")) }
            if (catalogRefreshFailed) item {
                Text(tr("Não foi possível atualizar as raids disponíveis. Os dados salvos foram preservados. Tente novamente.",
                    "Could not refresh available raids. Saved data was preserved. Try again.",
                    "No se pudieron actualizar las incursiones disponibles. Se conservaron los datos guardados. Inténtalo de nuevo."))
            }
            if (boss == null) items(formChoices.distinct(), key = { "candidate:${it.id}:${it.tier}" }) { choice ->
                AppSectionCard(Modifier.fillMaxWidth()) {
                    Row(
                        Modifier.fillMaxWidth().clickable(role = Role.Button) {
                            selectedId = choice.id
                            tier = choice.tier
                        }.padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        // Candidate selection must show portraits even before a raid report has been cached.
                        RaidPortrait(choice.id, offline = false, Modifier.size(64.dp), refreshToken = refresh)
                        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Text(name(choice.id), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                            Text(raidLabel(choice), style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        Icon(AppIcons.Next, contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
            boss?.let { b ->
                item { AppSectionCard(Modifier.fillMaxWidth()) { Column(Modifier.padding(14.dp),verticalArrangement=Arrangement.spacedBy(10.dp)) {
                    Row(verticalAlignment=Alignment.CenterVertically) {
                        RaidPortrait(b.id,bubble,Modifier.size(64.dp), refreshToken = refresh)
                        Column { Text(name(b.id),style=MaterialTheme.typography.titleLarge)
                            FlowRow(horizontalArrangement=Arrangement.spacedBy(6.dp)) { b.types.forEach { TypeBadge(it,language,showAssetIcon=true) } }
                        }
                    }
                    if(bubble && variantOptions.size > 1) {
                        RaidVariantSelector(RaidChoice(b.id,tier),variantOptions.map { it.choice },
                            label={variantLabel(it)},onSelect={selectedId=it.id; tier=it.tier})
                    } else Text(raidLabel(RaidChoice(b.id,tier)),style=MaterialTheme.typography.labelMedium)
                    if (shielded || raidHasShields(tier)) Text(
                        tr("Com escudos: leve um Pokémon megaevoluído. Cada treinador quebra um escudo; Groudon e Kyogre Primais não quebram escudos.",
                           "Shielded: bring a Mega-Evolved Pokémon. Each Trainer breaks one shield; Primal Groudon and Kyogre cannot break shields.",
                           "Con escudos: lleva un Pokémon megaevolucionado. Cada Entrenador rompe un escudo; Groudon y Kyogre Primigenios no los rompen."),
                        style = MaterialTheme.typography.bodySmall)
                    if (shielded && !raidHasShields(tier)) Text(
                        tr("As sugestões usam os dados Mega disponíveis; a estimativa não inclui a fase de escudos.",
                           "Suggestions use available Mega data; estimates do not include the shield phase.",
                           "Las sugerencias usan los datos Mega disponibles; la estimación no incluye la fase de escudos."),
                        style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text(tr("Movimentos","Moves","Movimientos"),style=MaterialTheme.typography.titleSmall)
                    Row(horizontalArrangement=Arrangement.spacedBy(10.dp)) {
                        Column(Modifier.weight(1f),verticalArrangement=Arrangement.spacedBy(6.dp)) {
                            Text(tr("Rápidos","Fast","Rápidos"),style=MaterialTheme.typography.labelLarge)
                            (report?.fast ?: b.fast.filterNot { it in b.elite }).forEach { RaidMoveRow(meta!!.move(it),moveLabel(it)) }
                        }
                        Column(Modifier.weight(1f),verticalArrangement=Arrangement.spacedBy(6.dp)) {
                            Text(tr("Carregados","Charged","Cargados"),style=MaterialTheme.typography.labelLarge)
                            (report?.charged ?: b.charged.filterNot { it in b.elite }).forEach { RaidMoveRow(meta!!.move(it),moveLabel(it)) }
                        }
                    }
                    if(report==null) Text(tr("Golpes do catálogo local; confirme a combinação desta raid.","Local catalog moves; confirm this raid's moveset.","Ataques del catálogo local; confirma la combinación de esta incursión."),style=MaterialTheme.typography.bodySmall)
                    Text(tr("Fraquezas","Weaknesses","Debilidades"),style=MaterialTheme.typography.titleSmall)
                    FlowRow(horizontalArrangement=Arrangement.spacedBy(6.dp),verticalArrangement=Arrangement.spacedBy(6.dp)) {
                        weakness.forEach { TypeBadge(it.attackType,language,showAssetIcon=true,inlinePrefix="${it.multiplier}×") }
                    }
                    Text(tr("PC de captura","Catch CP","PC de captura"),style=MaterialTheme.typography.titleSmall)
                    if(capture!=null) {
                        if(tier.contains("SHADOW")) Text(tr("67% é a referência 10/10/10; raids sombrosas podem ter IVs menores.","67% is the 10/10/10 reference; shadow raids may have lower IVs.","67% es la referencia 10/10/10; las incursiones oscuras pueden tener IV inferiores."),style=MaterialTheme.typography.bodySmall)
                        if(capture.id!=b.id) Text(name(capture.id),style=MaterialTheme.typography.bodySmall)
                        RaidCpRow("", "67%", "100%")
                        RaidCpRow(tr("Normal · Nv. 20","Normal · Lv. 20","Normal · Nv. 20"),"${raidCp(capture,10,false)}","${raidCp(capture,15,false)}")
                        RaidCpRow(tr("Com clima · Nv. 25","Boosted · Lv. 25","Con clima · Nv. 25"),"${raidCp(capture,10,true)}","${raidCp(capture,15,true)}")
                        Text(raidWeather(capture.types).joinToString(" · ") { weather -> when(weather) {
                            "sunny" -> tr("☀ Ensolarado / Limpo","☀ Sunny / Clear","☀ Soleado / Despejado")
                            "rain" -> tr("☂ Chuvoso","☂ Rainy","☂ Lluvioso")
                            "partly" -> tr("⛅ Parcialmente nublado","⛅ Partly cloudy","⛅ Parcialmente nublado")
                            "cloudy" -> tr("☁ Nublado","☁ Cloudy","☁ Nublado")
                            "wind" -> tr("≋ Ventando","≋ Windy","≋ Viento")
                            "snow" -> tr("❄ Neve","❄ Snow","❄ Nieve")
                            else -> tr("≋ Neblina","≋ Fog","≋ Niebla")
                        } },style=MaterialTheme.typography.bodySmall)
                    } else Text(tr("Faixa de captura desta forma indisponível.","Catch range unavailable for this form.","Intervalo de captura no disponible para esta forma."))
                } } }
                item { Row(verticalAlignment=Alignment.CenterVertically) {
                    Text(tr("Pokémon sugeridos","Suggested Pokémon","Pokémon sugeridos"),Modifier.weight(1f),style=MaterialTheme.typography.titleMedium)
                    listOf(40,50).forEach { value -> TextButton(onClick={level=value; repository.setPreferredLevel(value)}) { Text(if(level==value) "• $value" else "$value") } }
                } }
                if(loading) item { LinearProgressIndicator(Modifier.fillMaxWidth()) }
                if(failed) item { Text(tr("Falha ao atualizar. Os dados salvos foram preservados.","Update failed. Saved data was preserved.","No se pudo actualizar. Se conservaron los datos guardados.")) }
                if(!loading && report==null) item {
                    Column(verticalArrangement=Arrangement.spacedBy(8.dp)) {
                        Text(tr("Ainda não há sugestões salvas para este chefe.","There are no saved suggestions for this boss yet.","Todavía no hay sugerencias guardadas para este jefe."))

                    }
                }
                run {
                    val trainerRange = raidTrainerRange(trainerReport?.counters.orEmpty().map { it.estimator })
                    item {
                        AppSectionCard(Modifier.fillMaxWidth()) {
                            Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                Text(tr("Pessoas sugeridas", "Suggested trainers", "Personas sugeridas"),
                                    style = MaterialTheme.typography.titleMedium)
                                if (shielded || raidHasShields(tier)) {
                                    Text(tr("Estimativa de grupo indisponível para a fase de escudos.",
                                        "Group estimate unavailable for the shield phase.",
                                        "Estimación de grupo no disponible para la fase de escudos."),
                                        style = MaterialTheme.typography.bodySmall)
                                } else if (trainerRange != null) {
                                    val count = if (trainerRange.first == trainerRange.last) trainerRange.first.toString()
                                        else "${trainerRange.first}–${trainerRange.last}"
                                    Text(count + if (trainerRange.last == 1) tr(" pessoa", " trainer", " persona")
                                        else tr(" pessoas", " trainers", " personas"),
                                        style = MaterialTheme.typography.headlineSmall)
                                } else {
                                    Text(if (trainersLoading) tr("Calculando para o nível 20…", "Calculating for level 20…", "Calculando para el nivel 20…")
                                        else tr("Estimativa de nível 20 indisponível.", "Level-20 estimate unavailable.", "Estimación de nivel 20 no disponible."))
                                }
                                if (trainersFailed) {
                                    Text(tr("Não foi possível atualizar a estimativa de pessoas. Dados salvos, quando disponíveis, foram mantidos.",
                                        "Could not update the group estimate. Saved data was kept when available.",
                                        "No se pudo actualizar la estimación de personas. Se conservaron los datos guardados disponibles."),
                                        style = MaterialTheme.typography.bodySmall)

                                }
                            }
                        }
                    }
                }
                items(report?.counters.orEmpty(),key={it.id}) { counter -> AppSectionCard(Modifier.fillMaxWidth()) {
                    Row(Modifier.padding(12.dp),verticalAlignment=Alignment.CenterVertically) {
                        RaidPortrait(counter.id,bubble,Modifier.size(64.dp), refreshToken = refresh)
                        Column(Modifier.weight(1f),verticalArrangement=Arrangement.spacedBy(5.dp)) {
                            Text(name(counter.id),style=MaterialTheme.typography.titleSmall)
                            RaidMoveRow(counter.fast,tr("Rápido: ","Fast: ","Rápido: ")+moveLabel(counter.fast.id))
                            RaidMoveRow(counter.charged,tr("Carregado: ","Charged: ","Cargado: ")+moveLabel(counter.charged.id))
                        }
                    }
                } }
                report?.let { r -> item {
                    Text(tr("${configuredLegacyMoveSymbol()} Golpe especial","${configuredLegacyMoveSymbol()} Special move","${configuredLegacyMoveSymbol()} Ataque especial"),style=MaterialTheme.typography.bodySmall)
                    Text("Pokébattler · " + DateFormat.getDateTimeInstance(DateFormat.SHORT,DateFormat.SHORT).format(Date(r.fetchedAt)),style=MaterialTheme.typography.bodySmall)
                    if(!bubble) TextButton(onClick={openExternalUrl(context,"https://www.pokebattler.com/raids/${b.id}")}) { Text(tr("Abrir Pokébattler ↗","Open Pokébattler ↗","Abrir Pokébattler ↗")) }
                } }
            }
            if (navigationContentPadding > 0.dp) item {
        if(report!=null) AppActionButton(onClick={
            val types=report!!.counters.flatMap { listOf(it.fast.type,it.charged.type) }.filter { it.isNotBlank() }.distinct()
            val attacks=types.flatMap { type -> (1..3).map { "@$it${localizedTypeLabel(type,language)}" } }.joinToString(",")
            val species=report!!.counters.mapNotNull { meta?.pokemon?.get(it.id)?.dex?.takeIf { dex -> dex>0 } }.distinct().joinToString(",")
            val query=if(species.isNotBlank()) "$species&$attacks" else attacks
            clipboard.setText(AnnotatedString(query));copied=true
        },modifier=Modifier.fillMaxWidth().padding(horizontal=16.dp,vertical=8.dp)) { Text(if(copied) tr("Copiado","Copied","Copiado") else tr("Copiar filtro","Copy filter","Copiar filtro")) }
            }
        }
        if (navigationContentPadding == 0.dp) {
        if(report!=null) AppActionButton(onClick={
            val types=report!!.counters.flatMap { listOf(it.fast.type,it.charged.type) }.filter { it.isNotBlank() }.distinct()
            val attacks=types.flatMap { type -> (1..3).map { "@$it${localizedTypeLabel(type,language)}" } }.joinToString(",")
            val species=report!!.counters.mapNotNull { meta?.pokemon?.get(it.id)?.dex?.takeIf { dex -> dex>0 } }.distinct().joinToString(",")
            val query=if(species.isNotBlank()) "$species&$attacks" else attacks
            clipboard.setText(AnnotatedString(query));copied=true
        },modifier=Modifier.fillMaxWidth().padding(horizontal=16.dp,vertical=8.dp)) { Text(if(copied) tr("Copiado","Copied","Copiado") else tr("Copiar filtro","Copy filter","Copiar filtro")) }
        }
    }
}
@Composable internal fun RaidCpRow(label: String, low: String, high: String) {
    Row(Modifier.fillMaxWidth()) { Text(label,Modifier.weight(1.7f),style=MaterialTheme.typography.bodySmall)
        Text(low,Modifier.weight(1f)); Text(high,Modifier.weight(1f)) }
}
@Composable internal fun RaidMoveRow(move: RaidMove, label: String) {
    Row(verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(6.dp)) {
        if(move.type.isNotBlank()) AsyncImage("file:///android_asset/types/POKEMON_TYPE_${move.type.uppercase()}.png",localizedTypeLabel(move.type,appLanguage()),Modifier.size(20.dp))
        Text(label + if(move.special) " ${configuredLegacyMoveSymbol()}" else "",style=MaterialTheme.typography.bodySmall,modifier=Modifier.weight(1f))
    }
}
@Composable internal fun RaidPortrait(id: String, offline: Boolean, modifier: Modifier, refreshToken: Int = 0) {
    val context = LocalContext.current
    val url by produceState<String?>(null, id, refreshToken) {
        value = withContext(Dispatchers.IO) { RaidCounterRepository(context.applicationContext).image(id) }
    }
    val request = remember(context, url, offline, refreshToken) {
        ImageRequest.Builder(context).data(url)
            .transformations(CollectionSpriteTrim)
            // Opening the overlay remains cache-only; an explicit raid refresh
            // also authorizes downloading missing portraits and saving them.
            .networkCachePolicy(if (offline && refreshToken == 0) CachePolicy.DISABLED else CachePolicy.ENABLED)
            .memoryCachePolicy(CachePolicy.ENABLED)
            .diskCachePolicy(CachePolicy.ENABLED)
            .build()
    }
    // Recreate the painter even when the URL is unchanged, so a previous failed
    // request is retried on every press. Successful cached images are reused.
    key(id, refreshToken) {
        AsyncImage(request, null, modifier)
    }
}

@Composable
private fun RaidVariantSelector(
    selected: RaidChoice,
    choices: List<RaidChoice>,
    label: (RaidChoice) -> String,
    onSelect: (RaidChoice) -> Unit
) {
    var expanded by remember { mutableStateOf(false) }
    Box {
        AppActionButton(onClick = { expanded = true }, secondary = true) {
            Text(label(selected), style = MaterialTheme.typography.labelLarge)
            Spacer(Modifier.width(8.dp))
            Icon(AppIcons.Next, null, Modifier.size(18.dp).rotate(90f))
        }
        AppSelectionMenu(expanded, { expanded = false }, choices.distinct(), selected, label, onSelect)
    }
}
