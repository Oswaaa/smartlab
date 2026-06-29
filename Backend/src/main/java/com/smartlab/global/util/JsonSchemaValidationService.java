package com.smartlab.global.util;

import com.fasterxml.jackson.databind.JsonNode;
import com.networknt.schema.JsonSchema;
import com.networknt.schema.JsonSchemaFactory;
import com.networknt.schema.SpecVersion;
import com.networknt.schema.ValidationMessage;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Service;

import java.io.InputStream;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

@Service
/**
 * JsonSchemaValidation业务持久层核心操作服务。
 */
public class JsonSchemaValidationService {

    private final JsonSchemaFactory factory = JsonSchemaFactory.getInstance(SpecVersion.VersionFlag.V7);
    private final ConcurrentHashMap<String, JsonSchema> schemaCache = new ConcurrentHashMap<>();

    /**
     * 使用 resources/schemas 下的 JSON Schema 校验载荷。
     */
    public void validate(JsonNode payload, String schemaFileName) {
        if (payload == null || payload.isNull()) {
            return;
        }

        JsonSchema schema = schemaCache.computeIfAbsent(schemaFileName, this::loadSchema);
        Set<ValidationMessage> validationMessages = schema.validate(payload);

        if (!validationMessages.isEmpty()) {
            StringBuilder errors = new StringBuilder("JSON Schema 校验失败: " + schemaFileName + "\n");
            for (ValidationMessage message : validationMessages) {
                errors.append("- ").append(message.getMessage()).append("\n");
            }
            throw new IllegalArgumentException(errors.toString());
        }
    }

    private JsonSchema loadSchema(String schemaFileName) {
        try {
            ClassPathResource resource = new ClassPathResource("schemas/" + schemaFileName);
            try (InputStream inputStream = resource.getInputStream()) {
                return factory.getSchema(inputStream);
            }
        } catch (Exception e) {
            throw new RuntimeException("加载 JSON Schema 失败: " + schemaFileName, e);
        }
    }
}
