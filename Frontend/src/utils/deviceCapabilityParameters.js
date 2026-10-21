// Fixed mappings supply command parameters themselves. Only parameters referenced
// by a non-fixed mapping are inputs to the capability call; required is unrelated.
export function capabilityInputParameters(capability) {
  const parameters = (capability?.parameters || []).filter(parameter => !parameter.internal)
  const mappings = capability?.parameterMapping || []
  if (!mappings.length) return parameters
  const inputNames = new Set(mappings
    .filter(mapping => String(mapping.isFixedValue).toLowerCase() !== 'true')
    .map(mapping => mapping.capabilityParamName)
    .filter(Boolean))
  return parameters.filter(parameter => inputNames.has(parameter.name))
}
