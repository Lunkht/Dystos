package dystos.rt;

/** Classe de base des objets Dystos : affichage par défaut {@code <Nom>}, comme l'interpréteur. */
public class DObject {
    public DObject() {
    }

    @Override
    public String toString() {
        return "<" + getClass().getSimpleName() + ">";
    }
}
