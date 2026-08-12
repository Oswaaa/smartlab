const normalizedDeviceId = value => {
  const id = Number(value)
  return Number.isInteger(id) && id > 0 ? id : null
}

export function collectConstraintDeviceInstanceIds(rule) {
  const ids = new Set()
  Object.values(rule?.bindings || {}).forEach(binding => {
    const id = normalizedDeviceId(binding?.source?.deviceInstanceId)
    if (id != null) ids.add(id)
  })
  ;(rule?.violationActions || []).forEach(action => {
    const id = normalizedDeviceId(action?.deviceInstanceId)
    if (id != null) ids.add(id)
  })
  return [...ids]
}

const bindingDeviceIds = bindings => new Set(
  Object.values(bindings || {})
    .map(normalizedDeviceId)
    .filter(id => id != null)
)

export function reviewTaskConstraintsAfterBindingChange(rules, previousBindings, nextBindings, existingReviews = []) {
  const previousIds = bindingDeviceIds(previousBindings)
  const nextIds = bindingDeviceIds(nextBindings)
  const removedIds = new Set([...previousIds].filter(id => !nextIds.has(id)))

  return (rules || []).map((rule, index) => {
    const existingReview = existingReviews[index] || null
    const affected = collectConstraintDeviceInstanceIds(rule).some(id => removedIds.has(id))
    return affected ? '引用的设备实例已不在当前绑定中，请重新确认观测对象或处置动作' : existingReview
  })
}
