package com.vulsoft.dystos.playground;

import com.vulsoft.dystos.interp.Runner;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

/** Exécute un programme et retourne le résultat au format JSON de {@code Runner.Result.toJson()}. */
interface RunService {

    String run(String code);

    /** Dans le processus du serveur : simple, rapide, mais un appel Java bloquant ne peut pas être tué. */
    static RunService inProcess(Runner.Options options) {
        return code -> Runner.run(code, options).toJson();
    }

    /**
     * Une JVM fille jetable par exécution : environnement vide, mémoire plafonnée, arrêt forcé à
     * l'expiration. Plus robuste que le mode « dans le processus » (boucle native, manque de mémoire).
     */
    static RunService childProcess(String classpath, int memoryMb, long timeoutMs, long maxSteps, long maxOutputChars) {
        return code -> {
            long t0 = System.nanoTime();
            List<String> cmd = new ArrayList<>(List.of(
                    Path.of(System.getProperty("java.home"), "bin", "java").toString(),
                    "-Xmx" + memoryMb + "m", "-XX:+UseSerialGC", "-XX:TieredStopAtLevel=1",
                    "-Dfile.encoding=UTF-8", "-cp", classpath,
                    "com.vulsoft.dystos.interp.Main", "run", "-", "--sandbox", "--json",
                    "--timeout-ms", String.valueOf(timeoutMs), "--max-steps", String.valueOf(maxSteps),
                    "--max-output", String.valueOf(maxOutputChars)));
            ProcessBuilder pb = new ProcessBuilder(cmd);
            pb.environment().clear();
            Process p = null;
            try {
                p = pb.start();
                final Process proc = p;
                try (OutputStream os = proc.getOutputStream()) {
                    os.write(code.getBytes(StandardCharsets.UTF_8));
                } catch (IOException ignored) {
                    // le worker peut se terminer avant d'avoir tout lu : le résultat dira pourquoi
                }
                long limit = maxOutputChars * 4 + 4096;
                CompletableFuture<byte[]> out = CompletableFuture.supplyAsync(() -> readBounded(proc.getInputStream(), limit));
                CompletableFuture.runAsync(() -> readBounded(proc.getErrorStream(), 4096));
                if (!proc.waitFor(timeoutMs + 5_000L, TimeUnit.MILLISECONDS)) {
                    proc.destroyForcibly();
                    return errorJson("Exécution interrompue : délai dépassé", 3, t0);
                }
                String text = new String(out.get(2, TimeUnit.SECONDS), StandardCharsets.UTF_8).strip();
                int nl = text.lastIndexOf('\n');
                String last = nl >= 0 ? text.substring(nl + 1) : text;
                if (last.startsWith("{") && last.endsWith("}")) {
                    return last;
                }
                return errorJson("Exécution interrompue : le processus d'exécution s'est arrêté (code "
                        + proc.exitValue() + "), mémoire ou ressources insuffisantes ?", 3, t0);
            } catch (Exception e) {
                if (e instanceof InterruptedException) {
                    Thread.currentThread().interrupt();
                }
                return errorJson("Erreur interne du service d'exécution", 4, t0);
            } finally {
                if (p != null && p.isAlive()) {
                    p.destroyForcibly();
                }
            }
        };
    }

    private static byte[] readBounded(InputStream in, long max) {
        try (InputStream is = in) {
            java.io.ByteArrayOutputStream bo = new java.io.ByteArrayOutputStream();
            byte[] buf = new byte[4096];
            int n;
            while ((n = is.read(buf)) > 0) {
                if (bo.size() < max) {
                    bo.write(buf, 0, (int) Math.min(n, max - bo.size()));
                }
            }
            return bo.toByteArray();
        } catch (IOException e) {
            return new byte[0];
        }
    }

    private static String errorJson(String message, int exitCode, long t0) {
        return "{\"stdout\":\"\",\"stderr\":" + Json.quote(message + "\n") + ",\"exitCode\":" + exitCode
                + ",\"durationMs\":" + (System.nanoTime() - t0) / 1_000_000L + ",\"diagnostics\":[]}";
    }
}
