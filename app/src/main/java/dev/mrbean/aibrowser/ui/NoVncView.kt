package dev.mrbean.aibrowser.ui

import android.net.Uri
import android.view.ViewGroup
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.runtime.Composable
import androidx.compose.runtime.key
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.viewinterop.AndroidView

/**
 * A WebView hosting noVNC's `vnc.html` on the loopback URL, used by the Home
 * strip and the full-screen Preview. The WebView only ever loads 127.0.0.1
 * URLs; it is created with MATCH_PARENT params (without them noVNC's container
 * collapses and the canvas draws at 0 by 0) and is recreated only when
 * [viewOnly] changes, so callers keep a live viewer across recompositions.
 */
@Composable
fun NoVncView(
    modifier: Modifier = Modifier,
    viewOnly: Boolean,
    onReady: (WebView) -> Unit = {},
) {
    val url = remember(viewOnly) {
        // reconnect: noVNC retries on its own when the VNC server is not up
        // yet or drops, instead of parking on "Failed to connect".
        "http://127.0.0.1:6080/vnc.html" +
            "?autoconnect=true&resize=scale&reconnect=true&reconnect_delay=2000" +
            "&view_only=${if (viewOnly) "1" else "0"}"
    }
    key(viewOnly) {
        AndroidView(
            modifier = modifier,
            factory = { ctx ->
                WebView(ctx).apply {
                    // Without explicit params a WebView measures as wrap-content
                    // and reports a zero CSS viewport height, so noVNC's
                    // container collapses and the canvas draws at 0 by 0.
                    layoutParams = ViewGroup.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.MATCH_PARENT,
                    )
                    settings.javaScriptEnabled = true
                    settings.domStorageEnabled = true
                    webViewClient = object : WebViewClient() {
                        override fun shouldOverrideUrlLoading(
                            view: WebView?,
                            request: WebResourceRequest?,
                        ): Boolean = request?.url?.host != "127.0.0.1"

                        @Deprecated("Deprecated in Java")
                        override fun shouldOverrideUrlLoading(view: WebView?, url: String?): Boolean =
                            Uri.parse(url).host != "127.0.0.1"
                    }
                    onReady(this)
                }
            },
            update = { wv ->
                if (wv.url != url) wv.loadUrl(url)
            },
        )
    }
}