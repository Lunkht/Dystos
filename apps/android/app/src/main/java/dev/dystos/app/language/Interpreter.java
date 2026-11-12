package dev.dystos.app.language;

import java.util.ArrayList;
import java.util.List;

/**
 * Exécution d'un programme Dystos.
 *
 * L'interpréteur est hors ligne par nature : pas de JVM intégrée, pas d'accès
 * système. Il n'y a ni boucle infinie autorisée au-delà d'un budget d'étapes,
 * ni lecture de fichiers, ce qui le rend sûr à faire tourner depuis un
 * exercice.
 */
public final class Interpreter {

    /** Nombre maximal d'étapes, garde-fou contre les boucles infinies. */
    static final long DEFAULT_STEP_LIMIT = 500_000L;

    /** Nombre maximal de lignes de sortie affichées. */
    static final int DEFAULT_OUTPUT_LIMIT = 2_000;

    private final StringBuilder output = new StringBuilder();
    private final long stepLimit;
    private final int outputLimit;
    private long steps;
    private int outputLines;
    private boolean outputTruncated;

    public Interpreter() {
        this(DEFAULT_STEP_LIMIT, DEFAULT_OUTPUT_LIMIT);
    }

    public Interpreter(long stepLimit, int outputLimit) {
        this.stepLimit = stepLimit;
        this.outputLimit = outputLimit;
    }

    /** Analise puis exécute, et renvoie la sortie accumulée. */
    public static String run(String source) {
        return run(source, DEFAULT_STEP_LIMIT, DEFAULT_OUTPUT_LIMIT);
    }

    /** Analise puis exécute, avec un budget d'étapes et de sortie. */
    public static String run(String source, long stepLimit, int outputLimit) {
        List<Statement> program = Parser.parse(source);
        Interpreter interpreter = new Interpreter(stepLimit, outputLimit);
        interpreter.execute(program);
        return interpreter.toOutput();
    }

    /** Exécute un programme déjà analysé. */
    public void execute(List<Statement> program) {
        Scope builtins = new Scope(null);
        Builtins.install(builtins);
        globals = new Scope(builtins);
        runBlock(program, globals);
    }

    /**
     * Portée globale du programme.
     *
     * Les fonctions y accèdent comme parent de leur portée locale : c'est ce
     * qui rend la récursion possible, une fonction pouvant s'appeler elle-même.
     */
    private Scope globals;

    /** Sortie accumulée, sans saut de ligne final superflu. */
    public String toOutput() {
        String text = output.toString();
        while (text.endsWith("\n")) {
            text = text.substring(0, text.length() - 1);
        }
        if (outputTruncated) {
            text = text + "\n…sortie tronquée…";
        }
        return text;
    }

    private void runBlock(List<Statement> statements, Scope scope) {
        for (Statement statement : statements) {
            step();
            run(statement, scope);
        }
    }

    private void run(Statement statement, Scope scope) {
        if (statement instanceof VarDeclaration) {
            runDeclaration((VarDeclaration) statement, scope);
        } else if (statement instanceof Assignment) {
            runAssignment((Assignment) statement, scope);
        } else if (statement instanceof ExpressionStatement) {
            evaluate(((ExpressionStatement) statement).expression(), scope);
        } else if (statement instanceof FunctionDeclaration) {
            FunctionDeclaration declaration = (FunctionDeclaration) statement;
            scope.declare(declaration.name(), new FunctionValue(declaration), false, null);
        } else if (statement instanceof IfStatement) {
            runIf((IfStatement) statement, scope);
        } else if (statement instanceof ForStatement) {
            runFor((ForStatement) statement, scope);
        } else if (statement instanceof WhileStatement) {
            runWhile((WhileStatement) statement, scope);
        } else if (statement instanceof ReturnStatement) {
            Expression value = ((ReturnStatement) statement).value();
            throw ControlSignal.ret(value == null ? null : evaluate(value, scope));
        } else if (statement instanceof BreakStatement) {
            throw ControlSignal.brk();
        } else if (statement instanceof ContinueStatement) {
            throw ControlSignal.cont();
        } else {
            throw new DystosException(
                    "instruction non gérée : " + statement.getClass().getSimpleName(),
                    statement.line());
        }
    }

    private void runDeclaration(VarDeclaration statement, Scope scope) {
        Object value = evaluate(statement.value(), scope);
        Types.checkDeclaredType(value, statement.declaredType(), statement.line());
        scope.declare(statement.name(), value, statement.isMutable(), statement.declaredType());
    }

