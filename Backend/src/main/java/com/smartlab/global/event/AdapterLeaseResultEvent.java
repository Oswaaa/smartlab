package com.smartlab.global.event;

import com.fasterxml.jackson.databind.JsonNode;

/** MQTT 入站租约结果。由 VirtualLeaseService 消费，MQTT 桥不再反向依赖租约服务。 */
public record AdapterLeaseResultEvent(JsonNode payload) {
}
