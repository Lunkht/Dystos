import type {PrismTheme} from 'prism-react-renderer';
import type {Config} from '@docusaurus/types';
import type * as Preset from '@docusaurus/preset-classic';

// This runs in Node.js - Don't use client-side code here (browser APIs, JSX...)

const REPO_URL = 'https://github.com/Lunkht/Dystos';

/**
 * Coloration syntaxique du site.
 *
 * Le reste du site est monochrome : le code est le seul endroit où la couleur
 * porte du sens. La palette reste volontairement sourde (terracotta, sauge,
 * sable, bleu poussière) pour se poser sur le noir sans lueur ni néon.
 */
const dystosPrismTheme: PrismTheme = {
  plain: {
    color: '#cfcfca',
    backgroundColor: '#0c0c0c',
  },
  styles: [
    {
      types: ['comment', 'prolog', 'doctype', 'cdata'],
      style: {color: '#7c7c77', fontStyle: 'italic'},
    },
    {types: ['punctuation'], style: {color: '#8f8f8a'}},
    {types: ['namespace'], style: {opacity: 0.8}},
    {
      types: ['keyword', 'atrule', 'important'],
      style: {color: '#e2a48f', fontWeight: '600'},
    },
    {
      types: ['string', 'char', 'inserted', 'regex', 'attr-value'],
      style: {color: '#a8c9a3'},
    },
    {
      types: ['number', 'boolean', 'constant', 'symbol'],
      style: {color: '#e3c184'},
    },
    {
      types: ['function', 'class-name', 'builtin'],
      style: {color: '#9dbfd8'},
    },
    {types: ['tag', 'selector', 'deleted'], style: {color: '#e2a48f'}},
    {types: ['attr-name', 'property'], style: {color: '#d8c3a2'}},
    {types: ['operator', 'entity', 'url'], style: {color: '#b9b9b4'}},
    {types: ['variable'], style: {color: '#cfcfca'}},
    {
      types: ['annotation', 'decorator'],
      style: {color: '#c3aed6'},
    },
    {types: ['bold'], style: {fontWeight: '600'}},
    {types: ['italic'], style: {fontStyle: 'italic'}},
  ],
};


const config: Config = {
  title: 'Dystos',
  tagline: 'Un langage lisible comme Python, sûr comme Java, qui tourne sur la JVM.',
  favicon: 'img/favicon.svg',

  future: {
    v4: true,
  },

  url: 'https://dystos.dev',
  baseUrl: '/',
  // Le bandeau de secours de Docusaurus contient un lien vers docusaurus.io.
  // Le principe de souverainete interdit toute reference a un domaine externe.
  baseUrlIssueBanner: false,

  // Thème sombre unique : on force le mode avant le premier rendu pour
  // qu'un ancien choix « clair » stocké dans le navigateur ne réapparaisse pas.
  headTags: [
    {
      tagName: 'script',
      attributes: {},
      innerHTML:
        "try{localStorage.setItem('theme','dark');document.documentElement.setAttribute('data-theme','dark');}catch(e){}",
    },
  ],

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
          blogSidebarTitle: 'Articles récents',
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
      disableSwitch: true,
      respectPrefersColorScheme: false,
    },
    navbar: {
      logo: {
        alt: 'Logo Dystos',
        src: 'img/dystos_for_dark_mode.svg',
        srcDark: 'img/dystos_for_dark_mode.svg',
      },
      items: [
        {to: '/docs/introduction', label: 'Documentation', position: 'left'},
        {to: '/docs/tour', label: 'Apprendre', position: 'left'},
        {to: '/playground', label: 'Playground', position: 'left'},
        {to: '/examples', label: 'Exemples', position: 'left'},
        {to: '/blog', label: 'Actualités', position: 'left'},
        {to: '/communaute', label: 'Communauté', position: 'left'},
        {to: '/telechargements', label: 'Téléchargements', position: 'left'},
        {
          href: REPO_URL,
          position: 'right',
          icon: {
            alt: 'GitHub',
            src: '/img/github.svg',
          },
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
            {label: 'Page communauté', to: '/communaute'},
            {label: 'Le créateur', to: '/createur'},
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
      theme: dystosPrismTheme,
      darkTheme: dystosPrismTheme,
      additionalLanguages: ['dystos'],
    },
  } satisfies Preset.ThemeConfig,
};

export default config;