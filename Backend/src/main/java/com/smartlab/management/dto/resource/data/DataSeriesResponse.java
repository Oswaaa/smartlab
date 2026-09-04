package com.smartlab.management.dto.resource.data;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;

/**
 * 遥测走势图专用时序窗口与采样点。
 */
public record DataSeriesResponse(
        OffsetDateTime windowStart,
        OffsetDateTime windowEnd,
        OffsetDateTime earliestAvailable,
        OffsetDateTime latestAvailable,
        boolean live,
        List<Map<String, Object>> records
) {
}
