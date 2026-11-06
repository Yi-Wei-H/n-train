<template>
  <div class="page">
    <el-card class="search-card" shadow="never">
      <el-form inline>
        <el-form-item label="会话 ID">
          <el-input v-model="query.id" placeholder="精确匹配" clearable style="width: 160px" />
        </el-form-item>
        <el-form-item label="用户 ID">
          <el-input v-model="query.userId" placeholder="精确匹配" clearable style="width: 160px" />
        </el-form-item>
        <el-form-item label="状态">
          <el-select v-model="query.status" placeholder="全部" clearable style="width: 120px">
            <el-option v-for="s in CONV_STATUS" :key="s.value" :value="s.value" :label="s.label" />
          </el-select>
        </el-form-item>
        <el-form-item label="标题">
          <el-input v-model="query.title" placeholder="模糊匹配" clearable style="width: 160px" />
        </el-form-item>
        <el-form-item>
          <el-button type="primary" @click="load">查询</el-button>
        </el-form-item>
        <span class="search-note">
          <PageNote>
            <p>账户级单主会话：距上次有效交互满 72 小时自动翻篇为「归档」，归档会话只读（不可重生成标题）。</p>
            <p>标题由首轮一次性任务生成，失败保留「新会话」。</p>
          </PageNote>
        </span>
      </el-form>
    </el-card>

    <el-card class="table-card" shadow="never">
      <el-table :data="rows" v-loading="loading">
        <el-table-column prop="id" label="会话 ID" width="180" class-name="mono" />
        <el-table-column prop="userId" label="用户 ID" width="160" class-name="mono" />
        <el-table-column prop="title" label="标题" min-width="190" show-overflow-tooltip />
        <el-table-column label="标题状态" width="110" align="center">
          <template #default="{ row }">
            <el-tag :type="tagOf(TITLE_STATUS, row.titleStatus).type">{{ tagOf(TITLE_STATUS, row.titleStatus).label }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="messageCount" label="消息数" width="90" align="right" class-name="mono" />
        <el-table-column prop="lastMessageAt" label="最近消息时间" width="180" class-name="mono" />
        <el-table-column label="状态" width="100" align="center">
          <template #default="{ row }">
            <el-tag :type="tagOf(CONV_STATUS, row.status).type">{{ tagOf(CONV_STATUS, row.status).label }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column label="操作" width="130" fixed="right" align="center">
          <template #default="{ row }">
            <el-tooltip content="查看消息" placement="top" :show-after="0">
              <el-button
                class="row-action"
                text
                circle
                v-perm="'a:message:list'"
                aria-label="查看消息"
                @click="viewMessages(row)"
              ><el-icon><View /></el-icon></el-button>
            </el-tooltip>
            <el-tooltip
              v-if="row.status === 1"
              content="重新生成标题"
              placement="top"
              :show-after="0"
            >
              <el-button
                class="row-action"
                text
                circle
                v-perm="'a:conversation:regenerate-title'"
                aria-label="重新生成标题"
                :loading="regeneratingId === row.id"
                @click="regenerate(row)"
              ><el-icon><Refresh /></el-icon></el-button>
            </el-tooltip>
            <el-popconfirm title="确认删除该会话？" @confirm="remove(row)">
              <template #reference>
                <span class="row-action-wrap">
                  <el-tooltip content="删除" placement="top" :show-after="0">
                    <el-button
                      class="row-action is-danger"
                      text
                      circle
                      v-perm="'a:conversation:delete'"
                      aria-label="删除"
                    ><el-icon><Delete /></el-icon></el-button>
                  </el-tooltip>
                </span>
              </template>
            </el-popconfirm>
          </template>
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
import { useRouter } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import request from '../../utils/request'
import PageNote from '../../components/PageNote.vue'
import { CONV_STATUS, SLOW_TIMEOUT, TITLE_STATUS, tagOf } from './constants'

const router = useRouter()
const rows = ref([])
const total = ref(0)
const loading = ref(false)
const regeneratingId = ref(null)
const query = reactive({ pageNum: 1, pageSize: 10, id: '', userId: '', status: null, title: '' })

async function load() {
  loading.value = true
  try {
    const params = { pageNum: query.pageNum, pageSize: query.pageSize, status: query.status }
    // 空串视为不筛选，避免后端把 "" 当数字解析
    if (query.id) params.id = query.id
    if (query.userId) params.userId = query.userId
    if (query.title) params.title = query.title
    const page = await request.post('/agent/conversation/list', params)
    rows.value = page.list
    total.value = page.total
  } finally {
    loading.value = false
  }
}

function viewMessages(row) {
  router.push({ path: '/agent/message', query: { conversationId: row.id } })
}

async function regenerate(row) {
  try {
    await ElMessageBox.confirm('将同步调用标题模型并覆盖现有标题，确认继续？', '重新生成标题', { type: 'warning' })
  } catch (e) {
    return
  }
  regeneratingId.value = row.id
  try {
    const title = await request.post('/agent/conversation/regenerate-title', null, {
      params: { conversationId: row.id },
      timeout: SLOW_TIMEOUT
    })
    ElMessage.success(`新标题：${title}`)
    load()
  } finally {
    regeneratingId.value = null
  }
}

async function remove(row) {
  await request.delete(`/agent/conversation/${row.id}`)
  ElMessage.success('已删除')
  load()
}

onMounted(load)
</script>
