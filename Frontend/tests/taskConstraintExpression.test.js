import test from 'node:test'
import assert from 'node:assert/strict'
import { extractExpressionVariables, toBackendExpression, validateDisplayExpressionSyntax } from '../src/utils/constraintExpression.js'

test('keeps @ references synchronized with backend variable names', () => {
  assert.deepEqual(extractExpressionVariables('@temperature > @limit && @temperature < 100'), ['temperature', 'limit'])
  assert.equal(toBackendExpression('@temperature > @limit'), 'temperature > limit')
})

test('requires every variable reference to use @', () => {
  assert.throws(() => validateDisplayExpressionSyntax('@state == RUNNING'), /RUNNING.*引号/)
  assert.throws(() => validateDisplayExpressionSyntax('temperature > @limit'), /temperature.*@/)
})

test('accepts quoted string constants and built-in functions', () => {
  assert.doesNotThrow(() => validateDisplayExpressionSyntax("@state == 'RUNNING' && avg(@temperature, 60) > @limit"))
})
