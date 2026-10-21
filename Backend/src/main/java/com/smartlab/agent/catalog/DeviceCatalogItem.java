package com.smartlab.agent.catalog;

import com.fasterxml.jackson.annotation.JsonInclude;

import java.util.List;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record DeviceCatalogItem(
        Long deviceModelId,
        String deviceModelName,
        String categoryName,
        List<CatalogCapability> capabilities,
        List<CatalogAttribute> attributes,
        List<CatalogPort> ports,
        List<CatalogInterface> stateMachineInterfaces,
        List<CatalogStateRegion> stateRegions,
        String error) {

    public DeviceCatalogItem(Long deviceModelId, String deviceModelName, String categoryName,
            List<CatalogCapability> capabilities, List<CatalogAttribute> attributes,
            List<CatalogPort> ports, List<CatalogInterface> stateMachineInterfaces) {
        this(deviceModelId, deviceModelName, categoryName, capabilities, attributes, ports,
                stateMachineInterfaces, null, null);
    }
}
