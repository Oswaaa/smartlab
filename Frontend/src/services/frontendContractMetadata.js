import axios from 'axios'

let metadataPromise

export function invalidateFrontendContractMetadata() {
  metadataPromise = undefined
}

export function loadFrontendContractMetadata() {
  if (!metadataPromise) {
    metadataPromise = axios.get('/api/schema-metadata/frontend')
      .then(response => response.data?.data ?? response.data)
      .catch(error => {
        invalidateFrontendContractMetadata()
        throw error
      })
  }
  return metadataPromise
}
