import {themes as prismThemes} from 'prism-react-renderer';
import type {Config} from '@docusaurus/types';
import type * as Preset from '@docusaurus/preset-classic';

// This runs in Node.js - Don't use client-side code here (browser APIs, JSX...)

const REPO_URL = 'https://github.com/Lunkht/Dystos';

const config: Config = {
  title: 'Dystos',
  tagline: 'Un langage lisible comme Python, sûr comme Java, qui tourne sur la JVM.',
  favicon: 'img/favicon.ico',

  future: {
    v4: true,
  },

  url: 'https://dystos.dev',
  baseUrl: '/',
  // Le bandeau de secours de Docusaurus contient un lien vers docusaurus.io.
  // Le principe de souverainete interdit toute reference a un domaine externe.
  baseUrlIssueBanner: false,

  organizationName: 'Lunkht',
  projectName: 'Dystos',

  onBrokenLinks: 'throw',

  i18n: {
    defaultLocale: 'fr',
    locales: ['fr'],
  },

  markdown: {
    format: 'detect',
    hooks: {
      onBrokenMarkdownLinks: 'throw',
    },
  },

  presets: [
    [
      'classic',
      {
        docs: {
          sidebarPath: './sidebars.ts',
          editUrl: `${REPO_URL}/tree/main/site/`,
          routeBasePath: 'docs',
        },
        blog: {
          showReadingTime: true,
          blogTitle: 'Actualités',
          blogDescription: 'Versions de Dystos et décisions de conception.',
          feedOptions: {
            type: ['rss', 'atom'],
            xslt: true,
          },
          editUrl: `${REPO_URL}/tree/main/site/blog/`,
          onInlineTags: 'warn',
          onInlineAuthors: 'warn',
          onUntruncatedBlogPosts: 'warn',
        },
        theme: {
          customCss: './src/css/custom.css',
        },
      } satisfies Preset.Options,
    ],
  ],

  themeConfig: {
    metadata: [
      {name: 'keywords', content: 'Dystos, langage de programmation, JVM, apprentissage, statique'},
    ],
    colorMode: {
      defaultMode: 'dark',
      respectPrefersColorScheme: true,
    },
    navbar: {
      title: 'Dystos',
      logo: {
        alt: 'Logo Dystos',
        src: 'img/logo.svg',
      },
      items: [
        {to: '/docs/introduction', label: 'Documentation', position: 'left'},
        {to: '/docs/tour', label: 'Apprendre', position: 'left'},
        {to: '/playground', label: 'Playground', position: 'left'},
        {to: '/examples', label: 'Exemples', position: 'left'},
        {to: '/blog', label: 'Actualités', position: 'left'},
        {to: '/telechargements', label: 'Téléchargements', position: 'left'},
        {
          href: REPO_URL,
          label: 'GitHub',
          position: 'right',
        },
      ],
    },
    footer: {
      style: 'dark',
      links: [
        {
          title: 'Documentation',
          items: [
            {label: 'Introduction', to: '/docs/introduction'},
            {label: 'Démarrer', to: '/docs/demarrer/installation'},
            {label: 'Parcours guidé', to: '/docs/tour'},
          ],
        },
        {
          title: 'Ressources',
          items: [
            {label: 'Playground', to: '/playground'},
            {label: 'Exemples', to: '/examples'},
            {label: 'Téléchargements', to: '/telechargements'},
            {label: 'Aide-mémoire', to: '/docs/reference/aide-memoire'},
          ],
        },
        {
          title: 'Communauté',
          items: [
            {label: 'Dépôt GitHub', href: REPO_URL},
            {label: 'Contribuer', to: '/docs/communaute/contribuer'},
            {label: 'Signaler un bug', href: `${REPO_URL}/issues`},
            {label: 'Actualités', to: '/blog'},
          ],
        },
      ],
      copyright: `Copyright © ${new Date().getFullYear()} Dystos. Langage libre, distribué sous licence MIT.`,
    },
    prism: {
      theme: prismThemes.github,
      darkTheme: prismThemes.dracula,
      additionalLanguages: ['dystos'],
    },
  } satisfies Preset.ThemeConfig,
};

export default config;