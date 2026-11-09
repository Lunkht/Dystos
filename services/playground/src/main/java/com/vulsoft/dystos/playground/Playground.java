package com.vulsoft.dystos.playground;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import com.vulsoft.dystos.ast.Ast.SourceFile;
import com.vulsoft.dystos.interp.Runner;
import com.vulsoft.dystos.lexer.Highlighter;
import com.vulsoft.dystos.lexer.Lexer;
import com.vulsoft.dystos.lexer.LexerException;
import com.vulsoft.dystos.lexer.Token;
import com.vulsoft.dystos.parser.AstPrinter;
import com.vulsoft.dystos.parser.ParseException;
import com.vulsoft.dystos.parser.Parser;
import com.vulsoft.dystos.sema.Diagnostic;
import com.vulsoft.dystos.sema.SemanticAnalyzer;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Semaphore;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Service HTTP « playground » : analyse et exécution de code Dystos.
 *
 * <p>Routes : {@code GET /api/v1/health}, {@code GET /api/v1/limits}, {@code POST /api/v1/analyze},
 * {@code POST /api/v1/run}. Voir {@code README.md} pour le contrat complet.
 */
public final class Playground {

    public static final String VERSION = "0.1.0";

    /** Réglages (champs publics modifiables avant {@link #start}). */
    public static final class Config {
        public String bind = "127.0.0.1";
        public int port = 8080;
        /** {@code inprocess} ou {@code process} (une JVM fille jetable par exécution). */
        public String mode = "inprocess";
        /** Classpath de la JVM fille (mode {@code process}) ; par défaut, déduit de l'emplacement des classes de Dystos. */
        public String classpath = defaultClasspath();
        public int maxBodyBytes = 20_000;
        public int maxConcurrentRuns = 4;
        public int rateRunPerMinute = 20;
        public int rateAnalyzePerMinute = 120;
        /** Origine autorisée pour CORS (null : pas d'en-têtes CORS). */
        public String corsOrigin = null;
        /** Si vrai, l'adresse du client est lue dans {@code X-Forwarded-For} (derrière un proxy de confiance). */
        public boolean trustProxy = false;
        public int workerMemoryMb = 96;
        public long timeoutMs = 5_000;
        public long maxSteps = 20_000_000L;
        public boolean quiet = false;
    }

    /** Emplacements (dossiers ou jars) des modules de Dystos, indépendamment du lanceur utilisé. */
    static String defaultClasspath() {
        java.util.Set<String> parts = new java.util.LinkedHashSet<>();
        for (Class<?> c : List.of(Runner.class, SemanticAnalyzer.class, Parser.class, Lexer.class)) {
            try {
                parts.add(java.nio.file.Path.of(c.getProtectionDomain().getCodeSource().getLocation().toURI()).toString());
            } catch (Exception e) {
                // on retombe sur le classpath de la JVM courante
            }
        }
        return parts.isEmpty() ? System.getProperty("java.class.path") : String.join(java.io.File.pathSeparator, parts);
    }

    private final Config cfg;
    private final HttpServer server;
    private final ExecutorService pool;
    private final RunService runner;
    private final Semaphore runSlots;
    private final RateLimiter runLimiter;
    private final RateLimiter analyzeLimiter;
    private final Runner.Options sandbox;

