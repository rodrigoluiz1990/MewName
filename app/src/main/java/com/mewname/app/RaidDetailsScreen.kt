package com.mewname.app

import androidx.activity.compose.BackHandler
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
internal fun RaidDetailsScreen(bossName: String, initialTier: String = "RAID_LEVEL_5", bubble: Boolean = false, raidScreenText: String = "", detectedRaidLevel: Int? = null, shielded: Boolean = false, onBack: () -> Unit) {
    val context = LocalContext.current
    val language = appLanguage()
    fun tr(pt: String,en: String,es: String) = lt(language,pt,en,es)
    val repository = remember { RaidCounterRepository(context.applicationContext) }
    var meta by remember { mutableStateOf<RaidMetadata?>(null) }
    var pokemonNames by remember(language) { mutableStateOf<Map<Int,String>>(emptyMap()) }
    var moveNames by remember(language) { mutableStateOf<Map<String,String>>(emptyMap()) }
    var level by rememberSaveable { mutableIntStateOf(40) }
    var tier by rememberSaveable(bossName) { mutableStateOf(initialTier) }
    var inferredTier by remember(bossName) { mutableStateOf(false) }
    var selectedId by rememberSaveable(bossName) { mutableStateOf<String?>(bossName) }
    var formChoices by remember(bossName) { mutableStateOf<List<RaidChoice>>(emptyList()) }
    var refresh by remember { mutableIntStateOf(0) }
    var report by remember(selectedId,tier,level) { mutableStateOf<RaidReport?>(null) }
    var loading by remember { mutableStateOf(true) }
    var failed by remember { mutableStateOf(false) }
    var weakness by remember { mutableStateOf<List<TypeMatchupEntry>>(emptyList()) }
    val boss = remember(meta,selectedId) { selectedId?.let { meta?.resolve(it) } }
    val clipboard = LocalClipboardManager.current
    var copied by remember(report,language) { mutableStateOf(false) }
    // A service overlay has no Activity back dispatcher, even for a disabled handler.
    if (!bubble) BackHandler(onBack = onBack)
    LaunchedEffect(language) {
        try { withContext(Dispatchers.IO) {
            val loaded = repository.metadata()
            val identity = if (bubble) RaidBossResolver.resolve(bossName, raidScreenText, loaded,
                runCatching { repository.catalog().ifEmpty { repository.catalog(refresh = true) } }.getOrDefault(emptyList()), detectedRaidLevel) else null
            withContext(Dispatchers.Main) {
                if (bubble && !inferredTier) {
                    selectedId=identity?.selected?.id
                    tier=identity?.selected?.tier ?: initialTier
                    formChoices=identity?.alternatives.orEmpty()
                    inferredTier=true
                }
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
    }
    LaunchedEffect(boss?.id,tier,level,refresh) {
        val selected=boss ?: return@LaunchedEffect
        loading=true; failed=false
        try {
            weakness=withContext(Dispatchers.Default) { GameInfoRepository.matchupAgainst(context.applicationContext,selected.types).filter { it.multiplier > 1 } }
            report=withContext(Dispatchers.IO) { repository.cached(selected.id,tier,level) }
            if ((!bubble && report == null) || refresh > 0) report=withContext(Dispatchers.IO) { repository.refresh(selected.id,tier,level) }
        } catch(e: CancellationException) { throw e }
        catch(_: Exception) { failed=true }
        finally { loading=false }
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
    fun moveLabel(id: String) = moveNames[raidKey(id)] ?: raidName(id.removeSuffix("_FAST"))
    val capture = boss?.let { b -> raidCaptureId(b.id)?.let { meta?.pokemon?.get(it) } }
    val background=LocalAppAppearance.current.background
    Column(Modifier.fillMaxSize().background(Brush.verticalGradient(background))) {
        if(bubble) Box(Modifier.fillMaxWidth().height(30.dp).pointerInput(onBack) {
            var down=0f
            detectVerticalDragGestures(onDragStart={down=0f},onVerticalDrag={change,amount->change.consume();down+=amount},onDragEnd={if(down>60.dp.toPx())onBack()})
        }, contentAlignment=Alignment.Center) { Box(Modifier.size(42.dp,4.dp).background(MaterialTheme.colorScheme.outline.copy(alpha=.4f),RoundedCornerShape(4.dp))) }
        AppTopBar(title={Text(tr("Detalhes da raid","Raid details","Detalles de la incursión"))}, navigationIcon={
            IconButton(onClick=onBack) { Icon(if(bubble) Icons.Default.Close else Icons.Default.ArrowBack,tr("Fechar","Close","Cerrar")) }
        },actions={if(!bubble) IconButton(onClick={refresh++},enabled=!loading){Icon(Icons.Default.Refresh,tr("Atualizar","Refresh","Actualizar"))}})
        LazyColumn(Modifier.weight(1f),contentPadding=PaddingValues(16.dp),verticalArrangement=Arrangement.spacedBy(12.dp)) {
            if(meta==null && !failed) item { LinearProgressIndicator(Modifier.fillMaxWidth()) }
            if(meta==null && failed) item { Text(tr("Não foi possível carregar os dados da raid.","Could not load raid data.","No se pudieron cargar los datos.")) }
            if(meta!=null && boss==null) item { Text(if(formChoices.isNotEmpty()) tr("Chefe não identificado. Escolha uma raid atual deste nível.","Boss not identified. Choose a current raid at this level.","Jefe no identificado. Elige una incursión actual de este nivel.") else tr("Não foi possível identificar o chefe nem encontrar raids atuais deste nível.","The boss could not be identified and no current raids were found at this level.","No se pudo identificar al jefe ni encontrar incursiones actuales de este nivel.")) }
            if(bubble && boss==null && formChoices.isNotEmpty()) item {
                RaidVariantSelector(tr("Selecionar raid","Select raid","Seleccionar incursión"),formChoices,
                    label={"${name(it.id)} · ${raidLabel(it)}"},onSelect={selectedId=it.id; tier=it.tier})
            }
            boss?.let { b ->
                item { AppSectionCard(Modifier.fillMaxWidth()) { Column(Modifier.padding(14.dp),verticalArrangement=Arrangement.spacedBy(10.dp)) {
                    Row(verticalAlignment=Alignment.CenterVertically) {
                        RaidPortrait(b.id,bubble,Modifier.size(72.dp))
                        Column { Text(name(b.id),style=MaterialTheme.typography.titleLarge)
                            FlowRow(horizontalArrangement=Arrangement.spacedBy(6.dp)) { b.types.forEach { TypeBadge(it,language,showAssetIcon=true) } }
                        }
                    }
                    if(bubble && formChoices.size > 1) {
                        RaidVariantSelector(raidLabel(RaidChoice(b.id,tier)),formChoices,
                            label={raidLabel(it)},onSelect={selectedId=it.id; tier=it.tier})
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
                    Text(tr("Golpes possíveis do chefe","Possible boss attacks","Ataques posibles del jefe"),style=MaterialTheme.typography.titleSmall)
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
                        Text(tr("IVs: 10/10/10 e 15/15/15","IVs: 10/10/10 and 15/15/15","IV: 10/10/10 y 15/15/15"),style=MaterialTheme.typography.bodySmall)
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
                    listOf(40,50).forEach { value -> TextButton(onClick={level=value}) { Text(if(level==value) "• $value" else "$value") } }
                }; Text(tr("Sem clima · Megas, sombrosos e lendários incluídos","No weather · Megas, shadows and legendaries included","Sin clima · Megas, oscuros y legendarios incluidos"),style=MaterialTheme.typography.bodySmall) }
                if(loading) item { LinearProgressIndicator(Modifier.fillMaxWidth()) }
                if(failed) item { Text(tr("Falha ao atualizar. Os dados salvos foram preservados.","Update failed. Saved data was preserved.","No se pudo actualizar. Se conservaron los datos guardados.")) }
                if(!loading && report==null) item {
                    Column(verticalArrangement=Arrangement.spacedBy(8.dp)) {
                        Text(tr("Ainda não há sugestões salvas para este chefe.","There are no saved suggestions for this boss yet.","Todavía no hay sugerencias guardadas para este jefe."))
                        AppActionButton(
                            onClick={refresh++},
                            enabled=!loading,
                            modifier=Modifier.fillMaxWidth()
                        ) { Text(tr("Atualizar esta raid","Update this raid","Actualizar esta incursión")) }
                    }
                }
                items(report?.counters.orEmpty(),key={it.id}) { counter -> AppSectionCard(Modifier.fillMaxWidth()) {
                    Row(Modifier.padding(12.dp),verticalAlignment=Alignment.CenterVertically) {
                        RaidPortrait(counter.id,bubble,Modifier.size(56.dp))
                        Column(Modifier.weight(1f),verticalArrangement=Arrangement.spacedBy(5.dp)) {
                            Text(name(counter.id),style=MaterialTheme.typography.titleSmall)
                            RaidMoveRow(counter.fast,tr("Rápido: ","Fast: ","Rápido: ")+moveLabel(counter.fast.id))
                            RaidMoveRow(counter.charged,tr("Carregado: ","Charged: ","Cargado: ")+moveLabel(counter.charged.id))
                        }
                    }
                } }
                report?.let { r -> item { Text(tr("Filtro por espécies e tipos de golpes. Confira a forma e os golpes recomendados.","Filter by species and attack types. Check the recommended form and moves.","Filtro por especies y tipos de ataque. Revisa la forma y los ataques recomendados."),style=MaterialTheme.typography.bodySmall)
                    Text(tr("★ Golpe especial","★ Special move","★ Ataque especial"),style=MaterialTheme.typography.bodySmall)
                    Text("Pokébattler · " + DateFormat.getDateTimeInstance(DateFormat.SHORT,DateFormat.SHORT).format(Date(r.fetchedAt)),style=MaterialTheme.typography.bodySmall)
                    if(!bubble) TextButton(onClick={openExternalUrl(context,"https://www.pokebattler.com/raids/${b.id}")}) { Text(tr("Abrir Pokébattler ↗","Open Pokébattler ↗","Abrir Pokébattler ↗")) }
                } }
            }
        }
        if(report!=null) AppActionButton(onClick={
            val types=report!!.counters.flatMap { listOf(it.fast.type,it.charged.type) }.filter { it.isNotBlank() }.distinct()
            val attacks=types.flatMap { type -> (1..3).map { "@$it${localizedTypeLabel(type,language)}" } }.joinToString(",")
            val species=report!!.counters.mapNotNull { meta?.pokemon?.get(it.id)?.dex?.takeIf { dex -> dex>0 } }.distinct().joinToString(",")
            val query=if(species.isNotBlank()) "$species&$attacks" else attacks
            clipboard.setText(AnnotatedString(query));copied=true
        },modifier=Modifier.fillMaxWidth().padding(horizontal=16.dp,vertical=8.dp)) { Text(if(copied) tr("Copiado","Copied","Copiado") else tr("Copiar filtro","Copy filter","Copiar filtro")) }
    }
}
@Composable internal fun RaidCpRow(label: String, low: String, high: String) {
    Row(Modifier.fillMaxWidth()) { Text(label,Modifier.weight(1.7f),style=MaterialTheme.typography.bodySmall)
        Text(low,Modifier.weight(1f)); Text(high,Modifier.weight(1f)) }
}
@Composable internal fun RaidMoveRow(move: RaidMove, label: String) {
    Row(verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(6.dp)) {
        if(move.type.isNotBlank()) AsyncImage("file:///android_asset/types/POKEMON_TYPE_${move.type.uppercase()}.png",localizedTypeLabel(move.type,appLanguage()),Modifier.size(20.dp))
        Text(label + if(move.special) " ★" else "",style=MaterialTheme.typography.bodySmall,modifier=Modifier.weight(1f))
    }
}
@Composable internal fun RaidPortrait(id: String, offline: Boolean, modifier: Modifier) {
    val context=LocalContext.current
    val url by produceState<String?>(null,id) { value=withContext(Dispatchers.IO) { RaidCounterRepository(context.applicationContext).image(id) } }
    val request=ImageRequest.Builder(context).data(url)
        .networkCachePolicy(if(offline) CachePolicy.DISABLED else CachePolicy.ENABLED).build()
    AsyncImage(request,null,modifier)
}
@Composable
private fun RaidVariantSelector(
    selectedLabel: String,
    choices: List<RaidChoice>,
    label: (RaidChoice) -> String,
    onSelect: (RaidChoice) -> Unit
) {
    var expanded by remember { mutableStateOf(false) }
    Box {
        TextButton(onClick={expanded=true},contentPadding=PaddingValues(horizontal=0.dp)) {
            Text(selectedLabel,style=MaterialTheme.typography.labelLarge)
            Icon(Icons.Default.ArrowDropDown,contentDescription=null,modifier=Modifier.size(20.dp))
        }
        DropdownMenu(expanded=expanded,onDismissRequest={expanded=false}) {
            choices.forEach { choice ->
                DropdownMenuItem(text={Text(label(choice))},onClick={expanded=false;onSelect(choice)})
            }
        }
    }
}
