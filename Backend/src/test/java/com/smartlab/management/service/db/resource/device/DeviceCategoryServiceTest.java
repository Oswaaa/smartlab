package com.smartlab.management.service.db.resource.device;

import com.baomidou.mybatisplus.core.conditions.Wrapper;
import com.smartlab.management.entity.resource.device.DeviceCategory;
import com.smartlab.management.entity.resource.device.DeviceComponents;
import com.smartlab.management.entity.resource.device.DeviceModels;
import com.smartlab.management.mapper.resource.device.DeviceCategoryMapper;
import com.smartlab.management.mapper.resource.device.DeviceComponentsMapper;
import com.smartlab.management.mapper.resource.device.DeviceModelsMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentMatchers;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class DeviceCategoryServiceTest {

    private DeviceCategoryMapper categoryMapper;
    private DeviceModelsMapper deviceModelsMapper;
    private DeviceComponentsMapper deviceComponentsMapper;
    private DeviceCategoryService categoryService;

    @BeforeEach
    void setUp() {
        categoryMapper = mock(DeviceCategoryMapper.class);
        deviceModelsMapper = mock(DeviceModelsMapper.class);
        deviceComponentsMapper = mock(DeviceComponentsMapper.class);
        categoryService = new DeviceCategoryService(categoryMapper, deviceModelsMapper, deviceComponentsMapper);
    }

    @Test
    @DisplayName("删除类别：当类别下仍有子类别时，禁止删除")
    void testDelete_hasChildren_throwsException() {
        when(categoryMapper.selectCount(any(Wrapper.class))).thenReturn(1L);

        IllegalStateException ex = assertThrows(IllegalStateException.class, () -> categoryService.delete(1L));
        assertEquals("该类别下仍有子类别，不能删除", ex.getMessage());
        verify(categoryMapper, never()).deleteById(anyLong());
    }

    @Test
    @DisplayName("删除类别：当类别下仍有设备模型时，禁止删除")
    void testDelete_hasModels_throwsException() {
        when(categoryMapper.selectCount(any(Wrapper.class))).thenReturn(0L);
        when(deviceModelsMapper.selectCount(any(Wrapper.class))).thenReturn(2L);

        IllegalStateException ex = assertThrows(IllegalStateException.class, () -> categoryService.delete(1L));
        assertEquals("该类别下仍有设备模型，不能删除", ex.getMessage());
        verify(categoryMapper, never()).deleteById(anyLong());
    }

    @Test
    @DisplayName("删除类别：当类别出现在模型的组件结构清单(DEVICE_COMPONENTS.category_id)中时，禁止删除")
    void testDelete_hasComponents_throwsException() {
        when(categoryMapper.selectCount(any(Wrapper.class))).thenReturn(0L);
        when(deviceModelsMapper.selectCount(any(Wrapper.class))).thenReturn(0L);
        when(deviceComponentsMapper.selectCount(any(Wrapper.class))).thenReturn(1L);

        IllegalStateException ex = assertThrows(IllegalStateException.class, () -> categoryService.delete(1L));
        assertEquals("该类别出现在模型的组件结构清单中，不能删除", ex.getMessage());
        verify(categoryMapper, never()).deleteById(anyLong());
    }

    @Test
    @DisplayName("删除类别：无子类别、无模型、无组件清单引用时，允许删除")
    void testDelete_success() {
        when(categoryMapper.selectCount(any(Wrapper.class))).thenReturn(0L);
        when(deviceModelsMapper.selectCount(any(Wrapper.class))).thenReturn(0L);
        when(deviceComponentsMapper.selectCount(any(Wrapper.class))).thenReturn(0L);
        when(categoryMapper.deleteById(1L)).thenReturn(1);

        assertDoesNotThrow(() -> categoryService.delete(1L));
        verify(categoryMapper).deleteById(1L);
    }

    @Test
    @DisplayName("删除类别：ID为空时直接返回")
    void testDelete_nullId() {
        assertDoesNotThrow(() -> categoryService.delete(null));
        verify(categoryMapper, never()).deleteById(anyLong());
    }

    @Test
    @DisplayName("hasComponents：正确判断是否存在组件引用")
    void testHasComponents() {
        assertFalse(categoryService.hasComponents(null));

        when(deviceComponentsMapper.selectCount(any(Wrapper.class))).thenReturn(3L);
        assertTrue(categoryService.hasComponents(10L));

        when(deviceComponentsMapper.selectCount(any(Wrapper.class))).thenReturn(0L);
        assertFalse(categoryService.hasComponents(20L));
    }
}
