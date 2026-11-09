// Généré par tools/GenerateGrammar.java — ne pas modifier à la main.
// Usage : charger Prism, puis ce fichier ; le langage s'appelle « dystos » (alias « dys »).
(function (Prism) {
  Prism.languages.dystos = {
    'comment': { pattern: /#.*/, greedy: true },
    'fstring': {
      pattern: /[fF](?:"""[\s\S]*?"""|'''[\s\S]*?'''|"(?:\\.|[^"\\\n])*"|'(?:\\.|[^'\\\n])*')/,
      greedy: true,
      alias: 'string',
      inside: {
        'interpolation': {
          pattern: /\{(?!\{)(?:[^{}'"\n]|"[^"\n]*"|'[^'\n]*')+\}/,
          inside: {
            'format-spec': { pattern: /(?<!\?):[^{}'"\[\]()\n]*(?=\}$)/, alias: 'number' },
            'punctuation': /^\{|\}$/,
            'expression': { pattern: /[\s\S]+/, inside: null }
          }
        },
        'escape': { pattern: /\\(?:[nrt0\\'"\n]|u[0-9a-fA-F]{4})/, alias: 'string' },
        'fstring-text': { pattern: /[\s\S]+/, alias: 'string' }
      }
    },
    'string': {
      pattern: /"""[\s\S]*?"""|'''[\s\S]*?'''|"(?:\\.|[^"\\\n])*"|'(?:\\.|[^'\\\n])*'/,
      greedy: true,
      inside: { 'escape': { pattern: /\\(?:[nrt0\\'"\n]|u[0-9a-fA-F]{4})/, alias: 'entity' } }
    },
    'decorator': { pattern: /@[A-Za-z_][A-Za-z0-9_.]*/, alias: 'annotation' },
    'function-definition': {
      pattern: /(\bdef\s+)[A-Za-z_][A-Za-z0-9_]*/, lookbehind: true, alias: 'function'
    },
    'class-definition': {
      pattern: /(\b(?:class|interface|enum)\s+)[A-Za-z_][A-Za-z0-9_]*/, lookbehind: true, alias: 'class-name'
    },
    'keyword': /\b(?:def|class|interface|enum|abstract|final|import|package|if|elif|else|for|while|in|return|break|continue|pass|try|except|finally|raise|assert|match|case|with|as|val|var|and|or|not|is)\b/,
    'boolean': /\b(?:true|false)\b/,
    'constant': /\bnull\b/,
    'variable': { pattern: /\bself\b/, alias: 'keyword' },
    'builtin': /\b(?:int|float|bool|str|list|map|set|unit|any|Entry|Exception|ValueError|RuntimeError)\b|\b(?:print|len|range|sum|sorted|filter|enumerate|abs|min|max)(?=\()/,
    'function': /\b[A-Za-z_][A-Za-z0-9_]*(?=\()/,
    'constant-name': { pattern: /\b[A-Z][A-Z0-9_]+\b/, alias: 'constant' },
    'class-name': /\b[A-Z](?:[A-Za-z0-9_]*[a-z][A-Za-z0-9_]*)?\b/,
    'number': /\b(?:0[xX][0-9a-fA-F_]+|0[bB][01_]+|[0-9][0-9_]*(?:\.[0-9][0-9_]*)?(?:[eE][+-]?[0-9]+)?)\b/,
    'operator': /\*\*|\->|\?\.|\?:|==|!=|<=|>=|\+=|\-=|\*=|\/=|%=|\+|\-|\*|\/|%|<|>|=|\?|\|/,
    'punctuation': /[()\[\]{},:.]/
  };
  // L'expression d'une interpolation se colore avec le langage lui-même.
  Prism.languages.dystos.fstring.inside.interpolation.inside.expression.inside = Prism.languages.dystos;
  Prism.languages.dys = Prism.languages.dystos;
}(Prism));
