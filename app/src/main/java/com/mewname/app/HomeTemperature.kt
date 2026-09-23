package com.mewname.app

import android.content.Context
import android.Manifest
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.repeatOnLifecycle
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import kotlin.math.roundToInt

private data class WeatherCity(val label: String, val latitude: Double, val longitude: Double) {
    fun json() = JSONObject().put("label", label).put("latitude", latitude).put("longitude", longitude).toString()
}

private class HomeWeather(context: Context) {
    private val prefs = context.getSharedPreferences("home_weather", Context.MODE_PRIVATE)
    fun city(): WeatherCity? = runCatching {
        JSONObject(prefs.getString("city", null) ?: return null).let {
            WeatherCity(it.getString("label"), it.getDouble("latitude"), it.getDouble("longitude"))
        }
    }.getOrNull()
    fun automatic() = prefs.getBoolean("automatic", city() == null)
    fun automatic(enabled: Boolean) { prefs.edit().putBoolean("automatic", enabled).apply() }
    fun permissionAsked() = prefs.getBoolean("permission_asked", false)
    fun markPermissionAsked() { prefs.edit().putBoolean("permission_asked", true).apply() }
    fun select(city: WeatherCity) {
        prefs.edit().putString("city", city.json()).remove("temperature").remove("updated").apply()
    }
    fun invalidate() { prefs.edit().remove("updated").apply() }
    fun cached(): Double? {
        val age = System.currentTimeMillis() - prefs.getLong("updated", 0)
        return if (age in 0 until 30 * 60_000L)
            prefs.getString("temperature", null)?.toDoubleOrNull()?.takeIf { it.isFinite() } else null
    }
    suspend fun search(query: String, language: String): List<WeatherCity> {
        val data = request("https://geocoding-api.open-meteo.com/v1/search?name=" +
            URLEncoder.encode(query.trim(), "UTF-8") + "&count=5&language=" + language)
        val results = data.optJSONArray("results") ?: return emptyList()
        return (0 until results.length()).map { index ->
            val city = results.getJSONObject(index)
            WeatherCity(listOf(city.getString("name"), city.optString("admin1"), city.optString("country"))
                .filter { it.isNotBlank() }.distinct().joinToString(", "),
                city.getDouble("latitude"), city.getDouble("longitude"))
        }
    }
    suspend fun temperature(city: WeatherCity): Double {
        cached()?.let { return it }
        val data = request("https://api.open-meteo.com/v1/forecast?latitude=" + city.latitude +
            "&longitude=" + city.longitude + "&current=temperature_2m&temperature_unit=celsius")
        val value = data.getJSONObject("current").getDouble("temperature_2m")
        require(value.isFinite())
        if (city() == city) prefs.edit().putString("temperature", value.toString())
            .putLong("updated", System.currentTimeMillis()).apply()
        return value
    }
    private suspend fun request(address: String): JSONObject = withContext(Dispatchers.IO) {
        val connection = URL(address).openConnection() as HttpURLConnection
        try {
            connection.connectTimeout = 10_000
            connection.readTimeout = 10_000
            check(connection.responseCode == 200)
            connection.inputStream.bufferedReader().use { JSONObject(it.readText()) }
        } finally {
            connection.disconnect()
        }
    }
}

