<script setup lang="ts" generic="T extends Record<string, unknown>">
import { ref, onMounted, computed } from 'vue'

// =========================================================================
// Types
// =========================================================================

/** 后端返回的分页结果 */
export interface PageResult<T> {
  list: T[]
  total: number
}

/** 分页请求参数 */
export interface PageParams {
  page: number
  pageSize: number
}

/** 列配置 */
export interface Column<T = Record<string, unknown>> {
  /** 列标题 */
  label: string
  /** 数据字段名（slot 列可省略，通过具名插槽渲染） */
  prop?: string
  /** 列宽（px） */
  width?: number
  /** 最小列宽 */
  minWidth?: number
  /** 插槽名，设置后该列通过具名插槽渲染 */
  slot?: string
  /** 列对齐方式 */
  align?: 'left' | 'center' | 'right'
  /** 是否固定列 */
  fixed?: 'left' | 'right' | boolean
}

/** 表格组件属性 */
const props = withDefaults(defineProps<{
  columns: Column<T>[]
  api: (params: PageParams) => Promise<PageResult<T>>
  showPagination?: boolean
  pageSize?: number
  pageSizes?: number[]
  rowKey?: string
  /** 是否支持行展开（展开后在 #expand slot 中渲染内容） */
  expandable?: boolean
}>(), {
  showPagination: true,
  pageSize: 10,
  pageSizes: () => [10, 20, 50],
  rowKey: 'id',
  expandable: false,
})

const emit = defineEmits<{
  'expand-change': [row: T, expandedRows: T[]]
}>()

// =========================================================================
// State
// =========================================================================

const loading = ref(false)
const tableData = ref<T[]>([])
const currentPage = ref(1)
const currentPageSize = ref(props.pageSize)
const total = ref(0)

// =========================================================================
// Methods
// =========================================================================

async function fetchData(): Promise<void> {
  loading.value = true
  try {
    const result = await props.api({
      page: currentPage.value,
      pageSize: currentPageSize.value,
    })
    tableData.value = result.list
    total.value = result.total
  } finally {
    loading.value = false
  }
}

function handlePageChange(page: number): void {
  currentPage.value = page
  fetchData()
}

function handleSizeChange(size: number): void {
  currentPageSize.value = size
  currentPage.value = 1
  fetchData()
}

function refresh(): void {
  fetchData()
}

// =========================================================================
// Computed
// =========================================================================

/** 过滤出不需要 slot 的列 props（让 el-table 用 prop 自动取值） */
const textColumns = computed(() =>
  props.columns.filter((col) => !col.slot),
)

/** 需要 slot 的列 */
const slotColumns = computed(() =>
  props.columns.filter((col) => col.slot),
)

// =========================================================================
// Lifecycle
// =========================================================================

onMounted(() => {
  fetchData()
})

// =========================================================================
// Template refs
// =========================================================================

// eslint-disable-next-line @typescript-eslint/no-explicit-any
const elTableRef = ref<any>()

// =========================================================================
// Expose
// =========================================================================

defineExpose({
  refresh,
  /** 切换行展开状态，需要 table 启用 expandable */
  toggleRowExpansion: (row: T, expanded?: boolean) => {
    elTableRef.value?.toggleRowExpansion(row, expanded)
  },
})
</script>

<template>
  <div class="hify-table">
    <el-table
      ref="elTableRef"
      :data="tableData"
      :row-key="rowKey"
      :row-style="{ height: '52px' }"
      v-loading="loading"
      border
      stripe
      class="hify-table__inner"
      @expand-change="(row: T, rows: T[]) => emit('expand-change', row, rows)"
    >
      <template #empty>
        <el-empty description="暂无数据" />
      </template>

      <!-- 展开列（expandable 模式下自动添加） -->
      <el-table-column v-if="expandable" type="expand" width="48">
        <template #default="{ row }">
          <slot name="expand" :row="row" />
        </template>
      </el-table-column>

      <!-- 纯文本列（通过 prop 自动取值） -->
      <el-table-column
        v-for="col in textColumns"
        :key="col.prop"
        :label="col.label"
        :prop="col.prop"
        :width="col.width"
        :min-width="col.minWidth"
        :align="col.align"
        :fixed="col.fixed"
        show-overflow-tooltip
      />

      <!-- 自定义 slot 列 -->
      <el-table-column
        v-for="col in slotColumns"
        :key="col.prop"
        :label="col.label"
        :width="col.width"
        :min-width="col.minWidth"
        :align="col.align"
        :fixed="col.fixed"
      >
        <template #default="{ row }">
          <slot :name="col.slot" :row="row" />
        </template>
      </el-table-column>
    </el-table>

    <!-- 分页 -->
    <div v-if="showPagination && total > 0" class="hify-table__pagination">
      <el-pagination
        :current-page="currentPage"
        :page-size="currentPageSize"
        :page-sizes="pageSizes"
        :total="total"
        layout="total, sizes, prev, pager, next, jumper"
        background
        @current-change="handlePageChange"
        @size-change="handleSizeChange"
      />
    </div>
  </div>
</template>

<style scoped>
.hify-table {
  display: flex;
  flex-direction: column;
  gap: var(--hify-section-gap);
}

.hify-table__inner {
  width: 100%;
}

.hify-table__pagination {
  display: flex;
  justify-content: flex-end;
  padding-top: var(--hify-space-3);
  border-top: 1px solid var(--hify-border-light);
}
</style>