    private Playground(Config cfg) throws IOException {
        this.cfg = cfg;
        this.sandbox = Runner.Options.sandbox().withTimeout(cfg.timeoutMs).withMaxSteps(cfg.maxSteps);
        this.runner = cfg.mode.equals("process")
                ? RunService.childProcess(cfg.classpath, cfg.workerMemoryMb, cfg.timeoutMs, cfg.maxSteps, sandbox.maxOutputChars())
                : RunService.inProcess(sandbox);
        this.runSlots = new Semaphore(cfg.maxConcurrentRuns);
        this.runLimiter = new RateLimiter(cfg.rateRunPerMinute, 60_000);
        this.analyzeLimiter = new RateLimiter(cfg.rateAnalyzePerMinute, 60_000);
        AtomicInteger n = new AtomicInteger();
        // Grande pile : l'analyse est récursive, un code très imbriqué ne doit pas faire planter le serveur.
        this.pool = Executors.newFixedThreadPool(cfg.maxConcurrentRuns + 8, r -> {
            Thread t = new Thread(null, r, "playground-" + n.incrementAndGet(), 64L * 1024 * 1024);
            t.setDaemon(true);
            return t;
        });
        this.server = HttpServer.create(new InetSocketAddress(cfg.bind, cfg.port), 128);
        this.server.createContext("/", this::dispatch);
        this.server.setExecutor(pool);
    }

    public static Playground start(Config cfg) throws IOException {
        if (!cfg.mode.equals("inprocess") && !cfg.mode.equals("process")) {
            throw new IllegalArgumentException("mode inconnu : " + cfg.mode + " (inprocess ou process)");
        }
        Playground p = new Playground(cfg);
        p.server.start();
        return p;
    }

    public int port() {
        return server.getAddress().getPort();
    }

    public void stop() {
        server.stop(0);
        pool.shutdownNow();
    }

    // ------------------------------------------------------------------
    // Routage
    // ------------------------------------------------------------------

    private void dispatch(HttpExchange ex) {
        long t0 = System.nanoTime();
        int status = 500;
        try {
            status = route(ex);
            if (status == 0 && ex.getAttribute("status") instanceof Integer sent) {
                status = sent;   // une erreur de lecture du corps a déjà répondu
            }
        } catch (StackOverflowError e) {
            status = sendError(ex, 422, "code trop profondément imbriqué");
        } catch (Throwable e) {
            status = sendError(ex, 500, "erreur interne du service");
        } finally {
            ex.close();
            if (!cfg.quiet) {
                System.out.println(java.time.Instant.now() + " " + ex.getRequestMethod() + " "
                        + ex.getRequestURI().getPath() + " " + status + " " + (System.nanoTime() - t0) / 1_000_000L
                        + "ms " + clientIp(ex));
            }
        }
    }

    private int route(HttpExchange ex) throws IOException {
        if (cfg.corsOrigin != null) {
            ex.getResponseHeaders().set("Access-Control-Allow-Origin", cfg.corsOrigin);
            ex.getResponseHeaders().set("Vary", "Origin");
            if (ex.getRequestMethod().equals("OPTIONS")) {
                ex.getResponseHeaders().set("Access-Control-Allow-Methods", "GET, POST, OPTIONS");
                ex.getResponseHeaders().set("Access-Control-Allow-Headers", "Content-Type");
                ex.getResponseHeaders().set("Access-Control-Max-Age", "600");
                ex.sendResponseHeaders(204, -1);
                return 204;
            }
        }
        String path = ex.getRequestURI().getPath();
        String method = ex.getRequestMethod();
        switch (path) {
            case "/api/v1/health":
                if (!method.equals("GET")) {
                    return methodNotAllowed(ex, "GET");
                }
                return send(ex, 200, "{\"status\":\"ok\",\"version\":\"" + VERSION + "\"}");
            case "/api/v1/limits":
                if (!method.equals("GET")) {
                    return methodNotAllowed(ex, "GET");
                }
                return send(ex, 200, limitsJson());
            case "/api/v1/analyze":
                if (!method.equals("POST")) {
                    return methodNotAllowed(ex, "POST");
                }
                return analyze(ex);
            case "/api/v1/run":
                if (!method.equals("POST")) {
                    return methodNotAllowed(ex, "POST");
                }
                return run(ex);
            default:
                return sendError(ex, 404, "route inconnue : " + path);
        }
    }

    // ------------------------------------------------------------------
    // /api/v1/analyze
    // ------------------------------------------------------------------

