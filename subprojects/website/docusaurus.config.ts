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
import type { Config } from '@docusaurus/types';
import type { PrismTheme } from 'prism-react-renderer';
import remarkFences from './src/plugins/remarkFences';

// the code blocks take their colours from live's grammars in theme/CodeBlock/Content; the block itself from the tokens
const codeBlockTheme: PrismTheme = {
  plain: {
    color: 'var(--syntax-name)',
    backgroundColor: 'var(--surface-readonly)',
  },
  styles: [],
};

const liveBackendUrl = process.env.SEMANTIFYR_LIVE_URL ?? 'https://live.semantifyr.org';

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
  tagline:
    'Semantic library-driven formal verification of engineering models',
  favicon: 'favicon.ico',
  url: 'https://ftsrg.mit.bme.hu',
  baseUrl: '/semantifyr/',
  baseUrlIssueBanner: false,
  trailingSlash: true,
  staticDirectories: ['static', 'static-imported-branding', 'build/api-static'],
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
        path: 'src/docs',
        routeBasePath: '/',
        sidebarPath: './sidebars.ts',
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
    () => ({
      name: 'raw-sources',
      configureWebpack: () => ({
        resolve: {
          alias: {
            '@oxsts-test-models': path.resolve(__dirname, '../../oxsts-test-models'),
            '@oxsts-library': path.resolve(__dirname, '../oxsts.lang/src/main/resources/hu/bme/mit/semantifyr/oxsts/lang/library'),
            '@sysml-tutorial-models': path.resolve(__dirname, '../frontends/sysml/models/tutorial'),
            // the reference pairs of the trace files, which the frontends' tests check
            '@sysml-reference-trace': path.resolve(__dirname, '../frontends/sysml/models/etc'),
            '@gamma-reference-trace': path.resolve(__dirname, '../frontends/gamma/models/etc'),
            // written by the cliReference_* Gradle tasks
            '@cli-reference': path.resolve(__dirname, 'build/cli-reference'),
            // written by the cloneSysmlConstructs Gradle task from the SysML translation's registry
            '@sysml-constructs': path.resolve(__dirname, 'build/sysml-constructs'),
            // written by the cloneGrammars Gradle task
            '@grammars': path.resolve(__dirname, 'build/grammars'),
            // written by the generatedReference_* Gradle tasks
            '@generated-reference': path.resolve(__dirname, 'build/generated-reference'),
            '@vscode-extension': path.resolve(__dirname, '../semantifyr-vscode'),
          },
        },
        module: {
          rules: [
            { test: /\.(oxsts|sysml|gamma|txt|tsv)$/, type: 'asset/source' },
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
        ],
      } satisfies ClassicThemeOptions,
    ],
  ],
  themeConfig: {
    colorMode: {
      respectPrefersColorScheme: true,
    },
    prism: {
      theme: codeBlockTheme,
    },
    navbar: {
      hideOnScroll: true,
      logo: {
        alt: 'Semantifyr',
        src: 'img/logo-full-light.svg',
        srcDark: 'img/logo-full-dark.svg',
        height: 32,
      },
      items: [
        {
          type: 'doc',
          label: 'Learn',
          docId: 'learn/index',
          position: 'left',
        },
        {
          type: 'doc',
          label: 'Develop',
          docId: 'develop/index',
          position: 'left',
        },
        {
          label: 'API',
          // the API docs are published next to the site, outside the Docusaurus router
          to: 'pathname:///api/',
          target: '_self',
          position: 'left',
        },
        {
          href: 'https://github.com/ftsrg/semantifyr',
          position: 'right',
          className: 'header-github-link',
          'aria-label': 'GitHub repository',
        },
        {
          label: 'Try Live',
          position: 'right',
          href: liveBackendUrl,
          className: 'navbar__link--try-now',
        },
      ],
    },
    footer: {
      links: [
        {
          title: 'Learn',
          items: [
            {
              label: 'Introduction',
              to: '/learn',
            },
            {
              label: 'Language Reference',
              to: '/learn/language',
            },
            {
              label: 'Tutorials',
              to: '/learn/tutorials',
            },
          ],
        },
        {
          title: 'Develop',
          items: [
            {
              label: 'Developer Guide',
              to: '/develop',
            },
            {
              label: 'GitHub',
              href: 'https://github.com/ftsrg/semantifyr',
            },
          ],
        },
        {
          title: 'More',
          items: [
            {
              label: 'GitHub',
              href: 'https://github.com/ftsrg/semantifyr',
            },
            {
              label: 'BME MIT FTSRG',
              href: 'https://ftsrg.mit.bme.hu/en/',
            },
          ],
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
