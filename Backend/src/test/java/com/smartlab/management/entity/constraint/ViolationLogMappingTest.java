package com.smartlab.management.entity.constraint;

import com.baomidou.mybatisplus.annotation.TableField;
import com.fasterxml.jackson.databind.JsonNode;
import com.smartlab.global.util.JsonNodeSupport;
import com.smartlab.management.mapper.common.PostgresJsonbTypeHandler;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;

class ViolationLogMappingTest {

    @Test
    void mapsTheFourViolationEvidenceFieldsToJsonbColumns() throws Exception {
        assertJsonbField("observedVariable", "observed_variable");
        assertJsonbField("expression", "expression");
        assertJsonbField("actualValue", "actual_value");
        assertJsonbField("variableSnapshot", "variable_snapshot");
        assertThrows(NoSuchFieldException.class,
                () -> ViolationLog.class.getDeclaredField("expectedCondition"));
    }

    @Test
    void serializesExpressionAndDoesNotExposeExpectedCondition() throws Exception {
        ViolationLog log = new ViolationLog();
        ViolationLog.class.getMethod("setExpression", JsonNode.class)
                .invoke(log, JsonNodeSupport.toNode("temperature > limit"));

        JsonNode json = JsonNodeSupport.toNode(log);

        assertEquals("temperature > limit", json.path("expression").asText());
        assertFalse(json.has("expectedCondition"));
    }

    private void assertJsonbField(String fieldName, String columnName) throws Exception {
        Field field = ViolationLog.class.getDeclaredField(fieldName);
        assertEquals(JsonNode.class, field.getType());
        TableField mapping = field.getAnnotation(TableField.class);
        assertEquals(columnName, mapping.value());
        assertEquals(PostgresJsonbTypeHandler.class, mapping.typeHandler());
    }
}
