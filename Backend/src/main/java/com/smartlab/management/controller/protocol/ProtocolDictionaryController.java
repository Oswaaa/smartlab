package com.smartlab.management.controller.protocol;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.smartlab.global.protocol.ProtocolDictionaryService;
import com.smartlab.management.dto.common.ApiResponse;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/protocol/dictionary")
public class ProtocolDictionaryController {

    private final ProtocolDictionaryService protocolDictionaryService;

    public ProtocolDictionaryController(ProtocolDictionaryService protocolDictionaryService) {
        this.protocolDictionaryService = protocolDictionaryService;
    }

    @GetMapping
    public ApiResponse<JsonNode> dictionary() {
        return ApiResponse.ok(protocolDictionaryService.dictionary());
    }

    @GetMapping("/frontend-metadata")
    public ApiResponse<ObjectNode> frontendMetadata() {
        return ApiResponse.ok(protocolDictionaryService.frontendMetadata());
    }
}
