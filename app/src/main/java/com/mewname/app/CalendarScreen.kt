package com.mewname.app

import androidx.compose.foundation.background
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.mewname.app.domain.*
import kotlinx.coroutines.*
import java.time.LocalDate
import java.time.YearMonth
import java.time.DayOfWeek
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle
import java.util.Locale
import java.text.DateFormat
import java.util.Date

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun CalendarScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val language = appLanguage()
    fun tr(pt: String, en: String, es: String) = lt(language, pt, en, es)
    val locale = when(language) { AppLanguage.PT_BR -> Locale("pt", "BR"); AppLanguage.ES -> Locale("es"); else -> Locale.ENGLISH }
    var today by remember { mutableStateOf(LocalDate.now()) }
    LaunchedEffect(Unit) { while (true) { today = LocalDate.now(); delay(60_000) } }
    var dateText by rememberSaveable { mutableStateOf(today.toString()) }
    val selectedDate = LocalDate.parse(dateText)
    val month = YearMonth.from(selectedDate)
    var weekView by rememberSaveable { mutableStateOf(false) }
    var dayOnly by rememberSaveable { mutableStateOf(true) }
    val weekStart = selectedDate.minusDays((selectedDate.dayOfWeek.value - 1).toLong())
    val periodStart = if (weekView) weekStart else month.atDay(1)
    val periodEnd = if (weekView) weekStart.plusDays(6) else month.atEndOfMonth()
    val years = (periodStart.year..periodEnd.year).toList()
    var filter by rememberSaveable { mutableStateOf("") }
    var retry by remember { mutableIntStateOf(0) }
    var catalog by remember { mutableStateOf<CalendarCatalog?>(null) }
    var loading by remember { mutableStateOf(true) }
    var failed by remember { mutableStateOf(false) }
    var selectedEvent by remember { mutableStateOf<CalendarEvent?>(null) }
    val repository = remember { CalendarRepository(context.applicationContext) }
    LaunchedEffect(years, retry) {
        loading = true; failed = false; catalog = null
        val loaded = mutableMapOf<Int, CalendarCatalog>()
        fun publish() {
            catalog = loaded.values.takeIf { it.isNotEmpty() }?.let { values ->
                CalendarCatalog(values.flatMap { it.events }.distinctBy { it.id }, values.minOf { it.fetchedAt })
            }
        }
        try {
            withContext(Dispatchers.IO) {
                years.mapNotNull { year -> repository.cached(year)?.let { year to it } }.toMap()
            }.let { loaded.putAll(it) }
            publish()
            for (year in years) {
                val cached = loaded[year]
                if (retry > 0 || cached == null || System.currentTimeMillis() - cached.fetchedAt > 60 * 60 * 1000L) {
                    try {
                        loaded[year] = withContext(Dispatchers.IO) { repository.refresh(year) }
                        publish()
                    } catch (cancelled: CancellationException) { throw cancelled }
                    catch (error: Exception) {
                        android.util.Log.w("Calendar", "Unable to load calendar for " + year, error)
                        failed = true
                    }
                }
            }
        } finally { loading = false }
    }
    val periodEvents = remember(catalog, periodStart, periodEnd) {
        catalog?.events.orEmpty().filter {
            !it.start.toLocalDate().isAfter(periodEnd) && it.end.isAfter(periodStart.atStartOfDay())
        }.sortedWith(compareBy<CalendarEvent> { it.start }.thenBy { it.title })
    }
    val categories = periodEvents.distinctBy { it.category }.map { it.category to it.categoryLabel }
    val activeFilter = filter.takeIf { key -> categories.any { it.first == key } }.orEmpty()
    val filtered = remember(periodEvents, activeFilter) { periodEvents.filter { activeFilter.isBlank() || it.category == activeFilter } }
    val displayedEvents = if (dayOnly) filtered.filter { it.occursOn(selectedDate) } else filtered
    val formatter = DateTimeFormatter.ofPattern("dd MMM yyyy · HH:mm", locale)

    Scaffold(topBar = {
        AppTopBar(title = { Text(tr("Calendário", "Calendar", "Calendario")) },
            navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, tr("Voltar", "Back", "Volver")) } },
            actions = { IconButton(onClick = { retry++ }, enabled = !loading) { Icon(Icons.Default.Refresh, tr("Atualizar eventos", "Refresh events", "Actualizar eventos")) } })
    }) { padding ->
        LazyColumn(Modifier.fillMaxSize().background(Brush.verticalGradient(LocalAppAppearance.current.background))
            .padding(padding).padding(horizontal = 12.dp), verticalArrangement = Arrangement.spacedBy(12.dp),
            contentPadding = PaddingValues(vertical = 12.dp)) {
            item {
                AppSectionCard(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                            AppChoiceChip(!weekView, { weekView = false }, { Text(tr("Mês", "Month", "Mes")) })
                            AppChoiceChip(weekView, { weekView = true }, { Text(tr("Semana", "Week", "Semana")) })
                            Spacer(Modifier.weight(1f))
                            AppSecondaryButton(onClick = { dateText = today.toString(); filter = ""; dayOnly = true }) {
                                Text(tr("Hoje", "Today", "Hoy"))
                            }
                        }
                        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                            IconButton(onClick = {
                                dateText = (if (weekView) selectedDate.minusWeeks(1) else selectedDate.minusMonths(1)).toString()
                            }, modifier = Modifier.semantics {
                                contentDescription = if (weekView) tr("Semana anterior", "Previous week", "Semana anterior")
                                    else tr("Mês anterior", "Previous month", "Mes anterior")
                            }) { Text("‹", style = MaterialTheme.typography.headlineSmall) }
                            val periodTitle = if (weekView) {
                                val dateFormat = DateTimeFormatter.ofPattern("dd MMM", locale)
                                periodStart.format(dateFormat) + " – " + periodEnd.format(dateFormat) + " · " +
                                    (if (periodStart.year == periodEnd.year) periodEnd.year.toString()
                                     else periodStart.year.toString() + "/" + periodEnd.year)
                            } else month.month.getDisplayName(TextStyle.FULL, locale).replaceFirstChar { it.titlecase(locale) } + " " + month.year
                            Text(periodTitle, Modifier.weight(1f), textAlign = TextAlign.Center,
                                style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                            IconButton(onClick = {
                                dateText = (if (weekView) selectedDate.plusWeeks(1) else selectedDate.plusMonths(1)).toString()
                            }, modifier = Modifier.semantics {
                                contentDescription = if (weekView) tr("Próxima semana", "Next week", "Semana siguiente")
                                    else tr("Próximo mês", "Next month", "Mes siguiente")
                            }) { Text("›", style = MaterialTheme.typography.headlineSmall) }
                        }
                        Row(Modifier.fillMaxWidth()) {
                            (1..7).forEach { day -> Text(DayOfWeek.of(day).getDisplayName(TextStyle.SHORT, locale), Modifier.weight(1f), textAlign = TextAlign.Center, style = MaterialTheme.typography.labelSmall) }
                        }
                        val offset = month.atDay(1).dayOfWeek.value - 1
                        val rows = if (weekView) 1 else (offset + month.lengthOfMonth() + 6) / 7
                        repeat(rows) { week ->
                            Row(Modifier.fillMaxWidth()) {
                                repeat(7) { weekday ->
                                    val number = week * 7 + weekday - offset + 1
                                    if (!weekView && number !in 1..month.lengthOfMonth()) Spacer(Modifier.weight(1f).height(48.dp))
                                    else {
                                        val date = if (weekView) weekStart.plusDays(weekday.toLong()) else month.atDay(number)
                                        val count = filtered.count { it.occursOn(date) }
                                        val isSelected = date == selectedDate
                                        Column(Modifier.weight(1f).heightIn(min = 48.dp)
                                            .background(if (isSelected) appControlFill(true) else androidx.compose.ui.graphics.Color.Transparent, androidx.compose.foundation.shape.RoundedCornerShape(12.dp))
                                            .clickable { dateText = date.toString(); dayOnly = true }
                                            .semantics { selected = isSelected; contentDescription = date.format(DateTimeFormatter.ofLocalizedDate(java.time.format.FormatStyle.FULL).withLocale(locale)) + ", $count " + tr("eventos", "events", "eventos") }
                                            .padding(vertical = 6.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(3.dp)) {
                                            Text(date.dayOfMonth.toString(), fontWeight = if (date == today || isSelected) FontWeight.Bold else FontWeight.Normal,
                                                color = if (date == today) MaterialTheme.colorScheme.primary else LocalAppAppearance.current.text)
                                            Box(Modifier.size(4.dp).background(if (count > 0) MaterialTheme.colorScheme.primary else androidx.compose.ui.graphics.Color.Transparent, CircleShape))
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
            if (loading) item { Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
                AppLoadingIndicator(Modifier.size(20.dp)); Text(tr("Atualizando calendário…", "Updating calendar…", "Actualizando calendario…"))
            } }
            if (failed) item {
                AppStatusMessage(if (catalog != null) tr("Não foi possível atualizar. Exibindo os dados salvos.", "Could not refresh. Showing saved data.", "No se pudo actualizar. Mostrando datos guardados.")
                    else tr("Não foi possível carregar este ano. Verifique a conexão ou tente outro ano.", "Could not load this year. Check your connection or try another year.", "No se pudo cargar este año. Revisa la conexión o prueba otro año."), error = true)
                AppSecondaryButton(onClick = { retry++ }, enabled = !loading) { Text(tr("Tentar novamente", "Retry", "Reintentar")) }
            }
            if (categories.isNotEmpty()) item {
                LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    item { AppChoiceChip(activeFilter.isBlank(), { filter = "" }, { Text(tr("Todos", "All", "Todos")) }) }
                    items(categories, key = { it.first }) { (key, label) ->
                        AppChoiceChip(activeFilter == key, { filter = if (activeFilter == key) "" else key }, { Text(label) })
                    }
                }
            }
            item {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        Text(tr("Eventos", "Events", "Eventos"), Modifier.weight(1f),
                            style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                        Text(displayedEvents.size.toString(), style = MaterialTheme.typography.titleMedium)
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        AppChoiceChip(dayOnly, { dayOnly = true }, { Text(tr("Dia selecionado", "Selected day", "Día seleccionado")) })
                        AppChoiceChip(!dayOnly, { dayOnly = false }, {
                            Text(if (weekView) tr("Toda a semana", "Whole week", "Toda la semana")
                                else tr("Todo o mês", "Whole month", "Todo el mes"))
                        })
                    }
                    if (dayOnly) Text(
                        selectedDate.format(DateTimeFormatter.ofPattern("EEEE, dd MMMM", locale)).replaceFirstChar { it.titlecase(locale) },
                        style = MaterialTheme.typography.bodyMedium)
                }
            }
            if (displayedEvents.isEmpty() && !loading && !failed) item {
                AppStatusMessage(tr("Nenhum evento publicado para esta seleção.", "No published events for this selection.", "No hay eventos publicados para esta selección."))
            }
            items(displayedEvents, key = { it.id }) { event ->
                AppSectionCard(Modifier.fillMaxWidth().clickable { selectedEvent = event }) {
                    Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        Box(Modifier.size(64.dp)
                            .background(appControlFill(false), androidx.compose.foundation.shape.RoundedCornerShape(14.dp)),
                            contentAlignment = Alignment.Center) {
                            if (event.image != null) AsyncImage(event.image, null, Modifier.size(56.dp))
                            else Text(event.start.dayOfMonth.toString(), style = MaterialTheme.typography.headlineSmall)
                        }
                        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            Text(event.categoryLabel, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)
                            Text(event.title, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                            val shortDate = DateTimeFormatter.ofPattern("dd MMM · HH:mm", locale)
                            val interval = if (event.start.toLocalDate() == event.end.toLocalDate()) {
                                event.start.format(shortDate) + " – " + event.end.format(DateTimeFormatter.ofPattern("HH:mm", locale))
                            } else event.start.format(shortDate) + " → " + event.end.format(shortDate)
                            Text(interval, style = MaterialTheme.typography.bodySmall)
                        }
                        Text("›", style = MaterialTheme.typography.titleLarge)
                    }
                }
            }
            item {
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(tr("Fonte: Laboratório do Sam · horários locais", "Source: Laboratório do Sam · local times", "Fuente: Laboratório do Sam · horas locales"), style = MaterialTheme.typography.bodySmall)
                    catalog?.let { Text(tr("Última atualização: ", "Last updated: ", "Última actualización: ") + DateFormat.getDateTimeInstance(DateFormat.SHORT, DateFormat.SHORT, locale).format(Date(it.fetchedAt)), style = MaterialTheme.typography.bodySmall) }
                    Text(tr("Títulos e categorias seguem o idioma da fonte.", "Titles and categories use the source language.", "Los títulos y categorías usan el idioma de la fuente."), style = MaterialTheme.typography.bodySmall)
                }
            }
        }
    }
    selectedEvent?.let { event ->
        androidx.compose.ui.window.Dialog(onDismissRequest = { selectedEvent = null }) {
            AppSectionCard(Modifier.fillMaxWidth()) {
                Column(Modifier.verticalScroll(rememberScrollState()).padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text(event.categoryLabel, style = MaterialTheme.typography.labelMedium)
                    Text(event.title, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                    Text(tr("Início: ", "Starts: ", "Inicio: ") + event.start.format(formatter))
                    Text(tr("Fim: ", "Ends: ", "Fin: ") + event.end.format(formatter))
                    event.link?.let { link -> AppActionButton(onClick = { openExternalUrl(context, link) }, modifier = Modifier.fillMaxWidth()) { Text(tr("Ver publicação original", "View original post", "Ver publicación original")) } }
                    AppSecondaryButton(onClick = { selectedEvent = null }, modifier = Modifier.fillMaxWidth()) { Text(tr("Fechar", "Close", "Cerrar")) }
                }
            }
        }
    }
}
