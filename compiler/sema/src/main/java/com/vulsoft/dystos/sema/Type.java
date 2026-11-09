package com.vulsoft.dystos.sema;

import com.vulsoft.dystos.sema.Symbols.ClassSymbol;
import java.util.List;
import java.util.StringJoiner;

/** Types de Dystos. {@code nullable()} indique un type suffixé par {@code ?}. */
public sealed interface Type {

    boolean nullable();

    Type withNullable(boolean nullable);

    /** Type de la valeur {@code null}. */
    Type NULL = new NullType();
    /** « Aucune valeur » : résultat d'une fonction sans type de retour. */
    Type UNIT = new UnitType();
    /** Type d'une expression erronée : compatible avec tout, pour éviter les erreurs en cascade. */
    Type ERROR = new ErrorType();

    record ClassType(ClassSymbol cls, List<Type> args, boolean nullable) implements Type {
        public ClassType {
            args = List.copyOf(args);
        }

        @Override
        public Type withNullable(boolean n) {
            return n == nullable ? this : new ClassType(cls, args, n);
        }

        @Override
        public String toString() {
            StringBuilder sb = new StringBuilder(cls.name);
            if (!args.isEmpty()) {
                StringJoiner j = new StringJoiner(", ", "[", "]");
                for (Type a : args) {
                    j.add(a.toString());
                }
                sb.append(j);
            }
            return nullable ? sb.append('?').toString() : sb.toString();
        }
    }

    record FunctionType(List<Type> params, Type result, boolean nullable) implements Type {
        public FunctionType {
            params = List.copyOf(params);
        }

        @Override
        public Type withNullable(boolean n) {
            return n == nullable ? this : new FunctionType(params, result, n);
        }

        @Override
        public String toString() {
            StringJoiner j = new StringJoiner(", ", "(", ")");
            for (Type p : params) {
                j.add(p.toString());
            }
            String s = j + " -> " + result;
            return nullable ? "(" + s + ")?" : s;
        }
    }

    /** Paramètre de type (T). L'identité est (name, id) ; {@code nullable} est un suffixe d'usage. */
    record TypeVar(String name, int id, boolean nullable) implements Type {
        @Override
        public Type withNullable(boolean n) {
            return n == nullable ? this : new TypeVar(name, id, n);
        }

        /** Clé d'identité (sans le suffixe nullable). */
        public TypeVar key() {
            return nullable ? new TypeVar(name, id, false) : this;
        }

        @Override
        public String toString() {
            return nullable ? name + "?" : name;
        }
    }

    record NullType() implements Type {
        @Override
        public boolean nullable() {
            return true;
        }

        @Override
        public Type withNullable(boolean n) {
            return this;
        }

        @Override
        public String toString() {
            return "null";
        }
    }

    record UnitType() implements Type {
        @Override
        public boolean nullable() {
            return false;
        }

        @Override
        public Type withNullable(boolean n) {
            return this;
        }

        @Override
        public String toString() {
            return "unit";
        }
    }

    record ErrorType() implements Type {
        @Override
        public boolean nullable() {
            return false;
        }

        @Override
        public Type withNullable(boolean n) {
            return this;
        }

        @Override
        public String toString() {
            return "<erreur>";
        }
    }
}
