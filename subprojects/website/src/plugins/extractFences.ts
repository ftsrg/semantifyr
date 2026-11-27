/*
 * SPDX-FileCopyrightText: 2026 The Semantifyr Authors
 *
 * SPDX-License-Identifier: EPL-2.0
 */

import { mkdirSync, readdirSync, readFileSync, rmSync, writeFileSync } from 'node:fs';
import path from 'node:path';
import { escapeMarkdownHeadingIds, unwrapMdxCodeBlocks } from '@docusaurus/utils';
import { createProcessor } from '@mdx-js/mdx';
import remarkComment from '@slorber/remark-comment';
import remarkDirective from 'remark-directive';
import remarkFrontmatter from 'remark-frontmatter';
import remarkGfm from 'remark-gfm';
import { fencesOf } from './remarkFences.ts';

const WEBSITE = path.resolve(import.meta.dirname, '../..');
const DOCUMENTATION = path.join(WEBSITE, 'src/docs');
const OUTPUT = path.join(WEBSITE, 'build/fences');

const IMPORT = /^import\s.+\sfrom\s+'([^']+)';?$/;
const LESSON_STAGES = '@lesson-stages/';
const LESSON_STAGE_DIRECTORY = '@oxsts-lesson-stages';
const MODEL_EXTENSION = '.semantifyr';

interface ManifestFence {
  file: string;
  line: number;
  pageLines: number[];
  continued: boolean;
  issue: string | null;
}

interface ImportedModel {
  source: string;
  file: string;
}

interface ManifestPage {
  page: string;
  fences: ManifestFence[];
  importedModels: ImportedModel[];
}

const aliases = Object.entries(JSON.parse(readFileSync(path.join(WEBSITE, 'source-aliases.json'), 'utf8')) as Record<string, string>);

const lessonStages = aliases.find(([alias]) => {
  return alias === LESSON_STAGE_DIRECTORY;
})?.[1];

// parses a page as Docusaurus does before remarkFences runs
const processor = createProcessor({
  format: 'mdx',
  remarkPlugins: [
    remarkFrontmatter,
    remarkDirective,
    remarkGfm,
    remarkComment,
  ],
});

function importedModelsOf(text: string): ImportedModel[] {
  return text.split('\n').flatMap((line) => {
    const source = IMPORT.exec(line.trim())?.[1];
    if (source === undefined) {
      return [];
    }
    if (source.startsWith(LESSON_STAGES)) {
      if (lessonStages === undefined) {
        throw new Error(`source-aliases.json has no ${LESSON_STAGE_DIRECTORY}`);
      }
      return [{ source, file: path.posix.join(lessonStages, `${source.slice(LESSON_STAGES.length)}${MODEL_EXTENSION}`) }];
    }
    const alias = aliases.find(([candidate]) => {
      return source.startsWith(`${candidate}/`);
    });
    if (alias === undefined || !source.endsWith(MODEL_EXTENSION)) {
      return [];
    }
    return [{ source, file: path.posix.join(alias[1], source.slice(alias[0].length + 1)) }];
  });
}

function pageOf(page: string): ManifestPage {
  const text = readFileSync(path.join(DOCUMENTATION, page), 'utf8');
  const root = processor.parse(escapeMarkdownHeadingIds(unwrapMdxCodeBlocks(text)));
  const fences = fencesOf(root).map((fence): ManifestFence => {
    const file = `${page}/${fence.openingLine}${MODEL_EXTENSION}`;
    mkdirSync(path.dirname(path.join(OUTPUT, file)), { recursive: true });
    writeFileSync(path.join(OUTPUT, file), `${fence.model}\n`);
    return { file, line: fence.openingLine, pageLines: fence.pageLines, continued: fence.continued, issue: fence.issue };
  });
  return { page, fences, importedModels: importedModelsOf(text) };
}

rmSync(OUTPUT, { recursive: true, force: true });
mkdirSync(OUTPUT, { recursive: true });
const pages = readdirSync(DOCUMENTATION, { recursive: true, encoding: 'utf8' }).map((page) => {
  return page.split(path.sep).join(path.posix.sep);
}).filter((page) => {
  return page.endsWith('.md') || page.endsWith('.mdx');
}).sort().map((page) => {
  try {
    return pageOf(page);
  } catch (error) {
    throw new Error(`${page}: ${(error as Error).message}`, { cause: error });
  }
}).filter((page) => {
  return page.fences.length > 0 || page.importedModels.length > 0;
});
writeFileSync(path.join(OUTPUT, 'manifest.json'), JSON.stringify({ pages }));
