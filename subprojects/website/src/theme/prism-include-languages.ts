/*
 * SPDX-FileCopyrightText: 2025-2026 The Semantifyr Authors
 *
 * SPDX-License-Identifier: EPL-2.0
 */

import siteConfig from '@generated/docusaurus.config';
import type * as PrismNamespace from 'prismjs';
import type { Optional } from 'utility-types';

export default function prismIncludeLanguages(
  PrismObject: typeof PrismNamespace,
): void {
  const {
    themeConfig: { prism },
  } = siteConfig;
  const { additionalLanguages } = prism as { additionalLanguages: string[] };

  const PrismBefore = globalThis.Prism;
  globalThis.Prism = PrismObject;

  additionalLanguages.forEach((lang) => {
    if (lang === 'php') {
      require('prismjs/components/prism-markup-templating.js');
    }
    require(`prismjs/components/prism-${lang}`);
  });

  delete (globalThis as Optional<typeof globalThis, 'Prism'>).Prism;
  if (typeof PrismBefore !== 'undefined') {
    globalThis.Prism = PrismObject;
  }

  // Custom languages, token types chosen to match VS Code's default theme:
  //   keyword.control.*    -> keyword  (blue)
  //   constant.language.*  -> keyword  (blue; VS Code colors booleans/self/nothing same as keywords)
  //   constant.numeric.*   -> number   (light green in dark, green in light)
  //   keyword.operator.*   -> operator (light gray in dark, black in light)
  //   string.quoted.*      -> string   (brown/orange)
  //   comment.*            -> comment  (green)

  PrismObject.languages['oxsts'] = {
    comment: [
      { pattern: /\/\*[\s\S]*?\*\//, greedy: true },
      { pattern: /\/\/.*/, greedy: true },
    ],
    string: { pattern: /"(?:\\.|[^"\\])*"/, greedy: true },
    keyword:
      /\b(?:abstract|annotation|any|assume|bool|choice|class|containment|container|contains|datatype|derived|else|enum|env|extern|false|feature|features|for|global|havoc|if|import|in|inline|inlined|init|int|main|nothing|of|opposite|or|oxsts|package|prop|real|record|redefine|redefines|refers|reference|return|self|string|subsets|trace|tran|true|var|witness)\b/,
    operator: /(:=|==|!=|<=|>=|&&|\|\||\^\^|<|>|\+|-|\*|\/|!|\?\.)/,
    number: /\b\d+(?:\.\d+)?\b/,
    punctuation: /[{}[\]();:,.@]/,
  };

  PrismObject.languages['xsts'] = {
    comment: [
      { pattern: /\/\*[\s\S]*?\*\//, greedy: true },
      { pattern: /\/\/.*/, greedy: true },
    ],
    keyword:
      /\b(?:assume|boolean|choice|ctrl|default|do|else|env|enum|false|for|from|havoc|if|init|integer|local|or|prop|to|trans|true|type|var)\b/,
    operator: /(:=|==|!=|<=|>=|&&|\|\||=>|<|>|\+|-|\*|\/|%|!)/,
    number: /\b\d+\b/,
    punctuation: /[{}[\]();:,<>]/,
  };

  PrismObject.languages['gamma'] = {
    comment: [
      { pattern: /\/\*[\s\S]*?\*\//, greedy: true },
      { pattern: /\/\/.*/, greedy: true },
    ],
    keyword:
      /\b(?:AG|Boolean|EF|Integer|and|bind|case|channel|component|entry|event|exit|false|in|interface|isActive|not|or|out|package|port|property|provides|raise|region|requires|set|state|statechart|sync|timeout|to|transition|true|var|verification|when)\b/,
    operator: /(:=|==|!=|<=|>=|&&|\|\||<|>|\+|-|\*|\/|!)/,
    number: /\b\d+\b/,
    punctuation: /[{}[\]();:,.]/,
  };

  PrismObject.languages['sysmlv2'] = {
    comment: [
      { pattern: /\/\*[\s\S]*?\*\//, greedy: true },
      { pattern: /\/\/.*/, greedy: true },
    ],
    string: { pattern: /"(?:\\.|[^"\\])*"/, greedy: true },
    keyword:
      /\b(?:about|accept|action|actor|after|alias|all|allocate|allocation|analysis|and|as|assert|assign|assume|attribute|bind|binding|block|by|calc|case|comment|concern|conjugate|connection|constraint|decide|def|dependency|do|doc|else|end|entry|enum|event|exhibit|exit|expose|false|feature|filter|first|flow|for|fork|frame|from|if|import|in|include|individual|inout|interface|istype|item|join|language|merge|message|metadata|multiplicity|namespace|not|objective|occurrence|of|or|ordered|out|package|parallel|part|perform|port|private|protected|public|readonly|redefines|ref|rendering|rep|require|requirement|return|satisfy|send|snapshot|specializes|stakeholder|standard|start|state|subject|subsets|succession|then|timeslice|to|transition|true|type|usage|use|variant|verification|verify|via|view|viewpoint|when|xor)\b/,
    operator: /(:>>|:>|::|==|!=|<=|>=|&&|\|\||<|>|\+|-|\*|\/|!|~|=)/,
    number: /\b\d+(?:\.\d+)?\b/,
    punctuation: /[{}[\]();:,.]/,
  };
}
