package com.smartlab.management.service.db.resource.data;

import com.smartlab.management.dto.common.PageResult;
import com.smartlab.management.dto.resource.data.DataSeriesResponse;
import com.smartlab.management.entity.resource.data.DataIndex;
import com.smartlab.management.entity.workflow.Task;
import com.smartlab.global.contract.TaskLifecycleState;
import com.smartlab.management.mapper.resource.data.DataIndexMapper;
import com.smartlab.management.mapper.resource.data.DataTemplateDetailMapper;
import com.smartlab.management.mapper.workflow.TaskMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class DataRecordServiceTest {

    private DataIndexMapper dataIndexMapper;
    private DataTemplateDetailMapper detailMapper;
    private JdbcTemplate jdbcTemplate;
    private TaskMapper taskMapper;
    private DataRecordService service;

    @BeforeEach
    void setUp() {
        dataIndexMapper = mock(DataIndexMapper.class);
        detailMapper = mock(DataTemplateDetailMapper.class);
        jdbcTemplate = mock(JdbcTemplate.class);
        taskMapper = mock(TaskMapper.class);
        service = new DataRecordService(dataIndexMapper, detailMapper, jdbcTemplate, taskMapper);
    }

    private DataIndex createDataIndex(Long id, String table) {
        DataIndex index = new DataIndex();
        index.setId(id);
        index.setDataTable(table);
        index.setDataTemplateId(10L);
        index.setDeviceInstanceId(5L);
        return index;
    }

    @Test
    void pageByDataIndexIdWithoutTaskIdQueriesFullTable() {
        DataIndex index = createDataIndex(1L, "device_data_1");
        when(dataIndexMapper.selectById(1L)).thenReturn(index);
        when(jdbcTemplate.queryForObject(eq("select count(*) from \"device_data_1\""), eq(Long.class)))
                .thenReturn(100L);
        when(jdbcTemplate.queryForList(
                eq("select * from \"device_data_1\" order by create_time desc limit ? offset ?"),
                eq(50L), eq(0L)
        )).thenReturn(List.of(Map.of("id", 1, "value", 10.5)));

        PageResult<Map<String, Object>> result = service.pageByDataIndexId(1L, 1, 50);

        assertEquals(100L, result.getTotal());
        assertEquals(1, result.getRecords().size());
        verify(taskMapper, never()).selectById(any());
    }

    @Test
    void pageByDataIndexIdWithUnstartedTaskReturnsEmpty() {
        DataIndex index = createDataIndex(1L, "device_data_1");
        when(dataIndexMapper.selectById(1L)).thenReturn(index);

        Task task = new Task();
        task.setId(99L);
        task.setTaskStatus(TaskLifecycleState.PENDING.name());
        task.setStartTime(null);
        when(taskMapper.selectById(99L)).thenReturn(task);

        PageResult<Map<String, Object>> result = service.pageByDataIndexId(1L, 1, 50, 99L);

        assertEquals(0L, result.getTotal());
        assertTrue(result.getRecords().isEmpty());
        verify(jdbcTemplate, never()).queryForList(anyString(), any(Object[].class));
    }

    @Test
    void pageByDataIndexIdWithCompletedTaskFiltersByTimeRange() {
        DataIndex index = createDataIndex(1L, "device_data_1");
        when(dataIndexMapper.selectById(1L)).thenReturn(index);

        OffsetDateTime start = OffsetDateTime.of(2026, 9, 7, 9, 0, 0, 0, ZoneOffset.UTC);
        OffsetDateTime end = OffsetDateTime.of(2026, 9, 7, 9, 15, 0, 0, ZoneOffset.UTC);

        Task task = new Task();
        task.setId(100L);
        task.setTaskStatus(TaskLifecycleState.SUCCEEDED.name());
        task.setStartTime(start);
        task.setEndTime(end);
        when(taskMapper.selectById(100L)).thenReturn(task);

        when(jdbcTemplate.queryForObject(
                eq("select count(*) from \"device_data_1\" where create_time >= ? and create_time <= ?"),
                eq(Long.class),
                eq(start), eq(end)
        )).thenReturn(15L);

        when(jdbcTemplate.queryForList(
                eq("select * from \"device_data_1\" where create_time >= ? and create_time <= ? order by create_time desc limit ? offset ?"),
                eq(start), eq(end), eq(50L), eq(0L)
        )).thenReturn(List.of(Map.of("id", 2, "temperature", 36.5)));

        PageResult<Map<String, Object>> result = service.pageByDataIndexId(1L, 1, 50, 100L);

        assertEquals(15L, result.getTotal());
        assertEquals(1, result.getRecords().size());
    }

    @Test
    void seriesByDataIndexIdWithCompletedTaskSetsLiveFalseAndFullTaskWindow() {
        DataIndex index = createDataIndex(1L, "device_data_1");
        when(dataIndexMapper.selectById(1L)).thenReturn(index);

        OffsetDateTime start = OffsetDateTime.of(2026, 9, 7, 9, 0, 0, 0, ZoneOffset.UTC);
        OffsetDateTime end = OffsetDateTime.of(2026, 9, 7, 9, 30, 0, 0, ZoneOffset.UTC);

        Task task = new Task();
        task.setId(101L);
        task.setTaskStatus(TaskLifecycleState.SUCCEEDED.name());
        task.setStartTime(start);
        task.setEndTime(end);
        when(taskMapper.selectById(101L)).thenReturn(task);

        OffsetDateTime earliest = start.plusMinutes(1);
        OffsetDateTime latest = end.minusMinutes(1);
        when(jdbcTemplate.queryForObject(
                contains("select max(create_time) from \"device_data_1\" where create_time >= ? and create_time <= ?"),
                eq(OffsetDateTime.class), eq(start), eq(end)
        )).thenReturn(latest);

        when(jdbcTemplate.queryForObject(
                contains("select min(create_time) from \"device_data_1\" where create_time >= ? and create_time <= ?"),
                eq(OffsetDateTime.class), eq(start), eq(end)
        )).thenReturn(earliest);

        when(jdbcTemplate.queryForList(
                contains("select * from \"device_data_1\" where create_time >= ? and create_time <= ? order by create_time asc limit ?"),
                eq(earliest), eq(latest), eq(4000)
        )).thenReturn(List.of(Map.of("create_time", earliest, "val", 42.0)));

        DataSeriesResponse response = service.seriesByDataIndexId(1L, 60, 4000, null, null, 101L);

        assertFalse(response.live(), "Completed task should not follow live stream");
        assertEquals(start, response.windowStart(), "Window start should align with task start");
        assertEquals(end, response.windowEnd(), "Window end should align with task end");
        assertEquals(earliest, response.earliestAvailable());
        assertEquals(latest, response.latestAvailable());
        assertEquals(1, response.records().size());
    }

    @Test
    void seriesByDataIndexIdWithRunningTaskSetsLiveTrue() {
        DataIndex index = createDataIndex(1L, "device_data_1");
        when(dataIndexMapper.selectById(1L)).thenReturn(index);

        OffsetDateTime start = OffsetDateTime.of(2026, 9, 7, 9, 0, 0, 0, ZoneOffset.UTC);

        Task task = new Task();
        task.setId(102L);
        task.setTaskStatus(TaskLifecycleState.RUNNING.name());
        task.setStartTime(start);
        task.setEndTime(null);
        when(taskMapper.selectById(102L)).thenReturn(task);

        OffsetDateTime latest = start.plusMinutes(10);
        when(jdbcTemplate.queryForObject(
                contains("select max(create_time) from \"device_data_1\" where create_time >= ?"),
                eq(OffsetDateTime.class), eq(start)
        )).thenReturn(latest);

        when(jdbcTemplate.queryForObject(
                contains("select min(create_time) from \"device_data_1\" where create_time >= ?"),
                eq(OffsetDateTime.class), eq(start)
        )).thenReturn(start);

        when(jdbcTemplate.queryForList(
                contains("select * from \"device_data_1\" where create_time >= ? and create_time <= ? order by create_time asc limit ?"),
                eq(start), eq(latest), eq(4000)
        )).thenReturn(List.of(Map.of("create_time", latest, "val", 100)));

        DataSeriesResponse response = service.seriesByDataIndexId(1L, 60, 4000, null, null, 102L);

        assertTrue(response.live(), "Running task should follow live stream");
        assertEquals(start, response.windowStart());
        assertEquals(latest, response.windowEnd());
    }

    @Test
    void seriesByDataIndexIdWithUnstartedTaskReturnsEmptyResponse() {
        DataIndex index = createDataIndex(1L, "device_data_1");
        when(dataIndexMapper.selectById(1L)).thenReturn(index);

        Task task = new Task();
        task.setId(103L);
        task.setTaskStatus(TaskLifecycleState.PENDING.name());
        task.setStartTime(null);
        when(taskMapper.selectById(103L)).thenReturn(task);

        DataSeriesResponse response = service.seriesByDataIndexId(1L, 60, 4000, null, null, 103L);

        assertNull(response.windowStart());
        assertNull(response.windowEnd());
        assertFalse(response.live());
        assertTrue(response.records().isEmpty());
    }

    @Test
    void exportCsvWithTaskIdFiltersByTaskTimeRange() throws Exception {
        DataIndex index = createDataIndex(1L, "device_data_1");
        when(dataIndexMapper.selectById(1L)).thenReturn(index);
        when(detailMapper.selectList(any())).thenReturn(List.of());

        OffsetDateTime start = OffsetDateTime.of(2026, 9, 7, 9, 0, 0, 0, ZoneOffset.UTC);
        OffsetDateTime end = OffsetDateTime.of(2026, 9, 7, 9, 15, 0, 0, ZoneOffset.UTC);

        Task task = new Task();
        task.setId(104L);
        task.setStartTime(start);
        task.setEndTime(end);
        when(taskMapper.selectById(104L)).thenReturn(task);

        jakarta.servlet.http.HttpServletResponse response = mock(jakarta.servlet.http.HttpServletResponse.class);
        java.io.ByteArrayOutputStream baos = new java.io.ByteArrayOutputStream();
        jakarta.servlet.ServletOutputStream sos = new jakarta.servlet.ServletOutputStream() {
            @Override
            public boolean isReady() { return true; }
            @Override
            public void setWriteListener(jakarta.servlet.WriteListener writeListener) {}
            @Override
            public void write(int b) { baos.write(b); }
        };
        when(response.getOutputStream()).thenReturn(sos);

        service.exportCsv(1L, 104L, response);

        verify(response).setHeader(eq("Content-Disposition"), contains("device_data_1_task_104.csv"));
        verify(jdbcTemplate).query(
                eq("select * from \"device_data_1\" where create_time >= ? and create_time <= ? order by create_time desc"),
                any(org.springframework.jdbc.core.PreparedStatementSetter.class),
                any(org.springframework.jdbc.core.RowCallbackHandler.class)
        );
    }
}
