/*
 * SPDX-FileCopyrightText: 2025-2026 The Semantifyr Authors
 *
 * SPDX-License-Identifier: EPL-2.0
 */

import type { Options as DocsOptions } from '@docusaurus/plugin-content-docs';
import type { Options as PagesOptions } from '@docusaurus/plugin-content-pages';
import type { Options as ClassicThemeOptions } from '@docusaurus/theme-classic';
import type { UserThemeConfig } from '@docusaurus/theme-common';
import type { Config } from '@docusaurus/types';
import { themes } from 'prism-react-renderer';

export default {
  title: 'Semantifyr',
  tagline:
    'Semantic library-driven formal verification of engineering models',
  favicon: 'favicon.ico',
  url: 'https://ftsrg.mit.bme.hu',
  baseUrl: '/semantifyr/',
  baseUrlIssueBanner: false,
  trailingSlash: true,
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
        editUrl:
          'https://github.com/ftsrg/semantifyr/edit/main/subprojects/website',
      } satisfies DocsOptions,
    ],
    [
      '@docusaurus/plugin-content-pages',
      {} satisfies PagesOptions,
    ],
    '@docusaurus/plugin-sitemap',
  ],
  themes: [
    [
      '@docusaurus/theme-classic',
      {
        customCss: [require.resolve('./src/css/custom.css')],
      } satisfies ClassicThemeOptions,
    ],
  ],
  themeConfig: {
    colorMode: {
      respectPrefersColorScheme: true,
    },
    prism: {
      additionalLanguages: ['bash', 'java', 'kotlin'],
      theme: themes.vsLight,
      darkTheme: themes.vsDark,
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
          href: 'https://github.com/ftsrg/semantifyr',
          position: 'right',
          className: 'header-github-link',
          'aria-label': 'GitHub repository',
        },
        {
          label: 'Try Live',
          position: 'right',
          href: 'https://live.semantifyr.org',
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
      copyright: `Copyright &copy; 2023-2026 <a href="https://github.com/ftsrg/semantifyr/blob/main/CONTRIBUTORS.md" target="_blank">The Semantifyr Authors</a>. Available under the <a href="https://www.eclipse.org/legal/epl-2.0/">Eclipse Public License - v 2.0</a>.`,
    },
  } satisfies UserThemeConfig,
  future: {
    experimental_faster: {
      lightningCssMinimizer: true,
      mdxCrossCompilerCache: true,
      rspackBundler: false,
      swcHtmlMinimizer: true,
      swcJsLoader: true,
      swcJsMinimizer: true,
    },
  },
} satisfies Config;
