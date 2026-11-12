package dev.dystos.app.ide;

import android.os.Handler;
import android.os.Looper;
import android.webkit.JavascriptInterface;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;

import org.json.JSONObject;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;

import dev.dystos.app.domain.model.CheckResult;
import dev.dystos.app.di.ServiceLocator;
import dev.dystos.app.language.CodeAnalysis;

/**
 * JavaScript exécuté par le moteur du WebView local (aucune dépendance, aucun
 * réseau) : c'est le moteur V8 de l'appareil qui tourne le programme.
 *
 * Le WebView est créé sur le thread UI, mais l'exécution est asynchrone — un
 * moteur web ne peut pas bloquer le thread UI. Toute l'attente se fait sur un
 * thread d'arrière-plan, et un budget de temps remplace la boucle infinie.
 */
public final class JavascriptLanguage implements Language {

    private static final long ENGINE_READY_TIMEOUT_MS = 3_000L;
    private static final long RUN_TIMEOUT_MS = 5_000L;
    private static final String TIMEOUT_MESSAGE =
            "Le programme ne se termine pas : boucle infinie ?";

    private static final String DEFAULT_CODE =
            "function carre(n) {\n"
                    + "    return n * n;\n"
                    + "}\n\n"
                    + "let message = \"Bonjour depuis JavaScript !\";\n"
                    + "console.log(message);\n"
                    + "console.log(\"2 au carre = \" + carre(2));\n";

    private static final List<String> SYMBOLS = Arrays.asList(
            "(", ")", "[", "]", "{", "}", "=>", "?", ".", ",", ";", "=",
            "==", "===", "+", "-", "*", "/", "%", "!", "\"", "_");

    /** Sachant qu'un bloc de code entoure ces expressions dans l'analyse. */
    private static final String BASE_URL_CONTENT =
            "<html><head><meta name=\"viewport\""
                    + " content=\"width=device-width,initial-scale=1\">"
                    + "</head><body></body></html>";

    private final Handler mainHandler = new Handler(Looper.getMainLooper());
    private final ExecutorService executor = Executors.newSingleThreadExecutor();
    private final CountDownLatch engineReady = new CountDownLatch(1);
    private final AtomicReference<RunState> state = new AtomicReference<>();

    private volatile WebView webView;

    /** Pont entre le JavaScript et Java : sortie, fin, erreur. */
    private final class Bridge {

        @JavascriptInterface
        public void output(final String line) {
            RunState current = state.get();
            if (current != null && line != null) {
                current.output.add(line);
            }
        }

        @JavascriptInterface
        public void done(final String unused) {
            RunState current = state.get();
            if (current == null) {
                return;
            }
            current.result.set(CheckResult.success(join(current.output)));
            current.done.countDown();
        }

        @JavascriptInterface
        public void fail(final String message, final long line) {
            RunState current = state.get();
            if (current == null) {
                return;
            }
            current.result.set(CheckResult.runtimeError(
                    message == null ? "Erreur d'exécution" : message, (int) line));
            current.done.countDown();
        }
    }

    /** État propre à une exécution : sortie, résultat, fin. */
    private static final class RunState {
        private final List<String> output = Collections.synchronizedList(new ArrayList<>());
        private final AtomicReference<CheckResult> result = new AtomicReference<>();
        private final CountDownLatch done = new CountDownLatch(1);
    }

    @Override
    public String id() {
        return "javascript";
    }

    @Override
    public String displayName() {
        return "JavaScript";
    }

    @Override
    public String fileExtension() {
        return "js";
    }

    @Override
    public String defaultCode() {
        return DEFAULT_CODE;
    }

    @Override
    public List<String> symbols() {
        return SYMBOLS;
    }

    @Override
    public CodeAnalysis.Result analyse(String source) {
        return JavaScriptLexer.scan(source);
    }

    @Override
    public CheckResult run(String source) {
        // « run » doit être appelé hors du thread UI ; la voie normale est
        // runAsync, qui gère l'éparpillement des threads.
        throw new IllegalStateException("JavascriptLanguage exige runAsync");
    }

    @Override
    public boolean isAsync() {
        return true;
    }

    @Override
    public void runAsync(String source, Consumer<CheckResult> onResult) {
        executor.execute(() -> runBlocking(source, onResult));
    }

