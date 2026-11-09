package com.vulsoft.dystos.jvm.classfile;

import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

/**
 * Générateur de bytecode d'une méthode : suit la profondeur de pile (calcul de {@code max_stack}), résout les
 * étiquettes et construit la table d'exceptions.
 *
 * <p>Le code mort (après {@code goto}, {@code return}, {@code athrow}) est ignoré jusqu'à la prochaine étiquette
 * atteinte par un saut. Types de valeurs : {@code 'I'} (int, boolean), {@code 'J'} (long), {@code 'D'} (double),
 * {@code 'A'} (référence).
 */
public final class Code {

    // opcodes utilisés
    public static final int ACONST_NULL = 1, ICONST_0 = 3, ICONST_1 = 4, ICONST_M1 = 2, LCONST_0 = 9, LCONST_1 = 10,
            DCONST_0 = 14, DCONST_1 = 15, BIPUSH = 16, SIPUSH = 17;
    public static final int ILOAD = 21, LLOAD = 22, DLOAD = 24, ALOAD = 25;
    public static final int ISTORE = 54, LSTORE = 55, DSTORE = 57, ASTORE = 58;
    public static final int AALOAD = 50, AASTORE = 83, POP = 87, POP2 = 88, DUP = 89, DUP_X1 = 90, DUP_X2 = 91, DUP2 = 92,
            DUP2_X1 = 93, DUP2_X2 = 94, SWAP = 95;
    public static final int LADD = 97, DADD = 99, LSUB = 101, DSUB = 103, LMUL = 105, DMUL = 107, DNEG = 119,
            IXOR = 130, I2L = 133, L2D = 138, F2D = 141, D2F = 144;
    public static final int LCMP = 148, DCMPL = 151, DCMPG = 152;
    public static final int IFEQ = 153, IFNE = 154, IFLT = 155, IFGE = 156, IFGT = 157, IFLE = 158,
            IF_ACMPEQ = 165, IF_ACMPNE = 166, GOTO = 167, IFNULL = 198, IFNONNULL = 199;
    public static final int IRETURN = 172, LRETURN = 173, DRETURN = 175, ARETURN = 176, RETURN = 177;
    public static final int GETSTATIC = 178, PUTSTATIC = 179, GETFIELD = 180, PUTFIELD = 181, INVOKEVIRTUAL = 182,
            INVOKESPECIAL = 183, INVOKESTATIC = 184, INVOKEINTERFACE = 185;
    public static final int NEW = 187, ANEWARRAY = 189, ATHROW = 191, CHECKCAST = 192, INSTANCEOF = 193;

    private static final class Fixup {
        final int instrPos;
        final int operandPos;
        final Label label;

        Fixup(int instrPos, int operandPos, Label label) {
            this.instrPos = instrPos;
            this.operandPos = operandPos;
            this.label = label;
        }
    }

    private static final class Handler {
        final Label start;
        final Label end;
        final Label handler;
        final String type;

        Handler(Label start, Label end, Label handler, String type) {
            this.start = start;
            this.end = end;
            this.handler = handler;
            this.type = type;
        }
    }

    private final ConstPool cp;
    private byte[] buf = new byte[256];
    private int len;
    private int stack;
    private int maxStack;
    private int nextLocal;
    private int maxLocals;
    private boolean reachable = true;
    private final List<Fixup> fixups = new ArrayList<>();
    private final List<Handler> handlers = new ArrayList<>();
    private final List<int[]> lines = new ArrayList<>();

    Code(ConstPool cp, int initialLocals) {
        this.cp = cp;
        this.nextLocal = initialLocals;
        this.maxLocals = initialLocals;
    }

    public boolean reachable() {
        return reachable;
    }

    /** Les instructions émises à partir d'ici proviennent de la ligne source {@code line}. */
    public void line(int line) {
        if (!reachable || line <= 0) {
            return;
        }
        if (!lines.isEmpty()) {
            int[] last = lines.get(lines.size() - 1);
            if (last[1] == line) {
                return;
            }
            if (last[0] == len) {
                last[1] = line;
                return;
            }
        }
        lines.add(new int[] {len, line});
    }

    public int stackDepth() {
        return stack;
    }

    // ------------------------------------------------------------------
    // Variables locales
    // ------------------------------------------------------------------

