package dystos.rt;

/**
 * Fonction Dystos comme valeur (lambda, ou fonction nommée passée en argument). Convention unique : arguments et
 * résultat sont encadrés ({@code Object}) ; une fonction sans résultat retourne {@code null}.
 */
public interface Fn {
    Object call(Object[] args);
}
