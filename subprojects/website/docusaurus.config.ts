/*
 * SPDX-FileCopyrightText: 2025-2026 The Semantifyr Authors
 *
 * SPDX-License-Identifier: EPL-2.0
 */

import { readFileSync } from 'node:fs';
import path from 'node:path';
import type { Options as DocsOptions } from '@docusaurus/plugin-content-docs';
import type { Options as PagesOptions } from '@docusaurus/plugin-content-pages';
import type { Options as ClassicThemeOptions } from '@docusaurus/theme-classic';
import type { UserThemeConfig } from '@docusaurus/theme-common';
import type { Config, Plugin } from '@docusaurus/types';
import { encodeCompressedBase64Url } from '@semantifyr/editor-common';
import { servedExamples, type CaseManifest, type ExampleLanguage } from '@semantifyr/editor-common/examples';
import type { PrismTheme } from 'prism-react-renderer';
import remarkFences from './src/plugins/remarkFences';
import sourceAliases from './source-aliases.json';
import linkingFiles from './linking-files.json';
import { addressOf, learnParts, sections, SOURCE_CODE, type LearnPart } from './src/map';

// the code blocks take their colours from live's grammars in theme/CodeBlock/Content; the block itself from the tokens
const codeBlockTheme: PrismTheme = {
  plain: {
    color: 'var(--syntax-name)',
    backgroundColor: 'var(--surface-readonly)',
  },
  styles: [],
};

const liveBackendUrl = process.env.SEMANTIFYR_LIVE_URL ?? 'https://live.semantifyr.org';

const REPOSITORY = path.resolve(__dirname, '../..');

// the lessons' stages, which no registry serves: their pages link into live with the text in the address
const LESSON_STAGES = path.relative(REPOSITORY, path.resolve(__dirname, sourceAliases['@oxsts-lesson-stages']));

interface PageModelSource {
  language: ExampleLanguage;
  file: string;
  id?: string;
}

function manifestOf(directory: string): CaseManifest {
  return JSON.parse(readFileSync(path.join(REPOSITORY, directory, 'manifest.json'), 'utf8')) as CaseManifest;
}

function expectedVerdicts(file: string): Record<string, string> {
  const model = manifestOf(path.dirname(file)).models.find((candidate) => {
    return candidate.file === path.basename(file);
  });
  return Object.fromEntries((model?.cases ?? []).map((declaredCase) => {
    return [declaredCase.name, declaredCase.verdict];
  }));
}

// by the address a page imports each from
function pageModelSources(): Record<string, PageModelSource> {
  const served = servedExamples.map((example): [string, PageModelSource] => {
    return [`served-examples/${example.language}/${example.id}`, { language: example.language, file: example.file, id: example.id }];
  });
  const stages = manifestOf(LESSON_STAGES).models.map((model): [string, PageModelSource] => {
    return [`lesson-stages/${path.basename(model.file, '.semantifyr')}`, { language: 'oxsts', file: `${LESSON_STAGES}/${model.file}` }];
  });
  return Object.fromEntries([...served, ...stages]);
}

function pageModelsPlugin(): Plugin {
  let directory = '';
  return {
    name: 'page-models',
    async contentLoaded({ actions }) {
      for (const [address, source] of Object.entries(pageModelSources())) {
        const file = path.join(REPOSITORY, source.file);
        const model = { ...source, verdicts: expectedVerdicts(source.file), compressedModel: await encodeCompressedBase64Url(readFileSync(file, 'utf8')) };
        const written = await actions.createData(`${address}.js`, [
          `import text from ${JSON.stringify(file)};`,
          `export default { ...${JSON.stringify(model)}, text };`,
          '',
        ].join('\n'));
        directory = written.slice(0, -`${address}.js`.length);
      }
    },
    configureWebpack() {
      return { resolve: { alias: { '@served-examples': `${directory}served-examples`, '@lesson-stages': `${directory}lesson-stages` } } };
    },
  };
}

// every address of the site that the repository's files write opens a page of the built site; live's texts write it through their constant
function siteLinksPlugin(): Plugin {
  return {
    name: 'site-links',
    postBuild({ siteConfig, routesPaths }) {
      const routes = new Set(routesPaths);
      const address = new RegExp(`${siteConfig.url.replace(/[.*+?^${}()|[\]\\]/g, '\\$&')}(/[^\\s"'\`)\\]>#?]*)?`, 'g');
      const broken = linkingFiles.flatMap((file) => {
        const text = readFileSync(path.join(REPOSITORY, file), 'utf8').replace(/\$\{WEBSITE_URL\}/g, siteConfig.url);
        return [...text.matchAll(address)].filter((match) => {
          const pathname = match[1] ?? '/';
          return !routes.has(pathname.endsWith('/') ? pathname : `${pathname}/`);
        }).map((match) => {
          return `${file}: ${match[0]}`;
        });
      });
      if (broken.length > 0) {
        throw new Error(`These addresses of the site open no page: ${broken.join(', ')}`);
      }
    },
  };
}

function directoriesOf(part: LearnPart): string[] {
  return [
    ...(part.directory === undefined ? [] : [part.directory]),
    ...(part.parts ?? []).flatMap(directoriesOf),
  ];
}

const partDirectories = new Set(learnParts.flatMap(directoriesOf));

// the docs are routed at the site's root, so a page's address is its path under them
const DOCS = 'src/docs';

const nodeVersion = readFileSync(path.resolve(__dirname, '../../.nvmrc'), 'utf8').trim();

