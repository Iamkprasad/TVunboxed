package com.tvunboxed.app.web

import android.annotation.SuppressLint
import android.content.Context
import androidx.activity.OnBackPressedCallback
import androidx.activity.OnBackPressedDispatcher
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.graphics.Color
import android.net.http.SslError
import android.util.Log
import android.webkit.ConsoleMessage
import android.webkit.SslErrorHandler
import android.webkit.WebChromeClient
import android.webkit.WebResourceError
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.graphics.Color as ComposeColor
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.webkit.WebViewAssetLoader
import com.tvunboxed.app.BuildConfig
import com.tvunboxed.app.MainViewModel

private const val TAG = "WebViewDiag"
private const val ASSET_HOST = "appassets.androidplatform.net"
private const val DEMO_URL = "https://$ASSET_HOST/assets/demo2.html"

private val RENDER_DIAG_JS = """
(function(){
  function rect(el){ if(!el) return null; var r=el.getBoundingClientRect(); return {x:r.x,y:r.y,w:r.width,h:r.height}; }
  var body=document.body;
  var cs=getComputedStyle(body);
  var device=document.getElementById('device');
  var firstPage=document.querySelector('.page.active')||document.querySelector('.page');
  var pcs=firstPage?getComputedStyle(firstPage):null;
  var nonzero=0;
  document.querySelectorAll('*').forEach(function(e){ if(e.offsetWidth>0 && e.offsetHeight>0) nonzero++; });
  var visibleChildren=0;
  if(device){ Array.prototype.forEach.call(device.children,function(c){
    var r=c.getBoundingClientRect(), s=getComputedStyle(c);
    if(r.width>0 && r.height>0 && s.visibility!=='hidden' && s.display!=='none' && parseFloat(s.opacity)>0) visibleChildren++;
  }); }
  return JSON.stringify({
    innerWidth:window.innerWidth, innerHeight:window.innerHeight,
    docClientWidth:document.documentElement.clientWidth, docClientHeight:document.documentElement.clientHeight,
    bodyClientWidth:body.clientWidth, bodyClientHeight:body.clientHeight,
    bodyScrollHeight:body.scrollHeight,
    bodyBg:cs.backgroundColor, bodyColor:cs.color,
    bodyDisplay:cs.display, bodyVisibility:cs.visibility, bodyOpacity:cs.opacity,
    bodyMinHeight:cs.minHeight, bodyComputedHeight:cs.height,
    deviceComputedHeight:device?getComputedStyle(device).height:null,
    deviceComputedMaxHeight:device?getComputedStyle(device).maxHeight:null,
    deviceOverflow:device?getComputedStyle(device).overflow:null,
    screenComputedHeight:(function(){var e=document.getElementById('screen');return e?getComputedStyle(e).height:null})(),
    supportsDvh:CSS.supports('height','100dvh'), supportsVh:CSS.supports('height','100vh'),
    visualViewportHeight:window.visualViewport?window.visualViewport.height:null,
    deviceRect:rect(device), pageRect:rect(firstPage),
    pageDisplay:pcs?pcs.display:null, pageVisibility:pcs?pcs.visibility:null, pageOpacity:pcs?pcs.opacity:null,
    nonzeroElements:nonzero, deviceVisibleChildren:visibleChildren
  });
})()
""".trimIndent()

/**
 * Hosts the original demo2.html inside Compose using AndroidView.
 * Content is loaded through WebViewAssetLoader from a trusted https origin;
 * external navigation is blocked.
 */
@SuppressLint("SetJavaScriptEnabled")
@Composable
fun WebViewScreen(vm: MainViewModel, backDispatcher: OnBackPressedDispatcher, owner: androidx.lifecycle.LifecycleOwner, modifier: Modifier = Modifier) {
    var diagnostic by remember { mutableStateOf<String?>(null) }
    var webViewRef by remember { mutableStateOf<WebView?>(null) }
    val context = LocalContext.current

    DisposableEffect(Unit) {
        val manager = context.getSystemService(Context.SENSOR_SERVICE) as SensorManager
        val sensor = manager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)
        val listener = object : SensorEventListener {
            override fun onSensorChanged(event: SensorEvent?) {
                val v = event?.values ?: return
                val gamma = Math.toDegrees(Math.asin((v[0] / 9.81).coerceIn(-1.0, 1.0))).toFloat()
                val beta = Math.toDegrees(Math.asin((-v[1] / 9.81).coerceIn(-1.0, 1.0))).toFloat()
                webViewRef?.evaluateJavascript("if(window.__tvMotionData)window.__tvMotionData($gamma,$beta)", null)
            }
            override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {}
        }
        if (sensor != null) manager.registerListener(listener, sensor, SensorManager.SENSOR_DELAY_UI)
        onDispose { manager.unregisterListener(listener) }
    }

    DisposableEffect(backDispatcher) {
        val callback = object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                val wv = webViewRef
                if (wv == null) {
                    isEnabled = false
                    backDispatcher.onBackPressed()
                    return
                }
                wv.evaluateJavascript("!!(window.__tvBack&&window.__tvBack())") { handled ->
                    if (handled != "true") {
                        isEnabled = false
                        backDispatcher.onBackPressed()
                    }
                }
            }
        }
        backDispatcher.addCallback(owner, callback)
        onDispose { callback.remove() }
    }

    LaunchedEffect(Unit) {
        Log.d(TAG, "composeModifiers: parentBox=fillMaxSize+background(#05060C); androidView=fillMaxSize")
    }

    Box(Modifier.fillMaxSize().background(ComposeColor(0xFF05060C)).onGloballyPositioned {
        Log.d(TAG, "composeBoxSize=${it.size.width}x${it.size.height}")
    }) {
        AndroidView(
            modifier = Modifier.fillMaxSize().onGloballyPositioned {
                Log.d(TAG, "composeAndroidViewSize=${it.size.width}x${it.size.height}")
            },
            factory = { context: Context ->
                val wv = buildWebView(context) { diagnostic = it }
                wv.addJavascriptInterface(TvBridge(vm) { wv }, "AndroidTvBridge")
                webViewRef = wv
                wv
            },
            update = { /* created once */ }
        )
        diagnostic?.let { message ->
            Box(
                Modifier.fillMaxSize().background(ComposeColor(0xDD05060C)).padding(16.dp),
                contentAlignment = Alignment.TopStart
            ) {
                Text(
                    "WebView diagnostic\n\n$message",
                    color = ComposeColor(0xFFFFC3C3),
                    fontFamily = FontFamily.Monospace,
                    fontSize = 13.sp
                )
            }
        }
    }
}

