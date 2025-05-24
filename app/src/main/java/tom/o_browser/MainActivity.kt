package tom.o_browser

import android.app.Activity
import android.app.DownloadManager
import android.content.Context
import android.content.pm.PackageManager
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
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.border
import androidx.compose.foundation.interaction.FocusInteraction
import androidx.compose.foundation.interaction.InteractionSource
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.add
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.displayCutout
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.imePadding
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsControllerCompat
import kotlinx.coroutines.flow.collectLatest
import tom.o_browser.ui.theme.O_browserTheme
import java.util.jar.Manifest
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.FocusRequesterModifierNode
import androidx.core.view.WindowInsetsCompat


val default_page: String = "https://arc.net/"
var corner_radius: Dp = 24.dp


class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

//        WindowInsetsControllerCompat(window,window.decorView).isAppearanceLightNavigationBars = false


        setContent {
            O_browserTheme {
                val isDarkMode = isSystemInDarkTheme()
                val isImmersiveMode = remember { mutableStateOf(false) }

                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = when {
                        isDarkMode -> Color.Black
                        isImmersiveMode.value -> Color.Black
                        else -> Color.White
                    }
                ) {
                    MainScreen(
                        initialUrl = default_page,
                        onImmersiveModeChanged = { isImmersiveMode.value = it }
                    )
                }
            }
        }
    }


}

fun Activity.setImmersiveMode(enabled: Boolean) {
    val controller = WindowInsetsControllerCompat(window, window.decorView)
    if (enabled) {
        controller.hide(
            WindowInsetsCompat.Type.statusBars() or WindowInsetsCompat.Type.navigationBars()
        )
        controller.systemBarsBehavior =
            WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
    } else {
        controller.show(
            WindowInsetsCompat.Type.statusBars() or WindowInsetsCompat.Type.navigationBars()
        )
    }
}

@Composable
fun MainScreen(
    initialUrl: String,
    onImmersiveModeChanged: (Boolean) -> Unit
) {
    var text by remember { mutableStateOf(TextFieldValue(initialUrl)) }
    val history = remember { mutableStateListOf(initialUrl) }
    var currentIndex by remember { mutableStateOf(0) }
    var isSearchBarVisible by remember { mutableStateOf(true) }
    val focusRequester = remember { FocusRequester() }

    val topPadding = if (isSearchBarVisible) {
        WindowInsets.systemBars.asPaddingValues().calculateTopPadding()
    } else {
        WindowInsets.displayCutout.asPaddingValues().calculateTopPadding()
    }

    val activity = LocalContext.current as? Activity

    BackHandler(enabled = !isSearchBarVisible || currentIndex > 0) {
        if (!isSearchBarVisible) {
            isSearchBarVisible = true
        } else if (currentIndex > 0) {
            currentIndex--
            text = TextFieldValue(history[currentIndex])
        }
    }

    LaunchedEffect(isSearchBarVisible) {
        if (isSearchBarVisible) focusRequester.requestFocus()
        val immersive = !isSearchBarVisible
        activity?.setImmersiveMode(immersive)
        onImmersiveModeChanged(immersive)
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .imePadding()
            .padding(top = topPadding)
    ) {
        val horizontalPadding = if (isSearchBarVisible) 8.dp else 0.dp

        Box(modifier = Modifier.weight(1f)) {
            WebViewContainer(
                url = history[currentIndex],
                onPageFinished = { newUrl ->
                    if (newUrl != history.getOrNull(currentIndex)) {
                        while (history.size > currentIndex + 1) history.removeAt(history.lastIndex)
                        history.add(newUrl)
                        currentIndex++
                    }
                    text = TextFieldValue(newUrl)
                },
                onWebViewTouched = { isSearchBarVisible = false },
                horizontalPadding = horizontalPadding
            )
        }

        AnimatedVisibility(
            visible = isSearchBarVisible,
            enter = expandVertically(tween(300)),
            exit = shrinkVertically(tween(300))
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .animateContentSize()
            ) {
                SearchBar(
                    value = text,
                    onValueChange = { text = it },
                    onSearch = {
                        val input = text.text
                        val resolvedUrl = if (input.startsWith("http")) {
                            input
                        } else {
                            "https://www.google.com/search?q=${input.replace(" ", "+")}"
                        }

                        if (history.getOrNull(currentIndex) != resolvedUrl) {
                            while (history.size > currentIndex + 1) history.removeAt(history.lastIndex)
                            history.add(resolvedUrl)
                            currentIndex++
                        }

                        isSearchBarVisible = false
                    },
                    onFocusChanged = { focused -> if (focused) isSearchBarVisible = true },
                    focusRequester = focusRequester
                )
            }
        }
    }
}


@Composable
fun WebViewContainer(
    url: String,
    onPageFinished: (String) -> Unit,
    onWebViewTouched: () -> Unit,
    horizontalPadding: Dp = 0.dp
) {
    val context = LocalContext.current

    Box(modifier = Modifier
        .padding(horizontal = horizontalPadding)
        .fillMaxSize()) {
        AndroidView(
            factory = { ctx ->
                android.webkit.WebView(ctx).apply {
                    setOnTouchListener { _, _ ->
                        onWebViewTouched()
                        false
                    }

                    setDownloadListener { url, userAgent, contentDisposition, mimeType, _ ->
                        val filename = URLUtil.guessFileName(url, contentDisposition, mimeType)
                        val request = DownloadManager.Request(Uri.parse(url)).apply {
                            setMimeType(mimeType)
                            addRequestHeader("User-Agent", userAgent)
                            setDescription("Downloading file...")
                            setTitle(filename)
                            setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
                            setDestinationInExternalPublicDir(
                                Environment.DIRECTORY_DOWNLOADS,
                                filename
                            )
                            setAllowedOverMetered(true)
                            setAllowedOverRoaming(true)
                        }
                        val dm =
                            context.getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager
                        dm.enqueue(request)
                        Toast.makeText(context, "Downloading File...", Toast.LENGTH_SHORT).show()
                    }

                    settings.javaScriptEnabled = true
                    webViewClient = object : WebViewClient() {
                        override fun onPageFinished(
                            view: android.webkit.WebView?,
                            newUrl: String?
                        ) {
                            newUrl?.let { onPageFinished(it) }
                        }
                    }

                    loadUrl(url)
                }
            },
            update = { it.loadUrl(url) },
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
    onFocusChanged: (Boolean) -> Unit,
    focusRequester: FocusRequester,
    cornerRadius: Dp = corner_radius
) {
    val focusManager = LocalFocusManager.current

    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        singleLine = true,
        placeholder = { Text("Enter URL or search") },
        shape = RoundedCornerShape(cornerRadius),
        modifier = Modifier
            .fillMaxWidth()
            .padding(8.dp)
            .onFocusChanged {
                onFocusChanged(it.isFocused)
                if (it.isFocused) {
                    onValueChange(value.copy(selection = TextRange(0, value.text.length)))
                }
            }
            .focusRequester(focusRequester),
        keyboardOptions = KeyboardOptions.Default.copy(imeAction = ImeAction.Search),
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