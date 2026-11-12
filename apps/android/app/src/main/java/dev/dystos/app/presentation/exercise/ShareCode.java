package dev.dystos.app.presentation.exercise;

import android.content.Context;
import android.content.Intent;
import android.net.Uri;

import androidx.core.content.FileProvider;

import java.io.File;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;

/**
 * Partage d'un programme Dystos.
 *
 * Un `FileProvider` est nécessaire pour qu'une autre application lise le
 * fichier : passer un chemin brut serait refusé depuis Android 7. Le contenu
 * transite par le cache privé de l'application, que le système efface.
 */
public final class ShareCode {

    /** Extension des programmes Dystos. */
    static final String EXTENSION = ".dys";

    private ShareCode() {
    }

    /** Envoie le code comme texte brut, pour un copier-coller. */
    public static void text(Context context, String code, String title) {
        Intent intent = new Intent(Intent.ACTION_SEND);
        intent.setType("text/plain");
        intent.putExtra(Intent.EXTRA_SUBJECT, title);
        intent.putExtra(Intent.EXTRA_TEXT, code);
        context.startActivity(Intent.createChooser(intent, title));
    }

    /** Envoie le code comme fichier `.dys`, pour qui a Dystos d'installé. */
    public static void file(Context context, String code, String title) {
        Intent intent = new Intent(Intent.ACTION_SEND);
        intent.setType("text/plain");
        intent.putExtra(Intent.EXTRA_SUBJECT, title);
        intent.putExtra(Intent.EXTRA_STREAM, toUri(context, code));
        intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
        context.startActivity(Intent.createChooser(intent, title));
    }

    /** Écrit le code dans le cache et renvoie une URI lisible par autrui. */
    static Uri toUri(Context context, String code) {
        File directory = new File(context.getCacheDir(), "partage");
        if (!directory.exists() && !directory.mkdirs()) {
            throw new UncheckedIOException(new IOException(
                    "cache de partage indisponible : " + directory));
        }

        try {
            // Un nom unique par partage évite d'écraser un fichier déjà ouvert.
            File target = File.createTempFile("programme", EXTENSION, directory);
            Files.write(target.toPath(), code.getBytes(StandardCharsets.UTF_8));
            return FileProvider.getUriForFile(context,
                    context.getPackageName() + ".partage", target);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }
}