private fun buildWebView(context: Context, onDiagnostic: (String) -> Unit): WebView {
    val assetLoader = WebViewAssetLoader.Builder()
        .addPathHandler("/assets/", WebViewAssetLoader.AssetsPathHandler(context))
        .build()

    if (BuildConfig.DEBUG) WebView.setWebContentsDebuggingEnabled(true)

    Log.d(TAG, "loadUrl=$DEMO_URL")

    return WebView(context).apply {
        // Compose's AndroidView attaches children with WRAP_CONTENT layout params.
        // WebView derives CSS vh/dvh units from these, so they resolve to 0 and the
        // HTML's height:100dvh container collapses. Force a definite size.
        layoutParams = android.widget.FrameLayout.LayoutParams(
            android.view.ViewGroup.LayoutParams.MATCH_PARENT,
            android.view.ViewGroup.LayoutParams.MATCH_PARENT
        )
        setBackgroundColor(Color.parseColor("#05060C"))
        settings.javaScriptEnabled = true
        settings.useWideViewPort = true
        settings.loadWithOverviewMode = true
        settings.builtInZoomControls = false
        settings.displayZoomControls = false
        settings.domStorageEnabled = true

        webViewClient = object : WebViewClient() {
            override fun shouldInterceptRequest(
                view: WebView?,
                request: WebResourceRequest?
            ): WebResourceResponse? {
                val url = request?.url
                val response = url?.let { assetLoader.shouldInterceptRequest(it) }
                Log.d(TAG, "intercept url=$url status=${response?.statusCode} mime=${response?.mimeType}")
                return response
            }

            override fun shouldOverrideUrlLoading(
                view: WebView?,
                request: WebResourceRequest?
            ): Boolean {
                val host = request?.url?.host ?: return true
                Log.d(TAG, "navigate host=$host")
                return host != ASSET_HOST
            }

            override fun onPageStarted(view: WebView?, url: String?, favicon: android.graphics.Bitmap?) {
                Log.d(TAG, "onPageStarted url=$url")
            }

            override fun onPageFinished(view: WebView?, url: String?) {
                Log.d(TAG, "onPageFinished url=$url")
                view?.let { wv ->
                    Log.d(
                        TAG,
                        "webViewMetrics width=${wv.width} height=${wv.height} " +
                            "measured=${wv.measuredWidth}x${wv.measuredHeight} " +
                            "visibility=${wv.visibility} " +
                            "layoutParams=${wv.layoutParams?.javaClass?.simpleName}" +
                            "(${wv.layoutParams?.width}x${wv.layoutParams?.height}) " +
                            "parent=${wv.parent?.javaClass?.simpleName}"
                    )
                    wv.evaluateJavascript(RENDER_DIAG_JS) { value -> Log.d(TAG, "renderDiag=$value") }
                }
            }

            override fun onReceivedError(view: WebView?, request: WebResourceRequest?, error: WebResourceError?) {
                val msg = "onReceivedError code=${error?.errorCode} desc=${error?.description} url=${request?.url}"
                Log.e(TAG, msg)
                if (request?.isForMainFrame == true) onDiagnostic(msg)
            }

            override fun onReceivedHttpError(
                view: WebView?,
                request: WebResourceRequest?,
                errorResponse: WebResourceResponse?
            ) {
                val msg = "onReceivedHttpError status=${errorResponse?.statusCode} reason=${errorResponse?.reasonPhrase} url=${request?.url}"
                Log.e(TAG, msg)
                if (request?.isForMainFrame == true) onDiagnostic(msg)
            }

            override fun onReceivedSslError(view: WebView?, handler: SslErrorHandler?, error: SslError?) {
                val msg = "onReceivedSslError primary=${error?.primaryError} desc=${error?.toString()}"
                Log.e(TAG, msg)
                onDiagnostic(msg)
                handler?.cancel()
            }
        }

        webChromeClient = object : WebChromeClient() {
            override fun onConsoleMessage(message: ConsoleMessage?): Boolean {
                Log.d(TAG, "console: ${message?.message()} (${message?.sourceId()}:${message?.lineNumber()})")
                return true
            }
        }

        loadUrl(DEMO_URL)
    }
}
