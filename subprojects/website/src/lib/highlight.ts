/*
 * SPDX-FileCopyrightText: 2026 The Semantifyr Authors
 *
 * SPDX-License-Identifier: EPL-2.0
 */

import { SYNTAX_RULES } from '@semantifyr/editor-common/syntax';
import gammaGrammar from '@grammars/gamma.tmLanguage.json';
import oxstsGrammar from '@grammars/oxsts.tmLanguage.json';
import sysmlGrammar from '@grammars/sysml.tmLanguage.json';

type Captures = Record<string, { name?: string }>;

export interface RawRule {
  name?: string;
  contentName?: string;
  match?: string;
  begin?: string;
  end?: string;
  captures?: Captures;
  beginCaptures?: Captures;
  endCaptures?: Captures;
  patterns?: RawRule[];
  include?: string;
}

export interface RawGrammar {
  scopeName: string;
  patterns: RawRule[];
  repository?: Record<string, RawRule>;
}

export interface Token {
  text: string;
  scopes: readonly string[];
}

interface Rule {
  raw: RawRule;
  regex: RegExp;
}

interface Frame {
  rule: Rule;
  scopes: readonly string[];
  end: RegExp;
}

// the grammars live's editor loads, by the language of a code block
const GRAMMARS: Readonly<Record<string, RawGrammar>> = {
  oxsts: oxstsGrammar,
  gamma: gammaGrammar,
  sysmlv2: sysmlGrammar,
};

// the grammars' expressions are Oniguruma's, and those they use mean the same in JavaScript once ^ and $ work per line
function regexOf(source: string): RegExp {
  return new RegExp(source, 'dgm');
}

// the TextMate rules the grammars use, read as vscode-textmate reads them: the earliest match wins, a tie goes to the rule listed first and a begin rule's end is listed before its patterns
class Tokenizer {
  private readonly grammar: RawGrammar;
  private readonly rules = new Map<readonly RawRule[], Rule[]>();
  private readonly compiled = new Map<RawRule, Rule>();

  constructor(grammar: RawGrammar) {
    this.grammar = grammar;
  }

  tokenize(code: string): Token[][] {
    let stack: Frame[] = [];
    return code.split('\n').map((line) => {
      const tokens: Token[] = [];
      stack = this.tokenizeLine(line, stack, tokens);
      return tokens;
    });
  }

  private tokenizeLine(line: string, initial: Frame[], tokens: Token[]): Frame[] {
    const text = `${line}\n`;
    const stack = [...initial];
    let position = 0;
    const emit = (from: number, to: number, scopes: readonly string[]): void => {
      const end = Math.min(to, line.length);
      if (end <= from) {
        return;
      }
      const last = tokens.at(-1);
      if (last && sameScopes(last.scopes, scopes)) {
        last.text += line.slice(from, end);
      } else {
        tokens.push({ text: line.slice(from, end), scopes });
      }
    };
    const emitCaptured = (match: RegExpExecArray, scopes: readonly string[], captures: Captures | undefined): void => {
      const start = match.index;
      const characters = Array.from({ length: match[0].length }, () => {
        return [...scopes];
      });
      for (const [group, capture] of Object.entries(captures ?? {})) {
        const range = match.indices?.[Number(group)];
        if (range === undefined || capture.name === undefined) {
          continue;
        }
        for (let index = range[0]; index < range[1]; index++) {
          characters[index - start]?.push(capture.name);
        }
      }
      characters.forEach((characterScopes, offset) => {
        emit(start + offset, start + offset + 1, characterScopes);
      });
    };
    while (position <= line.length) {
      const top = stack.at(-1);
      const scopes = top?.scopes ?? [this.grammar.scopeName];
      const rules = this.patternsOf(top ? top.rule.raw.patterns ?? [] : this.grammar.patterns);
      const found = earliest(top ? [top.end, ...rules.map(regexOfRule)] : rules.map(regexOfRule), text, position);
      if (found === null) {
        emit(position, text.length, scopes);
        break;
      }
      const { match } = found;
      emit(position, match.index, scopes);
      const after = match.index + match[0].length;
      const rule = top ? rules[found.index - 1] : rules[found.index];
      if (top && rule === undefined) {
        stack.pop();
        emitCaptured(match, top.scopes.slice(0, top.scopes.length - (top.rule.raw.contentName === undefined ? 0 : 1)), top.rule.raw.endCaptures ?? top.rule.raw.captures);
        position = after;
        continue;
      }
      if (rule === undefined) {
        throw new Error(`the grammar ${this.grammar.scopeName} matched a rule it does not have`);
      }
      const ruleScopes = withName(scopes, rule.raw.name);
      if (rule.raw.begin !== undefined) {
        emitCaptured(match, ruleScopes, rule.raw.beginCaptures ?? rule.raw.captures);
        const pushed = withName(ruleScopes, rule.raw.contentName);
        stack.push({ rule, scopes: pushed, end: this.endOf(rule) });
        // a begin that matches nothing again where the same rule began would never end
        if (after === position && top?.rule === rule) {
          emit(after, text.length, pushed);
          break;
        }
        position = after;
        continue;
      }
      emitCaptured(match, ruleScopes, rule.raw.captures);
      // a match of nothing stops the line, as in vscode-textmate
      if (after === position) {
        emit(after, text.length, scopes);
        break;
      }
      position = after;
    }
    return stack;
  }