@Composable
internal fun HomeTemperature() {
    val context = LocalContext.current
    val language = appLanguage()
    val repository = remember(context) { HomeWeather(context.applicationContext) }
    var city by remember { mutableStateOf(repository.city()) }
    var temperature by remember { mutableStateOf(repository.cached()) }
    var unavailable by remember { mutableStateOf(false) }
    var loading by remember { mutableStateOf(false) }
    var showDialog by rememberSaveable { mutableStateOf(false) }
    var refresh by remember { mutableIntStateOf(0) }
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    var automatic by remember { mutableStateOf(repository.automatic()) }
    var locating by remember { mutableStateOf(false) }
    var locationFailed by remember { mutableStateOf(false) }
    var locationAttempt by remember { mutableIntStateOf(0) }
    var permissionPending by remember { mutableStateOf(false) }
    var fallbackShown by rememberSaveable { mutableStateOf(false) }
    val locationPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        permissionPending = false
        if (granted) {
            locationAttempt++
        } else {
            locating = false
            locationFailed = true
            fallbackShown = true
            showDialog = true
        }
    }
    LaunchedEffect(automatic, locationAttempt, lifecycle) {
        if (!automatic || permissionPending) return@LaunchedEffect
        repository.automatic(true)
        if (!WeatherDeviceLocation.permitted(context)) {
            if (!repository.permissionAsked()) {
                repository.markPermissionAsked()
                locating = true
                permissionPending = true
                locationPermission.launch(Manifest.permission.ACCESS_COARSE_LOCATION)
            } else {
                locationFailed = true
                if (!fallbackShown) { fallbackShown = true; showDialog = true }
            }
            return@LaunchedEffect
        }
        lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) {
            while (true) {
                locating = true
                try {
                    val coordinates = WeatherDeviceLocation.coordinates(context)
                    locationFailed = coordinates == null
                    if (coordinates != null) {
                        val detected = WeatherCity(
                            lt(language, "Localização aproximada do aparelho",
                                "Approximate device location", "Ubicación aproximada del dispositivo"),
                            coordinates.first, coordinates.second)
                        if (city != detected) {
                            repository.select(detected)
                            temperature = null
                            city = detected
                        }
                    } else if (!fallbackShown) {
                        fallbackShown = true
                        showDialog = true
                    }
                } finally { locating = false }
                delay(30 * 60_000L)
            }
        }
    }
    LaunchedEffect(city, refresh, lifecycle) {
        temperature = repository.cached()
        unavailable = false
        loading = false
        val selected = city ?: return@LaunchedEffect
        lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) {
            while (true) {
                loading = temperature == null
                try {
                    temperature = repository.temperature(selected)
                    unavailable = false
                } catch (cancelled: CancellationException) {
                    throw cancelled
                } catch (_: Exception) {
                    temperature = repository.cached()
                    unavailable = true
                } finally {
                    loading = false
                }
                delay(if (unavailable) 60_000L else 15 * 60_000L)
            }
        }
    }
    TextButton(onClick = { showDialog = true }, contentPadding = PaddingValues(horizontal = 8.dp),
        modifier = Modifier.heightIn(min = 48.dp)) {
        Text(when {
            locating && city == null -> lt(language, "Localizando…", "Locating…", "Localizando…")
            city == null -> lt(language, "Definir cidade", "Set city", "Elegir ciudad")
            temperature != null -> temperature!!.roundToInt().toString() + " °C"
            loading -> lt(language, "Consultando…", "Loading…", "Consultando…")
            unavailable -> lt(language, "Clima indisponível", "Weather unavailable", "Clima no disponible")
            else -> "— °C"
        },
            style = MaterialTheme.typography.labelLarge)
    }
    if (showDialog) {
        val scope = rememberCoroutineScope()
        val uriHandler = LocalUriHandler.current
        var query by rememberSaveable { mutableStateOf("") }
        var results by remember { mutableStateOf(emptyList<WeatherCity>()) }
        var searching by remember { mutableStateOf(false) }
        var message by remember { mutableStateOf<String?>(null) }
        AlertDialog(
            onDismissRequest = { showDialog = false },
            title = { Text(lt(language, "Temperatura", "Temperature", "Temperatura")) },
            text = {
                Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    city?.let { Text(it.label, style = MaterialTheme.typography.bodyMedium) }
                    if (locationFailed) Text(lt(language,
                        "Não foi possível obter a localização. Verifique a permissão e a localização do aparelho ou escolha uma cidade.",
                        "Location is unavailable. Check permission and device location, or choose a city.",
                        "No se pudo obtener la ubicación. Revisa el permiso y la ubicación del dispositivo o elige una ciudad."))
                    TextButton(enabled = !locating, onClick = {
                        repository.automatic(true)
                        automatic = true
                        locationFailed = false
                        fallbackShown = false
                        showDialog = false
                        if (WeatherDeviceLocation.permitted(context)) locationAttempt++
                        else {
                            permissionPending = true
                            repository.markPermissionAsked()
                            locationPermission.launch(Manifest.permission.ACCESS_COARSE_LOCATION)
                        }
                    }) { Text(lt(language, "Usar localização do aparelho",
                        "Use device location", "Usar ubicación del dispositivo")) }
                    if (unavailable) Text(lt(language, "Sem dados agora. Tente novamente.",
                        "No data right now. Try again.", "Sin datos ahora. Inténtalo de nuevo."))
                    Text(lt(language, "Escolha a cidade para consultar a temperatura.",
                        "Choose a city to check its temperature.", "Elige una ciudad para consultar la temperatura."))
                    OutlinedTextField(value = query, onValueChange = { query = it },
                        singleLine = true, label = { Text(lt(language, "Cidade", "City", "Ciudad")) })
                    TextButton(enabled = query.trim().length >= 2 && !searching, onClick = {
                        searching = true
                        results = emptyList()
                        message = null
                        val searchQuery = query
                        scope.launch {
                            try {
                                results = repository.search(searchQuery, when (language) {
                                    com.mewname.app.domain.AppLanguage.PT_BR -> "pt"
                                    com.mewname.app.domain.AppLanguage.ES -> "es"
                                    else -> "en"
                                })
                                if (results.isEmpty()) message = lt(language, "Nenhuma cidade encontrada.",
                                    "No cities found.", "No se encontraron ciudades.")
                            } catch (cancelled: CancellationException) {
                                throw cancelled
                            } catch (_: Exception) {
                                message = lt(language, "Não foi possível buscar. Verifique a conexão.",
                                    "Search failed. Check your connection.", "No se pudo buscar. Revisa la conexión.")
                            } finally { searching = false }
                        }
                    }) { Text(if (searching) lt(language, "Buscando…", "Searching…", "Buscando…")
                        else lt(language, "Buscar", "Search", "Buscar")) }
                    message?.let { Text(it) }
                    results.forEach { result ->
                        Text(result.label, modifier = Modifier.fillMaxWidth().clickable {
                            repository.automatic(false)
                            automatic = false
                            locating = false
                            locationFailed = false
                            repository.select(result)
                            temperature = null
                            city = result
                            refresh++
                            showDialog = false
                        }.padding(vertical = 14.dp))
                    }
                    TextButton(onClick = { uriHandler.openUri("https://open-meteo.com/") }) {
                        Text(lt(language, "Dados: Open-Meteo · Localidades: GeoNames",
                            "Data: Open-Meteo · Locations: GeoNames", "Datos: Open-Meteo · Lugares: GeoNames"),
                            style = MaterialTheme.typography.labelSmall)
                    }
                }
            },
            confirmButton = { TextButton(enabled = city != null, onClick = { repository.invalidate(); refresh++; showDialog = false }) {
                Text(lt(language, "Atualizar", "Refresh", "Actualizar"))
            } },
            dismissButton = { TextButton(onClick = { showDialog = false }) {
                Text(lt(language, "Fechar", "Close", "Cerrar"))
            } }
        )
    }
}