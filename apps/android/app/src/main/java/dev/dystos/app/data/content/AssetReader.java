package dev.dystos.app.data.content;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;

/** Lecture d'un fichier d'assets en mémoire. */
final class AssetReader {

    private static final Charset UTF_8 = StandardCharsets.UTF_8;

    private AssetReader() {
    }

    static String read(InputStream input) throws IOException {
        try (InputStream stream = input) {
            ByteArrayOutputStream buffer = new ByteArrayOutputStream(8192);
            byte[] chunk = new byte[8192];
            int read;
            while ((read = stream.read(chunk)) != -1) {
                buffer.write(chunk, 0, read);
            }
            // `ByteArrayOutputStream.toString(Charset)` n'existe qu'à partir de
            // l'API 33 : on décode donc explicitement pour rester compatible.
            return new String(buffer.toByteArray(), UTF_8);
        }
    }
}