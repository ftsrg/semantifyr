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
import { addressOf, learnParts, sections, SOURCE_CODE, type LearnPart } from './src/map';

// theme/CodeBlock/Content colours the code, so Prism colours only the block
const codeBlockTheme: PrismTheme = {
  plain: {
    color: 'var(--syntax-name)',
    backgroundColor: 'var(--surface-readonly)',
  },
  styles: [],
};

const liveBackendUrl = process.env.SEMANTIFYR_LIVE_URL ?? 'https://live.semantifyr.org';

const REPOSITORY = path.resolve(__dirname, '../..');

// no registry serves the lessons' stages, so live opens them by their text
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

function directoriesOf(part: LearnPart): string[] {
  return [
    ...(part.directory === undefined ? [] : [part.directory]),
    ...(part.parts ?? []).flatMap(directoriesOf),
  ];
}

const partDirectories = new Set(learnParts.flatMap(directoriesOf));

const nodeVersion = readFileSync(path.join(REPOSITORY, '.nvmrc'), 'utf8').trim();

export default {
  title: 'Semantifyr',
  favicon: 'img/favicon.ico',
  url: 'https://semantifyr.org',
  baseUrl: '/',
  baseUrlIssueBanner: false,
  trailingSlash: true,
  onBrokenAnchors: 'throw',
  staticDirectories: ['static-imported-branding', 'build/api-static'],
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
        path: 'src/docs',
        routeBasePath: '/',
        sidebarPath: './sidebars.ts',
        // a part of Learn links its index, so its list leaves the index out; Develop lists its index
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
    () => {
      return {
        name: 'raw-sources',
        configureWebpack: () => {
          return {
            resolve: {
              alias: {
                // extractFences and the Gradle build read source-aliases.json too
                ...Object.fromEntries(
                  Object.entries(sourceAliases).map(([alias, directory]) => {
                    return [alias, path.resolve(__dirname, directory)];
                  }),
                ),
                // written by the cloneGrammars Gradle task
                '@grammars': path.resolve(__dirname, 'build/grammars'),
              },
            },
            module: {
              rules: [
                { test: /\.(semantifyr|sysml|gamma)$/, type: 'asset/source' },
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
        hashed: true,
        docsDir: 'src/docs',
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
      // the phone's menu; on a desktop the header draws its own
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
        ...sections,
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
