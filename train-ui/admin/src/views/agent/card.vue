<template>
  <div class="page">
    <el-card class="search-card" shadow="never">
      <el-form inline>
        <el-form-item label="卡片名">
          <el-input v-model="query.cardName" placeholder="模糊匹配" clearable style="width: 160px" />
        </el-form-item>
        <el-form-item label="状态">
          <el-select v-model="query.status" placeholder="全部" clearable style="width: 120px">
            <el-option :value="1" label="上架" />
            <el-option :value="2" label="下架" />
          </el-select>
        </el-form-item>
        <el-form-item>
          <el-button type="primary" @click="load">查询</el-button>
          <el-button type="primary" plain v-perm="'a:card:save'" @click="openAdd">
            <el-icon><Plus /></el-icon>新增
          </el-button>
        </el-form-item>
        <span class="search-note">
          <PageNote>
            <p>卡片在 H5 聊天页点击即发送，只回传 cardId（提示词由服务端读取，不信任前端）。</p>
            <p>图标为标识符，H5 端维护「标识 → 图标」映射，未匹配回落 chat-o；活动窗口为空即常驻。</p>
          </PageNote>
        </span>
      </el-form>
    </el-card>

    <el-card class="table-card" shadow="never">
      <el-table :data="rows" v-loading="loading">
        <el-table-column prop="cardName" label="卡片名" min-width="140" />
        <el-table-column prop="promptText" label="提示词（隐藏，用户侧不可见）" min-width="240" show-overflow-tooltip />
        <el-table-column prop="icon" label="图标标识" width="130" class-name="mono">
          <template #default="{ row }">{{ row.icon || '—' }}</template>
        </el-table-column>
        <el-table-column prop="sortNo" label="排序" width="80" align="right" class-name="mono" />
        <el-table-column label="活动窗口" width="210">
          <template #default="{ row }">
            <el-tag v-if="!row.startAt && !row.endAt" type="info">常驻</el-tag>
            <span v-else class="window mono">{{ row.startAt || '—' }} ~ {{ row.endAt || '—' }}</span>
          </template>
        </el-table-column>
        <el-table-column label="状态" width="80" align="center">
          <template #default="{ row }">
            <el-switch
              :model-value="row.status === 1"
              size="small"
              class="is-neutral"
              v-perm="'a:card:save'"
              :disabled="isPending(row)"
              :aria-busy="isPending(row) ? 'true' : 'false'"
              @change="(v) => toggleStatus(row, v)"
            />
          </template>
        </el-table-column>
        <el-table-column label="操作" width="100" fixed="right" align="center">
          <template #default="{ row }">
            <el-tooltip content="编辑" placement="top" :show-after="0">
              <el-button
                class="row-action"
                text
                circle
                v-perm="'a:card:save'"
                aria-label="编辑"
                @click="openEdit(row)"
              ><el-icon><Edit /></el-icon></el-button>
            </el-tooltip>
            <el-popconfirm title="确认删除该卡片？" @confirm="remove(row)">
              <template #reference>
                <span class="row-action-wrap">
                  <el-tooltip content="删除" placement="top" :show-after="0">
                    <el-button
                      class="row-action is-danger"
                      text
                      circle
                      v-perm="'a:card:delete'"
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

  <el-dialog v-model="dialog" :title="form.id ? '编辑卡片' : '新增卡片'" width="640px" top="5vh" class="form-dialog" destroy-on-close>
    <el-form :model="form" label-width="100px">
      <el-form-item label="卡片名" required><el-input v-model="form.cardName" maxlength="32" show-word-limit /></el-form-item>
      <el-form-item label="提示词" required>
        <el-input
          v-model="form.promptText" type="textarea" :rows="3" maxlength="512" show-word-limit
          placeholder="用户点击卡片时实际发送给 AI 的提示词"
        />
      </el-form-item>
      <el-form-item label="图标标识">
        <el-select v-model="form.icon" filterable allow-create clearable placeholder="可自定义标识" style="width: 100%">
          <el-option v-for="i in CARD_ICONS" :key="i" :value="i" :label="i" />
        </el-select>
      </el-form-item>
      <el-form-item label="排序"><el-input-number v-model="form.sortNo" :min="0" :max="999" /></el-form-item>
      <el-form-item label="活动窗口">
        <div class="window-row">
          <el-date-picker v-model="form.startAt" type="datetime" value-format="YYYY-MM-DD HH:mm:ss" placeholder="开始（空=常驻）" />
          <span>~</span>
          <el-date-picker v-model="form.endAt" type="datetime" value-format="YYYY-MM-DD HH:mm:ss" placeholder="结束（空=不限）" />
        </div>
      </el-form-item>
      <el-form-item label="状态">
        <el-radio-group v-model="form.status">
          <el-radio :value="1">上架</el-radio>
          <el-radio :value="2">下架</el-radio>
        </el-radio-group>
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
import { CARD_ICONS, requireFields } from './constants'

const rows = ref([])
const total = ref(0)
const loading = ref(false)
const saving = ref(false)
const dialog = ref(false)
const query = reactive({ pageNum: 1, pageSize: 10, cardName: '', status: null })

const emptyForm = {
  id: null, cardName: '', promptText: '', icon: '', sortNo: 0, status: 1, startAt: null, endAt: null
}
const form = reactive({ ...emptyForm })

async function load() {
  loading.value = true
  try {
    const params = { pageNum: query.pageNum, pageSize: query.pageSize, status: query.status }
    if (query.cardName) params.cardName = query.cardName
    const page = await request.post('/agent/card/list', params)
    rows.value = page.list
    total.value = page.total
  } finally {
    loading.value = false
  }
}

function openAdd() {
  Object.assign(form, emptyForm, { id: null, status: 1 })
  dialog.value = true
}

function openEdit(row) {
  Object.assign(form, emptyForm, row)
  dialog.value = true
}

async function save() {
  const missing = requireFields(form, [['卡片名', form.cardName], ['提示词', form.promptText]])
  if (missing) return ElMessage.warning(missing)
  saving.value = true
  try {
    await request.post('/agent/card', form)
    ElMessage.success('保存成功')
    dialog.value = false
    load()
  } finally {
    saving.value = false
  }
}

const { isPending, toggle: toggleSwitch } = useOptimisticSwitch(
  (row, enabled) => request.post('/agent/card', { ...row, status: enabled ? 1 : 2 }),
  { on: 1, off: 2, onSuccess: () => ElMessage.success('已更新') }
)

function toggleStatus(row, enabled) {
  return toggleSwitch(row, enabled)
}

async function remove(row) {
  await request.delete(`/agent/card/${row.id}`)
  ElMessage.success('已删除')
  load()
}

onMounted(load)
</script>