    private void runAssignment(Assignment statement, Scope scope) {
        if (!scope.has(statement.name())) {
            throw new DystosException(
                    "affectation à une variable inconnue : '" + statement.name() + "'",
                    statement.line());
        }
        if (!scope.isMutable(statement.name())) {
            throw new DystosException(
                    "'" + statement.name() + "' est une val : elle n'est pas modifiable",
                    statement.line());
        }
        Object value = evaluate(statement.value(), scope);
        if (statement.compoundOperator() != null) {
            Object current = scope.get(statement.name());
            value = Binary.apply(
                    statement.compoundOperator(), current, value, statement.line());
        }
        Types.checkDeclaredType(value, scope.getDeclaredType(statement.name()), statement.line());
        scope.assign(statement.name(), value);
    }

    private void runIf(IfStatement statement, Scope scope) {
        if (Types.asBoolean(evaluate(statement.condition().expression(), scope),
                statement.line())) {
            runBlock(statement.thenBranch(), new Scope(scope));
            return;
        }
        for (IfStatement.Branch branch : statement.elifBranches()) {
            if (Types.asBoolean(evaluate(branch.condition().expression(), scope), statement.line())) {
                runBlock(branch.body(), new Scope(scope));
                return;
            }
        }
        if (statement.elseBranch() != null) {
            runBlock(statement.elseBranch(), new Scope(scope));
        }
    }

    private void runFor(ForStatement statement, Scope scope) {
        Object iterable = evaluate(statement.iterable(), scope);
        for (Object item : IterableSupport.iterate(iterable, statement.line())) {
            step();
            Scope loopScope = new Scope(scope);
            loopScope.declare(statement.variableName(), item, false, null);
            try {
                runBlock(statement.body(), loopScope);
            } catch (ControlSignal signal) {
                if (signal.kind() == ControlSignal.Kind.BREAK) {
                    return;
                }
                if (signal.kind() != ControlSignal.Kind.CONTINUE) {
                    throw signal;
                }
            }
        }
    }

    private void runWhile(WhileStatement statement, Scope scope) {
        while (Types.asBoolean(evaluate(statement.condition().expression(), scope),
                statement.line())) {
            step();
            Scope loopScope = new Scope(scope);
            try {
                runBlock(statement.body(), loopScope);
            } catch (ControlSignal signal) {
                if (signal.kind() == ControlSignal.Kind.BREAK) {
                    return;
                }
                if (signal.kind() != ControlSignal.Kind.CONTINUE) {
                    throw signal;
                }
            }
        }
    }

    // ------------------------------------------------------------- expressions

    Object evaluate(Expression expression, Scope scope) {
        if (expression instanceof Literal) {
            return ((Literal) expression).value();
        }
        if (expression instanceof Variable) {
            return scope.get(((Variable) expression).name());
        }
        if (expression instanceof FormattedString) {
            return evaluateFormatted((FormattedString) expression, scope);
        }
        if (expression instanceof ListLiteral) {
            List<Object> items = new ArrayList<>();
            for (Expression item : ((ListLiteral) expression).items()) {
                items.add(evaluate(item, scope));
            }
            return items;
        }
        if (expression instanceof Call) {
            return call((Call) expression, scope);
        }
        if (expression instanceof CallExpression) {
            return callExpression((CallExpression) expression, scope);
        }
        if (expression instanceof BinaryExpression) {
            BinaryExpression binary = (BinaryExpression) expression;
            Object left = evaluate(binary.left(), scope);
            Object right = evaluate(binary.right(), scope);
            return Binary.apply(binary.operator(), left, right, binary.line());
        }
        if (expression instanceof LogicalExpression) {
            return evaluateLogical((LogicalExpression) expression, scope);
        }
        if (expression instanceof UnaryExpression) {
            return evaluateUnary((UnaryExpression) expression, scope);
        }
        if (expression instanceof Condition) {
            return evaluate(((Condition) expression).expression(), scope);
        }
        throw new DystosException(
                "expression non gérée : " + expression.getClass().getSimpleName(),
                expression.line());
    }

    private Object evaluateLogical(LogicalExpression expression, Scope scope) {
        boolean left = Types.asBoolean(evaluate(expression.left(), scope), expression.line());
        if ("and".equals(expression.operator())) {
            if (!left) {
                return false;
            }
            return Types.asBoolean(evaluate(expression.right(), scope), expression.line());
        }
        if (left) {
            return true;
        }
        return Types.asBoolean(evaluate(expression.right(), scope), expression.line());
    }

    private Object evaluateUnary(UnaryExpression expression, Scope scope) {
        Object value = evaluate(expression.operand(), scope);
        if ("not".equals(expression.operator())) {
            return !Types.asBoolean(value, expression.line());
        }
        return Types.negate(value, expression.line());
    }

    private Object evaluateFormatted(FormattedString expression, Scope scope) {
        StringBuilder text = new StringBuilder();
        for (FormattedString.Part part : expression.parts()) {
            if (part.isLiteral()) {
                text.append(part.literal());
            } else {
                text.append(Display.render(evaluate(part.expression(), scope)));
            }
        }
        return text.toString();
    }

