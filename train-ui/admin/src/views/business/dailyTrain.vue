<template>
  <div class="page">
    <el-card class="search-card" shadow="never">
      <el-form inline>
        <el-form-item label="日期">
          <el-date-picker v-model="query.rideDate" type="date" value-format="YYYY-MM-DD" clearable />
        </el-form-item>
        <el-form-item label="车次">
          <el-input v-model="query.trainCode" clearable style="width: 130px" />
        </el-form-item>
        <el-form-item>
          <el-button type="primary" @click="load">查询</el-button>
          <el-button type="primary" plain v-perm="'b:daily:gen'" @click="genDialog = true">
            <el-icon><Calendar /></el-icon>生成每日车次
          </el-button>
        </el-form-item>
      </el-form>
    </el-card>

    <el-card class="table-card" shadow="never">
      <el-table :data="rows" v-loading="loading">
        <el-table-column prop="rideDate" label="日期" width="120" class-name="mono" />
        <el-table-column prop="trainCode" label="车次" width="100" class-name="mono" />
        <el-table-column label="区间" min-width="220">
          <template #default="{ row }">
            {{ stationName(stMap, row.departureStation) }} → {{ stationName(stMap, row.arrivalStation) }}
          </template>
        </el-table-column>
        <el-table-column prop="departureTime" label="发车" width="100" class-name="mono" />
        <el-table-column prop="arrivalTime" label="到达" width="100" class-name="mono" />
        <el-table-column label="状态" width="100" align="center">
          <template #default="{ row }">
            <el-tag :type="['info', 'warning', 'success'][row.status]">
              {{ ['未发车', '运行中', '已到达'][row.status] }}
            </el-tag>
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

  <el-dialog v-model="genDialog" title="生成每日车次" width="420px">
    <el-form label-width="60px">
      <el-form-item label="日期">
        <el-date-picker v-model="genDate" type="date" value-format="YYYY-MM-DD" style="width: 100%" />
      </el-form-item>
      <el-form-item label="车次">
        <el-input v-model="genTrainCode" placeholder="留空 = 该日全部车次" clearable />
      </el-form-item>
    </el-form>
    <template #footer>
      <el-button @click="genDialog = false">取消</el-button>
      <el-button type="danger" class="is-solid" :loading="regenLoading" @click="regenerate">重新生成</el-button>
      <el-button type="primary" :loading="genLoading" @click="generate">生成（幂等）</el-button>
    </template>
  </el-dialog>
</template>

<script setup>
import { onMounted, reactive, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import request from '../../utils/request'
import { loadStationMap, stationName } from '../../utils/stations'

const rows = ref([])
const stMap = ref({})
const total = ref(0)
const loading = ref(false)
const genDialog = ref(false)
const genLoading = ref(false)
const regenLoading = ref(false)
const genDate = ref('')
const genTrainCode = ref('')
const query = reactive({ pageNum: 1, pageSize: 10, rideDate: '', trainCode: '' })

async function load() {
  loading.value = true
  try {
    const params = { ...query }
    if (!params.rideDate) delete params.rideDate
    if (!params.trainCode) delete params.trainCode
    const page = await request.get('/daily-train/page', { params })
    rows.value = page.list
    total.value = page.total
  } finally {
    loading.value = false
  }
}

async function generate() {
  if (!genDate.value) {
    ElMessage.warning('选择日期')
    return
  }
  genLoading.value = true
  try {
    const params = { rideDate: genDate.value }
    if (genTrainCode.value) params.trainCode = genTrainCode.value.trim()
    await request.post('/daily-train/generate', null, { params })
    ElMessage.success('生成任务已提交，完成后请刷新列表查看')
    genDialog.value = false
    load()
  } finally {
    genLoading.value = false
  }
}

async function regenerate() {
  if (!genDate.value) {
    ElMessage.warning('选择日期')
    return
  }
  if (!genTrainCode.value || !genTrainCode.value.trim()) {
    ElMessage.warning('重新生成必须指定车次')
    return
  }
  const code = genTrainCode.value.trim()
  try {
    await ElMessageBox.confirm(
      `重新生成将删除 ${code} 在 ${genDate.value} 的全部每日车次数据后重建，旧车次已购买的车票无法失效。是否继续？`,
      '二次确认',
      { type: 'warning', confirmButtonText: '确认重新生成', cancelButtonText: '取消' }
    )
  } catch {
    return
  }
  regenLoading.value = true
  try {
    await request.post('/daily-train/regenerate', null,
      { params: { rideDate: genDate.value, trainCode: code } })
    ElMessage.success('重新生成任务已提交，完成后请刷新列表查看')
    genDialog.value = false
    load()
  } finally {
    regenLoading.value = false
  }
}

onMounted(() => {
  loadStationMap().then((m) => { stMap.value = m })
  load()
})
</script>
