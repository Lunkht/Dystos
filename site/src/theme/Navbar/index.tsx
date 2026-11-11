import {useEffect, useState, type ReactNode} from 'react';
import NavbarOriginal from '@theme-original/Navbar';

type NavbarProps = {
  logo?: {alt?: string; src: string};
  [key: string]: unknown;
};

/**
 * Navbar adaptatif : affiche dystos.svg en mode clair et
 * dystos_for_dark_mode.svg en mode sombre.
 */
export default function Navbar(props: NavbarProps): ReactNode {
  const [isDark, setIsDark] = useState<boolean | null>(null);

  useEffect(() => {
    const root = document.documentElement;
    const update = () => {
      setIsDark(root.dataset.theme === 'dark');
    };
    update();

    const observer = new MutationObserver(() => update());
    observer.observe(root, {attributes: true, attributeFilter: ['data-theme']});

    return () => observer.disconnect();
  }, []);

  const logoSrc = isDark === false
    ? '/img/dystos.svg'
    : '/img/dystos_for_dark_mode.svg';

  return (
    <NavbarOriginal
      {...props}
      logo={{alt: 'Logo Dystos', src: logoSrc}}
    />
  );
}
