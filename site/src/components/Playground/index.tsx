import {useCallback, useEffect, useRef, useState} from 'react';
import type {ReactNode} from 'react';
import clsx from 'clsx';

import examplesData from '@site/static/examples/index.json';

import styles from './styles.module.css';

type ExampleEntry = {id: string; nom: string; code: string};

const EXAMPLES = examplesData as ExampleEntry[];

type Severity = 'error' | 'warning' | 'info';

type Diagnostic = {
  severity: Severity;
  code: string;
  message: string;
  line: number;
  column: number;
  endLine?: number;
  endColumn?: number;
};

type AnalyzeResponse = {
  diagnostics: Diagnostic[];
  tokens?: Token[];
  ast?: string;
};

type Token = {
  kind: string;
  text: string;
  line: number;
  column: number;
};

type RunResponse = {
  stdout: string;
  stderr: string;
  exitCode: number;
  durationMs: number;
  diagnostics: Diagnostic[];
};

const DEFAULT_CODE = `def fibonacci(n: int) -> int:
    if n < 2:
        return n
    return fibonacci(n - 1) + fibonacci(n - 2)

for i in range(10):
    print(fibonacci(i))
`;

const ENDPOINT =
  (typeof process !== 'undefined' && process.env?.PLAYGROUND_URL) ||
  'http://localhost:8080';

type Tab = 'diagnostics' | 'output' | 'tokens' | 'ast';

/** Délai avant réanalyse automatique après la dernière frappe (ms). */
const ANALYZE_DEBOUNCE_MS = 400;