    private int analyze(HttpExchange ex) throws IOException {
        if (!analyzeLimiter.tryAcquire(clientIp(ex), System.currentTimeMillis())) {
            return tooMany(ex, "trop de requêtes, réessayez dans une minute");
        }
        Map<String, Object> req = readJsonObject(ex);
        if (req == null) {
            return 0;
        }
        Object code = req.get("code");
        if (!(code instanceof String)) {
            return sendError(ex, 400, "le champ 'code' (chaîne) est obligatoire");
        }
        boolean tokens = false;
        boolean ast = false;
        boolean highlight = false;
        Object inc = req.get("include");
        if (inc != null) {
            if (!(inc instanceof List<?> list)) {
                return sendError(ex, 400, "'include' doit être un tableau de chaînes");
            }
            for (Object o : list) {
                if ("tokens".equals(o)) {
                    tokens = true;
                } else if ("ast".equals(o)) {
                    ast = true;
                } else if ("highlight".equals(o)) {
                    highlight = true;
                } else {
                    return sendError(ex, 400, "valeur inconnue dans 'include' : " + o + " (tokens, ast, highlight)");
                }
            }
        }
        String src = (String) code;

        SourceFile file = null;
        List<Diagnostic> diags;
        try {
            file = Parser.parse(src);
            diags = SemanticAnalyzer.analyze(List.of(new SemanticAnalyzer.SourceUnit("main", file)),
                    sandbox.javaPrefixes(), sandbox.javaDenied()).diagnostics();
        } catch (LexerException e) {
            diags = List.of(parseDiag("E0001", e.getMessage(), e.line(), e.column()));
        } catch (ParseException e) {
            diags = List.of(parseDiag("E0002", e.getMessage(), e.line(), e.column()));
        }
        boolean ok = diags.stream().noneMatch(Diagnostic::isError);

        StringBuilder sb = new StringBuilder("{\"ok\":").append(ok).append(",\"diagnostics\":").append(Diagnostic.toJson(diags));
        if (tokens) {
            sb.append(",\"tokens\":");
            try {
                sb.append(tokensJson(Lexer.tokenize(src)));
            } catch (LexerException e) {
                sb.append("[]");
            }
        }
        if (ast) {
            sb.append(",\"ast\":").append(file == null ? "null" : Json.quote(AstPrinter.print(file)));
        }
        if (highlight) {
            sb.append(",\"highlight\":").append(highlightJson(src));
        }
        return send(ex, 200, sb.append('}').toString());
    }

    private static Diagnostic parseDiag(String code, String message, int line, int col) {
        int i = message.indexOf(" : ");
        return new Diagnostic(Diagnostic.Severity.ERROR, code, i >= 0 ? message.substring(i + 3) : message, line, col, null);
    }

    private static String tokensJson(List<Token> tokens) {
        StringBuilder sb = new StringBuilder("[");
        for (int i = 0; i < tokens.size(); i++) {
            Token t = tokens.get(i);
            sb.append(i > 0 ? "," : "").append("{\"type\":\"").append(t.type()).append("\",\"text\":")
                    .append(Json.quote(t.text())).append(",\"line\":").append(t.line())
                    .append(",\"column\":").append(t.column()).append('}');
        }
        return sb.append(']').toString();
    }

    private static String highlightJson(String src) {
        StringBuilder sb = new StringBuilder("[");
        boolean first = true;
        for (Highlighter.Span s : Highlighter.highlight(src)) {
            sb.append(first ? "" : ",").append("{\"start\":").append(s.start()).append(",\"end\":").append(s.end())
                    .append(",\"kind\":\"").append(s.kind().name().toLowerCase(Locale.ROOT)).append("\"}");
            first = false;
        }
        return sb.append(']').toString();
    }

    // ------------------------------------------------------------------
    // /api/v1/run
    // ------------------------------------------------------------------

