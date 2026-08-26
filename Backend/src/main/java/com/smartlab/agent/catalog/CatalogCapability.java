package com.smartlab.agent.catalog;

public record CatalogCapability(
        String capabilityName,
        String displayName,
        java.util.List<CatalogParameter> parameters) {
}
