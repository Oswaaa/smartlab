package com.smartlab.management.service.db.resource.data;

import com.smartlab.management.dto.resource.data.DataTemplateSaveDTO;
import com.smartlab.management.entity.resource.data.DataTemplateMain;
import com.smartlab.management.mapper.resource.data.DataIndexMapper;
import com.smartlab.management.mapper.resource.data.DataTemplateDetailMapper;
import com.smartlab.management.mapper.resource.data.DataTemplateMainMapper;
import com.smartlab.management.mapper.resource.device.DeviceModelsMapper;
import com.smartlab.management.mapper.resource.device.PropertyTypeMapper;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class DataTemplateServiceTest {

    @Test
    void customTemplateMustReferenceExistingModel() {
        DeviceModelsMapper models = mock(DeviceModelsMapper.class);
        DataTemplateService service = service(mock(DataTemplateMainMapper.class), mock(DataIndexMapper.class), models);
        DataTemplateMain main = new DataTemplateMain();
        main.setTemplateName("custom");
        main.setDeviceModelId(88L);
        DataTemplateSaveDTO dto = new DataTemplateSaveDTO();
        dto.setMain(main);

        assertThrows(IllegalArgumentException.class, () -> service.saveCustomTemplate(dto));
    }

    @Test
    void templateIsFrozenAfterAnyDataSetHasBeenCreated() {
        DataTemplateMainMapper templates = mock(DataTemplateMainMapper.class);
        DataIndexMapper dataIndexes = mock(DataIndexMapper.class);
        DataTemplateMain existing = new DataTemplateMain();
        existing.setId(3L);
        existing.setDeviceModelId(7L);
        existing.setIsDefault(false);
        when(templates.selectByIdForUpdate(3L)).thenReturn(existing);
        when(dataIndexes.selectCount(any())).thenReturn(1L);
        DataTemplateService service = service(templates, dataIndexes, mock(DeviceModelsMapper.class));
        DataTemplateMain update = new DataTemplateMain();
        update.setId(3L);
        update.setTemplateName("changed");
        update.setDeviceModelId(7L);
        DataTemplateSaveDTO dto = new DataTemplateSaveDTO();
        dto.setMain(update);

        assertThrows(IllegalStateException.class, () -> service.saveCustomTemplate(dto));
    }

    @Test
    void defaultTemplateCannotBeDeletedIndependently() {
        DataTemplateMainMapper templates = mock(DataTemplateMainMapper.class);
        DataTemplateMain existing = new DataTemplateMain();
        existing.setId(3L);
        existing.setDeviceModelId(7L);
        existing.setIsDefault(true);
        when(templates.selectByIdForUpdate(3L)).thenReturn(existing);
        DataTemplateService service = service(templates, mock(DataIndexMapper.class), mock(DeviceModelsMapper.class));

        assertThrows(IllegalStateException.class, () -> service.delete("3"));
    }

    private DataTemplateService service(DataTemplateMainMapper templates,
                                        DataIndexMapper dataIndexes,
                                        DeviceModelsMapper models) {
        return new DataTemplateService(
                templates,
                mock(DataTemplateDetailMapper.class),
                dataIndexes,
                models,
                mock(PropertyTypeMapper.class));
    }
}