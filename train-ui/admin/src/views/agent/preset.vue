<template>
  <div class="page">
    <el-card class="search-card" shadow="never">
      <el-form inline>
        <el-form-item label="模型类型">
          <el-select v-model="query.modelType" placeholder="全部" clearable style="width: 140px">
            <el-option v-for="t in MODEL_TYPES" :key="t.value" :value="t.value" :label="t.label" />
          </el-select>
        </el-form-item>
        <el-form-item>
          <el-button type="primary" @click="load">查询</el-button>
          <el-button type="primary" plain v-perm="'a:preset:save'" @click="openAdd">
            <el-icon><Plus /></el-icon>新增
          </el-button>
        </el-form-item>
        <span class="search-note">
          <PageNote>
            <p>预设仅作创建模型的模板：一键接入即全量快照拷贝，创建后修改预设不影响已建模型配置。</p>
          </PageNote>
        </span>
      </el-form>
    </el-card>

    <el-card class="table-card" shadow="never">
      <el-table :data="rows" v-loading="loading">
        <el-table-column prop="vendor" label="厂商" width="100" />
        <el-table-column prop="presetName" label="预设名" min-width="130" show-overflow-tooltip />
        <el-table-column label="类型" width="80" align="center">
          <template #default="{ row }">
            <el-tag :type="row.modelType === 2 ? 'primary' : 'info'">{{ labelOf(MODEL_TYPES, row.modelType) }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="baseUrl" label="base_url" min-width="180" show-overflow-tooltip class-name="mono" />
        <el-table-column label="上游格式" width="130">
          <template #default="{ row }">{{ labelOf(API_FORMATS, row.apiFormat) }}</template>
        </el-table-column>
        <el-table-column label="认证方式" width="100">
          <template #default="{ row }">{{ labelOf(AUTH_TYPES, row.authType) }}</template>
        </el-table-column>
        <el-table-column prop="apiModelId" label="api_model_id" min-width="150" show-overflow-tooltip class-name="mono" />
        <el-table-column prop="defaultParams" label="默认参数" min-width="120" show-overflow-tooltip class-name="mono" />
        <el-table-column label="状态" width="70" align="center">
          <template #default="{ row }">
            <el-switch
              :model-value="row.status === 1"
              size="small"
              class="is-neutral"
              v-perm="'a:preset:save'"
              :disabled="isPending(row)"
              :aria-busy="isPending(row) ? 'true' : 'false'"
              @change="(v) => toggleStatus(row, v)"
            />
          </template>
        </el-table-column>
        <el-table-column label="操作" width="90" fixed="right" align="center">
          <template #default="{ row }">
            <el-tooltip content="编辑" placement="top" :show-after="0">
              <el-button
                class="row-action"
                text
                circle
                v-perm="'a:preset:save'"
                aria-label="编辑"
                @click="openEdit(row)"
              ><el-icon><Edit /></el-icon></el-button>
            </el-tooltip>
            <el-popconfirm title="确认删除该预设？" @confirm="remove(row)">
              <template #reference>
                <span class="row-action-wrap">
                  <el-tooltip content="删除" placement="top" :show-after="0">
                    <el-button
                      class="row-action is-danger"
                      text
                      circle
                      v-perm="'a:preset:delete'"
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

  <el-dialog v-model="dialog" :title="form.id ? '编辑预设' : '新增预设'" width="640px" top="5vh" class="form-dialog" destroy-on-close>
    <el-form :model="form" label-width="110px">
      <el-row :gutter="12">
        <el-col :span="12">
          <el-form-item label="预设名" required><el-input v-model="form.presetName" placeholder="如 DeepSeek-Chat" /></el-form-item>
        </el-col>
        <el-col :span="12">
          <el-form-item label="厂商" required><el-input v-model="form.vendor" placeholder="deepseek / zhipu …" /></el-form-item>
        </el-col>
      </el-row>
      <el-form-item label="模型类型" required>
        <el-radio-group v-model="form.modelType">
          <el-radio v-for="t in MODEL_TYPES" :key="t.value" :value="t.value">{{ t.label }}</el-radio>
        </el-radio-group>
      </el-form-item>
      <el-form-item label="base_url" required><el-input v-model="form.baseUrl" placeholder="https://…" class="mono" /></el-form-item>
      <el-form-item label="上游格式" required>
        <el-select v-model="form.apiFormat" style="width: 100%" @change="onFormatChange">
          <el-option v-for="f in API_FORMATS" :key="f.value" :value="f.value" :label="f.label" />
        </el-select>
      </el-form-item>
      <el-form-item label="认证方式" required>
        <el-select v-model="form.authType" :disabled="form.apiFormat === 1" style="width: 100%" @change="onAuthChange">
          <el-option v-for="a in AUTH_TYPES" :key="a.value" :value="a.value" :label="a.label" />
        </el-select>
      </el-form-item>
      <el-form-item v-if="form.authType !== 1" label="认证字段名" required>
        <el-input v-model="form.authKeyName" placeholder="如 x-api-key / api_key" class="mono" />
      </el-form-item>
      <el-form-item label="api_model_id" required><el-input v-model="form.apiModelId" class="mono" /></el-form-item>
      <el-form-item label="默认参数">
        <el-input v-model="form.defaultParams" type="textarea" :rows="11" :placeholder="PARAMS_PLACEHOLDER" class="mono" />
        <div class="form-hint">
          选填。thinkingType 仅 Anthropic 格式生效（enabled 需同配 thinkingBudgetTokens）；
          extraBody 仅 OpenAI 系生效，按厂商文档透传非标键（如智谱 thinking 开关）。
        </div>
      </el-form-item>
      <el-form-item label="说明"><el-input v-model="form.description" /></el-form-item>
      <el-form-item label="状态">
        <el-radio-group v-if="form.id" v-model="form.status">
          <el-radio :value="1">上架</el-radio>
          <el-radio :value="2">下架</el-radio>
        </el-radio-group>
        <span v-else class="form-hint">新增固定为上架，保存后可在列表下架</span>
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
import { API_FORMATS, AUTH_TYPES, MODEL_TYPES, PARAMS_PLACEHOLDER, labelOf, requireFields } from './constants'

const rows = ref([])
const total = ref(0)
const loading = ref(false)
const saving = ref(false)
const dialog = ref(false)
const query = reactive({ pageNum: 1, pageSize: 10, modelType: null })

const emptyForm = {
  id: null, vendor: '', presetName: '', modelType: 1, baseUrl: '', apiFormat: 1,
  authType: 1, authKeyName: '', apiModelId: '', defaultParams: '',
  description: '', status: 1
}
const form = reactive({ ...emptyForm })

async function load() {
  loading.value = true
  try {
    const page = await request.post('/agent/preset/list', query)
    rows.value = page.list
    total.value = page.total
  } finally {
    loading.value = false
  }
}

function openAdd() {
  Object.assign(form, emptyForm, { id: null })
  dialog.value = true
}

function openEdit(row) {
  Object.assign(form, emptyForm, row)
  dialog.value = true
}

function onFormatChange() {
  if (form.apiFormat === 1) {
    form.authType = 1
    form.authKeyName = ''
  }
}

function onAuthChange() {
  if (form.authType === 1) form.authKeyName = ''
}

function validParams(text) {
  if (!text || !text.trim()) return true
  try {
    JSON.parse(text)
    return true
  } catch (e) {
    return false
  }
}

async function save() {
  const missing = requireFields(form, [
    ['厂商', form.vendor], ['预设名', form.presetName], ['base_url', form.baseUrl], ['api_model_id', form.apiModelId]
  ])
  if (missing) return ElMessage.warning(missing)
  if (form.authType !== 1 && !form.authKeyName) return ElMessage.warning('请填写认证字段名')
  if (!validParams(form.defaultParams)) return ElMessage.warning('默认参数必须是合法 JSON')
  saving.value = true
  try {
    await request.post('/agent/preset', form)
    ElMessage.success('保存成功')
    dialog.value = false
    load()
  } finally {
    saving.value = false
  }
}

const { isPending, toggle: toggleSwitch } = useOptimisticSwitch(
  (row) => request.put(`/agent/preset/${row.id}/status`),
  { on: 1, off: 2, onSuccess: () => ElMessage.success('已更新') }
)

function toggleStatus(row, enabled) {
  return toggleSwitch(row, enabled)
}

async function remove(row) {
  await request.delete(`/agent/preset/${row.id}`)
  ElMessage.success('已删除')
  load()
}

onMounted(load)
</script>
