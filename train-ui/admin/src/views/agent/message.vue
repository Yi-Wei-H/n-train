<template>
  <div class="page">
    <div class="page-back">
      <el-button link @click="back">
        <el-icon><ArrowLeft /></el-icon>返回会话列表
      </el-button>
    </div>

    <el-card class="search-card" shadow="never">
      <el-form inline>
        <el-form-item label="会话">
          <code class="mono">{{ conversationId || '—' }}</code>
        </el-form-item>
        <el-form-item label="角色">
          <el-select v-model="query.role" placeholder="全部" clearable style="width: 160px">
            <el-option v-for="r in MSG_ROLES" :key="r.value" :value="r.value" :label="r.label" />
          </el-select>
        </el-form-item>
        <el-form-item>
          <el-button type="primary" @click="load">查询</el-button>
        </el-form-item>
        <span class="search-note">
          <PageNote>
            <p>一条消息 = 一轮对话（用户输入 + AI 回复）；role=system 为系统任务轮（如标题生成），仅管理面可查。</p>
            <p>展开行查看完整输入与回复。</p>
          </PageNote>
        </span>
      </el-form>
    </el-card>

    <el-card class="table-card" shadow="never">
      <el-empty v-if="!conversationId" :image-size="72" description="缺少会话 ID，请从会话管理进入" />
      <template v-else>
        <el-table :data="rows" v-loading="loading">
          <el-table-column type="expand">
            <template #default="{ row }">
              <div class="expand">
                <div class="expand-label">用户输入（userContent）</div>
                <div class="expand-box">{{ row.userContent || '—' }}</div>
                <div class="expand-label">AI 回复（reply）</div>
                <div class="expand-box">{{ row.reply || '—（失败轮，回复为空）' }}</div>
                <div v-if="row.tools" class="expand-label">调用工具：<code class="mono">{{ row.tools }}</code></div>
                <div v-if="row.refs" class="expand-label">RAG 引用：<code class="mono">{{ row.refs }}</code></div>
              </div>
            </template>
          </el-table-column>
          <el-table-column prop="id" label="消息 ID" width="170" show-overflow-tooltip class-name="mono" />
          <el-table-column prop="createdAt" label="时间" width="170" class-name="mono" />
          <el-table-column label="角色" width="100" align="center">
            <template #default="{ row }">
              <el-tag :type="tagOf(MSG_ROLES, row.role).type">{{ row.role }}</el-tag>
            </template>
          </el-table-column>
          <el-table-column label="用户输入" min-width="180" show-overflow-tooltip>
            <template #default="{ row }">{{ row.userContent || '—' }}</template>
          </el-table-column>
          <el-table-column label="AI 回复" min-width="200" show-overflow-tooltip>
            <template #default="{ row }">{{ row.reply || '—（失败轮）' }}</template>
          </el-table-column>
          <el-table-column label="卡片" width="80" align="right" class-name="mono">
            <template #default="{ row }">{{ row.cardId ?? '—' }}</template>
          </el-table-column>
          <el-table-column label="调用工具" min-width="140" show-overflow-tooltip class-name="mono">
            <template #default="{ row }">{{ row.tools || '—' }}</template>
          </el-table-column>
          <el-table-column prop="chatModelId" label="模型 ID" width="110" class-name="mono" />
        </el-table>
        <div class="table-foot">
          <el-pagination
            layout="total, prev, pager, next"
            :total="total"
            :page-size="query.pageSize"
            :current-page="query.pageNum"
            @current-change="onPageChange"
          />
        </div>
      </template>
    </el-card>
  </div>
</template>

<script setup>
import { onMounted, reactive, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import request from '../../utils/request'
import PageNote from '../../components/PageNote.vue'
import { MSG_ROLES, tagOf } from './constants'

const route = useRoute()
const router = useRouter()
// 雪花 ID 超出 JS 安全整数范围，保持字符串原样传递（Number() 会丢精度）
const conversationId = route.query.conversationId || null
const rows = ref([])
const total = ref(0)
const loading = ref(false)
const query = reactive({ pageNum: 1, pageSize: 10, role: null })

async function load() {
  if (!conversationId) return
  loading.value = true
  try {
    const page = await request.post('/agent/message/list', {
      pageNum: query.pageNum,
      pageSize: query.pageSize,
      conversationId,
      role: query.role || null
    })
    rows.value = page.list
    total.value = page.total
  } finally {
    loading.value = false
  }
}

function onPageChange(page) {
  query.pageNum = page
  load()
}

function back() {
  router.push('/agent/conversation')
}

onMounted(load)
</script>
