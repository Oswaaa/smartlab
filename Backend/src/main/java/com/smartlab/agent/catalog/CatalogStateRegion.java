package com.smartlab.agent.catalog;

import java.util.List;

public record CatalogStateRegion(String regionName, String regionType, List<String> states) {
}
