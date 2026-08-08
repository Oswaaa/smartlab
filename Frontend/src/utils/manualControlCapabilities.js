export function normalizeManualControlCapabilities(capabilities) {
  return (Array.isArray(capabilities) ? capabilities : [])
    .map(capability => {
      const capabilityName = capability?.capabilityName || capability?.name || capability?.commandId || ''
      return {
        ...capability,
        capabilityName,
        displayName: capability?.displayName || capabilityName,
        adapterCommandName: capability?.adapterCommandName || capability?.commandName || '',
        parameters: Array.isArray(capability?.parameters) ? capability.parameters : []
      }
    })
    .filter(capability => capability.capabilityName)
}