    private int run(HttpExchange ex) throws IOException {
        if (!runLimiter.tryAcquire(clientIp(ex), System.currentTimeMillis())) {
            return tooMany(ex, "trop d'exécutions, réessayez dans une minute");
        }
        Map<String, Object> req = readJsonObject(ex);
        if (req == null) {
            return 0;
        }
        Object code = req.get("code");
        if (!(code instanceof String)) {
            return sendError(ex, 400, "le champ 'code' (chaîne) est obligatoire");
        }
        if (!runSlots.tryAcquire()) {
            return tooMany(ex, "serveur occupé, réessayez dans un instant");
        }
        String result;
        try {
            result = runner.run((String) code);
        } finally {
            runSlots.release();     // libéré AVANT d'envoyer la réponse : le client peut relancer aussitôt
        }
        return send(ex, 200, result);
    }

    private String limitsJson() {
        StringBuilder prefixes = new StringBuilder("[");
        List<String> p = sandbox.javaPrefixes();
        for (int i = 0; i < p.size(); i++) {
            prefixes.append(i > 0 ? "," : "").append(Json.quote(p.get(i)));
        }
        prefixes.append(']');
        return "{\"version\":\"" + VERSION + "\",\"mode\":\"" + cfg.mode + "\",\"maxBodyBytes\":" + cfg.maxBodyBytes
                + ",\"maxSteps\":" + sandbox.maxSteps() + ",\"timeoutMs\":" + sandbox.timeoutMillis()
                + ",\"maxOutputChars\":" + sandbox.maxOutputChars() + ",\"maxDepth\":" + sandbox.maxDepth()
                + ",\"maxConcurrentRuns\":" + cfg.maxConcurrentRuns + ",\"rateRunPerMinute\":" + cfg.rateRunPerMinute
                + ",\"rateAnalyzePerMinute\":" + cfg.rateAnalyzePerMinute + ",\"filesEnabled\":false"
                + ",\"javaPrefixes\":" + prefixes + "}";
    }

    // ------------------------------------------------------------------
    // Entrées / sorties HTTP
    // ------------------------------------------------------------------

    /** Lit et valide le corps JSON ; en cas d'erreur, répond et retourne null. */
    private Map<String, Object> readJsonObject(HttpExchange ex) throws IOException {
        String ct = ex.getRequestHeaders().getFirst("Content-Type");
        if (ct == null || !ct.toLowerCase(Locale.ROOT).startsWith("application/json")) {
            sendError(ex, 415, "Content-Type 'application/json' requis");
            return null;
        }
        String cl = ex.getRequestHeaders().getFirst("Content-Length");
        if (cl != null) {
            try {
                if (Long.parseLong(cl.trim()) > cfg.maxBodyBytes) {
                    sendError(ex, 413, "corps trop volumineux (maximum " + cfg.maxBodyBytes + " octets)");
                    return null;
                }
            } catch (NumberFormatException e) {
                sendError(ex, 400, "Content-Length invalide");
                return null;
            }
        }
        byte[] body = readBounded(ex.getRequestBody(), cfg.maxBodyBytes);
        if (body == null) {
            sendError(ex, 413, "corps trop volumineux (maximum " + cfg.maxBodyBytes + " octets)");
            return null;
        }
        Object parsed;
        try {
            parsed = Json.parse(new String(body, StandardCharsets.UTF_8));
        } catch (IllegalArgumentException e) {
            sendError(ex, 400, "JSON invalide : " + e.getMessage());
            return null;
        }
        if (!(parsed instanceof Map<?, ?>)) {
            sendError(ex, 400, "le corps doit être un objet JSON");
            return null;
        }
        @SuppressWarnings("unchecked")
        Map<String, Object> m = (Map<String, Object>) parsed;
        return m;
    }