  private patternsOf(patterns: readonly RawRule[]): Rule[] {
    let rules = this.rules.get(patterns);
    if (rules === undefined) {
      rules = this.expand(patterns, new Set());
      this.rules.set(patterns, rules);
    }
    return rules;
  }

  private expand(patterns: readonly RawRule[], including: Set<RawRule>): Rule[] {
    return patterns.flatMap((pattern) => {
      const included = this.resolve(pattern);
      if (included.match !== undefined || included.begin !== undefined) {
        return [this.compile(included)];
      }
      if (including.has(included)) {
        return [];
      }
      return this.expand(included.patterns ?? [], new Set([...including, included]));
    });
  }

  private resolve(pattern: RawRule): RawRule {
    const include = pattern.include;
    if (include === undefined) {
      return pattern;
    }
    if (include === '$self' || include === '$base') {
      return { patterns: this.grammar.patterns };
    }
    const entry = include.startsWith('#') ? this.grammar.repository?.[include.slice(1)] : undefined;
    if (entry === undefined) {
      throw new Error(`the grammar ${this.grammar.scopeName} includes ${include}, which it does not define`);
    }
    return entry;
  }

  private endOf(rule: Rule): RegExp {
    if (rule.raw.end === undefined) {
      throw new Error(`the grammar ${this.grammar.scopeName} has a begin rule without an end: ${rule.raw.begin ?? ''}`);
    }
    return regexOf(rule.raw.end);
  }

  private compile(raw: RawRule): Rule {
    let rule = this.compiled.get(raw);
    if (rule === undefined) {
      rule = { raw, regex: regexOf(raw.match ?? raw.begin ?? '') };
      this.compiled.set(raw, rule);
    }
    return rule;
  }
}

function regexOfRule(rule: Rule): RegExp {
  return rule.regex;
}

function withName(scopes: readonly string[], name: string | undefined): readonly string[] {
  return name === undefined ? scopes : [...scopes, name];
}

function sameScopes(first: readonly string[], second: readonly string[]): boolean {
  return first.length === second.length && first.every((scope, index) => {
    return scope === second[index];
  });
}

function earliest(candidates: readonly RegExp[], text: string, position: number): { match: RegExpExecArray, index: number } | null {
  let best: { match: RegExpExecArray, index: number } | null = null;
  candidates.forEach((regex, index) => {
    regex.lastIndex = position;
    const match = regex.exec(text);
    if (match !== null && (best === null || match.index < best.match.index)) {
      best = { match, index };
    }
  });
  return best;
}

const tokenizers = new Map<string, Tokenizer>();

export function tokenizerOf(grammar: RawGrammar): (code: string) => Token[][] {
  let tokenizer = tokenizers.get(grammar.scopeName);
  if (tokenizer === undefined) {
    tokenizer = new Tokenizer(grammar);
    tokenizers.set(grammar.scopeName, tokenizer);
  }
  const found = tokenizer;
  return (code) => {
    return found.tokenize(code);
  };
}

// the lines of a code block in the language's tokens, or null for a language live has no grammar for
export function highlight(code: string, language: string): Token[][] | null {
  const grammar = GRAMMARS[language];
  return grammar === undefined ? null : tokenizerOf(grammar)(code);
}

export interface TokenStyle {
  color?: string;
  fontStyle?: 'italic';
}

function matches(selector: string, scope: string): boolean {
  return scope === selector || scope.startsWith(`${selector}.`);
}

// as a TextMate theme reads the rules: the innermost scope that a rule matches decides, the longest matching selector first
export function styleOf(scopes: readonly string[]): TokenStyle {
  for (const scope of [...scopes].reverse()) {
    let best: { length: number, token: string, italic: boolean } | null = null;
    for (const rule of SYNTAX_RULES) {
      for (const selector of rule.scopes) {
        if (matches(selector, scope) && (best === null || selector.length > best.length)) {
          best = { length: selector.length, token: rule.token, italic: rule.italic === true };
        }
      }
    }
    if (best !== null) {
      return best.italic ? { color: `var(--${best.token})`, fontStyle: 'italic' } : { color: `var(--${best.token})` };
    }
  }
  return {};
}
