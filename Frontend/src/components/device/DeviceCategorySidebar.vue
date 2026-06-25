<template>
    <div class="sidebar-container">
        <div class="sidebar-header">
            <h3 class="title">设备分类</h3>
        </div>

        <div class="tree-wrapper">
            <el-tree :data="deviceCategories" :props="defaultProps" node-key="deviceClassId"
                :current-node-key="selectedCategory?.deviceClassId" highlight-current default-expand-all
                :expand-on-click-node="false" @node-click="handleNodeClick" class="category-tree">
                <template #default="{ node, data }">
                    <div class="custom-tree-node">
                        <el-icon class="node-icon">
                            <Folder v-if="data.children && data.children.length > 0" />
                            <Document v-else />
                        </el-icon>
                        <span class="node-label">{{ node.label }}</span>
                    </div>
                </template>
            </el-tree>

            <el-empty v-if="!deviceCategories || deviceCategories.length === 0" description="暂无设备分类" :image-size="60" />
        </div>
    </div>
</template>

<script setup>
import { Folder, Document } from '@element-plus/icons-vue';

defineProps({
    deviceCategories: {
        type: Array,
        required: true,
        default: () => []
    },
    selectedCategory: {
        type: Object,
        default: null
    }
});

const emit = defineEmits(['select-category', 'add-category']);

// el-tree 的配置项，告诉它读取哪个字段作为显示文本和子节点
const defaultProps = {
    children: 'children',
    label: 'deviceClassName'
};

// 点击树节点时，触发父组件的方法
const handleNodeClick = (data) => {
    emit('select-category', data);
};
</script>

<style scoped>
.sidebar-container {
    height: 100%;
    display: flex;
    flex-direction: column;
    background-color: #ffffff;
}

.sidebar-header {
    display: flex;
    justify-content: space-between;
    align-items: center;
    padding: 20px;
    border-bottom: 1px solid #f0f2f5;
}

.sidebar-header .title {
    margin: 0;
    font-size: 16px;
    font-weight: 600;
    color: #2c3e50;
}

.tree-wrapper {
    flex: 1;
    overflow-y: auto;
    padding: 15px 10px;
}

/* 美化 Element Plus 的 Tree 组件 */
.category-tree {
    background: transparent;
}

:deep(.el-tree-node__content) {
    height: 38px;
    border-radius: 6px;
    margin-bottom: 2px;
    transition: background-color 0.2s;
}

:deep(.el-tree-node.is-current > .el-tree-node__content) {
    background-color: #ecf5ff !important;
    color: #409EFF;
    font-weight: bold;
}

.custom-tree-node {
    display: flex;
    align-items: center;
    font-size: 14px;
    width: 100%;
}

.node-icon {
    margin-right: 8px;
    font-size: 16px;
    color: #909399;
}

:deep(.is-current) .node-icon {
    color: #409EFF;
}

.node-label {
    flex: 1;
    overflow: hidden;
    text-overflow: ellipsis;
    white-space: nowrap;
}

/* 侧边栏滚动条美化 */
.tree-wrapper::-webkit-scrollbar {
    width: 6px;
}

.tree-wrapper::-webkit-scrollbar-thumb {
    background-color: #dcdfe6;
    border-radius: 3px;
}

.tree-wrapper::-webkit-scrollbar-thumb:hover {
    background-color: #c0c4cc;
}
</style>
