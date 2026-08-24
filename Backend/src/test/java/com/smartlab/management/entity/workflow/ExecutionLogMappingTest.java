package com.smartlab.management.entity.workflow;

import com.baomidou.mybatisplus.annotation.TableName;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import java.time.OffsetDateTime;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class ExecutionLogMappingTest {

    @Test
    void mapsExecutionLogEntityToExecutionLogTable() {
        TableName tableName = ExecutionLog.class.getAnnotation(TableName.class);

        assertThat(tableName).isNotNull();
        assertThat(tableName.value()).isEqualTo("\"EXECUTION_LOG\"");
    }

    @Test
    void serializesLogTimeWithMilliseconds() throws Exception {
        ObjectMapper mapper = new ObjectMapper()
                .registerModule(new JavaTimeModule())
                .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
        ExecutionLog log = new ExecutionLog();
        log.setLogTime(OffsetDateTime.parse("2026-08-22T00:36:44.123+08:00"));

        assertThat(mapper.writeValueAsString(log)).contains("2026-08-22T00:36:44.123+08:00");
    }
}