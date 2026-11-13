import type {ComponentProps, ReactNode} from 'react';
import clsx from 'clsx';
import {
  HtmlClassNameProvider,
  PageMetadata,
  ThemeClassNames,
} from '@docusaurus/theme-common';
import BlogLayout from '@theme/BlogLayout';
import BlogListPaginator from '@theme/BlogListPaginator';
import BlogPostItems from '@theme/BlogPostItems';
import BlogListPageStructuredData from '@theme/BlogListPage/StructuredData';
import SearchMetadata from '@theme/SearchMetadata';

import styles from './styles.module.css';

type Props = {
  metadata: ComponentProps<typeof BlogListPaginator>['metadata'] & {
    blogTitle?: string;
    blogDescription?: string;
  };
  items: ComponentProps<typeof BlogPostItems>['items'];
  sidebar: NonNullable<ComponentProps<typeof BlogLayout>['sidebar']>;
};

/**
 * Liste des actualités.
 *
 * Le thème d'origine n'affiche aucun en-tête de page : on ajoute un titre et
 * une accroche, puis on présente chaque billet comme une carte à filets.
 */
export default function BlogListPage({
  metadata,
  items,
  sidebar,
}: Props): ReactNode {
  return (
    <HtmlClassNameProvider
      className={clsx(
        ThemeClassNames.wrapper.blogPages,
        ThemeClassNames.page.blogListPage,
      )}>
      <PageMetadata
        title={metadata.blogTitle}
        description={metadata.blogDescription}
      />
      <SearchMetadata tag="blog_posts_list" />
      <BlogListPageStructuredData
        metadata={metadata}
        items={items}
        sidebar={sidebar}
      />
      <BlogLayout sidebar={sidebar}>
        <header className={styles.hero}>
          <p className={styles.label}>Journal</p>
          <h1 className={styles.title}>Actualités</h1>
          {metadata.blogDescription ? (
            <p className={styles.lede}>{metadata.blogDescription}</p>
          ) : null}
        </header>

        <div className={styles.list}>
          <BlogPostItems items={items} />
        </div>

        <BlogListPaginator metadata={metadata} />
      </BlogLayout>
    </HtmlClassNameProvider>
  );
}
