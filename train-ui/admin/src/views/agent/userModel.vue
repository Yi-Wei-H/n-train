<template>
  <div class="page">
    <el-card class="search-card" shadow="never">
      <el-form inline>
        <el-form-item label="用户 ID">
          <el-input v-model="query.userId" placeholder="精确匹配" clearable style="width: 160px" />
        </el-form-item>
        <el-form-item label="状态">
          <el-select v-model="query.status" placeholder="全部" clearable style="width: 120px">
            <el-option :value="1" label="可用" />
            <el-option :value="2" label="禁用" />
          </el-select>
        </el-form-item>
        <el-form-item>
          <el-button type="primary" @click="load">查询</el-button>
        </el-form-item>
        <span class="search-note">
          <PageNote>
            <p>授权行随用户注册自动创建，不提供新增。禁用后用户侧表现为「暂不支持使用 AI 交流功能」。</p>
            <p>日配额按「用户 + 模型 + 当日成功调用数」统计，0 表示不限。</p>
          </PageNote>
        </span>
      </el-form>
    </el-card>

    <el-card class="table-card" shadow="never">
      <el-table :data="rows" v-loading="loading">
        <el-table-column prop="userId" label="用户 ID" width="170" class-name="mono" />
        <el-table-column label="授权状态" width="120" align="center">
          <template #default="{ row }">
            <el-switch
              :model-value="row.status === 1"
              size="small"
              class="is-neutral"
              v-perm="'a:user-model:save'"
              :disabled="isPending(row)"
              :aria-busy="isPending(row) ? 'true' : 'false'"
              @change="(v) => toggleStatus(row, v)"
            />
          </template>
        </el-table-column>
        <el-table-column label="每日配额" width="140">
          <template #default="{ row }">
            <el-tag v-if="!row.dailyQuota" type="info">不限</el-tag>
            <span v-else class="mono">{{ row.dailyQuota }} 次/日</span>
          </template>
        </el-table-column>
        <el-table-column prop="modelId" label="模型 ID" min-width="150" class-name="mono">
          <template #default="{ row }">{{ row.modelId ?? '跟随当前启用模型' }}</template>
        </el-table-column>
        <el-table-column label="操作" width="100" align="center" fixed="right">
          <template #default="{ row }">
            <el-tooltip content="编辑授权" placement="top" :show-after="0">
              <el-button
                class="row-action"
                text
                circle
                v-perm="'a:user-model:save'"
                aria-label="编辑授权"
                @click="openEdit(row)"
              ><el-icon><Edit /></el-icon></el-button>
            </el-tooltip>
            <el-popconfirm title="删除仅纠错用（授权行随用户注册自动创建），确认删除？" width="260" @confirm="remove(row)">
              <template #reference>
                <span class="row-action-wrap">
                  <el-tooltip content="删除" placement="top" :show-after="0">
                    <el-button
                      class="row-action is-danger"
                      text
                      circle
                      v-perm="'a:user-model:delete'"
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

  <el-dialog v-model="dialog" title="编辑用户授权" width="480px" top="5vh" class="form-dialog" destroy-on-close>
    <el-form :model="form" label-width="100px">
      <el-form-item label="用户 ID"><span class="mono">{{ form.userId }}</span></el-form-item>
      <el-form-item label="授权状态">
        <el-radio-group v-model="form.status">
          <el-radio :value="1">可用</el-radio>
          <el-radio :value="2">禁用</el-radio>
        </el-radio-group>
      </el-form-item>
      <el-form-item label="每日配额">
        <el-input-number v-model="form.dailyQuota" :min="0" :max="100000" />
        <div class="form-hint">0 表示不限。</div>
      </el-form-item>
    </el-form>
    <template #footer>
      <el-button @click="dialog = false">取消</el-button>
      <el-button type="primary" :loading="saving" @click="save">保存</el-button>
    </template>
  </el-dialog>
</template>

<script setup>
import { onMounted, reactive, ref } from 'vue'
import { ElMessage } from 'element-plus'
import request from '../../utils/request'
import PageNote from '../../components/PageNote.vue'
import { useOptimisticSwitch } from '../../composables/useOptimisticSwitch'

const rows = ref([])
const total = ref(0)
const loading = ref(false)
const saving = ref(false)
const dialog = ref(false)
const query = reactive({ pageNum: 1, pageSize: 10, userId: '', status: null })
const form = reactive({ userId: null, status: 1, dailyQuota: 0 })

async function load() {
  loading.value = true
  try {
    const params = { pageNum: query.pageNum, pageSize: query.pageSize, status: query.status }
    // 空串视为不筛选，避免后端把 "" 当用户 id 解析
    if (query.userId) params.userId = query.userId
    const page = await request.post('/agent/user-model/list', params)
    rows.value = page.list
    total.value = page.total
  } finally {
    loading.value = false
  }
}

const { isPending, toggle: toggleSwitch } = useOptimisticSwitch(
  (row, enabled) => request.put('/agent/user-model', {
    userId: row.userId,
    status: enabled ? 1 : 2,
    dailyQuota: row.dailyQuota || 0
  }),
  { key: 'userId', on: 1, off: 2, onSuccess: () => ElMessage.success('已更新') }
)

function toggleStatus(row, enabled) {
  return toggleSwitch(row, enabled)
}

function openEdit(row) {
  Object.assign(form, {
    userId: row.userId,
    status: row.status,
    dailyQuota: row.dailyQuota || 0
  })
  dialog.value = true
}

async function save() {
  saving.value = true
  try {
    await request.put('/agent/user-model', { userId: form.userId, status: form.status, dailyQuota: form.dailyQuota })
    ElMessage.success('保存成功')
    dialog.value = false
    load()
  } finally {
    saving.value = false
  }
}

async function remove(row) {
  await request.delete(`/agent/user-model/${row.userId}`)
  ElMessage.success('已删除')
  load()
}

onMounted(load)
</script>
