package com.vulsoft.dystos.playground;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

class PlaygroundTest {

    private static final Path EXAMPLES = Path.of("../../compiler/interp/src/test/resources/exemples");
    private static final HttpClient HTTP = HttpClient.newHttpClient();
    private static Playground shared;

    @BeforeAll
    static void start() throws IOException {
        shared = Playground.start(config());
    }

    @AfterAll
    static void stop() {
        shared.stop();
    }

    private static Playground.Config config() {
        Playground.Config c = new Playground.Config();
        c.port = 0;
        c.quiet = true;
        c.rateRunPerMinute = 10_000;
        c.rateAnalyzePerMinute = 10_000;
        return c;
    }

    // ---- aides -----------------------------------------------------------

    private static HttpResponse<String> get(Playground p, String path) throws Exception {
        return HTTP.send(HttpRequest.newBuilder(URI.create("http://127.0.0.1:" + p.port() + path)).GET().build(),
                HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
    }

    private static HttpResponse<String> post(Playground p, String path, String body, String contentType) throws Exception {
        HttpRequest.Builder b = HttpRequest.newBuilder(URI.create("http://127.0.0.1:" + p.port() + path))
                .POST(HttpRequest.BodyPublishers.ofString(body, StandardCharsets.UTF_8));
        if (contentType != null) {
            b.header("Content-Type", contentType);
        }
        return HTTP.send(b.build(), HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
    }

    private static String codeBody(String code) {
        return "{\"code\":" + Json.quote(code) + "}";
    }

    private static Map<String, Object> json(HttpResponse<String> r) {
        @SuppressWarnings("unchecked")
        Map<String, Object> m = (Map<String, Object>) Json.parse(r.body());
        return m;
    }

    private static HttpResponse<String> run(Playground p, String code) throws Exception {
        return post(p, "/api/v1/run", codeBody(code), "application/json");
    }

    private static String example(String prefix) throws IOException {
        try (var s = Files.list(EXAMPLES)) {
            return Files.readString(s.filter(x -> x.getFileName().toString().startsWith(prefix)).sorted().findFirst().orElseThrow());
        }
    }

    // ---- routes simples ----------------------------------------------------

    @Test
    void healthAndLimits() throws Exception {
        HttpResponse<String> h = get(shared, "/api/v1/health");
        assertEquals(200, h.statusCode());
        assertEquals("ok", json(h).get("status"));
        assertTrue(h.headers().firstValue("Content-Type").orElse("").startsWith("application/json"));
        Map<String, Object> l = json(get(shared, "/api/v1/limits"));
        assertEquals(20_000L, l.get("maxBodyBytes"));
        assertEquals(false, l.get("filesEnabled"));
        assertTrue(((List<?>) l.get("javaPrefixes")).contains("java.util."));
    }

    // ---- analyze ---------------------------------------------------------------

    @Test
    void analyzeReportsDiagnosticsOfExample11() throws Exception {
        HttpResponse<String> r = post(shared, "/api/v1/analyze", codeBody(example("11")), "application/json");
        assertEquals(200, r.statusCode());
        Map<String, Object> m = json(r);
        assertEquals(false, m.get("ok"));
        Map<?, ?> d = (Map<?, ?>) ((List<?>) m.get("diagnostics")).get(0);
        assertEquals("E0201", d.get("code"));
        assertEquals(6L, d.get("line"));
    }

    @Test
    void analyzeValidProgramWithAllOptions() throws Exception {
        String body = "{\"code\":" + Json.quote("def main():\n    print(\"é\")\n") + ",\"include\":[\"tokens\",\"ast\",\"highlight\"]}";
        Map<String, Object> m = json(post(shared, "/api/v1/analyze", body, "application/json"));
        assertEquals(true, m.get("ok"));
        assertEquals(List.of(), m.get("diagnostics"));
        List<?> tokens = (List<?>) m.get("tokens");
        assertEquals("DEF", ((Map<?, ?>) tokens.get(0)).get("type"));
        assertEquals("EOF", ((Map<?, ?>) tokens.get(tokens.size() - 1)).get("type"));
        assertTrue(((String) m.get("ast")).startsWith("SourceFile"));
        Map<?, ?> first = (Map<?, ?>) ((List<?>) m.get("highlight")).get(0);
        assertEquals("keyword", first.get("kind"));
        assertEquals(0L, first.get("start"));
        assertEquals(3L, first.get("end"));
    }

    @Test
    void analyzeOptionalPartsAreOmittedByDefault() throws Exception {
        Map<String, Object> m = json(post(shared, "/api/v1/analyze", codeBody("x = 1"), "application/json"));
        assertFalse(m.containsKey("tokens") || m.containsKey("ast") || m.containsKey("highlight"));
    }

    @Test
    void analyzeSyntaxAndLexicalErrorsBecomeDiagnostics() throws Exception {
        Map<String, Object> syn = json(post(shared, "/api/v1/analyze",
                "{\"code\":\"if x\\n    pass\\n\",\"include\":[\"tokens\",\"ast\"]}", "application/json"));
        assertEquals("E0002", ((Map<?, ?>) ((List<?>) syn.get("diagnostics")).get(0)).get("code"));
        assertNotNull(syn.get("tokens"));
        assertEquals(null, syn.get("ast"));
        Map<String, Object> lex = json(post(shared, "/api/v1/analyze", "{\"code\":\"x = $\",\"include\":[\"tokens\"]}", "application/json"));
        assertEquals("E0001", ((Map<?, ?>) ((List<?>) lex.get("diagnostics")).get(0)).get("code"));
        assertEquals(List.of(), lex.get("tokens"));
    }

    @Test
    void analyzeRefusesDangerousJavaImports() throws Exception {
        Map<String, Object> m = json(post(shared, "/api/v1/analyze", codeBody("import java.lang.Runtime\n"), "application/json"));
        assertEquals("E0105", ((Map<?, ?>) ((List<?>) m.get("diagnostics")).get(0)).get("code"));
    }

    @Test
    void analyzeSurvivesVeryDeepNesting() throws Exception {
        String code = "x = " + "(".repeat(5000) + "1" + ")".repeat(5000) + "\n";
        HttpResponse<String> r = post(shared, "/api/v1/analyze", codeBody(code), "application/json");
        assertTrue(r.statusCode() == 200 || r.statusCode() == 422, "statut " + r.statusCode());
        assertEquals(200, get(shared, "/api/v1/health").statusCode());
    }

    // ---- run -----------------------------------------------------------------------

    @Test
    void runExecutesExample20() throws Exception {
        Map<String, Object> m = json(run(shared, example("20")));
        assertEquals(0L, m.get("exitCode"));
        assertEquals("riz\nintrouvable\nValeur totale : 278.00\n", m.get("stdout"));
    }

    @Test
    void runKeepsUnicode() throws Exception {
        Map<String, Object> m = json(run(shared, "print(\"é😀\")"));
        assertEquals("é😀\n", m.get("stdout"));
    }

    @Test
    void runReportsCompileErrorsWithoutExecuting() throws Exception {
        Map<String, Object> m = json(run(shared, example("11")));
        assertEquals(2L, m.get("exitCode"));
        assertEquals("", m.get("stdout"));
        assertEquals("E0201", ((Map<?, ?>) ((List<?>) m.get("diagnostics")).get(0)).get("code"));
    }

    @Test
    void runRefusesDangerousProgramsAtCompileTime() throws Exception {
        for (String imp : List.of("java.lang.Runtime", "java.io.File", "java.net.URL", "java.lang.System")) {
            Map<String, Object> m = json(run(shared, "import " + imp + "\nprint(1)"));
            assertEquals(2L, m.get("exitCode"), imp);
        }
    }

    @Test
    void runDisablesFiles() throws Exception {
        Map<String, Object> m = json(run(shared, "import io\nf = io.open(\"x.txt\", \"w\")"));
        assertEquals(1L, m.get("exitCode"));
        assertTrue(((String) m.get("stderr")).contains("désactivé"));
    }

    // ---- erreurs de requête --------------------------------------------------------

    @Test
    void invalidRequestsAreRejected() throws Exception {
        assertEquals(400, post(shared, "/api/v1/run", "{oups", "application/json").statusCode());
        assertEquals(400, post(shared, "/api/v1/run", "[1,2]", "application/json").statusCode());
        assertEquals(400, post(shared, "/api/v1/run", "{}", "application/json").statusCode());
        assertEquals(400, post(shared, "/api/v1/run", "{\"code\": 12}", "application/json").statusCode());
        assertEquals(400, post(shared, "/api/v1/analyze", "{\"code\":\"x\",\"include\":[\"bizarre\"]}", "application/json").statusCode());
        assertEquals(400, post(shared, "/api/v1/analyze", "{\"code\":\"x\",\"include\":\"tokens\"}", "application/json").statusCode());
        assertEquals(415, post(shared, "/api/v1/run", codeBody("x"), "text/plain").statusCode());
        assertEquals(415, post(shared, "/api/v1/run", codeBody("x"), null).statusCode());
        HttpResponse<String> notAllowed = get(shared, "/api/v1/run");
        assertEquals(405, notAllowed.statusCode());
        assertEquals("POST", notAllowed.headers().firstValue("Allow").orElse(""));
        assertEquals(404, get(shared, "/api/v1/inconnu").statusCode());
        assertEquals(405, post(shared, "/api/v1/health", "{}", "application/json").statusCode());
    }

    @Test
    void oversizedBodiesAreRejected() throws Exception {
        String big = codeBody("x = 1\n".repeat(5000));
        assertTrue(big.length() > 20_000);
        assertEquals(413, post(shared, "/api/v1/run", big, "application/json").statusCode());
        assertEquals(413, post(shared, "/api/v1/analyze", big, "application/json").statusCode());
        assertEquals(200, get(shared, "/api/v1/health").statusCode());
    }

    @Test
    void errorBodiesAreJson() throws Exception {
        HttpResponse<String> r = post(shared, "/api/v1/run", "{oups", "application/json");
        assertTrue(json(r).get("error").toString().contains("JSON invalide"));
    }

    // ---- limites de débit, concurrence, proxy, CORS ---------------------------------------

    @Test
    void rateLimitingIsPerClient() throws Exception {
        Playground.Config c = config();
        c.rateRunPerMinute = 3;
        Playground p = Playground.start(c);
        try {
            for (int i = 0; i < 3; i++) {
                assertEquals(200, run(p, "print(1)").statusCode());
            }
            HttpResponse<String> r = run(p, "print(1)");
            assertEquals(429, r.statusCode());
            assertEquals("60", r.headers().firstValue("Retry-After").orElse(""));
            assertEquals(200, post(p, "/api/v1/analyze", codeBody("x = 1"), "application/json").statusCode());
        } finally {
            p.stop();
        }
    }

    @Test
    void forwardedForIsOnlyTrustedWhenConfigured() throws Exception {
        Playground.Config c = config();
        c.rateRunPerMinute = 1;
        c.trustProxy = true;
        Playground p = Playground.start(c);
        try {
            HttpResponse<String> a1 = forwarded(p, "10.0.0.1");
            HttpResponse<String> a2 = forwarded(p, "10.0.0.1");
            HttpResponse<String> b1 = forwarded(p, "10.0.0.2");
            assertEquals(200, a1.statusCode());
            assertEquals(429, a2.statusCode());
            assertEquals(200, b1.statusCode());
        } finally {
            p.stop();
        }
        c = config();
        c.rateRunPerMinute = 1;
        Playground q = Playground.start(c);   // trustProxy = false : l'en-tête est ignoré
        try {
            assertEquals(200, forwarded(q, "10.0.0.1").statusCode());
            assertEquals(429, forwarded(q, "10.0.0.2").statusCode());
        } finally {
            q.stop();
        }
    }

    private static HttpResponse<String> forwarded(Playground p, String ip) throws Exception {
        return HTTP.send(HttpRequest.newBuilder(URI.create("http://127.0.0.1:" + p.port() + "/api/v1/run"))
                        .header("Content-Type", "application/json").header("X-Forwarded-For", ip)
                        .POST(HttpRequest.BodyPublishers.ofString(codeBody("print(1)"))).build(),
                HttpResponse.BodyHandlers.ofString());
    }

    @Test
    void concurrentRunsAreBounded() throws Exception {
        Playground.Config c = config();
        c.maxConcurrentRuns = 1;
        c.timeoutMs = 1_500;
        c.maxSteps = Long.MAX_VALUE;
        Playground p = Playground.start(c);
        try {
            CompletableFuture<HttpResponse<String>> slow = CompletableFuture.supplyAsync(() -> {
                try {
                    return run(p, "while true:\n    pass\n");
                } catch (Exception e) {
                    throw new RuntimeException(e);
                }
            });
            Thread.sleep(400);
            HttpResponse<String> busy = run(p, "print(1)");
            assertEquals(429, busy.statusCode());
            assertEquals(3L, json(slow.get()).get("exitCode"));
            assertEquals(200, run(p, "print(1)").statusCode());   // le créneau est libéré
        } finally {
            p.stop();
        }
    }

    @Test
    void corsHeadersOnlyWhenConfigured() throws Exception {
        assertTrue(get(shared, "/api/v1/health").headers().firstValue("Access-Control-Allow-Origin").isEmpty());
        Playground.Config c = config();
        c.corsOrigin = "https://exemple.org";
        Playground p = Playground.start(c);
        try {
            assertEquals("https://exemple.org",
                    get(p, "/api/v1/health").headers().firstValue("Access-Control-Allow-Origin").orElse(""));
            HttpResponse<String> pre = HTTP.send(HttpRequest.newBuilder(URI.create("http://127.0.0.1:" + p.port() + "/api/v1/run"))
                    .method("OPTIONS", HttpRequest.BodyPublishers.noBody()).build(), HttpResponse.BodyHandlers.ofString());
            assertEquals(204, pre.statusCode());
            assertTrue(pre.headers().firstValue("Access-Control-Allow-Methods").orElse("").contains("POST"));
        } finally {
            p.stop();
        }
    }

    // ---- mode « processus fils » -------------------------------------------------------------

    @Test
    void childProcessModeRunsAndKillsPrograms() throws Exception {
        Playground.Config c = config();
        c.mode = "process";
        c.timeoutMs = 1_000;
        c.maxSteps = Long.MAX_VALUE;
        Playground p = Playground.start(c);
        try {
            Map<String, Object> ok = json(run(p, example("09")));
            assertEquals(0L, ok.get("exitCode"), ok.toString());
            assertEquals("13.0\n", ok.get("stdout"));
            Map<String, Object> accents = json(run(p, "print(\"é\")"));
            assertEquals("é\n", accents.get("stdout"));
            Map<String, Object> loop = json(run(p, "while true:\n    pass\n"));
            assertEquals(3L, loop.get("exitCode"), loop.toString());
            Map<String, Object> bad = json(run(p, example("11")));
            assertEquals(2L, bad.get("exitCode"));
            assertEquals(200, get(p, "/api/v1/health").statusCode());
        } finally {
            p.stop();
        }
    }

    @Test
    void childProcessModeSurvivesMemoryExhaustion() throws Exception {
        Playground.Config c = config();
        c.mode = "process";
        c.workerMemoryMb = 48;
        c.timeoutMs = 20_000;
        c.maxSteps = Long.MAX_VALUE;
        Playground p = Playground.start(c);
        try {
            Map<String, Object> m = json(run(p, "xs: list[int] = []\nwhile true:\n    xs.add(1)\n"));
            assertTrue(((Long) m.get("exitCode")) >= 3L, m.toString());
            assertEquals(200, get(p, "/api/v1/health").statusCode());
            assertEquals(0L, json(run(p, "print(1)")).get("exitCode"));
        } finally {
            p.stop();
        }
    }
}
