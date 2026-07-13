package com.smartlab.management.entity.workflow;

import com.baomidou.mybatisplus.annotation.TableName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class ExecutionLogMappingTest {

    @Test
    void mapsExecutionLogEntityToExecutionLogTable() {
        TableName tableName = ExecutionLog.class.getAnnotation(TableName.class);

        assertThat(tableName).isNotNull();
        assertThat(tableName.value()).isEqualTo("\"EXECUTION_LOG\"");
    }
}