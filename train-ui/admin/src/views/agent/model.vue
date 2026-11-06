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
          <el-button type="primary" plain v-perm="'a:model:save'" @click="openAdd">
            <el-icon><Plus /></el-icon>新增
          </el-button>
        </el-form-item>
      </el-form>
    </el-card>

    <el-card class="table-card" shadow="never">
      <el-table :data="rows" v-loading="loading">
        <el-table-column prop="modelName" label="模型名" min-width="130" show-overflow-tooltip />
        <el-table-column label="类型" width="80" align="center">
          <template #default="{ row }">
            <el-tag :type="row.modelType === 2 ? 'primary' : 'info'">{{ labelOf(MODEL_TYPES, row.modelType) }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="provider" label="厂商" width="100" />
        <el-table-column label="上游格式" width="140">
          <template #default="{ row }">{{ labelOf(API_FORMATS, row.apiFormat) }}</template>
        </el-table-column>
        <el-table-column label="认证方式" width="110">
          <template #default="{ row }">{{ labelOf(AUTH_TYPES, row.authType) }}</template>
        </el-table-column>
        <el-table-column prop="apiModelId" label="api_model_id" min-width="160" show-overflow-tooltip class-name="mono" />
        <el-table-column label="API Key" width="110" class-name="mono">
          <template #default="{ row }">{{ row.apiKeyMasked || '—' }}</template>
        </el-table-column>
        <el-table-column label="状态" width="70" align="center">
          <template #default="{ row }">
            <el-switch
              :model-value="row.status === 1"
              size="small"
              class="is-neutral"
              v-perm="'a:model:save'"
              :disabled="isPending(row)"
              :aria-busy="isPending(row) ? 'true' : 'false'"
              @change="(v) => toggleStatus(row, v)"
            />
          </template>
        </el-table-column>
        <el-table-column prop="remark" label="备注" min-width="130" show-overflow-tooltip />
        <el-table-column label="操作" width="120" fixed="right" align="center">
          <template #default="{ row }">
            <el-tooltip content="编辑" placement="top" :show-after="0">
              <el-button
                class="row-action"
                text
                circle
                v-perm="'a:model:save'"
                aria-label="编辑"
                @click="openEdit(row)"
              ><el-icon><Edit /></el-icon></el-button>
            </el-tooltip>
            <el-tooltip content="测试连通性" placement="top" :show-after="0">
              <el-button
                class="row-action"
                text
                circle
                v-perm="'a:model:test'"
                aria-label="测试连通性"
                :loading="testingId === row.id"
                @click="runTest(row)"
              ><el-icon><Connection /></el-icon></el-button>
            </el-tooltip>
            <el-popconfirm title="确认删除该模型配置？" @confirm="remove(row)">
              <template #reference>
                <span class="row-action-wrap">
                  <el-tooltip content="删除" placement="top" :show-after="0">
                    <el-button
                      class="row-action is-danger"
                      text
                      circle
                      v-perm="'a:model:delete'"
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

  <!-- 新增/编辑模型：预设一键快照 → 表单可改，保存后与预设互不影响 -->
  <el-dialog v-model="dialog" :title="form.id ? '编辑模型配置' : '新增模型配置'" width="700px" top="5vh" class="form-dialog" destroy-on-close>
    <el-form :model="form" label-width="120px">
      <el-form-item v-if="!form.id && canReadPreset" label="从预设接入">
        <el-select v-model="presetKey" placeholder="— 不使用预设，自定义 —" clearable style="width: 100%" @change="applyPreset">
          <el-option
            v-for="p in presets"
            :key="p.id"
            :value="p.id"
            :label="`${p.presetName}（${p.vendor} · ${labelOf(MODEL_TYPES, p.modelType)}）`"
          />
        </el-select>
        <div class="form-hint">选中即把 baseURL / 上游格式 / 认证方式 / api_model_id / 默认参数全量快照填入本表单。</div>
      </el-form-item>
      <el-row :gutter="12">
        <el-col :span="12">
          <el-form-item label="模型名" required><el-input v-model="form.modelName" placeholder="如 DeepSeek-Chat" /></el-form-item>
        </el-col>
        <el-col :span="12">
          <el-form-item label="厂商标识" required><el-input v-model="form.provider" placeholder="deepseek / zhipu …" /></el-form-item>
        </el-col>
      </el-row>
      <el-form-item label="模型类型" required>
        <el-radio-group v-model="form.modelType" :disabled="!!form.id">
          <el-radio v-for="t in MODEL_TYPES" :key="t.value" :value="t.value">{{ t.label }}</el-radio>
        </el-radio-group>
        <span v-if="form.id" class="form-hint">编辑时不可修改（文字/向量不可互转）</span>
      </el-form-item>
      <el-form-item label="base_url" required><el-input v-model="form.baseUrl" placeholder="https://…" class="mono" /></el-form-item>
      <el-form-item label="上游格式" required>
        <el-select v-model="form.apiFormat" style="width: 100%" @change="onFormatChange">
          <el-option v-for="f in API_FORMATS" :key="f.value" :value="f.value" :label="f.label" />
        </el-select>
        <div v-if="form.modelType === 2" class="form-hint">向量模型仅支持 OpenAI 兼容格式。</div>
      </el-form-item>
      <el-form-item label="认证方式" required>
        <el-select v-model="form.authType" :disabled="form.apiFormat === 1" style="width: 100%" @change="onAuthChange">
          <el-option v-for="a in AUTH_TYPES" :key="a.value" :value="a.value" :label="a.label" />
        </el-select>
        <div v-if="form.apiFormat === 1" class="form-hint">OpenAI 兼容格式仅支持 Bearer 认证。</div>
      </el-form-item>
      <el-form-item v-if="form.authType !== 1" label="认证字段名" required>
        <el-input v-model="form.authKeyName" placeholder="如 x-api-key / api_key" class="mono" />
      </el-form-item>
      <el-form-item label="api_model_id" required>
        <div style="display: flex; gap: 8px; width: 100%">
          <el-input v-model="form.apiModelId" placeholder="厂商侧模型 ID" class="mono" />
          <el-button :loading="remoteLoading" v-perm="'a:model:save'" @click="loadRemote">拉取上游模型</el-button>
        </div>
      </el-form-item>
      <el-form-item v-if="remoteOpen" label=" " label-width="120px">
        <div class="remote-panel">
          <div class="remote-head">
            <el-input v-model="remoteKeyword" size="small" placeholder="过滤 id / 属主 / 展示名" style="width: 220px" />
            <el-tag v-if="remoteTruncated" type="warning">结果过多已截断</el-tag>
            <el-button size="small" text @click="remoteOpen = false">收起</el-button>
          </div>
          <div class="remote-list">
            <div v-for="m in filteredRemoteModels" :key="m.id" class="remote-item" @click="pickRemote(m.id)">
              <code class="mono">{{ m.id }}</code>
              <span class="remote-meta">{{ m.ownedBy || m.displayName || '' }}</span>
            </div>
            <el-empty v-if="!filteredRemoteModels.length" :image-size="48" description="无匹配模型" />
          </div>
        </div>
      </el-form-item>
      <el-form-item label="API Key" :required="!form.id">
        <el-input
          v-model="form.apiKey"
          type="password"
          show-password
          class="mono"
          :placeholder="form.id ? `留空保持不变（现值 ${form.currentKey || '—'}）` : '必填，16~200 字符'"
        />
      </el-form-item>
      <el-form-item label="默认参数">
        <el-input v-model="form.params" type="textarea" :rows="11" :placeholder="PARAMS_PLACEHOLDER" class="mono" />
        <div class="form-hint">
          选填。thinkingType 仅 Anthropic 格式生效；extraBody 仅 OpenAI 系生效（厂商非标键透传，如 thinking 开关）。
        </div>
      </el-form-item>
      <el-form-item label="备注"><el-input v-model="form.remark" /></el-form-item>
      <el-form-item label="状态">
        <el-radio-group v-if="form.id" v-model="form.status">
          <el-radio :value="2">停用</el-radio>
          <el-radio :value="1">启用</el-radio>
        </el-radio-group>
        <span v-else class="form-hint">新增固定为停用，保存后在列表启用</span>
        <div class="form-hint">启用会互斥停用同类型当前启用模型（向量模型同一时间仅一个启用）。</div>
      </el-form-item>
    </el-form>
    <template #footer>
      <el-button @click="dialog = false">取消</el-button>
      <el-button type="primary" :loading="saving" @click="save">保存</el-button>
    </template>
  </el-dialog>

  <!-- 连通性测试结果 -->
  <el-dialog v-model="testDialog" title="连通性测试" width="440px">
    <template v-if="testResult">
      <el-tag :type="testResult.success ? 'success' : 'danger'">
        {{ testResult.success ? '连通正常' : '连通失败' }}
      </el-tag>
      <el-descriptions :column="1" border style="margin-top: 12px">
        <el-descriptions-item label="耗时">{{ testResult.latencyMs }} ms</el-descriptions-item>
        <el-descriptions-item v-if="testResult.dimension" label="向量维度">{{ testResult.dimension }}</el-descriptions-item>
        <el-descriptions-item v-if="testResult.errMsg" label="失败原因">{{ testResult.errMsg }}</el-descriptions-item>
      </el-descriptions>
    </template>
  </el-dialog>
</template>

<script setup>
import { computed, onMounted, reactive, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import request from '../../utils/request'
import { useOptimisticSwitch } from '../../composables/useOptimisticSwitch'
import {
  API_FORMATS, AUTH_TYPES, MODEL_TYPES, PARAMS_PLACEHOLDER, SLOW_TIMEOUT, labelOf, requireFields
} from './constants'

const rows = ref([])
const total = ref(0)
const loading = ref(false)
const saving = ref(false)
const dialog = ref(false)
const presets = ref([])
const presetKey = ref(null)

const query = reactive({ pageNum: 1, pageSize: 10, modelType: null })

const emptyForm = {
  id: null, modelName: '', modelType: 1, provider: '', baseUrl: '', apiFormat: 1, authType: 1,
  authKeyName: '', apiModelId: '', apiKey: '', params: '', remark: '', status: 2, currentKey: ''
}
const form = reactive({ ...emptyForm })

const remoteOpen = ref(false)
const remoteLoading = ref(false)
const remoteKeyword = ref('')
const remoteModels = ref([])
const remoteTruncated = ref(false)

/** 关键字对已拉取结果即时过滤（服务端过滤只在点击拉取时生效，面板内输入需前端过滤） */
const filteredRemoteModels = computed(() => {
  const kw = remoteKeyword.value.trim().toLowerCase()
  if (!kw) return remoteModels.value
  return remoteModels.value.filter((m) =>
    [m.id, m.ownedBy, m.displayName].some((v) => v && String(v).toLowerCase().includes(kw)))
})

const testingId = ref(null)
const testDialog = ref(false)
const testResult = ref(null)

async function load() {
  loading.value = true
  try {
    const page = await request.post('/agent/model/list', query)
    rows.value = page.list
    total.value = page.total
  } finally {
    loading.value = false
  }
}

/** 是否可见预设下拉：预设查询是独立权限，只授模型权限的角色不提示无权限错误 */
const canReadPreset = computed(() => {
  const perms = JSON.parse(localStorage.getItem('ntrain-admin-perms') || '[]')
  return perms.includes('*') || perms.includes('a:preset:list')
})

/** 预设下拉：首次打开新增弹窗时加载（预设量级小，一次取满） */
async function loadPresets() {
  if (presets.value.length) return
  try {
    const page = await request.post('/agent/preset/list', { pageNum: 1, pageSize: 100 })
    presets.value = page.list || []
  } catch (e) {
    presets.value = []
  }
}

function openAdd() {
  Object.assign(form, emptyForm, { id: null, status: 2 })
  presetKey.value = null
  remoteOpen.value = false
  dialog.value = true
  if (canReadPreset.value) loadPresets()
}

function openEdit(row) {
  Object.assign(form, emptyForm, {
    id: row.id, modelName: row.modelName, modelType: row.modelType, provider: row.provider,
    baseUrl: row.baseUrl, apiFormat: row.apiFormat, authType: row.authType,
    authKeyName: row.authKeyName || '', apiModelId: row.apiModelId, apiKey: '',
    params: row.params || '', remark: row.remark || '', status: row.status,
    currentKey: row.apiKeyMasked || ''
  })
  presetKey.value = null
  remoteOpen.value = false
  dialog.value = true
}

/** 预设是模板：选中即全量快照拷贝，之后改预设不影响本表单 */
function applyPreset(id) {
  const p = presets.value.find((x) => x.id === id)
  if (!p) return
  Object.assign(form, {
    modelName: p.presetName, modelType: p.modelType, provider: p.vendor, baseUrl: p.baseUrl,
    apiFormat: p.apiFormat, authType: p.authType, authKeyName: p.authKeyName || '',
    apiModelId: p.apiModelId, params: p.defaultParams || ''
  })
}

/** OpenAI 兼容格式仅支持 Bearer：切格式时同步约束认证方式 */
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
    ['模型名', form.modelName], ['厂商标识', form.provider], ['base_url', form.baseUrl],
    ['api_model_id', form.apiModelId]
  ])
  if (missing) return ElMessage.warning(missing)
  if (form.authType !== 1 && !form.authKeyName) return ElMessage.warning('请填写认证字段名')
  if (!form.id && (!form.apiKey || form.apiKey.length < 16)) return ElMessage.warning('API Key 必填且不少于 16 字符')
  if (!validParams(form.params)) return ElMessage.warning('默认参数必须是合法 JSON')

  const body = { ...form }
  delete body.currentKey
  // 编辑留空时省略 apiKey，服务端即沿用原值（传空串会被当成清空）
  if (!body.apiKey) delete body.apiKey
  saving.value = true
  try {
    await request.post('/agent/model', body)
    ElMessage.success('保存成功')
    dialog.value = false
    load()
  } finally {
    saving.value = false
  }
}