    /** Réserve un emplacement ({@code kind} : 'I', 'J', 'D' ou 'A'). */
    public int newLocal(char kind) {
        int slot = nextLocal;
        nextLocal += size(kind);
        maxLocals = Math.max(maxLocals, nextLocal);
        return slot;
    }

    public void load(char kind, int slot) {
        varOp(opLoad(kind), slot);
        push(size(kind));
    }

    public void store(char kind, int slot) {
        varOp(opStore(kind), slot);
        pop(size(kind));
    }

    private static int opLoad(char k) {
        switch (k) {
            case 'I': return ILOAD;
            case 'J': return LLOAD;
            case 'D': return DLOAD;
            default: return ALOAD;
        }
    }

    private static int opStore(char k) {
        switch (k) {
            case 'I': return ISTORE;
            case 'J': return LSTORE;
            case 'D': return DSTORE;
            default: return ASTORE;
        }
    }

    private void varOp(int op, int slot) {
        if (!reachable) {
            return;
        }
        if (slot <= 255) {
            u1(op);
            u1(slot);
        } else {
            u1(196);
            u1(op);
            u2(slot);
        }
    }

    public static int size(char kind) {
        return kind == 'J' || kind == 'D' ? 2 : 1;
    }

    // ------------------------------------------------------------------
    // Constantes
    // ------------------------------------------------------------------

    public void aconstNull() {
        simple(ACONST_NULL, 1);
    }

    public void iconst(int v) {
        if (!reachable) {
            return;
        }
        if (v >= -1 && v <= 5) {
            u1(ICONST_0 + v);
        } else if (v >= -128 && v <= 127) {
            u1(BIPUSH);
            u1(v);
        } else if (v >= -32768 && v <= 32767) {
            u1(SIPUSH);
            u2(v);
        } else {
            ldc(cp.integer(v));
        }
        push(1);
    }

    public void lconst(long v) {
        if (!reachable) {
            return;
        }
        if (v == 0 || v == 1) {
            u1(LCONST_0 + (int) v);
        } else {
            u1(20);
            u2(cp.longConst(v));
        }
        push(2);
    }

    public void dconst(double v) {
        if (!reachable) {
            return;
        }
        if (Double.doubleToRawLongBits(v) == 0L) {
            u1(DCONST_0);
        } else if (v == 1.0) {
            u1(DCONST_1);
        } else {
            u1(20);
            u2(cp.doubleConst(v));
        }
        push(2);
    }

    public void ldcString(String s) {
        if (!reachable) {
            return;
        }
        ldc(cp.string(s));
        push(1);
    }

    private void ldc(int idx) {
        if (idx <= 255) {
            u1(18);
            u1(idx);
        } else {
            u1(19);
            u2(idx);
        }
    }

    // ------------------------------------------------------------------
    // Instructions simples
    // ------------------------------------------------------------------

    /** Instruction sans opérande avec son effet sur la pile (positif : empile). */
    public void simple(int opcode, int delta) {
        if (!reachable) {
            return;
        }
        u1(opcode);
        adjust(delta);
        if (opcode == ATHROW || (opcode >= IRETURN && opcode <= RETURN)) {
            reachable = false;
        }
    }

    public void pop(char kind) {
        simple(size(kind) == 2 ? POP2 : POP, -size(kind));
    }

    public void dup(char kind) {
        simple(size(kind) == 2 ? DUP2 : DUP, size(kind));
    }

    public void swapRefUnder(char topKind) {
        // ..., ref, valeur -> ..., valeur, ref  (valeur de taille 1 ou 2)
        if (size(topKind) == 1) {
            simple(SWAP, 0);
        } else {
            simple(DUP2_X1, 2);
            simple(POP2, -2);
        }
    }

    public void checkcast(String internalName) {
        typeOp(CHECKCAST, internalName, 0);
    }

    public void instanceOf(String internalName) {
        typeOp(INSTANCEOF, internalName, 0);
    }

    public void anew(String internalName) {
        typeOp(NEW, internalName, 1);
    }

    public void anewarray(String internalName) {
        typeOp(ANEWARRAY, internalName, 0);
    }

    private void typeOp(int op, String name, int delta) {
        if (!reachable) {
            return;
        }
        u1(op);
        u2(cp.cls(name));
        adjust(delta);
    }

