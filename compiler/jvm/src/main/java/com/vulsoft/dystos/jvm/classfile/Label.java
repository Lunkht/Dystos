package com.vulsoft.dystos.jvm.classfile;

/** Cible de saut. {@code depth} : profondeur de pile à cette cible (-1 tant qu'inconnue). */
public final class Label {
    int pos = -1;
    int depth = -1;

    /** Vrai si un saut vers cette étiquette a déjà été émis ou si elle est déjà posée. */
    public boolean isUsed() {
        return depth >= 0 || pos >= 0;
    }
}
