import {useCallback, useEffect, useRef, useState} from 'react';
import type {ReactNode} from 'react';
import clsx from 'clsx';

import styles from './styles.module.css';

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

export default function Playground(): ReactNode {
  const [code, setCode] = useState(DEFAULT_CODE);
  const [tab, setTab] = useState<Tab>('diagnostics');
  const [diagnostics, setDiagnostics] = useState<Diagnostic[]>([]);
  const [result, setResult] = useState<RunResponse | null>(null);
  const [tokens, setTokens] = useState<Token[]>([]);
  const [ast, setAst] = useState<string>('');
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState<string | null>(null);
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

  useEffect(() => {
    void analyze();
    // Analyse au premier rendu uniquement.
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, []);

  const lineCount = code.split('\n').length;

  return (
    <div className={styles.playground}>
      <div className={styles.toolbar}>
        <button
          type="button"
          className="button button--primary button--sm"
          onClick={() => void analyze()}
          disabled={busy}>
          Analyser
        </button>
        <button
          type="button"
          className="button button--secondary button--sm"
          onClick={() => void run()}
          disabled={busy}>
          Exécuter
        </button>
        <span className={styles.endpoint}>{ENDPOINT}</span>
      </div>

      <div className={styles.panes}>
        <div className={clsx(styles.pane, styles.editorPane)}>
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

        <div className={clsx(styles.pane, styles.resultPane)}>
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

          <div className={styles.panel}>
            {error && <p className={styles.errorText}>{error}</p>}

            {tab === 'diagnostics' && !error && (
              <ul className={styles.diagnostics}>
                {diagnostics.length === 0 && (
                  <li className={styles.okText}>Aucune erreur détectée.</li>
                )}
                {diagnostics.map((diagnostic, index) => (
                  <li
                    key={`${diagnostic.code}-${index}`}
                    className={clsx(
                      styles.diagnostic,
                      styles[`severity${capitalize(diagnostic.severity)}`],
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
              <pre className={styles.output}>
                {result
                  ? `${result.stdout}${result.stderr}`
                  : 'Aucune exécution.'}
                {result && (
                  <span className={styles.duration}>
                    {` (sortie en ${result.durationMs} ms, code ${result.exitCode})`}
                  </span>
                )}
              </pre>
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
    </div>
  );
}

function capitalize(value: string): string {
  return value.charAt(0).toUpperCase() + value.slice(1);
}