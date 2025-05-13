package tom.o_browser

import android.app.DownloadManager
import android.content.Context
import android.net.Uri
import android.os.Bundle
import android.os.Environment
import android.webkit.URLUtil
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.border
import androidx.compose.foundation.interaction.FocusInteraction
import androidx.compose.foundation.interaction.InteractionSource
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsControllerCompat
import kotlinx.coroutines.flow.collectLatest
import tom.o_browser.ui.theme.O_browserTheme

val default_page: String = "https://oo3.deno.dev/i"
var corner_radius: Dp = 48.dp
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

//        WindowInsetsControllerCompat(window,window.decorView).isAppearanceLightNavigationBars = false

        setContent {
            O_browserTheme {
                Surface(modifier = Modifier.fillMaxSize(),
                    color = if (isSystemInDarkTheme()) Color.Black else Color.White) {
                    MainScreen(default_page)

                }
            }
        }
    }
}


@Composable
fun MainScreen(initialUrl: String) {
    var text by remember { mutableStateOf(TextFieldValue(initialUrl)) }

    // ✅ URL history stack
    val history = remember { mutableStateListOf(initialUrl) }
    var currentIndex by remember { mutableStateOf(0) }

    // ✅ System back = go back in history
    BackHandler(enabled = currentIndex > 0) {
        if (currentIndex > 0) {
            currentIndex--
            text = TextFieldValue(history[currentIndex]) // Update search box too
        }
    }

    Column(
        modifier = Modifier
            .padding(WindowInsets.systemBars.asPaddingValues())
            .fillMaxSize()
    ) {
        SearchBar(
            value = text,
            onValueChange = { text = it },
            onSearch = {
                val input = text.text
                val resolvedUrl = if (input.startsWith("http://") || input.startsWith("https://")) {
                    input
                } else {
                    "https://www.google.com/search?q=" + input.replace(" ", "+")
                }

                // Avoid adding duplicate or navigating forward in stack
                if (history.getOrNull(currentIndex) != resolvedUrl) {
                    // Trim forward history
                    while (history.size > currentIndex + 1) {
                        history.removeAt(history.lastIndex)
                    }

                    history.add(resolvedUrl)
                    currentIndex++
                }
            }
        )

        Box(modifier = Modifier.weight(1f)) {
            WebView(
                url = history[currentIndex],
                onPageFinished = { newUrl ->
                    if (newUrl != history.getOrNull(currentIndex)) {
                        // Trim forward history
                        while (history.size > currentIndex + 1) history.removeAt(history.lastIndex)

                        history.add(newUrl)
                        currentIndex++
                    }

                    text = TextFieldValue(newUrl)
                }
            )
        }
    }
}

@Composable
fun WebView(
    url: String,
    onPageFinished: (String) -> Unit
) {
    Box(
        modifier = Modifier
            .padding(horizontal = 8.dp)
            .border(2.dp, Color.Black, RoundedCornerShape(corner_radius))
            .fillMaxSize()
    ) {
        AndroidView(
            factory = { context ->
                WebView(context).apply {
                    setDownloadListener { url, userAgent, contentDisposition, mimeType, contentLength ->
                        val request = DownloadManager.Request(Uri.parse(url))
                        request.setMimeType(mimeType)
                        request.addRequestHeader("User-Agent", userAgent)
                        request.setDescription("Downloading file...")
                        request.setTitle(URLUtil.guessFileName(url, contentDisposition, mimeType))
                        request.allowScanningByMediaScanner()
                        request.setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
                        request.setDestinationInExternalPublicDir(
                            Environment.DIRECTORY_DOWNLOADS,
                            URLUtil.guessFileName(url, contentDisposition, mimeType)
                        )

                        val dm = context.getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager
                        dm.enqueue(request)

                        Toast.makeText(context, "Downloading File...", Toast.LENGTH_LONG).show()
                    }
                    settings.javaScriptEnabled = true
                    webViewClient = object : WebViewClient() {
                        override fun onPageFinished(view: WebView?, newUrl: String?) {
                            newUrl?.let { onPageFinished(it) }
                        }
                    }
                    loadUrl(url)
                }
            },
            update = {
                if (it.url != url) it.loadUrl(url)
            },
            modifier = Modifier
                .fillMaxSize()
                .clip(RoundedCornerShape(corner_radius))
        )
    }
}





@Composable
fun SearchBar(
    value: TextFieldValue,
    onValueChange: (TextFieldValue) -> Unit,
    onSearch: () -> Unit,
    cornerRadius: Dp = 48.dp
) {
    val focusManager = LocalFocusManager.current
    val interactionSource = remember { MutableInteractionSource() }

    // Track focus state to manage selection
    var hasFocus by remember { mutableStateOf(false) }

    LaunchedEffect(interactionSource) {
        interactionSource.interactions.collectLatest { interaction ->
            when (interaction) {
                is FocusInteraction.Focus -> {
                    hasFocus = true
                    onValueChange(
                        value.copy(selection = TextRange(0, value.text.length))
                    )
                }

                is FocusInteraction.Unfocus -> {
                    hasFocus = false
                    onValueChange(
                        value.copy(selection = TextRange(value.text.length)) // clears selection
                    )
                }

                else -> {}
            }
        }
    }

    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        singleLine = true,
        placeholder = { Text("enter url") },
        shape = RoundedCornerShape(cornerRadius),
        interactionSource = interactionSource,
        modifier = Modifier
            .fillMaxWidth()
            .padding(8.dp),
        keyboardOptions = KeyboardOptions.Default.copy(
            imeAction = ImeAction.Search
        ),
        keyboardActions = KeyboardActions(
            onSearch = {
                focusManager.clearFocus()
                onSearch()
            }
        )
    )
}
@Preview(showBackground = true)
@Composable
fun GreetingPreview() {
    O_browserTheme {
    }
}