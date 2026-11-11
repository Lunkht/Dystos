import type {SVGProps} from 'react';

/**
 * Icônes SVG inline — Dystos.
 * Aucune police externe, aucun service tiers : des vecteurs purs,
 * cohérents avec la philosophie de souveraineté du projet.
 */

type IconName =
  | 'code'
  | 'coffee'
  | 'rocket'
  | 'home'
  | 'play'
  | 'terminal'
  | 'download'
  | 'external'
  | 'github'
  | 'check'
  | 'alert'
  | 'info';

export function Icon({name, size = 20, className, ...props}: {name: IconName; size?: number; className?: string} & SVGProps<SVGSVGElement>) {
  const icons: Record<IconName, React.ReactNode> = {
    code: (
      <svg
        xmlns="http://www.w3.org/2000/svg"
        width={size}
        height={size}
        viewBox="0 0 24 24"
        fill="none"
        stroke="currentColor"
        strokeWidth={2}
        strokeLinecap="round"
        strokeLinejoin="round"
        className={className}
        {...props}>
        <polyline points="16 18 22 12 16 6" />
        <polyline points="8 6 2 12 8 18" />
      </svg>
    ),
    coffee: (
      <svg
        xmlns="http://www.w3.org/2000/svg"
        width={size}
        height={size}
        viewBox="0 0 24 24"
        fill="none"
        stroke="currentColor"
        strokeWidth={2}
        strokeLinecap="round"
        strokeLinejoin="round"
        className={className}
        {...props}>
        <path d="M21 8v3c0 1.9-1.6 3.5-3.5 4h-7c-1.9 0-3.5-1.6-3.5-4V8m6 0h6m-6 0v3m6 0v3m-6 0v3m-6 0v3m6 0v3m-6 0v3M3 16l4-2m0 0l4-2m-4 2v-6" />
      </svg>
    ),
    rocket: (
      <svg
        xmlns="http://www.w3.org/2000/svg"
        width={size}
        height={size}
        viewBox="0 0 24 24"
        fill="none"
        stroke="currentColor"
        strokeWidth={2}
        strokeLinecap="round"
        strokeLinejoin="round"
        className={className}
        {...props}>
        <path d="M16.5 5.5a2.1 2.1 0 0 1 3 3L8.7 17.8a2.1 2.1 0 0 1-3 3L5.5 16.5a2.1 2.1 0 0 1 3-3l9.7-9.7z" />
        <path d="M12.5 12.5a2.1 2.1 0 0 1 3 3M18.5 6.5a2.1 2.1 0 0 1 3 3" />
      </svg>
    ),
    home: (
      <svg
        xmlns="http://www.w3.org/2000/svg"
        width={size}
        height={size}
        viewBox="0 0 24 24"
        fill="none"
        stroke="currentColor"
        strokeWidth={2}
        strokeLinecap="round"
        strokeLinejoin="round"
        className={className}
        {...props}>
        <path d="M3 9l9-7 9 7v11a2 2 0 0 1-2 2H5a2 2 0 0 1-2-2z" />
        <polyline points="9 22 9 12 15 12 15 22" />
      </svg>
    ),
    play: (
      <svg
        xmlns="http://www.w3.org/2000/svg"
        width={size}
        height={size}
        viewBox="0 0 24 24"
        fill="none"
        stroke="currentColor"
        strokeWidth={2}
        strokeLinecap="round"
        strokeLinejoin="round"
        className={className}
        {...props}>
        <polygon points="5 3 19 12 5 21 5 3" />
      </svg>
    ),
    terminal: (
      <svg
        xmlns="http://www.w3.org/2000/svg"
        width={size}
        height={size}
        viewBox="0 0 24 24"
        fill="none"
        stroke="currentColor"
        strokeWidth={2}
        strokeLinecap="round"
        strokeLinejoin="round"
        className={className}
        {...props}>
        <rect x="2" y="2" width="20" height="20" rx="2" ry="2" />
        <polyline points="4 7 10 7 10 11" />
        <line x1="6" y1="15" x2="18" y2="15" />
      </svg>
    ),
    download: (
      <svg
        xmlns="http://www.w3.org/2000/svg"
        width={size}
        height={size}
        viewBox="0 0 24 24"
        fill="none"
        stroke="currentColor"
        strokeWidth={2}
        strokeLinecap="round"
        strokeLinejoin="round"
        className={className}
        {...props}>
        <path d="M21 15v4a2 2 0 0 1-2 2H5a2 2 0 0 1-2-2v-4" />
        <polyline points="7 10 12 15 17 10" />
        <line x1="12" y1="15" x2="12" y2="3" />
      </svg>
    ),
    external: (
      <svg
        xmlns="http://www.w3.org/2000/svg"
        width={size}
        height={size}
        viewBox="0 0 24 24"
        fill="none"
        stroke="currentColor"
        strokeWidth={2}
        strokeLinecap="round"
        strokeLinejoin="round"
        className={className}
        {...props}>
        <path d="M15 3H6v12" />
        <path d="M10 14 21 3" />
      </svg>
    ),
    github: (
      <svg
        xmlns="http://www.w3.org/2000/svg"
        width={size}
        height={size}
        viewBox="0 0 24 24"
        fill="none"
        stroke="currentColor"
        strokeWidth={2}
        strokeLinecap="round"
        strokeLinejoin="round"
        className={className}
        {...props}>
        <path d="M9 19c-5 1.5-5-2.5-7-3m14 6v-3.8c0-3.3-3-7.3-7-7.3h-1v2m4 0c0-3.3-3-7.3-7-7.3h-1v2" />
        <circle cx="12" cy="9" r="2" />
      </svg>
    ),
    check: (
      <svg
        xmlns="http://www.w3.org/2000/svg"
        width={size}
        height={size}
        viewBox="0 0 24 24"
        fill="none"
        stroke="currentColor"
        strokeWidth={2}
        strokeLinecap="round"
        strokeLinejoin="round"
        className={className}
        {...props}>
        <polyline points="20 6 9 17 4 12" />
      </svg>
    ),
    alert: (
      <svg
        xmlns="http://www.w3.org/2000/svg"
        width={size}
        height={size}
        viewBox="0 0 24 24"
        fill="none"
        stroke="currentColor"
        strokeWidth={2}
        strokeLinecap="round"
        strokeLinejoin="round"
        className={className}
        {...props}>
        <path d="M10 29v-6" />
        <path d="M14 29v-6" />
        <path d="M19 7V3a1 1 0 0 0-1-1H6a1 1 0 0 0-1 1v18a1 1 0 0 0 1 1h12a1 1 0 0 0 1-1z" />
      </svg>
    ),
    info: (
      <svg
        xmlns="http://www.w3.org/2000/svg"
        width={size}
        height={size}
        viewBox="0 0 24 24"
        fill="none"
        stroke="currentColor"
        strokeWidth={2}
        strokeLinecap="round"
        strokeLinejoin="round"
        className={className}
        {...props}>
        <path d="M14 7v10" />
        <circle cx="12" cy="10" r="1" />
      </svg>
    ),
  };

  const icon = icons[name];
  if (!icon) {
    return <svg width={size} height={size} className={className} {...props} />;
  }
  return icon;
}
