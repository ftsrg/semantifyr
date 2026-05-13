/*
 * SPDX-FileCopyrightText: 2026 The Semantifyr Authors
 *
 * SPDX-License-Identifier: EPL-2.0
 */

import globals from 'globals';
import pluginJs from '@eslint/js';
import tseslint from 'typescript-eslint';
import pluginReact from 'eslint-plugin-react';
import pluginReactHooks from 'eslint-plugin-react-hooks';

export const sharedFiles = ['**/*.{js,mjs,cjs,ts,tsx,mts,cts}'];

export const sharedIgnores = [
  '**/dist/',
  '**/build/',
  '**/node_modules/',
  '**/.gradle/',
];

export const testFiles = ['**/__tests__/**', '**/*.test.{ts,tsx,js,jsx}', '**/*.spec.{ts,tsx,js,jsx}'];

export function baseConfig({ tsconfigRootDir, files = sharedFiles, ignores = [], globalsSource = globals.node, react = false } = {}) {
  const blocks = [
    { files },
    { ignores: [...sharedIgnores, ...ignores] },
    { languageOptions: { globals: globalsSource } },
    pluginJs.configs.recommended,
    ...tseslint.configs.strictTypeChecked,
    ...tseslint.configs.stylisticTypeChecked,
    {
      languageOptions: {
        parserOptions: {
          projectService: {
            allowDefaultProject: ['eslint.config.js', 'eslint.config.mjs', 'esbuild.mjs'],
          },
          tsconfigRootDir,
        },
      },
      rules: {
        '@typescript-eslint/consistent-type-imports': ['error', { prefer: 'type-imports', fixStyle: 'inline-type-imports' }],
        '@typescript-eslint/no-import-type-side-effects': 'error',
        '@typescript-eslint/restrict-template-expressions': ['error', { allowNumber: true, allowBoolean: true }],
        '@typescript-eslint/no-empty-function': ['error', { allow: ['arrowFunctions', 'constructors', 'private-constructors'] }],
        '@typescript-eslint/no-unused-vars': ['error', { argsIgnorePattern: '^_', varsIgnorePattern: '^_', caughtErrorsIgnorePattern: '^_' }],
      },
    },
    {
      files: ['eslint.config.js', 'eslint.config.mjs', 'esbuild.mjs'],
      ...tseslint.configs.disableTypeChecked,
    },
    {
      files: testFiles,
      rules: {
        '@typescript-eslint/no-non-null-assertion': 'off',
        '@typescript-eslint/no-empty-function': 'off',
        '@typescript-eslint/require-await': 'off',
        '@typescript-eslint/no-unsafe-assignment': 'off',
        '@typescript-eslint/no-unsafe-call': 'off',
        '@typescript-eslint/no-unsafe-member-access': 'off',
        '@typescript-eslint/no-unsafe-argument': 'off',
        '@typescript-eslint/no-unsafe-return': 'off',
        '@typescript-eslint/unbound-method': 'off',
      },
    },
  ];

  if (react) {
    blocks.push(
      {
        ...pluginReact.configs.flat.recommended,
        settings: { react: { version: 'detect' } },
      },
      pluginReact.configs.flat['jsx-runtime'],
      {
        plugins: { 'react-hooks': pluginReactHooks },
        rules: {
          'react-hooks/rules-of-hooks': 'error',
          'react-hooks/exhaustive-deps': 'warn',
        },
      },
    );
  }

  return tseslint.config(...blocks);
}
