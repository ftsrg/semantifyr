/*
 * SPDX-FileCopyrightText: 2025-2026 The Semantifyr Authors
 *
 * SPDX-License-Identifier: EPL-2.0
 */

import { readdirSync, readFileSync } from 'node:fs';
import path from 'node:path';
import type { Options as DocsOptions } from '@docusaurus/plugin-content-docs';
import type { Options as PagesOptions } from '@docusaurus/plugin-content-pages';
import type { Options as ClassicThemeOptions } from '@docusaurus/theme-classic';
import type { UserThemeConfig } from '@docusaurus/theme-common';
import type { Config, Plugin } from '@docusaurus/types';
import { servedExamples, type CaseManifest, type ServedExample } from '@semantifyr/editor-common/examples';
import type { PrismTheme } from 'prism-react-renderer';
import remarkFences from './src/plugins/remarkFences';
import sourceAliases from './source-aliases.json';
import { addressOf, learnParts, sections, SOURCE_CODE } from './src/map';

// the code blocks take their colours from live's grammars in theme/CodeBlock/Content; the block itself from the tokens
const codeBlockTheme: PrismTheme = {
  plain: {
    color: 'var(--syntax-name)',
    backgroundColor: 'var(--surface-readonly)',
  },
  styles: [],
};

const liveBackendUrl = process.env.SEMANTIFYR_LIVE_URL ?? 'https://live.semantifyr.org';

function expectedVerdicts(example: ServedExample): Record<string, string> {
  const file = path.resolve(__dirname, '../..', example.file);
  const manifest = JSON.parse(readFileSync(path.join(path.dirname(file), 'manifest.json'), 'utf8')) as CaseManifest;
  const model = manifest.models.find((candidate) => {
    return candidate.file === path.basename(file);
  });
  return Object.fromEntries((model?.cases ?? []).map((declaredCase) => {
    return [declaredCase.name, declaredCase.verdict];
  }));
}

function servedExamplesPlugin(): Plugin {
  let directory = '';
  return {
    name: 'served-examples',
    async contentLoaded({ actions }) {
      for (const example of servedExamples) {
        const address = `${example.language}/${example.id}`;
        const written = await actions.createData(`${address}.js`, [
          `import text from ${JSON.stringify(path.resolve(__dirname, '../..', example.file))};`,
          `export default { language: ${JSON.stringify(example.language)}, id: ${JSON.stringify(example.id)}, text, verdicts: ${JSON.stringify(expectedVerdicts(example))} };`,
          '',
        ].join('\n'));
        directory = written.slice(0, -`${address}.js`.length);
      }
    },
    configureWebpack() {
      return { resolve: { alias: { '@served-examples': directory } } };
    },
  };
}

// the docs are routed at the site's root, so a page's address is its path under them
const DOCS = 'src/docs';

// a backend's page is the one place that adds it, so the architecture's names cannot fall behind the reference
function backendsPlugin(): Plugin {
  const pages = 'learn/reference/backends';
  const directory = path.resolve(__dirname, DOCS, pages);
  return {
    name: 'backends',
    contentLoaded({ actions }) {
      const backends = readdirSync(directory).filter((file) => {
        return file.endsWith('.mdx');
      }).map((file) => {
        const text = readFileSync(path.join(directory, file), 'utf8');
        const name = /^title: (['"]?)(.+)\1$/m.exec(text)?.[2];
        const position = Number(/^sidebar_position: (.+)$/m.exec(text)?.[1]);
        if (name === undefined || Number.isNaN(position)) {
          throw new Error(`The backend's page ${file} names no title or no sidebar position`);
        }
        return { name, position, page: `/${pages}/${path.basename(file, '.mdx')}/` };
      }).sort((left, right) => {
        return left.position - right.position;
      }).map(({ name, page }) => {
        return { name, page };
      });
      actions.setGlobalData({ backends });
    },
  };
}

// every built page names its own description and the card for a shared link, or the build fails
function pageHeadsPlugin(): Plugin {
  return {
    name: 'page-heads',
    postBuild({ outDir }) {
      const pages = readdirSync(outDir, { recursive: true, encoding: 'utf8' }).filter((file) => {
        return file.endsWith('.html') && !file.startsWith(`api${path.sep}`);
      });
      const failing = pages.filter((file) => {
        const html = readFileSync(path.join(outDir, file), 'utf8');
        // the minifier drops the quotes it can
        return !/<meta[^>]+name="?description"?[^>]+content="?[^"\s>]/.test(html) || !/<meta[^>]+property="?og:image"?[^>]+content="?[^"\s>]*social-card\.png/.test(html);
      });
      if (failing.length > 0) {
        throw new Error(`These pages name no description or no card for a shared link: ${failing.join(', ')}`);
      }
    },
  };
}

