<template>
  <div class="page">
    <el-card class="search-card" shadow="never">
      <el-form inline>
        <el-form-item label="用户 ID">
          <el-input v-model="query.userId" placeholder="精确匹配" clearable style="width: 140px" />
        </el-form-item>
        <el-form-item label="模型 ID">
          <el-input v-model="query.modelId" placeholder="精确匹配" clearable style="width: 140px" />
        </el-form-item>
        <el-form-item label="会话 ID">
          <el-input v-model="query.conversationId" placeholder="精确匹配" clearable style="width: 160px" />
        </el-form-item>
        <el-form-item label="结果">
          <el-select v-model="query.callStatus" placeholder="全部" clearable style="width: 120px">
            <el-option v-for="s in CALL_STATUS" :key="s.value" :value="s.value" :label="s.label" />
          </el-select>
        </el-form-item>
        <el-form-item>
          <el-button type="primary" @click="load">查询</el-button>
        </el-form-item>
        <span class="search-note">
          <PageNote>
            <p>不可变流水表：只增不改。</p>
            <p>日配额按「用户 + 模型 + 当日成功行数」聚合，对应「用户授权」页的每日配额。</p>
          </PageNote>
        </span>
      </el-form>
    </el-card>

    <el-card class="table-card" shadow="never">
      <el-table :data="rows" v-loading="loading">
        <el-table-column prop="createdAt" label="时间" width="150" class-name="mono" />
        <el-table-column prop="userId" label="用户 ID" width="120" show-overflow-tooltip class-name="mono" />
        <el-table-column prop="modelId" label="模型 ID" width="100" class-name="mono" />
        <el-table-column prop="conversationId" label="会话 ID" width="150" show-overflow-tooltip class-name="mono" />
        <el-table-column prop="inputTokens" label="输入 tokens" width="100" align="right" class-name="mono">
          <template #default="{ row }">{{ row.inputTokens ?? '—' }}</template>
        </el-table-column>
        <el-table-column prop="cachedTokens" label="缓存命中" width="90" align="right" class-name="mono">
          <template #default="{ row }">{{ row.cachedTokens ?? '—' }}</template>
        </el-table-column>
        <el-table-column prop="outputTokens" label="输出 tokens" width="100" align="right" class-name="mono">
          <template #default="{ row }">{{ row.outputTokens ?? '—' }}</template>
        </el-table-column>
        <el-table-column prop="latencyMs" label="耗时" width="80" align="right" class-name="mono">
          <template #default="{ row }">{{ row.latencyMs != null ? `${row.latencyMs} ms` : '—' }}</template>
        </el-table-column>
        <el-table-column label="结果" width="80" align="center">
          <template #default="{ row }">
            <el-tag :type="tagOf(CALL_STATUS, row.callStatus).type">{{ tagOf(CALL_STATUS, row.callStatus).label }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="errMsg" label="失败原因" min-width="170" show-overflow-tooltip>
          <template #default="{ row }">{{ row.errMsg || '—' }}</template>
        </el-table-column>
      </el-table>
      <div class="table-foot">
        <el-pagination
          layout="total, prev, pager, next"
          :total="total"
          :page-size="query.pageSize"
          v-model:current-page="query.pageNum"
          @current-change="load"
        />
      </div>
    </el-card>
  </div>
</template>

<script setup>
import { onMounted, reactive, ref } from 'vue'
import request from '../../utils/request'
import PageNote from '../../components/PageNote.vue'
import { CALL_STATUS, tagOf } from './constants'

const rows = ref([])
const total = ref(0)
const loading = ref(false)
const query = reactive({ pageNum: 1, pageSize: 10, userId: '', modelId: '', conversationId: '', callStatus: null })

async function load() {
  loading.value = true
  try {
    const params = { pageNum: query.pageNum, pageSize: query.pageSize, callStatus: query.callStatus }
    // 空串视为不筛选，避免后端把 "" 当数字解析
    if (query.userId) params.userId = query.userId
    if (query.modelId) params.modelId = query.modelId
    if (query.conversationId) params.conversationId = query.conversationId
    const page = await request.post('/agent/usage/list', params)
    rows.value = page.list
    total.value = page.total
  } finally {
    loading.value = false
  }
}

onMounted(load)
</script>