export default {
  title: 'Semantifyr',
  favicon: 'img/favicon.ico',
  url: 'https://semantifyr.org',
  baseUrl: '/',
  baseUrlIssueBanner: false,
  trailingSlash: true,
  onBrokenAnchors: 'throw',
  staticDirectories: ['static-imported-branding', 'build/live-screenshots', 'build/api-static'],
  customFields: {
    liveBackendUrl,
    nodeVersion,
  },
  headTags: [
    {
      tagName: 'link',
      attributes: {
        rel: 'icon',
        type: 'image/png',
        sizes: '192x192',
        href: '/img/logo-192.png',
      },
    },
    {
      tagName: 'link',
      attributes: {
        rel: 'icon',
        type: 'image/png',
        sizes: '512x512',
        href: '/img/logo-512.png',
      },
    },
    {
      tagName: 'link',
      attributes: {
        rel: 'apple-touch-icon',
        sizes: '180x180',
        href: '/img/apple-touch-icon.png',
      },
    },
  ],
  plugins: [
    [
      '@docusaurus/plugin-content-docs',
      {
        path: DOCS,
        routeBasePath: '/',
        sidebarPath: './sidebars.ts',
        // a part of Learn links its index, so the index is no page of the part's own; Develop lists its index
        async sidebarItemsGenerator({ defaultSidebarItemsGenerator, ...generation }) {
          const items = await defaultSidebarItemsGenerator(generation);
          if (!partDirectories.has(generation.item.dirName)) {
            return items;
          }
          return items.filter((item) => {
            return !(item.type === 'doc' && item.id === `${generation.item.dirName}/index`);
          });
        },
        remarkPlugins: [
          remarkFences,
        ],
        editUrl:
          'https://github.com/ftsrg/semantifyr/edit/main/subprojects/website',
      } satisfies DocsOptions,
    ],
    [
      '@docusaurus/plugin-content-pages',
      {} satisfies PagesOptions,
    ],
    '@docusaurus/plugin-sitemap',
    pageModelsPlugin,
    siteLinksPlugin,
    () => {
      return {
        name: 'raw-sources',
        configureWebpack: () => {
          return {
            resolve: {
              alias: {
                // the sources the pages import, which the website's test and its Gradle tasks read from the same file
                ...Object.fromEntries(
                  Object.entries(sourceAliases).map(([alias, directory]) => {
                    return [alias, path.resolve(__dirname, directory)];
                  }),
                ),
                // written by the cloneGrammars Gradle task
                '@grammars': path.resolve(__dirname, 'build/grammars'),
                '@phases': path.resolve(__dirname, 'build/phases'),
              },
            },
            module: {
              rules: [
                { test: /\.(semantifyr|sysml|gamma|get)$/, type: 'asset/source' },
              ],
            },
          };
        },
      };
    },
  ],
  themes: [
    [
      '@docusaurus/theme-classic',
      {
        customCss: [
          require.resolve('@semantifyr/editor-common/tokens.css'),
          require.resolve('@semantifyr/editor-common/marks.css'),
          require.resolve('./src/css/custom.css'),
          require.resolve('./src/css/header.css'),
          require.resolve('./src/css/search.css'),
        ],
      } satisfies ClassicThemeOptions,
    ],
    [
      '@easyops-cn/docusaurus-search-local',
      {
        // the index is built with the site and searched in the reader's browser
        hashed: true,
        indexBlog: false,
        indexPages: true,
        docsRouteBasePath: '/',
        explicitSearchResultPath: true,
        searchBarShortcutHint: false,
      },
    ],
  ],
  themeConfig: {
    colorMode: {
      respectPrefersColorScheme: true,
    },
    prism: {
      theme: codeBlockTheme,
    },
    image: 'img/social-card.png',
    navbar: {
      logo: {
        alt: 'Semantifyr',
        src: 'img/logo-full-light.svg',
        srcDark: 'img/logo-full-dark.svg',
        height: 21,
      },
      // the phone's menu; the header draws the same entries from the map on a desktop
      items: [
        {
          type: 'dropdown',
          label: 'Learn',
          to: '/learn/',
          position: 'left',
          items: learnParts.map((part) => {
            return { label: part.label, to: addressOf(part) };
          }),
        },
        ...sections.filter((section) => {
          return section.label !== 'Learn';
        }).map((section) => {
          return { label: section.label, to: section.to, position: 'left' as const };
        }),
        {
          label: 'GitHub',
          href: SOURCE_CODE,
          position: 'right',
        },
        {
          label: 'Open Live',
          href: liveBackendUrl,
          position: 'right',
        },
      ],
    },
    footer: {
      links: [
        {
          label: 'Learn',
          to: '/learn/',
        },
        {
          label: 'Develop',
          to: '/develop/',
        },
        {
          label: 'Publications',
          to: '/publications/',
        },
        {
          label: 'Semantifyr Live',
          href: liveBackendUrl,
        },
        {
          label: 'Privacy',
          to: '/learn/live/privacy/',
        },
        {
          label: 'GitHub',
          href: SOURCE_CODE,
        },
      ],
      copyright: `Copyright &copy; 2023-2026 <a href="https://github.com/ftsrg/semantifyr/blob/main/CONTRIBUTORS.md" target="_blank">The Semantifyr Authors</a>. Available under the <a href="https://www.eclipse.org/legal/epl-2.0/" target="_blank">Eclipse Public License - v 2.0</a>.`,
    },
  } satisfies UserThemeConfig,
  future: {
    faster: {
      lightningCssMinimizer: true,
      mdxCrossCompilerCache: true,
      rspackBundler: false,
      swcHtmlMinimizer: true,
      swcJsLoader: true,
      swcJsMinimizer: true,
    },
  },
} satisfies Config;
