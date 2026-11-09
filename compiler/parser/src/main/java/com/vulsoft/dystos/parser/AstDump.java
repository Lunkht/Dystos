package com.vulsoft.dystos.parser;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

/** Utilitaire : {@code java -cp ... com.vulsoft.dystos.parser.AstDump fichier.dys} */
public final class AstDump {

    private AstDump() {
    }

    public static void main(String[] args) throws IOException {
        if (args.length != 1) {
            System.err.println("usage : AstDump <fichier.dys>");
            System.exit(2);
        }
        try {
            System.out.print(AstPrinter.print(Parser.parse(Files.readString(Path.of(args[0])))));
        } catch (RuntimeException e) {
            System.err.println(args[0] + " : " + e.getMessage());
            System.exit(1);
        }
    }
}
