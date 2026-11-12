package dev.dystos.app.language;

import java.util.List;

/** Instruction conditionnelle {@code if} / {@code elif} / {@code else}. */
public final class IfStatement implements Statement {

    /** Paire condition / corps pour un {@code elif}. */
    public static final class Branch {
        private final Condition condition;
        private final List<Statement> body;

        public Branch(Condition condition, List<Statement> body) {
            this.condition = condition;
            this.body = body;
        }

        public Condition condition() {
            return condition;
        }

        public List<Statement> body() {
            return body;
        }
    }

    private final Condition condition;
    private final List<Statement> thenBranch;
    private final List<Branch> elifBranches;
    private final List<Statement> elseBranch;
    private final int line;

    public IfStatement(Condition condition, List<Statement> thenBranch,
                       List<Branch> elifBranches, List<Statement> elseBranch, int line) {
        this.condition = condition;
        this.thenBranch = thenBranch;
        this.elifBranches = elifBranches;
        this.elseBranch = elseBranch;
        this.line = line;
    }

    public Condition condition() {
        return condition;
    }

    public List<Statement> thenBranch() {
        return thenBranch;
    }

    public List<Branch> elifBranches() {
        return elifBranches;
    }

    public List<Statement> elseBranch() {
        return elseBranch;
    }

    @Override
    public int line() {
        return line;
    }
}