    public void ret(char kind) {
        switch (kind) {
            case 'V': simple(RETURN, 0); break;
            case 'I': simple(IRETURN, -1); break;
            case 'J': simple(LRETURN, -2); break;
            case 'D': simple(DRETURN, -2); break;
            default: simple(ARETURN, -1);
        }
    }

    // ------------------------------------------------------------------
    // Champs et méthodes
    // ------------------------------------------------------------------

    public void getstatic(String owner, String name, String desc) {
        fieldOp(GETSTATIC, owner, name, desc, size(desc.charAt(0)));
    }

    public void putstatic(String owner, String name, String desc) {
        fieldOp(PUTSTATIC, owner, name, desc, -size(desc.charAt(0)));
    }

    public void getfield(String owner, String name, String desc) {
        fieldOp(GETFIELD, owner, name, desc, size(desc.charAt(0)) - 1);
    }

    public void putfield(String owner, String name, String desc) {
        fieldOp(PUTFIELD, owner, name, desc, -size(desc.charAt(0)) - 1);
    }

    private void fieldOp(int op, String owner, String name, String desc, int delta) {
        if (!reachable) {
            return;
        }
        u1(op);
        u2(cp.field(owner, name, desc));
        adjust(delta);
    }

    public void invokestatic(String owner, String name, String desc) {
        invoke(INVOKESTATIC, owner, name, desc, false);
    }

    public void invokevirtual(String owner, String name, String desc) {
        invoke(INVOKEVIRTUAL, owner, name, desc, false);
    }

    public void invokespecial(String owner, String name, String desc) {
        invoke(INVOKESPECIAL, owner, name, desc, false);
    }

    public void invokeinterface(String owner, String name, String desc) {
        invoke(INVOKEINTERFACE, owner, name, desc, true);
    }

    private void invoke(int op, String owner, String name, String desc, boolean iface) {
        if (!reachable) {
            return;
        }
        int args = argSlots(desc);
        int ret = returnSlots(desc);
        u1(op);
        u2(iface ? cp.interfaceMethod(owner, name, desc) : cp.method(owner, name, desc));
        if (iface) {
            u1(args + 1);
            u1(0);
        }
        adjust(ret - args - (op == INVOKESTATIC ? 0 : 1));
    }

    /** Nombre d'emplacements de pile occupés par les arguments d'un descripteur de méthode. */
    public static int argSlots(String desc) {
        int n = 0;
        int i = 1;
        while (desc.charAt(i) != ')') {
            char c = desc.charAt(i);
            if (c == 'J' || c == 'D') {
                n += 2;
                i++;
            } else if (c == 'L') {
                n++;
                i = desc.indexOf(';', i) + 1;
            } else if (c == '[') {
                while (desc.charAt(i) == '[') {
                    i++;
                }
                if (desc.charAt(i) == 'L') {
                    i = desc.indexOf(';', i) + 1;
                } else {
                    i++;
                }
                n++;
            } else {
                n++;
                i++;
            }
        }
        return n;
    }

    public static int returnSlots(String desc) {
        char c = desc.charAt(desc.indexOf(')') + 1);
        return c == 'V' ? 0 : (c == 'J' || c == 'D') ? 2 : 1;
    }

    // ------------------------------------------------------------------
    // Sauts, étiquettes, exceptions
    // ------------------------------------------------------------------

    /** Saut conditionnel ou inconditionnel vers {@code target}. */
    public void jump(int opcode, Label target) {
        if (!reachable) {
            return;
        }
        int delta;
        switch (opcode) {
            case GOTO: delta = 0; break;
            case IF_ACMPEQ:
            case IF_ACMPNE: delta = -2; break;
            default: delta = -1;
        }
        int at = len;
        u1(opcode);
        int operand = len;
        u2(0);
        adjust(delta);
        fixups.add(new Fixup(at, operand, target));
        mergeDepth(target);
        if (opcode == GOTO) {
            reachable = false;
        }
    }

    private void mergeDepth(Label l) {
        if (l.depth < 0) {
            l.depth = stack;
        } else if (l.depth != stack) {
            throw new IllegalStateException("profondeur de pile incohérente à un saut : " + stack + " au lieu de " + l.depth);
        }
    }

