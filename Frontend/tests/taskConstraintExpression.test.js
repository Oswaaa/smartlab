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
  assert.doesNotThrow(() => validateDisplayExpressionSyntax('rate(@value, 10) > @limit'))
})

test('requires temporal functions to include a time window', () => {
  assert.throws(() => validateDisplayExpressionSyntax('rate(@value) > @limit'), /rate\(\) 需要两个参数/)
  assert.throws(() => validateDisplayExpressionSyntax('delta(@value) > 5'), /delta\(\) 需要两个参数/)
})

test('requires a boolean predicate, not a numeric fragment', () => {
  assert.throws(() => validateDisplayExpressionSyntax('delta(@value, 10)'), /必须返回 true\/false/)
  assert.throws(() => validateDisplayExpressionSyntax('avg(@value, 60)'), /必须返回 true\/false/)
  assert.throws(() => validateDisplayExpressionSyntax('rate(@value, 10)'), /必须返回 true\/false/)
  assert.throws(
    () => validateDisplayExpressionSyntax('delta(@value, 10)avg(@value, 60)'),
    /无法解析的内容/
  )
})
