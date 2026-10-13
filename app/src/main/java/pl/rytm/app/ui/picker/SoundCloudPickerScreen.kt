package pl.rytm.app.ui.picker

import android.annotation.SuppressLint
import android.graphics.Bitmap
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import kotlinx.coroutines.launch
import pl.rytm.app.R
import pl.rytm.app.domain.model.MusicKind
import pl.rytm.app.domain.model.MusicSelection
import pl.rytm.app.music.SoundCloudLinks
import pl.rytm.app.music.SoundCloudResolver
import java.net.URLEncoder

/**
 * Wybór muzyki: oficjalna strona SoundCloud (wyszukiwarka, Twoje playlisty po zalogowaniu)
 * w przeglądarce wbudowanej. Aplikacja tylko odczytuje adres otwartego utworu/playlisty.
 * Hasła wpisujesz wyłącznie na stronie SoundCloud – Rytm ich nie widzi ani nie zapisuje.
 */
@SuppressLint("SetJavaScriptEnabled")
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SoundCloudPickerScreen(onBack: () -> Unit, onPicked: (MusicSelection) -> Unit, startUrl: String? = null) {
    var tab by remember { mutableIntStateOf(0) }
    var query by remember { mutableStateOf("") }
    var currentUrl by remember { mutableStateOf(startUrl ?: "https://soundcloud.com/discover") }
    var loading by remember { mutableStateOf(false) }
    var webView by remember { mutableStateOf<WebView?>(null) }
    var resolving by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var pasted by remember { mutableStateOf("") }
    var preview by remember { mutableStateOf<MusicSelection?>(null) }
    var previewWarning by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()
    val resolver = remember { SoundCloudResolver() }

    val detected = SoundCloudLinks.parse(currentUrl) as? SoundCloudLinks.Parsed.Supported

    fun resolve(input: String, pageTitle: String? = null) {
        scope.launch {
            resolving = true
            when (val r = resolver.resolve(input)) {
                is SoundCloudResolver.Result.Ok -> {
                    previewWarning = r.warning
                    // Prywatna treść bez metadanych z oEmbed – użyj tytułu z otwartej strony.
                    preview = if (r.warning != null && !pageTitle.isNullOrBlank()) r.selection.copy(title = pageTitle) else r.selection
                }
                is SoundCloudResolver.Result.Error -> error = r.message
            }
            resolving = false
        }
    }

    BackHandler(enabled = tab == 0 && webView?.canGoBack() == true) { webView?.goBack() }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Muzyka z SoundCloud") },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Wstecz") } },
            )
        },
        bottomBar = {
            if (tab == 0) {
                Card(
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(8.dp),
                ) {
                    Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                        Icon(painterResource(R.drawable.ic_music), null)
                        Spacer(Modifier.width(8.dp))
                        Text(
                            when (detected?.kind) {
                                MusicKind.TRACK -> "Otwarty utwór – możesz go wybrać"
                                MusicKind.PLAYLIST -> "Otwarta playlista – możesz ją wybrać"
                                null -> "Otwórz utwór lub playlistę, aby ją wybrać"
                            },
                            style = MaterialTheme.typography.bodyMedium,
                            modifier = Modifier.weight(1f),
                        )
                        Button(enabled = detected != null && !resolving, onClick = {
                            val view = webView
                            if (view == null) {
                                resolve(currentUrl)
                            } else {
                                // Własna prywatna playlista: weź link z kodem dostępu (s-…) z danych strony.
                                view.evaluateJavascript(SoundCloudLinks.SECRET_LINK_JS) { result ->
                                    val json = runCatching { org.json.JSONTokener(result).nextValue() as? String }.getOrNull()
                                    val found = json?.takeIf { it.isNotBlank() }?.let { runCatching { org.json.JSONObject(it) }.getOrNull() }
                                    val secretUrl = found?.optString("u")?.takeIf { it.isNotBlank() }
                                    resolve(secretUrl ?: currentUrl, found?.optString("t"))
                                }
                            }
                        }) { Text("Wybierz") }
                    }
                }
            }
        },
    ) { padding ->
        Column(
            Modifier
                .fillMaxSize()
                .padding(padding)
                .imePadding(),
        ) {
            TabRow(selectedTabIndex = tab) {
                Tab(selected = tab == 0, onClick = { tab = 0 }, text = { Text("Szukaj w SoundCloud") })
                Tab(selected = tab == 1, onClick = { tab = 1 }, text = { Text("Wklej link") })
            }
            if (resolving || loading) LinearProgressIndicator(Modifier.fillMaxWidth())
            if (tab == 0) {
                Row(Modifier.padding(8.dp), verticalAlignment = Alignment.CenterVertically) {
                    OutlinedTextField(
                        value = query,
                        onValueChange = { query = it },
                        placeholder = { Text("Szukaj utworu, artysty, playlisty…") },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                        keyboardActions = KeyboardActions(onSearch = {
                            webView?.loadUrl(searchUrl(query))
                        }),
                        modifier = Modifier.weight(1f),
                    )
                    IconButton(onClick = { webView?.loadUrl(searchUrl(query)) }) { Icon(Icons.Default.Search, "Szukaj") }
                }
                Box(Modifier.weight(1f)) {
                    AndroidView(
                        modifier = Modifier.fillMaxSize(),
                        factory = { ctx ->
                            WebView(ctx).apply {
                                settings.javaScriptEnabled = true
                                settings.domStorageEnabled = true
                                settings.mediaPlaybackRequiresUserGesture = true
                                webViewClient = object : WebViewClient() {
                                    override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest): Boolean {
                                        val host = request.url.host.orEmpty()
                                        // Zostajemy na SoundCloud (i jego stronach logowania); reszta – poza aplikacją.
                                        return !(host.endsWith("soundcloud.com") || host.endsWith("sndcdn.com") ||
                                            host.endsWith("google.com") || host.endsWith("facebook.com") ||
                                            host.endsWith("apple.com"))
                                    }

                                    override fun onPageStarted(view: WebView, url: String, favicon: Bitmap?) {
                                        loading = true
                                        currentUrl = url
                                    }

                                    override fun onPageFinished(view: WebView, url: String) {
                                        loading = false
                                        currentUrl = url
                                    }

                                    override fun doUpdateVisitedHistory(view: WebView, url: String, isReload: Boolean) {
                                        // SoundCloud to aplikacja jednostronicowa – adres zmienia się bez przeładowania.
                                        currentUrl = url
                                    }
                                }
                                loadUrl(currentUrl)
                                webView = this
                            }
                        },
                        onRelease = { it.destroy() },
                    )
                }
            } else {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text(
                        "W aplikacji SoundCloud otwórz utwór lub playlistę → „Udostępnij” → „Kopiuj link” i wklej go tutaj. " +
                            "Możesz też wybrać „Udostępnij” → „Rytm”. Prywatne playlisty: użyj linku z kodem „s-…” " +
                            "(tak wygląda link prywatny) albo kodu „Osadź” (embed).",
                        style = MaterialTheme.typography.bodyMedium,
                    )
                    OutlinedTextField(
                        value = pasted,
                        onValueChange = { pasted = it },
                        label = { Text("Link SoundCloud") },
                        placeholder = { Text("https://soundcloud.com/…") },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri, imeAction = ImeAction.Done),
                        modifier = Modifier.fillMaxWidth(),
                    )
                    Button(enabled = pasted.isNotBlank() && !resolving, onClick = { resolve(pasted) }) { Text("Sprawdź link") }
                }
            }
        }
    }

    preview?.let { sel ->
        AlertDialog(
            onDismissRequest = { preview = null },
            title = { Text(if (sel.kind == MusicKind.PLAYLIST) "Wybrać playlistę?" else "Wybrać utwór?") },
            text = {
                Column {
                    Text(sel.title, style = MaterialTheme.typography.titleMedium, maxLines = 3, overflow = TextOverflow.Ellipsis)
                    if (sel.artist.isNotBlank()) Text(sel.artist)
                    Spacer(Modifier.width(4.dp))
                    previewWarning?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.tertiary) }
                    if (SoundCloudLinks.isSecret(sel.url) || sel.embedUrl?.contains("secret_token") == true) {
                        Text("Prywatna treść – link z kodem dostępu zostanie zapamiętany.", style = MaterialTheme.typography.bodySmall)
                    }
                    Text(
                        "Muzyka odtworzy się w oficjalnym odtwarzaczu SoundCloud. Niektóre utwory (np. tylko dla SoundCloud Go+ " +
                            "lub z wyłączonym osadzaniem) mogą grać jako 30-sekundowy fragment albo wcale.",
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    preview = null
                    onPicked(sel)
                }) { Text("Wybierz") }
            },
            dismissButton = { TextButton(onClick = { preview = null }) { Text("Anuluj") } },
        )
    }
    error?.let { msg ->
        AlertDialog(
            onDismissRequest = { error = null },
            title = { Text("Nie udało się") },
            text = { Text(msg) },
            confirmButton = { TextButton(onClick = { error = null }) { Text("OK") } },
        )
    }
}

private fun searchUrl(q: String): String =
    if (q.isBlank()) "https://soundcloud.com/discover" else "https://soundcloud.com/search?q=" + URLEncoder.encode(q.trim(), "UTF-8")
