package com.smartlab.management.controller.schema;

import com.fasterxml.jackson.databind.node.ObjectNode;
import com.smartlab.global.schema.SchemaMetadataService;
import com.smartlab.management.dto.common.ApiResponse;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/schema-metadata")
public class SchemaMetadataController {

    private final SchemaMetadataService schemaMetadataService;

    public SchemaMetadataController(SchemaMetadataService schemaMetadataService) {
        this.schemaMetadataService = schemaMetadataService;
    }

    @GetMapping("/frontend")
    public ApiResponse<ObjectNode> frontendMetadata() {
        return ApiResponse.ok(schemaMetadataService.frontendMetadata());
    }
}
