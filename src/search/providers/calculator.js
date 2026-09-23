'use strict';

/**
 * Instant answers for arithmetic, e.g. "2 * (3 + 4)" or "2^10 =".
 * A small recursive-descent parser (no eval) supporting + - * / % ^ and parentheses.
 */

function tokenizeExpression(input) {
  const tokens = [];
  const re = /\s*(\d+(?:\.\d+)?|\.\d+|[-+*/%^()])/y;
  let pos = 0;
  while (pos < input.length) {
    re.lastIndex = pos;
    const m = re.exec(input);
    if (!m) return null;
    tokens.push(m[1]);
    pos = re.lastIndex;
    if (/^\s*$/.test(input.slice(pos))) break;
  }
  return tokens;
}

function evaluate(input) {
  const expr = String(input).trim().replace(/=\s*$/, '').replace(/[×x]/g, '*').replace(/÷/g, '/');
  // Must contain an operator between operands, otherwise "42" or "2024" would be "answered".
  if (!/\d\s*[-+*/%^]\s*[\d(.]|\)\s*[-+*/%^]/.test(expr)) return null;
  const tokens = tokenizeExpression(expr);
  if (!tokens) return null;

  let i = 0;
  const peek = () => tokens[i];
  const next = () => tokens[i++];

  // expr   := term (('+' | '-') term)*
  // term   := unary (('*' | '/' | '%') unary)*
  // unary  := '-' unary | power
  // power  := atom ('^' unary)?
  // atom   := number | '(' expr ')'
  function parseExpr() {
    let v = parseTerm();
    while (peek() === '+' || peek() === '-') v = next() === '+' ? v + parseTerm() : v - parseTerm();
    return v;
  }
  function parseTerm() {
    let v = parseUnary();
    while (peek() === '*' || peek() === '/' || peek() === '%') {
      const op = next();
      const rhs = parseUnary();
      v = op === '*' ? v * rhs : op === '/' ? v / rhs : v % rhs;
    }
    return v;
  }
  function parseUnary() {
    if (peek() === '-') {
      next();
      return -parseUnary();
    }
    if (peek() === '+') {
      next();
      return parseUnary();
    }
    return parsePower();
  }
  function parsePower() {
    const base = parseAtom();
    if (peek() === '^') {
      next();
      return base ** parseUnary();
    }
    return base;
  }
  function parseAtom() {
    const t = next();
    if (t === '(') {
      const v = parseExpr();
      if (next() !== ')') throw new SyntaxError('expected )');
      return v;
    }
    if (t !== undefined && /^[\d.]/.test(t)) return Number(t);
    throw new SyntaxError(`unexpected ${t}`);
  }

  try {
    const value = parseExpr();
    if (i !== tokens.length || !Number.isFinite(value)) return null;
    return Number(value.toPrecision(12));
  } catch {
    return null;
  }
}

function createCalculatorProvider() {
  const answer = (query) => {
    const value = evaluate(query);
    if (value === null) return [];
    return [{ type: 'answer', title: String(value), description: `${query.replace(/=\s*$/, '').trim()} =`, score: 0.99 }];
  };
  return { id: 'calculator', name: 'Answer', order: 0, suggest: answer, search: answer };
}

module.exports = { createCalculatorProvider, evaluate };
