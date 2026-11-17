package com.kwata.tracker;

import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.webkit.WebView;
import com.getcapacitor.Bridge;
import com.getcapacitor.BridgeActivity;
import com.getcapacitor.WebViewListener;

/**
 * Capacitor shell for Kwata Tracker.
 * The SOS home-screen widget and the long-press "SOS" shortcut launch this activity with
 * action {@link #ACTION_SOS} and data kwata://sos. The web app then opens its SOS countdown
 * through window.KT.sos() (same 5-second countdown and PIN-to-stop as the in-app button).
 */
public class MainActivity extends BridgeActivity {

    public static final String ACTION_SOS = "com.kwata.tracker.SOS";
    public static final String EXTRA_SOS = "kwata_sos";

    /** Calls KT.sos() as soon as the web app has defined it (retries for ~10 s). */
    private static final String SOS_JS =
        "(function k(n){try{if(window.KT&&window.KT.sos){window.KT.sos();return;}}catch(e){}" +
        "if(n<50)setTimeout(function(){k(n+1);},200);})(0);";

    private boolean created = false;
    private boolean pendingSos = false;
    private boolean pageLoaded = false;
    private String pendingJs = null;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        registerPlugin(StepsPlugin.class);
        registerPlugin(WidgetBridgePlugin.class);
        super.onCreate(savedInstanceState);
        // Cold start from the widget or shortcut: fire once the page has loaded.
        pendingSos = savedInstanceState == null && isSos(getIntent());
        pendingJs = savedInstanceState == null ? linkJs(getIntent()) : null;
        Bridge b = getBridge();
        if (b != null) {
            b.addWebViewListener(new WebViewListener() {
                @Override
                public void onPageLoaded(WebView webView) {
                    pageLoaded = true;
                    if (pendingJs != null) { runJs(pendingJs); pendingJs = null; }
                    if (pendingSos) {
                        pendingSos = false;
                        runSos();
                    }
                }
            });
        }
        created = true;
    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        // BridgeActivity.load() also routes the launch intent through here during onCreate; that case is handled above.
        if (!created) return;
        String js = linkJs(intent);
        if (js != null) { setIntent(intent); if (pageLoaded) runJs(js); else pendingJs = js; return; }
        if (!isSos(intent)) return;
        setIntent(intent);
        if (pageLoaded) runSos();
        else pendingSos = true;
    }

    /** kwata://buzz and kwata://map from the circle widget. */
    private String linkJs(Intent intent) {
        if (intent == null || intent.getData() == null || !"kwata".equals(intent.getData().getScheme())) return null;
        String h = intent.getData().getHost();
        String fn = "buzz".equals(h) ? "KTbuzzAll" : "map".equals(h) ? "KTshowMap" : null;
        if (fn == null) return null;
        return "(function k(n){try{if(window." + fn + "){window." + fn + "();return;}}catch(e){}if(n<50)setTimeout(function(){k(n+1);},200);})(0);";
    }

    private void runJs(final String js) {
        final Bridge b = getBridge();
        if (b == null || b.getWebView() == null || js == null) return;
        final WebView wv = b.getWebView();
        wv.post(() -> wv.evaluateJavascript(js, null));
    }

    private void runSos() {
        final Bridge b = getBridge();
        if (b == null || b.getWebView() == null) return;
        final WebView wv = b.getWebView();
        wv.post(() -> wv.evaluateJavascript(SOS_JS, null));
    }

    static boolean isSos(Intent intent) {
        if (intent == null) return false;
        if (ACTION_SOS.equals(intent.getAction())) return true;
        if (intent.getBooleanExtra(EXTRA_SOS, false)) return true;
        Uri d = intent.getData();
        return d != null && "kwata".equals(d.getScheme()) && "sos".equals(d.getHost());
    }
}
