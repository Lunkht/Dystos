package dystos.rt;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.List;

/**
 * Fichier texte Dystos ({@code io.open}). Mêmes règles que l'interpréteur : modes {@code r}, {@code w}, {@code a} ;
 * l'écriture est tamponnée et enregistrée à la fermeture ; un fichier fermé ne peut plus être lu ni écrit.
 */
public final class DFile implements AutoCloseable {
    private final Path path;
    private final String mode;
    private final StringBuilder buf = new StringBuilder();
    private boolean closed;

    DFile(Path path, String mode) {
        this.path = path;
        this.mode = mode;
    }

    public void write(String s) {
        if (mode.equals("r") || closed) {
            throw Rt.rt("fichier non ouvert en écriture");
        }
        buf.append(s);
    }

    public String read() {
        requireReadable();
        try {
            return Files.readString(path);
        } catch (IOException e) {
            throw Rt.rt("erreur d'entrée/sortie : " + e.getMessage());
        }
    }

    public List<Object> lines() {
        requireReadable();
        try {
            return new ArrayList<Object>(Files.readAllLines(path));
        } catch (IOException e) {
            throw Rt.rt("erreur d'entrée/sortie : " + e.getMessage());
        }
    }

    @Override
    public void close() {
        if (!closed && !mode.equals("r")) {
            try {
                Files.writeString(path, buf, StandardOpenOption.CREATE, StandardOpenOption.WRITE,
                        mode.equals("a") ? StandardOpenOption.APPEND : StandardOpenOption.TRUNCATE_EXISTING);
            } catch (IOException e) {
                closed = true;
                throw Rt.rt("erreur d'entrée/sortie : " + e.getMessage());
            }
        }
        closed = true;
    }

    private void requireReadable() {
        if (!mode.equals("r") || closed) {
            throw Rt.rt("fichier non ouvert en lecture");
        }
    }

    @Override
    public String toString() {
        return "<File>";
    }
}
