<template>
  <div class="page">
    <el-card class="table-card" shadow="never">
      <div class="table-toolbar">
        <span class="table-title">定时任务</span>
        <span class="spacer"></span>
        <el-button type="primary" plain @click="openAdd">
          <el-icon><Plus /></el-icon>新增任务
        </el-button>
        <el-button @click="load">
          <el-icon><Refresh /></el-icon>刷新
        </el-button>
      </div>

      <el-table :data="rows" v-loading="loading">
        <el-table-column label="任务" min-width="180">
          <template #default="{ row }">
            <div>{{ row.cnName }}</div>
            <div class="cell-sub mono">{{ row.name }}</div>
          </template>
        </el-table-column>
        <el-table-column prop="description" label="描述" min-width="160" show-overflow-tooltip />
        <el-table-column label="状态" width="90" align="center">
          <template #default="{ row }">
            <el-tag :type="stateType(row.state)">{{ stateText(row.state) }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column label="执行周期" min-width="200">
          <template #default="{ row }">
            <div class="mono">{{ row.cronExpression }}</div>
            <div class="cell-sub">{{ row.cronDesc }}</div>
          </template>
        </el-table-column>
        <el-table-column prop="preFireTime" label="上次执行" width="160" class-name="mono" />
        <el-table-column prop="nextFireTime" label="下次执行" width="160" class-name="mono" />
        <el-table-column label="操作" width="150" fixed="right" align="center">
          <template #default="{ row }">
            <el-popconfirm title="确认立即执行一次？（不影响原计划）" @confirm="operate('run', row)">
              <template #reference>
                <span class="row-action-wrap">
                  <el-tooltip content="立即执行" placement="top" :show-after="0">
                    <el-button class="row-action" text circle aria-label="立即执行">
                      <el-icon><VideoPlay /></el-icon>
                    </el-button>
                  </el-tooltip>
                </span>
              </template>
            </el-popconfirm>
            <el-tooltip
              v-if="row.state === 'NORMAL' || row.state === 'BLOCKED'"
              content="暂停"
              placement="top"
              :show-after="0"
            >
              <el-button class="row-action" text circle aria-label="暂停" @click="operate('pause', row)">
                <el-icon><VideoPause /></el-icon>
              </el-button>
            </el-tooltip>
            <el-tooltip
              v-if="row.state === 'PAUSED' || row.state === 'ERROR'"
              content="恢复"
              placement="top"
              :show-after="0"
            >
              <el-button class="row-action" text circle aria-label="恢复" @click="operate('resume', row)">
                <el-icon><RefreshRight /></el-icon>
              </el-button>
            </el-tooltip>
            <el-tooltip content="编辑" placement="top" :show-after="0">
              <el-button class="row-action" text circle aria-label="编辑" @click="openEdit(row)">
                <el-icon><Edit /></el-icon>
              </el-button>
            </el-tooltip>
            <el-popconfirm title="确认删除该任务？" @confirm="operate('delete', row)">
              <template #reference>
                <span class="row-action-wrap">
                  <el-tooltip content="删除" placement="top" :show-after="0">
                    <el-button class="row-action is-danger" text circle aria-label="删除">
                      <el-icon><Delete /></el-icon>
                    </el-button>
                  </el-tooltip>
                </span>
              </template>
            </el-popconfirm>
          </template>
        </el-table-column>
      </el-table>
    </el-card>
  </div>

  <el-dialog v-model="dialog" :title="form.name ? '编辑任务' : '新增任务'" width="560px" top="5vh" class="form-dialog" destroy-on-close>
    <el-form label-width="90px">
      <el-form-item label="任务">
        <el-select v-if="!form.name" v-model="form.name" placeholder="选择白名单任务" style="width: 100%" @change="onClassChange">
          <el-option
            v-for="c in classes" :key="c.name" :value="c.name"
            :label="`${c.desc}（${c.description}）`"
          />
        </el-select>
        <el-input v-else :model-value="form.name" disabled />
      </el-form-item>
      <el-form-item label="描述">
        <el-input v-model="form.description" placeholder="选填" />
      </el-form-item>
      <el-form-item label="定时方式">
        <el-segmented v-model="cronMode" :options="cronModes" />
      </el-form-item>

      <template v-if="cronMode === 'simple'">
        <el-form-item label="频率">
          <el-select v-model="simple.freq" style="width: 180px">
            <el-option label="每天定时" value="day" />
            <el-option label="每小时第 N 分钟" value="hour" />
            <el-option label="每隔 N 分钟" value="everyMinute" />
            <el-option label="每隔 N 秒" value="everySecond" />
          </el-select>
        </el-form-item>
        <el-form-item v-if="simple.freq === 'day'" label="时间">
          <el-time-picker v-model="simple.time" format="HH:mm" value-format="HH:mm" placeholder="选择时间" />
        </el-form-item>
        <el-form-item v-else-if="simple.freq === 'hour'" label="第几分钟">
          <el-input-number v-model="simple.minute" :min="0" :max="59" />
        </el-form-item>
        <el-form-item v-else label="间隔">
          <el-input-number v-model="simple.interval" :min="1" :max="59" />
          <span class="form-hint" style="margin: 0 0 0 8px">
            {{ simple.freq === 'everyMinute' ? '分钟' : '秒' }}
          </span>
        </el-form-item>
      </template>
      <el-form-item v-else label="cron">
        <el-input v-model="advancedCron" placeholder="如 0 30 1 * * ?" class="mono" />
      </el-form-item>

      <el-form-item v-if="currentCron" label="预览">
        <div v-if="preview" class="preview-ok">
          <el-tag type="success">可用</el-tag>
          <div>
            <div>{{ preview.desc }}</div>
            <div class="cell-sub">
              未来执行：<span v-for="(t, i) in preview.nextTimes" :key="i">{{ t }}{{ i < preview.nextTimes.length - 1 ? '、' : '' }}</span>
            </div>
          </div>
        </div>
        <div v-else-if="previewError" class="preview-err">{{ previewError }}</div>
      </el-form-item>
    </el-form>
    <template #footer>
      <el-button @click="dialog = false">取消</el-button>
      <el-button type="primary" :disabled="!!previewError || !currentCron" @click="save">确定</el-button>
    </template>
  </el-dialog>
</template>

<script setup>
import { computed, onMounted, reactive, ref, watch } from 'vue'
import { ElMessage } from 'element-plus'
// 定时任务在 train-task 服务（网关 /api/task/**），与管理台 /api/admin 分离
import request from '../../utils/taskRequest'

const cronModes = [
  { label: '常用', value: 'simple' },
  { label: '高级（cron）', value: 'advanced' }
]

const rows = ref([])
const classes = ref([])
const loading = ref(false)
const dialog = ref(false)
const form = reactive({ name: '', description: '', group: 'DEFAULT' })

const cronMode = ref('simple')
const simple = reactive({ freq: 'day', time: '01:30', minute: 0, interval: 5 })
const advancedCron = ref('')
const preview = ref(null)
const previewError = ref('')
let previewTimer = null

const currentCron = computed(() => {
  if (cronMode.value === 'advanced') return (advancedCron.value || '').trim()
  const s = simple
  if (s.freq === 'day' && s.time) {
    const [h, m] = s.time.split(':')
    return `0 ${Number(m)} ${Number(h)} * * ?`
  }
  if (s.freq === 'hour') return `0 ${s.minute} * * * ?`
  if (s.freq === 'everyMinute') return `0 0/${s.interval} * * * ?`
  if (s.freq === 'everySecond') return `0/${s.interval} * * * * ?`
  return ''
})

watch(currentCron, (cron) => {
  if (previewTimer) clearTimeout(previewTimer)
  preview.value = null
  previewError.value = ''
  if (!cron) return
  previewTimer = setTimeout(async () => {
    try {
      preview.value = await request.get('/job/cron-preview', { params: { cron } })
    } catch (e) {
      previewError.value = e.message || '表达式非法'
    }
  }, 500)
})

function stateText(s) {
  return { NORMAL: '运行中', PAUSED: '已暂停', ERROR: '错误', BLOCKED: '阻塞中', ACQUIRED: '已获取', WAITING: '等待中' }[s] || s
}
function stateType(s) {
  return { NORMAL: 'success', PAUSED: 'warning', ERROR: 'danger', BLOCKED: 'warning' }[s] || 'info'
}

/** 已有 cron 解析回常用模式（失败则切高级模式） */
function parseCronToSimple(cron) {
  let m
  if ((m = cron.match(/^0 (\d+) (\d+) \* \* \?$/))) {
    simple.freq = 'day'
    simple.time = `${String(m[2]).padStart(2, '0')}:${String(m[1]).padStart(2, '0')}`
    return
  }
  if ((m = cron.match(/^0 (\d+) \* \* \?$/))) {
    simple.freq = 'hour'
    simple.minute = Number(m[1])
    return
  }
  if ((m = cron.match(/^0 0\/(\d+) \* \* \?$/))) {
    simple.freq = 'everyMinute'
    simple.interval = Number(m[1])
    return
  }
  if ((m = cron.match(/^0\/(\d+) \* \* \* \?$/))) {
    simple.freq = 'everySecond'
    simple.interval = Number(m[1])
    return
  }
  cronMode.value = 'advanced'
  advancedCron.value = cron
}

async function load() {
  loading.value = true
  try {
    rows.value = await request.get('/job/query')
  } finally {
    loading.value = false
  }
}

async function loadClasses() {
  classes.value = await request.get('/job/class-list')
}

function openAdd() {
  form.name = ''
  form.description = ''
  cronMode.value = 'simple'
  simple.freq = 'day'
  simple.time = '01:30'
  advancedCron.value = ''
  preview.value = null
  previewError.value = ''
  dialog.value = true
}

/** 新增时选中任务 → 自动带出该任务的默认 cron（能解析则转常用模式，否则高级模式） */
function onClassChange(name) {
  const c = classes.value.find((x) => x.name === name)
  if (c?.defaultCron) parseCronToSimple(c.defaultCron)
}

function openEdit(row) {
  form.name = row.name
  form.group = row.group
  form.description = row.description || ''
  parseCronToSimple(row.cronExpression)
  dialog.value = true
}

async function save() {
  if (!form.name) return ElMessage.warning('请选择任务')
  const editing = rows.value.some((r) => r.name === form.name)
  await request.post(editing ? '/job/reschedule' : '/job/add', {
    name: form.name,
    group: form.group,
    description: form.description,
    cronExpression: currentCron.value
  })
  ElMessage.success(editing ? '已修改' : '已新增')
  dialog.value = false
  load()
}

async function operate(action, row) {
  await request.post(`/job/${action}`, { name: row.name, group: row.group })
  ElMessage.success('操作成功')
  load()
}

onMounted(() => {
  load()
  loadClasses()
})
</script>
