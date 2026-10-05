/**
 * Grammaire de coloration Dystos pour Prism (utilisée par le site).
 *
 * Cette grammaire doit rester alignée avec les jetons produits par le lexer
 * du compilateur (voir compiler/). Le compilateur est la source de vérité :
 * quand ses jetons évoluent, cette grammaire est mise à jour dans le même
 * changement.
 */

type PrismLanguages = Record<string, unknown>;

const KEYWORDS =
  /\b(?:def|val|var|class|interface|enum|record|object|if|elif|else|for|while|match|case|return|break|continue|import|package|extends|implements|new|this|self|super|try|catch|finally|throw|as|is|in|and|or|not|typealias|static|public|private|protected|internal|fun|where|yield)\b/;

const CONSTANTS = /\b(?:true|false|null|none)\b/;

const TYPES =
  /\b(?:int|float|double|string|bool|char|void|any|list|dict|set|tuple|range|bytes|unit)\b/;

const BUILTINS =
  /(^|[^\w.])(?:print|println|len|input|read|abs|min|max|sum|sorted|reversed|enumerate|zip|assert|typeof|hash|open)\b/;

const OPERATOR = /->|=>|\?\.|\?:|\?\?|::|\.\.\.|[+\-*/%=!<>|&^~?:]+|\.{2,3}/;

const NUMBER =
  /\b(?:0[xX][\da-fA-F_]+|0[bB][01_]+|0[oO][0-7_]+|(?:\d[\d_]*)?\.\d[\d_]*(?:[eE][+-]?\d[\d_]*)?|\d[\d_]*(?:\.\d*)?(?:[eE][+-]?\d[\d_]*)?)[fFdDlLuU]?\b/;

export const DYSTOS_PRISM_GRAMMAR = {
  comment: {
    pattern: /(^|[^\\])#.*/,
    lookbehind: true,
    greedy: true,
  },
  string: {
    pattern:
      /(^|[^\\])(?:[rbf]{0,2})(?:"""[\s\S]*?"""|'''[\s\S]*?'''|"(?:\\.|[^"\\\n])*"|'(?:\\.|[^'\\\n])*')/,
    lookbehind: true,
    greedy: true,
  },
  interpolation: {
    pattern: /\{[^{}\n]*\}/,
    inside: {},
  },
  'class-name': {
    pattern:
      /(\b(?:class|interface|enum|record|object|new|extends|implements|as|is|case)\s+)[A-Z]\w*/,
    lookbehind: true,
  },
  keyword: KEYWORDS,
  constant: CONSTANTS,
  type: TYPES,
  builtin: BUILTINS,
  function: /\b[A-Za-z_]\w*(?=\s*\()/,
  'attr-name': {
    pattern: /(\.)[A-Za-z_]\w*/,
    lookbehind: true,
  },
  number: NUMBER,
  operator: OPERATOR,
  punctuation: /[{}[\];(),.:]/,
};

export function registerDystosLanguage(prism: {
  languages: PrismLanguages;
}): void {
  if (prism.languages.dystos) {
    return;
  }
  prism.languages.dystos = DYSTOS_PRISM_GRAMMAR;
}