// the build's constants, which every page that states one takes from here: the version from gradle.properties
function gradleProperty(name: string): string {
  const properties = readFileSync(path.resolve(__dirname, '../../gradle.properties'), 'utf8');
  const value = new RegExp(`^${name}=(.*)$`, 'm').exec(properties)?.[1]?.trim();
  if (value === undefined) {
    throw new Error(`gradle.properties sets no ${name}`);
  }
  return value;
}

const version = gradleProperty('version');
const nodeVersion = readFileSync(path.resolve(__dirname, '../../.nvmrc'), 'utf8').trim();

// the limit of the public instance, as the deployment's example configuration sets it
const liveLimitSeconds = /^VERIFY_TIMEOUT_SECONDS=(\d+)$/m.exec(readFileSync(path.resolve(__dirname, '../../deploy/live/env.example'), 'utf8'))?.[1];
if (liveLimitSeconds === undefined) {
  throw new Error('deploy/live/env.example sets no VERIFY_TIMEOUT_SECONDS');
}

// only the deploying workflow names the commit, and its tag when it is a release, so any other build stays cacheable
const commit = process.env.SEMANTIFYR_SITE_COMMIT ?? '';
const released = process.env.SEMANTIFYR_SITE_TAG === `v${version}`;

// the one place the site says which state of the tool it describes
function siteState(): string {
  if (released) {
    return `These pages describe Semantifyr ${version}.`;
  }
  if (commit === '') {
    return 'These pages describe the development branch of Semantifyr.';
  }
  return `These pages describe the development branch of Semantifyr at commit <a href="https://github.com/ftsrg/semantifyr/commit/${commit}"><code>${commit.slice(0, 8)}</code></a>.`;
}

export default {
  title: 'Semantifyr',
  tagline: 'A framework to support the declarative definition of engineering model semantics',
  favicon: 'favicon.ico',
  url: 'https://ftsrg.mit.bme.hu',
  baseUrl: '/semantifyr/',
  baseUrlIssueBanner: false,
  trailingSlash: true,
  staticDirectories: ['static-imported-branding', 'build/api-static'],
  customFields: {
    liveBackendUrl,
    nodeVersion,
    liveLimitSeconds,
  },
  headTags: [
    {
      tagName: 'link',
      attributes: {
        rel: 'icon',
        type: 'image/png',
        sizes: '192x192',
        href: '/semantifyr/img/logo-192.png',
      },
    },
    {
      tagName: 'link',
      attributes: {
        rel: 'icon',
        type: 'image/png',
        sizes: '512x512',
        href: '/semantifyr/img/logo-512.png',
      },
    },
    {
      tagName: 'link',
      attributes: {
        rel: 'apple-touch-icon',
        sizes: '180x180',
        href: '/semantifyr/img/apple-touch-icon.png',
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
        // a part of Learn links its index, so the index is no page of the part's own
        async sidebarItemsGenerator({ defaultSidebarItemsGenerator, ...generation }) {
          const items = await defaultSidebarItemsGenerator(generation);
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
    servedExamplesPlugin,
    pageHeadsPlugin,
    backendsPlugin,
    () => ({
      name: 'raw-sources',
      configureWebpack: () => ({
        resolve: {
          alias: {
            // the sources the pages import, which the website's test and its Gradle tasks read from the same file
            ...Object.fromEntries(
              Object.entries(sourceAliases).map(([alias, directory]) => {
                return [alias, path.resolve(__dirname, directory)];
              }),
            ),
            // written by the cliReference_* Gradle tasks
            '@cli-reference': path.resolve(__dirname, 'build/cli-reference'),
            // written by the cloneSysmlConstructs Gradle task from the SysML translation's registry
            '@sysml-constructs': path.resolve(__dirname, 'build/sysml-constructs'),
            // written by the cloneGrammars Gradle task
            '@grammars': path.resolve(__dirname, 'build/grammars'),
            // written by the generatedReference_* Gradle tasks
            '@generated-reference': path.resolve(__dirname, 'build/generated-reference'),
            // written by the phases Gradle task
            '@phases': path.resolve(__dirname, 'build/phases'),
            '@vscode-extension': path.resolve(__dirname, '../semantifyr-vscode'),
          },
        },
        module: {
          rules: [
            { test: /\.(oxsts|sysml|gamma|get|txt|tsv)$/, type: 'asset/source' },
            // a JSON file a page shows as it is written
            { test: /\.json$/, resourceQuery: /raw/, type: 'asset/source' },
          ],
        },
      }),
    }),
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
          label: 'Source code',
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
          label: 'What Live\'s Server Keeps',
          to: '/learn/tools/privacy/',
        },
        {
          label: 'Source code',
          href: SOURCE_CODE,
        },
      ],
      copyright: `${siteState()}<br/>Copyright &copy; 2023-2026 <a href="https://github.com/ftsrg/semantifyr/blob/main/CONTRIBUTORS.md" target="_blank">The Semantifyr Authors</a>. Available under the <a href="https://www.eclipse.org/legal/epl-2.0/">Eclipse Public License - v 2.0</a>.`,
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
