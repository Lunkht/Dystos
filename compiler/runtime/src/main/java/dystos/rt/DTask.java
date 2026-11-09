package dystos.rt;

/**
 * Tâche concurrente ({@code concurrent.run}) : la fonction s'exécute dans son propre thread dès la création ;
 * {@code await()} attend la fin puis retourne le résultat, ou relance l'exception levée par la tâche.
 * Une tâche jamais attendue va quand même au bout avant la fin du programme (thread non démon).
 */
public final class DTask {
    private final Thread thread;
    private Object result;
    private Throwable error;

    DTask(Fn task) {
        this.thread = new Thread(() -> {
            try {
                result = task.call(new Object[0]);
            } catch (Throwable t) {
                error = t;
            }
        }, "dystos-task");
        thread.start();
    }

    public Object await() {
        try {
            thread.join();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw Rt.rt("tâche interrompue");
        }
        if (error != null) {
            if (error instanceof StackOverflowError) {
                throw Rt.rt("profondeur de récursion maximale dépassée");
            }
            if (error instanceof RuntimeException) {
                throw (RuntimeException) error;
            }
            if (error instanceof Error) {
                throw (Error) error;
            }
            throw Rt.rt(String.valueOf(error));
        }
        return result;
    }

    @Override
    public String toString() {
        return "<Task>";
    }
}