    /** Exécution réelle, sur le thread d'arrière-plan. */
    private void runBlocking(String source, Consumer<CheckResult> onResult) {
        if (!ensureEngine()) {
            onResult.accept(CheckResult.runtimeError(
                    "Impossible de préparer le moteur JavaScript.", 0));
            return;
        }

        final RunState run = new RunState();
        state.set(run);

        mainHandler.post(() -> {
            if (webView != null) {
                webView.evaluateJavascript(harnessFor(source), null);
            } else {
                run.done.countDown();
            }
        });

        boolean completed = await(run.done, RUN_TIMEOUT_MS);
        state.compareAndSet(run, null);

        CheckResult result = run.result.get();
        if (!completed) {
            mainHandler.post(this::resetEngine);
            onResult.accept(CheckResult.timeout(TIMEOUT_MESSAGE));
        } else if (result != null) {
            onResult.accept(result);
        } else {
            onResult.accept(CheckResult.runtimeError("Exécution interrompue.", 0));
        }
    }

    private boolean ensureEngine() {
        if (webView != null) {
            return true;
        }
        mainHandler.post(() -> {
            webView = createWebView();
            engineReady.countDown();
        });
        return await(engineReady, ENGINE_READY_TIMEOUT_MS);
    }

    /** Le WebView est vidé de son contexte JavaScript (boucle infinie). */
    private void resetEngine() {
        WebView web = webView;
        if (web != null) {
            web.loadDataWithBaseURL(null, BASE_URL_CONTENT,
                    "text/html", "utf-8", null);
        }
    }

    private WebView createWebView() {
        WebView web = new WebView(ServiceLocator.appContext());
        web.setBackgroundColor(0x00000000);
        WebSettings settings = web.getSettings();
        settings.setJavaScriptEnabled(true);
        settings.setAllowFileAccess(false);
        settings.setAllowContentAccess(false);
        settings.setBlockNetworkLoads(true);
        settings.setCacheMode(WebSettings.LOAD_NO_CACHE);
        web.addJavascriptInterface(new Bridge(), "bridge");
        web.setWebViewClient(new WebViewClient() {
            @Override
            public void onPageFinished(WebView view, String url) {
                super.onPageFinished(view, url);
                if (engineReady.getCount() > 0) {
                    engineReady.countDown();
                }
            }
        });
        web.loadDataWithBaseURL(null, BASE_URL_CONTENT,
                "text/html", "utf-8", null);
        return web;
    }

    /**
     * Enveloppe le programme dans une sonde : console redirigée vers la
     * console de l'IDE, erreurs (y compris de syntaxe) captées avec la ligne.
     */
    private String harnessFor(String source) {
        String code = JSONObject.quote(source == null ? "" : source);
        String harness = "(function() {"
                + "  var __lines = [];"
                + "  function __str(v) {"
                + "    if (v === null) return 'null';"
                + "    if (v === undefined) return 'undefined';"
                + "    if (typeof v === 'string') return v;"
                + "    try { return JSON.stringify(v); } catch (e) { return String(v); }"
                + "  }"
                + "  function __log() {"
                + "    var out = [];"
                + "    for (var i = 0; i < arguments.length; i++) out.push(__str(arguments[i]));"
                + "    var text = out.join(' ');"
                + "    __lines.push(text);"
                + "    bridge.output(text);"
                + "  }"
                + "  window.console = { log: __log, info: __log, warn: __log, error: __log };"
                + "  window.print = __log;"
                + "  var src = " + code + ";"
                + "  try {"
                + "    eval(src);"
                + "    bridge.done('');"
                + "  } catch (e) {"
                + "    var line = 0;"
                + "    try {"
                + "      if (e && e.stack) {"
                + "        var m = String(e.stack).match(/(?:^|\\n)\\s*at[^\\n]*:(\\d+):\\d+/);"
                + "        if (m) line = parseInt(m[1], 10);"
                + "      }"
                + "    } catch (ignore) {}"
                + "    bridge.fail(String(e && e.message ? e.message : e), line);"
                + "  }"
                + "})();";
        return harness;
    }

    private static String join(List<String> lines) {
        return String.join("\n", new ArrayList<>(lines));
    }

    private static boolean await(CountDownLatch latch, long millis) {
        try {
            return latch.await(millis, TimeUnit.MILLISECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return false;
        }
    }
}