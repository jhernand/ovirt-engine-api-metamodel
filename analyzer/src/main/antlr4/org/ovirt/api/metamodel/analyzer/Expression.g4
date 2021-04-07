/*
 * Copyright oVirt Authors
 * SPDX-License-Identifier: Apache-2.0
 */

// This file contains the grammar for the expressions used in the model language:

grammar Expression;

@header {
import java.math.BigInteger;
import java.util.List;

import org.ovirt.api.metamodel.concepts.Name;
import org.ovirt.api.metamodel.concepts.Expression;
}

statements returns [List<Expression> result]:
  statement*
;

statement returns [Expression result]:
  'assert' booleanExpression ';' # StatementAssert
| 'return' expression? ';' # StatementReturn
| expression? ';' # StatementCall
;

expression returns [Expression result]:
  booleanExpression
| arithmeticExpression
;

booleanExpression returns [Expression result]:
  booleanTerm
;

booleanTerm returns [Expression result]:
  booleanFactor # BooleanUnaryTerm
| left = booleanTerm '||' right = booleanFactor # BooleanBinaryTerm
;

booleanFactor returns [Expression result]:
  booleanPrimary # BooleanUnaryFactor
| left = booleanFactor '&&' right = booleanPrimary # BooleanBinaryFactor
;

booleanPrimary returns [Expression result]:
  booleanAtom # BooleanPrimaryAtom
| '!' booleanPrimary # BooleanPrimaryNegation
;

booleanAtom returns [Expression result]:
  arithmeticAtom
| relationalExpression
;

relationalExpression returns [Expression result]:
  left = arithmeticExpression operator = ('==' | '!=' | '>' | '>=' | '<' | '<=') right = arithmeticExpression
;

arithmeticExpression returns [Expression result]:
  arithmethicTerm
;

arithmethicTerm returns [Expression result]:
  arithmethicFactor # ArithmeticUnaryTerm
| left = arithmethicTerm operator = ('+' | '-') right = arithmethicFactor # ArithmeticBinaryTerm
;

arithmethicFactor returns [Expression result]:
  arithmeticPrimary # ArithmeticUnaryFactor
| left = arithmethicFactor operator = ('/' | '*' | '%') right = arithmeticPrimary # ArithmeticBinaryFactor
;

arithmeticPrimary returns [Expression result]:
  arithmeticAtom # ArithmeticPrimaryAtom
| sign = ('+' | '-') arithmeticPrimary # ArithmeticPrimarySign
;

arithmeticAtom returns [Expression result]:
  literal # ArithmeticAtomLiteral
| identifier # ArithmeticAtomId
| call # ArithmeticAtomCall
| '(' expression ')' # ArithmeticAtomParenthesized
| arithmeticAtom '.' identifier # ArithmeticAtomId
| arithmeticAtom '.' call # ArithmeticAtomCall
| arithmeticAtom '[' arithmeticExpression ']' # ArithmeticAtomArray
;

call returns [Expression result]:
  identifier '(' callParameters? ')'
;

callParameters returns [List<Expression> result]:
  expression (',' expression)*
;

literal returns [Object result]:
  ('true' | 'false') # LiteralBoolean
| integer # LiteralNumeric
| 'null' # LiteralNull
;

identifier returns [Name name]:
  IDENTIFIER
;

integer returns [BigInteger value]:
  INTEGER
;

// Numeric literals:
INTEGER:
  [0-9]+
;

// Identifiers:
IDENTIFIER:
  [a-zA-Z_][a-zA-Z0-9_]*
;

// White space and comments:
WHITE_SPACE:
  [ \t\r\n\u000C]+ -> skip
;

BLOCK_COMMENT:
  '/*' .*? '*/' -> skip
;

LINE_COMMENT:
  '//' ~[\r\n]* -> skip
;
