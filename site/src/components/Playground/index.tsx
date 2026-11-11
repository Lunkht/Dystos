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

const TAB_LABELS: Record<Tab, string> = {
  diagnostics: 'Diagnostics',
  output: 'Sortie',
  tokens: 'Jetons',
  ast: 'AST',
};

export default function Playground(): ReactNode {
  const [code, setCode] = useState(DEFAULT_CODE);
  const [tab, setTab] = useState<Tab>('diagnostics');
  const [diagnostics, setDiagnostics] = useState<Diagnostic[]>([]);
  const [result, setResult] = useState<RunResponse | null>(null);
  const [tokens, setTokens] = useState<Token[]>([]);
  const [ast, setAst] = useState<string>('');
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [loadedExample, setLoadedExample] = useState<string | null>(null);
  const [sidebarOpen, setSidebarOpen] = useState(false);
  const textareaRef = useRef<HTMLTextAreaElement | null>(null);

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
          ? cause.message
          : 'impossible de joindre le service de playground',
      );
      setDiagnostics([]);
      setTokens([]);
      setAst('');
    } finally {
      setBusy(false);
    }
  }, [code]);

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
          ? cause.message
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

  // Ouvre un exemple depuis la sidebar.
  useEffect(() => {
    const id = new URLSearchParams(window.location.search).get('exemple');
    const example = id ? EXAMPLES.find((entry) => entry.id === id) : undefined;
    if (example) {
      setCode(example.code);
      setLoadedExample(example.nom);
    }
  }, []);

  useEffect(() => {
    void analyze();
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [loadedExample]);

  const lineCount = code.split('\n').length;

  return (
    <div className={clsx(styles.playground, sidebarOpen && styles.sidebarOpen)}>
      {/* Sidebar gauche — explorateur */}
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
                  setCode(example.code);
                  setLoadedExample(example.nom);
                  setSidebarOpen(false);
                }}>
                {example.nom}
              </button>
            ))}
          </div>
        </div>
        <div className={styles.sidebarSection}>
          <span className={styles.sidebarSectionLabel}>Fichiers</span>
          <div className={styles.exampleList}>
            <button type="button" className={styles.exampleItem}>
              main.dys
            </button>
          </div>
        </div>
      </aside>

      {/* Overlay pour fermer la sidebar sur mobile */}
      {sidebarOpen && (
        <div
          className={styles.sidebarOverlay}
          onClick={() => setSidebarOpen(false)}
        />
      )}

      {/* Contenu principal */}
      <div className={styles.main}>
        {/* Header supérieure — barre d'outils IDE */}
        <header className={styles.toolbar}>
          <div className={styles.toolbarLeft}>
            <button
              type="button"
              className={clsx(styles.toolbarButton, styles.sidebarToggle)}
              onClick={() => setSidebarOpen(true)}
              aria-label="Ouvrir l'explorateur">
              Menu
            </button>
            {loadedExample && (
              <span className={styles.activeExample}>
                {loadedExample}
              </span>
            )}
          </div>

          <div className={styles.toolbarCenter}>
            <button
              type="button"
              className={clsx(styles.toolbarButton, styles.btnAnalyze)}
              onClick={() => void analyze()}
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
              {busy ? '● Occupé' : '● Prêt'}
            </span>
            <span className={styles.endpoint}>{ENDPOINT}</span>
          </div>
        </header>

        {/* Zone éditeur + résultats */}
        <div className={styles.panes}>
          {/* Éditeur de code */}
          <div className={clsx(styles.pane, styles.editorPane)}>
            <div className={styles.editorHeader}>
              <span className={styles.editorTab}>main.dys</span>
              <div className={styles.editorActions}>
                <button
                  type="button"
                  className={styles.editorActionButton}
                  aria-label="Réduire">
                  —
                </button>
                <button
                  type="button"
                  className={styles.editorActionButton}
                  aria-label="Fermer">
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
                onChange={(event) => setCode(event.target.value)}
                onKeyDown={onTabKeyDown}
                spellCheck={false}
                aria-label="Éditeur de code Dystos"
              />
            </div>
          </div>

          {/* Panneau de résultats */}
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
                  <button
                    type="button"
                    className={styles.clearButton}
                    onClick={() => setError(null)}
                    aria-label="Effacer l'erreur">
                    ✕
                  </button>
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
                        {diagnostic.line}:{diagnostic.column}{' '}
                        {diagnostic.message}
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

              {tab === 'ast' && (
                <pre className={styles.output}>{ast || '—'}</pre>
              )}
            </div>
          </div>
        </div>

        {/* Barre de statut IDE */}
        <footer className={styles.statusBar}>
          <div className={styles.statusLeft}>
            <span>main.dys</span>
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
