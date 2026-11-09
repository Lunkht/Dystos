package dystos.rt;

/**
 * Boîte partagée pour une variable locale modifiable capturée par une lambda : la lambda et la fonction qui
 * l'a créée lisent et écrivent la même valeur.
 */
public final class Ref {
    public Object v;

    public Ref(Object v) {
        this.v = v;
    }
}
