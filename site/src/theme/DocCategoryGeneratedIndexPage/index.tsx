import type {ReactNode} from 'react';
import Link from '@docusaurus/Link';
import {PageMetadata} from '@docusaurus/theme-common';
import {useCurrentSidebarCategory, useDocById} from '@docusaurus/plugin-content-docs/client';
import useBaseUrl from '@docusaurus/useBaseUrl';
import DocBreadcrumbs from '@theme/DocBreadcrumbs';
import DocPaginator from '@theme/DocPaginator';
import DocVersionBadge from '@theme/DocVersionBadge';
import DocVersionBanner from '@theme/DocVersionBanner';
import Heading from '@theme/Heading';

import styles from './styles.module.css';

type DocLink = {
  type: 'link';
  label: string;
  href: string;
  docId?: string;
};

type DocCategory = {
  type: 'category';
  label: string;
  href?: string;
  items?: SidebarItem[];
};

type SidebarItem = DocLink | DocCategory;

type GeneratedIndex = {
  title: string;
  description?: string;
  keywords?: string[];
  image?: string;
  navigation: {
    previous?: {title: string; permalink: string};
    next?: {title: string; permalink: string};
  };
};

type Props = {
  categoryGeneratedIndex: GeneratedIndex;
};

function isLink(item: SidebarItem): item is DocLink {
  return item.type === 'link';
}

/**
 * Une leçon : son rang, son titre, sa description et un lien direct.
 *
 * Les descriptions viennent du front matter des pages, donc rien n'est
 * dupliqué à la main dans l'index.
 */
function Lesson({item, rang}: {item: DocLink; rang: number}): ReactNode {
  const doc = useDocById(item.docId);

  return (
    <li className={styles.lesson}>
      <Link to={item.href} className={styles.lessonLink}>
        <span className={styles.lessonNumber}>
          {String(rang).padStart(2, '0')}
        </span>
        <span>
          <span className={styles.lessonTitle}>{item.label}</span>
          {doc?.description ? (
            <span className={styles.lessonDescription}>{doc.description}</span>
          ) : null}
        </span>
        <span className={styles.lessonArrow} aria-hidden="true">
          →
        </span>
      </Link>
    </li>
  );
}

function DocCategoryGeneratedIndexPageMetadata({
  categoryGeneratedIndex,
}: Props): ReactNode {
  return (
    <PageMetadata
      title={categoryGeneratedIndex.title}
      description={categoryGeneratedIndex.description}
      keywords={categoryGeneratedIndex.keywords}
      image={useBaseUrl(categoryGeneratedIndex.image)}
    />
  );
}

/**
 * Sommaire d'un parcours (« Apprendre »).
 *
 * Docusaurus n'affiche par défaut qu'une carte par sous-catégorie, ce qui
 * donnait une page presque vide. On déroule ici les leçons de chaque étape,
 * avec leur description, pour que le parcours se lise d'un coup d'œil.
 */
export default function DocCategoryGeneratedIndexPage({
  categoryGeneratedIndex,
}: Props): ReactNode {
  const category = useCurrentSidebarCategory() as unknown as {
    items?: SidebarItem[];
  };
  const items = category.items ?? [];
  const directLinks = items.filter(isLink);
  const steps = items.filter(
    (item): item is DocCategory => item.type === 'category',
  );

  return (
    <>
      <DocCategoryGeneratedIndexPageMetadata
        categoryGeneratedIndex={categoryGeneratedIndex}
      />
      <div className={styles.page}>
        <DocVersionBanner />
        <DocBreadcrumbs />
        <DocVersionBadge />

        <header className={styles.header}>
          <p className={styles.label}>Apprendre</p>
          <Heading as="h1" className={styles.title}>
            {categoryGeneratedIndex.title}
          </Heading>
          {categoryGeneratedIndex.description ? (
            <p className={styles.lede}>
              {categoryGeneratedIndex.description}
            </p>
          ) : null}
        </header>

        {directLinks.length > 0 ? (
          <ol className={styles.lessons}>
            {directLinks.map((item, index) => (
              <Lesson key={item.href} item={item} rang={index + 1} />
            ))}
          </ol>
        ) : null}

        {steps.map((step) => {
          const lessons = (step.items ?? []).filter(isLink);

          return (
            <section key={step.label} className={styles.step}>
              <div className={styles.stepHead}>
                <Heading as="h2" className={styles.stepTitle}>
                  {step.label}
                </Heading>
                <span className={styles.stepMeta}>
                  {lessons.length} leçon{lessons.length > 1 ? 's' : ''}
                </span>
              </div>
              <ol className={styles.lessons}>
                {lessons.map((item, index) => (
                  <Lesson key={item.href} item={item} rang={index + 1} />
                ))}
              </ol>
            </section>
          );
        })}

        <DocPaginator
          previous={categoryGeneratedIndex.navigation.previous}
          next={categoryGeneratedIndex.navigation.next}
        />
      </div>
    </>
  );
}
