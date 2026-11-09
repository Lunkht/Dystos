# dystos-lexer (Phase 1)

Analyseur lexical du langage **Dystos**. Java 21, aucune dépendance à l'exécution.

## Utilisation

```java
List<Token> tokens = Lexer.tokenize(Files.readString(Path.of("exemple.dys")));
```

La liste se termine toujours par `EOF`. Une erreur lève `LexerException` (ligne, colonne, message).

## Build et tests

```
cd compiler/lexer
mvn test
```

JUnit 5 est la seule dépendance (tests uniquement). Pour construire hors ligne, préparer
un dépôt Maven local (`mvn dependency:go-offline` une fois, puis `mvn -o test`).

## Règles lexicales

| Sujet | Règle |
|---|---|
| Indentation | Espaces uniquement, tout nombre cohérent accepté ; tabulation dans l'indentation = erreur |
| Blocs | `INDENT` / `DEDENT` émis au changement de niveau ; équilibrés avant `EOF` |
| Lignes vides, commentaires `#` | Aucun jeton |
| Délimiteurs `( ) [ ] { }` | Retours à la ligne ignorés à l'intérieur ; non-appariement = erreur |
| Continuation | `\` en fin de ligne |
| Nombres | `1_000`, `0xFF`, `0b101`, `3.14`, `1e3` ; le texte du jeton n'a pas de `_` |
| Chaînes | `'..'`, `".."`, `"""..."""` ; échappements `\n \t \r \0 \\ \' \" \uXXXX` |
| f-strings | `f".."` : jeton `FSTRING` contenant le texte **brut** (découpage `{expr}` fait par le parser) |
| `?:` | `ELVIS` dans `a ?: b` ; mais `QUESTION` + `COLON` si le `:` termine la ligne (`-> Produit?:`) |
| Mots-clés | `def class interface enum abstract final import package if elif else for while in return break continue pass try except finally raise assert match case with as val var true false null and or not is` |

Les mots-clés restent utilisables comme noms de méthodes Java après un `.` : c'est au parser de
l'autoriser (ex. `pattern.match(...)`).

## Fichiers

```
src/main/java/com/vulsoft/dystos/lexer/
  TokenType.java  Token.java  LexerException.java  Lexer.java
src/test/java/com/vulsoft/dystos/lexer/LexerTest.java
src/test/resources/exemples/*.dys      # les 20 exemples de la Phase 0
```

## Colorateur tolérant : `Highlighter`

Le lexer s'arrête à la première erreur et ignore les commentaires ; un éditeur a besoin du contraire.
`Highlighter.highlight(texte)` ne lève jamais d'exception et retourne des `Span(start, end, kind)` triés, sans
chevauchement, en **indices UTF-16** (directement utilisables avec `Spannable` sur Android).

```java
for (Highlighter.Span s : Highlighter.highlight(source)) { /* s.start(), s.end(), s.kind() */ }
```
`Kind` : `KEYWORD, CONSTANT, TYPE, FUNCTION, BUILTIN, SELF, NUMBER, STRING, ESCAPE, COMMENT, DECORATOR, OPERATOR,
PUNCTUATION` ; les identifiants ordinaires n'ont pas de span. Les f-strings sont colorées par morceaux
(texte, `{`, expression colorée récursivement, format `:.2f`, `}`).

Les listes `KEYWORDS`, `TYPES`, `BUILTINS`, `OPERATORS` sont la source des grammaires générées
(`scripts/generate-grammar.sh`). **Si vous ajoutez un mot-clé** : `TokenType`, `Lexer`, puis
`GenerateGrammar` (qui refuse de générer tant que le mot n'est pas classé), puis `scripts/generate-grammar.sh`.