    /** Pose l'étiquette ici. */
    public void label(Label l) {
        l.pos = len;
        if (reachable) {
            if (l.depth < 0) {
                l.depth = stack;
            } else if (l.depth != stack) {
                throw new IllegalStateException("profondeur de pile incohérente à une étiquette : " + stack + " au lieu de " + l.depth);
            }
        } else if (l.depth >= 0) {
            stack = l.depth;
            reachable = true;
        }
    }

    /** Pose l'étiquette d'un gestionnaire d'exception : l'exception est sur la pile. */
    public void handlerLabel(Label l) {
        l.pos = len;
        l.depth = 1;
        stack = 1;
        maxStack = Math.max(maxStack, 1);
        reachable = true;
    }

    /** Déclare une zone protégée [start, end) dont le gestionnaire est {@code handler} ({@code type} null : tout). */
    public void tryCatch(Label start, Label end, Label handler, String type) {
        handlers.add(new Handler(start, end, handler, type));
    }

    // ------------------------------------------------------------------
    // Assemblage
    // ------------------------------------------------------------------

    private void adjust(int delta) {
        stack += delta;
        if (stack < 0) {
            throw new IllegalStateException("pile vide : instruction invalide (code en cours d'écriture)");
        }
        maxStack = Math.max(maxStack, stack);
    }

    private void push(int n) {
        adjust(n);
    }

    private void pop(int n) {
        adjust(-n);
    }

    private void u1(int v) {
        ensure(1);
        buf[len++] = (byte) v;
    }

    private void u2(int v) {
        ensure(2);
        buf[len++] = (byte) (v >> 8);
        buf[len++] = (byte) v;
    }

    private void ensure(int n) {
        if (len + n > buf.length) {
            buf = java.util.Arrays.copyOf(buf, Math.max(buf.length * 2, len + n));
        }
    }

    /** Attribut « Code » complet. */
    byte[] toAttribute() {
        for (Fixup f : fixups) {
            if (f.label.pos < 0) {
                throw new IllegalStateException("étiquette jamais posée");
            }
            int off = f.label.pos - f.instrPos;
            if (off < Short.MIN_VALUE || off > Short.MAX_VALUE) {
                throw new MethodTooLargeException();
            }
            buf[f.operandPos] = (byte) (off >> 8);
            buf[f.operandPos + 1] = (byte) off;
        }
        if (len > 65535) {
            throw new MethodTooLargeException();
        }
        ByteArrayOutputStream bo = new ByteArrayOutputStream();
        DataOutputStream o = new DataOutputStream(bo);
        try {
            List<int[]> table = new ArrayList<>();
            for (Handler h : handlers) {
                if (h.start.pos >= 0 && h.end.pos > h.start.pos && h.handler.pos >= 0) {
                    table.add(new int[] {h.start.pos, h.end.pos, h.handler.pos, h.type == null ? 0 : cp.cls(h.type)});
                }
            }
            o.writeShort(maxStack);
            o.writeShort(maxLocals);
            o.writeInt(len);
            o.write(buf, 0, len);
            o.writeShort(table.size());
            for (int[] e : table) {
                o.writeShort(e[0]);
                o.writeShort(e[1]);
                o.writeShort(e[2]);
                o.writeShort(e[3]);
            }
            if (lines.isEmpty()) {
                o.writeShort(0);
            } else {
                o.writeShort(1);
                o.writeShort(cp.utf8("LineNumberTable"));
                o.writeInt(2 + 4 * lines.size());
                o.writeShort(lines.size());
                for (int[] l : lines) {
                    o.writeShort(l[0]);
                    o.writeShort(l[1]);
                }
            }
        } catch (IOException e) {
            throw new IllegalStateException(e);
        }
        return bo.toByteArray();
    }

    /** Position courante dans le code (en octets). */
    public int position() {
        return len;
    }

    /** Position d'une étiquette posée, ou -1. */
    public static int positionOf(Label l) {
        return l.pos;
    }

    /** La méthode dépasse les limites du format (65 535 octets, ou saut de plus de 32 Ko). */
    public static final class MethodTooLargeException extends RuntimeException {
        private static final long serialVersionUID = 1L;

        public MethodTooLargeException() {
            super("méthode trop grande pour le backend JVM");
        }
    }
}