    /** Lit au plus {@code max} octets ; retourne null si le flux est plus long. */
    private static byte[] readBounded(InputStream in, int max) throws IOException {
        ByteArrayOutputStream bo = new ByteArrayOutputStream();
        byte[] buf = new byte[4096];
        int n;
        while ((n = in.read(buf)) > 0) {
            bo.write(buf, 0, n);
            if (bo.size() > max) {
                return null;
            }
        }
        return bo.toByteArray();
    }

    private int send(HttpExchange ex, int status, String json) throws IOException {
        byte[] b = json.getBytes(StandardCharsets.UTF_8);
        ex.getResponseHeaders().set("Content-Type", "application/json; charset=utf-8");
        ex.getResponseHeaders().set("Cache-Control", "no-store");
        ex.getResponseHeaders().set("X-Content-Type-Options", "nosniff");
        ex.setAttribute("status", status);
        ex.sendResponseHeaders(status, b.length);
        ex.getResponseBody().write(b);
        return status;
    }

    private int sendError(HttpExchange ex, int status, String message) {
        try {
            return send(ex, status, "{\"error\":" + Json.quote(message) + "}");
        } catch (IOException e) {
            return status;
        }
    }

    private int methodNotAllowed(HttpExchange ex, String allowed) {
        ex.getResponseHeaders().set("Allow", allowed);
        return sendError(ex, 405, "méthode non autorisée (" + allowed + " attendu)");
    }

    private int tooMany(HttpExchange ex, String message) {
        ex.getResponseHeaders().set("Retry-After", "60");
        return sendError(ex, 429, message);
    }

    private String clientIp(HttpExchange ex) {
        if (cfg.trustProxy) {
            String xff = ex.getRequestHeaders().getFirst("X-Forwarded-For");
            if (xff != null && !xff.isBlank()) {
                return xff.split(",")[0].trim();
            }
        }
        return ex.getRemoteAddress().getAddress().getHostAddress();
    }

    // ------------------------------------------------------------------
    // Lancement
    // ------------------------------------------------------------------

    public static void main(String[] args) throws IOException {
        Config c = new Config();
        for (int i = 0; i < args.length; i++) {
            switch (args[i]) {
                case "--bind": c.bind = value(args, ++i); break;
                case "--port": c.port = Integer.parseInt(value(args, ++i)); break;
                case "--mode": c.mode = value(args, ++i); break;
                case "--max-concurrent": c.maxConcurrentRuns = Integer.parseInt(value(args, ++i)); break;
                case "--rate-run": c.rateRunPerMinute = Integer.parseInt(value(args, ++i)); break;
                case "--rate-analyze": c.rateAnalyzePerMinute = Integer.parseInt(value(args, ++i)); break;
                case "--cors-origin": c.corsOrigin = value(args, ++i); break;
                case "--trust-proxy": c.trustProxy = true; break;
                case "--worker-mem": c.workerMemoryMb = Integer.parseInt(value(args, ++i)); break;
                case "--timeout-ms": c.timeoutMs = Long.parseLong(value(args, ++i)); break;
                case "--max-steps": c.maxSteps = Long.parseLong(value(args, ++i)); break;
                case "--help":
                    System.out.println("Options : --bind H --port N --mode inprocess|process --max-concurrent N "
                            + "--rate-run N --rate-analyze N --cors-origin URL --trust-proxy --worker-mem MB "
                            + "--timeout-ms N --max-steps N");
                    return;
                default:
                    System.err.println("option inconnue : " + args[i]);
                    System.exit(64);
            }
        }
        Playground p = start(c);
        System.out.println("Playground Dystos " + VERSION + " à l'écoute sur http://" + c.bind + ":" + p.port()
                + " (mode " + c.mode + ")");
        Runtime.getRuntime().addShutdownHook(new Thread(p::stop));
    }

    private static String value(String[] args, int i) {
        if (i >= args.length) {
            System.err.println("valeur manquante après " + args[i - 1]);
            System.exit(64);
        }
        return args[i];
    }
}