    private Object call(Call call, Scope scope) {
        List<Parameter> parameters = Builtins.signatureOf(call.name());

        if (parameters == null) {
            Object callee = scope.get(call.name());
            if (!(callee instanceof FunctionValue)) {
                throw new DystosException("fonction inconnue : '" + call.name() + "'",
                        call.line());
            }
            return invoke((FunctionValue) callee, call, scope);
        }

        List<Object> values = evaluateArguments(call, scope);
        return Builtins.call(call.name(), values, call.line(), this);
    }

    private Object callExpression(CallExpression expression, Scope scope) {
        Object callee = evaluate(expression.callee(), scope);
        if (!(callee instanceof FunctionValue)) {
            throw new DystosException("cette valeur n'est pas une fonction",
                    expression.line());
        }
        FunctionValue function = (FunctionValue) callee;
        List<Object> values = new ArrayList<>();
        for (Expression argument : expression.arguments()) {
            values.add(evaluate(argument, scope));
        }
        return invoke(function, function.name(), values, expression.line());
    }

    private List<Object> evaluateArguments(Call call, Scope scope) {
        List<Object> values = new ArrayList<>();
        for (int i = 0; i < call.size(); i++) {
            values.add(evaluate(call.argument(i), scope));
        }
        return values;
    }

    private Object invoke(FunctionValue function, Call call, Scope callerScope) {
        List<Parameter> parameters = function.parameters();

        Scope local = new Scope(globals);
        java.util.Set<String> filled = new java.util.HashSet<>();

        for (int i = 0; i < call.size(); i++) {
            Object value = evaluate(call.argument(i), callerScope);
            String argumentName = call.argumentName(i);
            int target = argumentName == null
                    ? i
                    : indexOfParameter(parameters, argumentName, call.line());
            Parameter parameter = parameters.get(target);
            Types.checkDeclaredType(value, parameter.declaredType(), call.line());
            local.declare(parameter.name(), value, false, parameter.declaredType());
            filled.add(parameter.name());
        }

        for (Parameter parameter : parameters) {
            if (filled.contains(parameter.name())) {
                continue;
            }
            if (parameter.defaultValue() == null) {
                throw new DystosException(
                        "argument manquant pour le paramètre '" + parameter.name() + "'",
                        call.line());
            }
            local.declare(parameter.name(), evaluate(parameter.defaultValue(), local),
                    false, parameter.declaredType());
        }

        if (call.size() > parameters.size()) {
            throw new DystosException(
                    "trop d'arguments pour '" + function.name() + "' ("
                            + parameters.size() + " attendu(s))", call.line());
        }

        return runFunctionBody(function, local, call.line());
    }

    private Object invoke(FunctionValue function, String callSite,
                          List<Object> values, int line) {
        List<Parameter> parameters = function.parameters();
        if (values.size() > parameters.size()) {
            throw new DystosException(
                    "trop d'arguments pour '" + callSite + "' ("
                            + parameters.size() + " attendu(s))", line);
        }

        Scope local = new Scope(globals);
        for (int i = 0; i < parameters.size(); i++) {
            Parameter parameter = parameters.get(i);
            Object value;
            if (i < values.size()) {
                value = values.get(i);
            } else if (parameter.defaultValue() != null) {
                value = evaluate(parameter.defaultValue(), local);
            } else {
                throw new DystosException(
                        "argument manquant pour le paramètre '" + parameter.name() + "'", line);
            }
            Types.checkDeclaredType(value, parameter.declaredType(), line);
            local.declare(parameter.name(), value, false, parameter.declaredType());
        }
        return runFunctionBody(function, local, line);
    }

    private Object runFunctionBody(FunctionValue function, Scope local, int line) {
        try {
            runBlock(function.body(), local);
            return null;
        } catch (ControlSignal signal) {
            if (signal.kind() == ControlSignal.Kind.RETURN) {
                Object value = signal.value();
                Types.checkDeclaredType(value, function.returnType(), line);
                return value;
            }
            throw signal;
        }
    }

    private int indexOfParameter(List<Parameter> parameters, String name, int line) {
        for (int i = 0; i < parameters.size(); i++) {
            if (parameters.get(i).name().equals(name)) {
                return i;
            }
        }
        throw new DystosException("paramètre inconnu : '" + name + "'", line);
    }

    /** Ajoute une ligne à la sortie, en respectant la limite. */
    void print(Object text) {
        if (outputLines >= outputLimit) {
            outputTruncated = true;
            return;
        }
        output.append(Display.render(text)).append('\n');
        outputLines++;
    }

    private void step() {
        if (++steps > stepLimit) {
            throw new DystosException(
                    "arrêt du programme : trop d'étapes, boucle infinie ?");
        }
    }
}