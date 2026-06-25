package com.smartlab.management.dto;

import com.smartlab.management.entity.DataTemplateDetail;
import com.smartlab.management.entity.DataTemplateMain;
import lombok.Data;

import java.util.ArrayList;
import java.util.List;

/**
 * 数据模板保存 DTO。
 *
 * 用于一次保存 DATA_TEMPLATE_MAIN 和 DATA_TEMPLATE_DETAIL。
 */
@Data
public class DataTemplateSaveDTO {

    /** 模板主表。 */
    private DataTemplateMain main;

    /** 模板字段明细。 */
    private List<DataTemplateDetail> details = new ArrayList<>();
}

