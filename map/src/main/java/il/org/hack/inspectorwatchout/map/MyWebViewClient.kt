package il.org.hack.inspectorwatchout.map

import android.content.Context
import android.os.Build
import android.util.Log
import android.view.View
import android.webkit.*

class MyWebViewClient(val mContext: Context, val mWebView: WebView, val appUrl: String): WebViewClient() {

    init {

        // web view settings
        val webSettings = mWebView.settings
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            webSettings.mixedContentMode = WebSettings.MIXED_CONTENT_ALWAYS_ALLOW
        }
        webSettings.javaScriptEnabled = true
        webSettings.setGeolocationEnabled(true)
        webSettings.databaseEnabled = true
        webSettings.domStorageEnabled = true
        val databasePath = mContext.applicationContext.getDir("database", Context.MODE_PRIVATE).path
        webSettings.databasePath = databasePath
        webSettings.userAgentString = "Inspector Watch App v" + BuildConfig.VERSION_NAME + "/ Android " + Build.VERSION.CODENAME

        mWebView.webChromeClient = object : WebChromeClient() {
            override fun onGeolocationPermissionsShowPrompt(origin: String, callback: GeolocationPermissions.Callback) {
                callback.invoke(origin, true, true)
            }
        }
    }

    override fun onReceivedError(view: WebView, request: WebResourceRequest, error: WebResourceError) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            if (request.isForMainFrame) {
                mWebView.loadUrl(appUrl)
            }
        }
        Log.d("WebResourceError", error.toString())
    }

    override fun onPageFinished(view: WebView, url: String) {
        super.onPageFinished(view, url)
        mWebView.visibility = View.VISIBLE
    }

}