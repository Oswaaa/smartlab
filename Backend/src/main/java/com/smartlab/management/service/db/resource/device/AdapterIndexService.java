package com.smartlab.management.service.db.resource.device;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.smartlab.management.entity.AdapterIndex;
import com.smartlab.management.mapper.AdapterIndexMapper;
import com.smartlab.management.service.db.common.ManagementCrudService;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;

/**
 * Adapter 索引表服务。
 * 对应 ADAPTER_INDEX 表，用于维护设备执行代理的注册配置与在线状态。
 */
@Service
public class AdapterIndexService extends ManagementCrudService<AdapterIndex> {

    private final AdapterIndexMapper mapper;

    public AdapterIndexService(AdapterIndexMapper mapper) {
        super(mapper);
        this.mapper = mapper;
    }

    /**
     * 查询全部 Adapter 索引，按更新时间倒序排列。
     */
    @Override
    public List<AdapterIndex> list() {
        return mapper.selectList(Wrappers.<AdapterIndex>lambdaQuery().orderByDesc(AdapterIndex::getUpdateTime, AdapterIndex::getId));
    }

    /**
     * 按 Adapter 标识名查询注册记录。
     */
    public AdapterIndex getByName(String adapterName) {
        if (adapterName == null || adapterName.isBlank()) {
            return null;
        }
        return mapper.selectOne(
                Wrappers.<AdapterIndex>lambdaQuery()
                        .eq(AdapterIndex::getAdapterName, adapterName.trim())
                        .last("limit 1")
        );
    }

    /**
     * 保存 Adapter 索引，自动维护创建时间和更新时间。
     */
    @Override
    public AdapterIndex save(AdapterIndex entity) {
        if (entity.getAdapterName() == null || entity.getAdapterName().isBlank()) {
            throw new IllegalArgumentException("Adapter 标识名不能为空");
        }
        entity.setAdapterName(entity.getAdapterName().trim());
        LocalDateTime now = LocalDateTime.now();
        if (entity.getId() == null) {
            entity.setCreateTime(now);
            if (entity.getStatus() == null || entity.getStatus().isBlank()) {
                entity.setStatus("UNKNOWN");
            }
        }
        entity.setUpdateTime(now);
        return super.save(entity);
    }

    /**
     * 记录 Adapter 心跳并更新状态。
     */
    public AdapterIndex heartbeat(String adapterName, String status) {
        AdapterIndex adapter = getByName(adapterName);
        if (adapter == null) {
            adapter = new AdapterIndex();
            adapter.setAdapterName(adapterName);
        }
        adapter.setStatus(status == null || status.isBlank() ? "ONLINE" : status);
        adapter.setLastHeartbeat(LocalDateTime.now());
        return save(adapter);
    }
}
