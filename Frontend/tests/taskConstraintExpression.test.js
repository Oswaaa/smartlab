import test from 'node:test'
import assert from 'node:assert/strict'
import { extractExpressionVariables, toBackendExpression, validateDisplayExpressionSyntax, explainConstraintExpression, formatViolationActionTaken, describeBindingTarget, formatRuleSentenceTokens, formatDeviceActionLabel } from '../src/utils/constraintExpression.js'

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

test('explains temporal functions without exposing the template', () => {
  const text = explainConstraintExpression('rate(value, 10) > limit', null)
  assert.match(text, /每秒的平均变化量/)
  assert.match(text, /瞬时判定/)
  assert.doesNotMatch(text, /公式模板/)
})

test('translates violation actionTaken without protocol tokens', () => {
  const models = [{
    id: 1,
    capabilities: [{ capabilityName: 'capability_2', displayName: '散热' }]
  }]
  const instances = [{ id: 8, instanceName: '温度传感器#1', deviceModelId: 1 }]
  const view = formatViolationActionTaken(
    { actionTaken: 'DEVICE_CAPABILITY:CONSTRAINT_EXECUTE:capability_2', deviceInstanceId: 8 },
    models,
    instances
  )
  assert.equal(view.status, '已下发')
  assert.equal(view.summary, '温度传感器#1 · 散热')
  assert.doesNotMatch(view.summary, /CONSTRAINT_EXECUTE|capability_2/)

  const failed = formatViolationActionTaken(
    { actionTaken: 'FAILED:DEVICE_CAPABILITY:设备离线', deviceInstanceId: 8 },
    models,
    instances
  )
  assert.equal(failed.ok, false)
  assert.equal(failed.status, '下发失败')
  assert.equal(failed.detail, '设备离线')
  assert.doesNotMatch(JSON.stringify(failed), /CONSTRAINT_EXECUTE/)
})

test('binding target puts the attribute first and object type second', () => {
  const models = [{
    id: 1,
    modelName: '温度传感器',
    attributes: [{ attributeName: 'temperature', displayName: '实时温度' }]
  }]
  const instances = [{ id: 8, instanceName: '温度传感器#1', deviceModelId: 1 }]
  const view = describeBindingTarget({
    bindingType: 'OBSERVABLE',
    source: {
      sourceType: 'DEVICE_ATTRIBUTE',
      deviceInstanceId: 8,
      deviceModelId: 1,
      targetName: 'temperature'
    }
  }, models, instances)
  assert.equal(view.primary, '实时温度')
  assert.equal(view.secondary, '温度传感器#1·属性')
})

test('homologous device actions resolve displayName from the selected model', () => {
  const models = [{
    id: 1,
    capabilities: [{ capabilityName: 'capability_2', displayName: '散热' }]
  }]
  const tokens = formatRuleSentenceTokens({
    expression: 'x > 1',
    bindings: {},
    violationActions: [{ actionType: 'DEVICE_CAPABILITY', deviceModelId: 1, capabilityName: 'capability_2' }]
  }, models, [])
  assert.equal(tokens.actionLabels[0], '与观测同源.散热')

  const specOnly = formatDeviceActionLabel(
    { actionType: 'DEVICE_CAPABILITY', deviceModelId: 2, capabilityName: 'capability_2' },
    [{ id: 2, capabilitySpec: { capabilities: [{ capabilityName: 'capability_2', displayName: '散热' }] } }],
    []
  )
  assert.equal(specOnly, '与观测同源.散热')
})
