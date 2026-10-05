package dev.dystos.app;

import android.app.Application;

import dev.dystos.app.core.ThemePreferences;
import dev.dystos.app.di.ServiceLocator;

/**
 * Point d'entrée de l'application.
 *
 * L'application est entièrement hors ligne : aucun service distant, aucune
 * permission réseau. Le graphe d'objets est minimal et créé ici.
 */
public class DystosApplication extends Application {

    @Override
    public void onCreate() {
        super.onCreate();
        // Le thème sombre est l'ambiance par défaut de Dystos.
        ThemePreferences.apply(this);
        ServiceLocator.initialize(this);
    }
}