const { isPending, toggle: toggleSwitch } = useOptimisticSwitch(
  (row) => request.put(`/agent/model/${row.id}/status`),
  {
    on: 1,
    off: 2,
    onSuccess: (row, enabled) => {
      // 后端启用同类型模型会互斥停用其它模型，前端同步联动，避免整表重载
      if (enabled) {
        rows.value.forEach((item) => {
          if (item.id !== row.id && item.modelType === row.modelType) item.status = 2
        })
      }
      ElMessage.success('已更新')
    }
  }
)

async function toggleStatus(row, enabled) {
  if (enabled) {
    try {
      await ElMessageBox.confirm(
        `启用后将自动停用当前启用的同类型模型（${labelOf(MODEL_TYPES, row.modelType)}），确认启用？`,
        '启用模型', { type: 'warning' }
      )
    } catch (e) {
      return
    }
  }
  await toggleSwitch(row, enabled)
}

async function runTest(row) {
  testingId.value = row.id
  try {
    testResult.value = await request.post(`/agent/model/${row.id}/test`, null, { timeout: SLOW_TIMEOUT })
    testDialog.value = true
  } finally {
    testingId.value = null
  }
}

async function loadRemote() {
  if (!form.baseUrl) {
    return ElMessage.warning('拉取上游模型需要 base_url')
  }
  // key 留空：编辑已保存的模型时由后端取库存 key；新增未保存必须填写
  if (!form.apiKey && !form.id) {
    return ElMessage.warning('请先填写 API Key（保存后可留空直接拉取）')
  }
  remoteLoading.value = true
  try {
    const body = {
      baseUrl: form.baseUrl,
      apiFormat: form.apiFormat,
      authType: form.authType,
      authKeyName: form.authKeyName
    }
    if (form.apiKey) {
      body.apiKey = form.apiKey
    } else {
      body.modelId = form.id
    }
    const result = await request.post('/agent/model/list-remote', body, { timeout: SLOW_TIMEOUT })
    if (!result.success) {
      remoteOpen.value = false
      return ElMessage.error(result.errMsg || '拉取失败')
    }
    remoteModels.value = result.models || []
    remoteTruncated.value = !!result.truncated
    remoteOpen.value = true
  } finally {
    remoteLoading.value = false
  }
}

function pickRemote(id) {
  form.apiModelId = id
  remoteOpen.value = false
}

async function remove(row) {
  await request.delete(`/agent/model/${row.id}`)
  ElMessage.success('已删除')
  load()
}

onMounted(load)
</script>