export default function Playground(): ReactNode {
  const [code, setCode] = useState(DEFAULT_CODE);
  const [tab, setTab] = useState<Tab>('diagnostics');
  const [diagnostics, setDiagnostics] = useState<Diagnostic[]>([]);
  const [result, setResult] = useState<RunResponse | null>(null);
  const [tokens, setTokens] = useState<Token[]>([]);
  const [ast, setAst] = useState<string>('');
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState<ErrorState>(null);
  const [loadedExample, setLoadedExample] = useState<string | null>(null);
  const [sidebarOpen, setSidebarOpen] = useState(false);
  const textareaRef = useRef<HTMLTextAreaElement | null>(null);
  const debounceRef = useRef<ReturnType<typeof setTimeout> | null>(null);
  const retryRef = useRef<(() => void) | null>(null);

  /** Déclenche une analyse complète (diagnostics + tokens + AST). */
  const analyze = useCallback(async () => {
    setBusy(true);
    setError(null);
    try {
      const response = await fetch(`${ENDPOINT}/api/v1/analyze`, {
        method: 'POST',
        headers: {'Content-Type': 'application/json'},
        body: JSON.stringify({code, include: ['tokens', 'ast']}),
      });
      if (!response.ok) {
        throw new Error(`service indisponible (HTTP ${response.status})`);
      }
      const data = (await response.json()) as AnalyzeResponse;
      setDiagnostics(data.diagnostics ?? []);
      setTokens(data.tokens ?? []);
      setAst(data.ast ?? '');
    } catch (cause) {
      setError(
        cause instanceof Error
          ? cause.message === 'Failed to fetch'
            ? 'Impossible de joindre le service de playground. Vérifiez que le backend est démarré sur le port 8080.'
            : cause.message
          : 'impossible de joindre le service de playground',
      );
      setDiagnostics([]);
      setTokens([]);
      setAst('');
    } finally {
      setBusy(false);
    }
  }, [code]);

  /** Analyse avec debounce : appelée à chaque frappe. */
  const analyzeDebounced = useCallback(() => {
    if (debounceRef.current) {
      clearTimeout(debounceRef.current);
    }
    debounceRef.current = setTimeout(() => {
      void analyze();
    }, ANALYZE_DEBOUNCE_MS);
  }, [analyze]);

  const retryAnalyze = useCallback(() => {
    setError(null);
    void analyze();
  }, [analyze]);

  const run = useCallback(async () => {
    setBusy(true);
    setError(null);
    setTab('output');
    try {
      const response = await fetch(`${ENDPOINT}/api/v1/run`, {
        method: 'POST',
        headers: {'Content-Type': 'application/json'},
        body: JSON.stringify({code, stdin: ''}),
      });
      if (!response.ok) {
        throw new Error(`service indisponible (HTTP ${response.status})`);
      }
      const data = (await response.json()) as RunResponse;
      setResult(data);
      setDiagnostics(data.diagnostics ?? []);
    } catch (cause) {
      setError(
        cause instanceof Error
          ? cause.message === 'Failed to fetch'
            ? 'Impossible de joindre le service de playground. Vérifiez que le backend est démarré sur le port 8080.'
            : cause.message
          : 'impossible de joindre le service de playground',
      );
      setResult(null);
    } finally {
      setBusy(false);
    }
  }, [code]);

  const onTabKeyDown = (event: React.KeyboardEvent<HTMLTextAreaElement>) => {
    if (event.key !== 'Tab') {
      return;
    }
    event.preventDefault();
    const target = event.currentTarget;
    const {selectionStart, selectionEnd} = target;
    const next = `${code.slice(0, selectionStart)}    ${code.slice(selectionEnd)}`;
    setCode(next);
    requestAnimationFrame(() => {
      target.selectionStart = selectionStart + 4;
      target.selectionEnd = selectionStart + 4;
    });
  };

  /**
   * Charge un exemple depuis l'URL (?exemple=) ou depuis la barre latérale.
   * Réinitialise l'état d'exécution pour ne pas afficher de résultats obsolètes.
   */
  const loadExample = (id: string) => {
    const example = EXAMPLES.find((entry) => entry.id === id);
    if (!example) {
      return;
    }
    setCode(example.code);
    setLoadedExample(example.nom);
    setResult(null);
    setDiagnostics([]);
    setTokens([]);
    setAst('');
    setError(null);
  };

  useEffect(() => {
    const id = new URLSearchParams(window.location.search).get('exemple');
    if (id) {
      loadExample(id);
    } else if (!loadedExample) {
      // Aucun exemple demandé : repartir du code par défaut.
      setCode(DEFAULT_CODE);
      setResult(null);
      setDiagnostics([]);
      setTokens([]);
      setAst('');
      setError(null);
    }
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, []);

  useEffect(() => {
    void analyze();
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [loadedExample]);

  const lineCount = code.split('\n').length;

  /** Réinitialise l'éditeur avec le code par défaut. */
  const resetToDefault = () => {
    setCode(DEFAULT_CODE);
    setLoadedExample(null);
    setResult(null);
    setDiagnostics([]);
    setTokens([]);
    setAst('');
    setError(null);
    setSidebarOpen(false);
  };

  return (
    <div className={clsx(styles.playground, sidebarOpen && styles.sidebarOpen)}>
      {/* Sidebar — explorateur */}
      <aside className={styles.sidebar}>
        <div className={styles.sidebarHeader}>
          <span className={styles.sidebarTitle}>Explorateur</span>
          <button
            type="button"
            className={styles.sidebarClose}
            onClick={() => setSidebarOpen(false)}
            aria-label="Fermer l'explorateur">
            Fermer
          </button>
        </div>
        <div className={styles.sidebarSection}>
          <span className={styles.sidebarSectionLabel}>Exemples</span>
          <div className={styles.exampleList}>
            {EXAMPLES.map((example) => (
              <button
                key={example.id}
                type="button"
                className={clsx(styles.exampleItem, {
                  [styles.exampleActive]: loadedExample === example.nom,
                })}
                onClick={() => {
                  loadExample(example.id);
                  setSidebarOpen(false);
                }}>
                {example.nom}
              </button>
            ))}
          </div>
        </div>
      </aside>

      {sidebarOpen && <div className={styles.sidebarOverlay} onClick={() => setSidebarOpen(false)} />}

      <div className={styles.main}>
        <header className={styles.toolbar}>
          <div className={styles.toolbarLeft}>
            <button
              type="button"
              className={clsx(styles.toolbarButton, styles.sidebarToggle)}
              onClick={() => setSidebarOpen(true)}
              aria-label="Ouvrir l'explorateur">
              Menu
            </button>
            <button
              type="button"
              className={clsx(styles.toolbarButton, styles.btnNew)}
              onClick={resetToDefault}
              aria-label="Nouveau fichier">
              Nouveau
            </button>
            {loadedExample && (
              <span className={styles.activeExample}>{loadedExample}</span>
            )}
          </div>

          <div className={styles.toolbarCenter}>
            <button
              type="button"
              className={clsx(styles.toolbarButton, styles.btnAnalyze)}
              onClick={() => {
                if (debounceRef.current) {
                  clearTimeout(debounceRef.current);
                }
                void analyze();
              }}
              disabled={busy}
              aria-label="Analyser le code">
              Analyser
            </button>
            <button
              type="button"
              className={clsx(styles.toolbarButton, styles.btnRun)}
              onClick={() => void run()}
              disabled={busy}
              aria-label="Exécuter le code">
              Exécuter
            </button>
          </div>

          <div className={styles.toolbarRight}>
            <span className={styles.statusIndicator}>
              {busy ? 'Occupé' : 'Prêt'}
            </span>
            <span className={styles.endpoint}>{ENDPOINT}</span>
          </div>
        </header>

        <div className={styles.panes}>
          <div className={clsx(styles.pane, styles.editorPane)}>
            <div className={styles.editorHeader}>
              <span className={styles.editorTab}>
                {loadedExample ? `${loadedExample}.dys` : 'main.dys'}
              </span>
              <div className={styles.editorActions}>
                <button type="button" className={styles.editorActionButton} aria-label="Réduire">
                  —
                </button>
                <button type="button" className={styles.editorActionButton} aria-label="Fermer">
                  ✕
                </button>
              </div>
            </div>
            <div className={styles.editorWrapper}>
              <div className={styles.gutter} aria-hidden="true">
                {Array.from({length: lineCount}, (_, index) => (
                  <span key={index}>{index + 1}</span>
                ))}
              </div>
              <textarea
                ref={textareaRef}
                className={styles.editor}
                value={code}
                onChange={(event) => {
                  setCode(event.target.value);
                  // L'utilisateur édite manuellement : l'exemple chargé n'est plus d'actualité.
                  if (loadedExample) {
                    setLoadedExample(null);
                  }
                  analyzeDebounced();
                }}
                onKeyDown={onTabKeyDown}
                spellCheck={false}
                aria-label="Éditeur de code Dystos"
              />
            </div>
          </div>

          <div className={clsx(styles.pane, styles.resultPane)}>
            <div className={styles.resultHeader}>
              <div className={styles.tabs} role="tablist">
                {(
                  [
                    ['diagnostics', `Diagnostics (${diagnostics.length})`],
                    ['output', 'Sortie'],
                    ['tokens', `Jetons (${tokens.length})`],
                    ['ast', 'AST'],
                  ] as [Tab, string][]
                ).map(([id, label]) => (
                  <button
                    key={id}
                    type="button"
                    role="tab"
                    aria-selected={tab === id}
                    className={clsx(styles.tab, tab === id && styles.tabActive)}
                    onClick={() => setTab(id)}>
                    {label}
                  </button>
                ))}
              </div>
              <div className={styles.resultActions}>
                {error && (
                  <>
                    <button
                      type="button"
                      className={styles.clearButton}
                      onClick={() => setError(null)}
                      aria-label="Effacer l'erreur">
                      ✕
                    </button>
                    {retryRef.current && (
                      <button
                        type="button"
                        className={styles.toolbarButton}
                        onClick={retryRef.current}
                        aria-label="Réessayer l'analyse">
                        ↻
                      </button>
                    )}
                  </>
                )}
              </div>
            </div>

            <div className={styles.panel}>
              {error && (
                <div className={styles.errorBanner}>
                  <span className={styles.errorIcon}>Erreur</span>
                  <span className={styles.errorText}>{error}</span>
                </div>
              )}

              {tab === 'diagnostics' && !error && (
                <ul className={styles.diagnostics}>
                  {diagnostics.length === 0 && (
                    <li className={styles.okText}>
                      <span className={styles.okIcon}>OK</span> Aucune erreur
                      détectée.
                    </li>
                  )}
                  {diagnostics.map((diagnostic, index) => (
                    <li
                      key={`${diagnostic.code}-${index}`}
                      className={clsx(
                        styles.diagnostic,
                        styles[`severity${diagnostic.severity.charAt(0).toUpperCase() + diagnostic.severity.slice(1)}`],
                      )}>
                      <span className={styles.diagnosticCode}>
                        {diagnostic.code}
                      </span>
                      <span>
                        {diagnostic.line}:{diagnostic.column} {diagnostic.message}
                      </span>
                    </li>
                  ))}
                </ul>
              )}

              {tab === 'output' && (
                <div className={styles.outputWrapper}>
                  <pre className={styles.output}>
                    {result
                      ? `${result.stdout}${result.stderr}`
                      : 'Aucune exécution.'}
                    {result && (
                      <span className={styles.duration}>
                        {` (${result.durationMs} ms • code ${result.exitCode})`}
                      </span>
                    )}
                  </pre>
                </div>
              )}

              {tab === 'tokens' && (
                <ol className={styles.tokens}>
                  {tokens.map((token, index) => (
                    <li key={`${token.line}-${token.column}-${index}`}>
                      <span className={styles.tokenKind}>{token.kind}</span>
                      <span className={styles.tokenText}>{token.text}</span>
                      <span className={styles.tokenPosition}>
                        {`${token.line}:${token.column}`}
                      </span>
                    </li>
                  ))}
                </ol>
              )}

              {tab === 'ast' && <pre className={styles.output}>{ast || '—'}</pre>}
            </div>
          </div>
        </div>

        <footer className={styles.statusBar}>
          <div className={styles.statusLeft}>
            <span>{loadedExample ? loadedExample : 'main'}.dys</span>
            <span>{lineCount} lignes</span>
            <span>UTF-8</span>
            <span>Dystos</span>
          </div>
          <div className={styles.statusRight}>
            <span>Espace d'ins: 4</span>
            <span>Ligne finale: {lineCount}</span>
          </div>
        </footer>
      </div>
    </div>
  );
}
