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
internal fun RaidDetailsScreen(bossName: String, initialTier: String = "RAID_LEVEL_5", bubble: Boolean = false, onBack: () -> Unit) {
    val context = LocalContext.current
    val language = appLanguage()
    fun tr(pt: String,en: String,es: String) = lt(language,pt,en,es)
    val repository = remember { RaidCounterRepository(context.applicationContext) }
    var meta by remember { mutableStateOf<RaidMetadata?>(null) }
    var pokemonNames by remember(language) { mutableStateOf<Map<Int,String>>(emptyMap()) }
    var moveNames by remember(language) { mutableStateOf<Map<String,String>>(emptyMap()) }
    var level by rememberSaveable { mutableIntStateOf(40) }
    var tier by rememberSaveable(bossName) { mutableStateOf(initialTier) }
    var inferredTier by remember { mutableStateOf(false) }
    var refresh by remember { mutableIntStateOf(0) }
    var report by remember(bossName,tier,level) { mutableStateOf<RaidReport?>(null) }
    var loading by remember { mutableStateOf(true) }
    var failed by remember { mutableStateOf(false) }
    var weakness by remember { mutableStateOf<List<TypeMatchupEntry>>(emptyList()) }
    val boss = remember(meta,bossName) { meta?.resolve(bossName) }
    val clipboard = LocalClipboardManager.current
    var copied by remember(report,language) { mutableStateOf(false) }
    // A service overlay has no Activity back dispatcher, even for a disabled handler.
    if (!bubble) BackHandler(onBack = onBack)
    LaunchedEffect(language) {
        try { withContext(Dispatchers.IO) {
            val loaded = repository.metadata()
            val resolved = loaded.resolve(bossName)
            val inferred = if(bubble && resolved!=null) raidTier(resolved.id,runCatching { repository.catalog() }.getOrDefault(emptyList())) else initialTier
            withContext(Dispatchers.Main) { if(bubble && !inferredTier) { tier=inferred;inferredTier=true }; meta=loaded }
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
            if (!bubble && (report==null || refresh>0)) report=withContext(Dispatchers.IO) { repository.refresh(selected.id,tier,level) }
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
            if(meta!=null && boss==null) item { Text(tr("Não foi possível identificar a forma deste chefe. Selecione a raid pelo app.","Boss form not identified. Select the raid in the app.","Forma del jefe no identificada. Selecciona la incursión en la app.")) }
            boss?.let { b ->
                item { AppSectionCard(Modifier.fillMaxWidth()) { Column(Modifier.padding(14.dp),verticalArrangement=Arrangement.spacedBy(10.dp)) {
                    Row(verticalAlignment=Alignment.CenterVertically) {
                        RaidPortrait(b.id,bubble,Modifier.size(72.dp))
                        Column { Text(name(b.id),style=MaterialTheme.typography.titleLarge)
                            FlowRow(horizontalArrangement=Arrangement.spacedBy(6.dp)) { b.types.forEach { TypeBadge(it,language,showAssetIcon=true) } }
                        }
                    }
                    Text("Raid · " + tier.removePrefix("RAID_LEVEL_").replace('_',' '), style=MaterialTheme.typography.labelMedium)
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
                if(!loading && report==null) item { Text(if(bubble) tr("Atualize esta raid na tela Raids do app para ver as sugestões aqui.","Update this raid in the app's Raids screen to see suggestions here.","Actualiza esta incursión en la pantalla Raids de la app para ver sugerencias aquí.") else tr("Sem sugestões salvas. Toque em atualizar para tentar novamente.","No saved suggestions. Tap refresh to retry.","Sin sugerencias guardadas. Pulsa actualizar para reintentar.")) }
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
@Composable private fun RaidCpRow(label: String, low: String, high: String) {
    Row(Modifier.fillMaxWidth()) { Text(label,Modifier.weight(1.7f),style=MaterialTheme.typography.bodySmall)
        Text(low,Modifier.weight(1f)); Text(high,Modifier.weight(1f)) }
}
@Composable private fun RaidMoveRow(move: RaidMove, label: String) {
    Row(verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(6.dp)) {
        if(move.type.isNotBlank()) AsyncImage("file:///android_asset/types/POKEMON_TYPE_${move.type.uppercase()}.png",localizedTypeLabel(move.type,appLanguage()),Modifier.size(20.dp))
        Text(label + if(move.special) " ★" else "",style=MaterialTheme.typography.bodySmall,modifier=Modifier.weight(1f))
    }
}
@Composable private fun RaidPortrait(id: String, offline: Boolean, modifier: Modifier) {
    val context=LocalContext.current
    val url by produceState<String?>(null,id) { value=withContext(Dispatchers.IO) { RaidCounterRepository(context.applicationContext).image(id) } }
    val request=ImageRequest.Builder(context).data(url)
        .networkCachePolicy(if(offline) CachePolicy.DISABLED else CachePolicy.ENABLED).build()
    AsyncImage(request,null,modifier)
}