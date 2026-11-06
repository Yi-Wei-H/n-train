<template>
  <div class="page">
    <el-card class="search-card" shadow="never">
      <el-form inline>
        <el-form-item label="日期">
          <el-date-picker v-model="query.rideDate" type="date" value-format="YYYY-MM-DD" clearable />
        </el-form-item>
        <el-form-item label="车次">
          <el-input v-model="query.trainCode" clearable style="width: 130px" placeholder="留空查全部" />
        </el-form-item>
        <el-form-item label="席别">
          <el-select v-model="query.seatType" placeholder="全部" clearable style="width: 120px">
            <el-option v-for="(n, v) in seatTypes" :key="v" :value="Number(v)" :label="n" />
          </el-select>
        </el-form-item>
        <el-form-item>
          <el-button type="primary" @click="load">查询</el-button>
        </el-form-item>
      </el-form>
    </el-card>

    <el-card class="table-card" shadow="never">
      <el-table :data="filtered" v-loading="loading" max-height="640">
        <el-table-column prop="trainCode" label="车次" width="100" class-name="mono" />
        <el-table-column label="出发站" min-width="140">
          <template #default="{ row }">{{ stationName(stMap, row.fromStation) }}</template>
        </el-table-column>
        <el-table-column label="到达站" width="140">
          <template #default="{ row }">{{ stationName(stMap, row.toStation) }}</template>
        </el-table-column>
        <el-table-column prop="departureTime" label="出发" width="110" class-name="mono" />
        <el-table-column prop="arrivalTime" label="到达" width="110" class-name="mono" />
        <el-table-column label="席别" width="100" align="center">
          <template #default="{ row }">{{ seatName(row.seatType) }}</template>
        </el-table-column>
        <el-table-column label="余票" width="90" align="right">
          <template #default="{ row }">
            <span :class="row.remaining > 0 ? 'text-success num' : 'text-muted num'">
              {{ row.remaining > 0 ? row.remaining : '无' }}
            </span>
          </template>
        </el-table-column>
        <el-table-column label="票价（元）" width="120" align="right">
          <template #default="{ row }"><span class="money">¥{{ row.price }}</span></template>
        </el-table-column>
      </el-table>
      <el-empty v-if="searched && !filtered.length" description="没有余票数据（可能该车次尚未生成）" />
    </el-card>
  </div>
</template>

<script setup>
import { computed, onMounted, reactive, ref } from 'vue'
import { ElMessage } from 'element-plus'
import request from '../../utils/request'
import { loadStationMap, stationName } from '../../utils/stations'

const seatTypes = { 1: '商务座', 2: '一等座', 3: '二等座', 4: '硬卧', 5: '硬座' }
const query = reactive({ rideDate: '', trainCode: '', seatType: null })
const tickets = ref([])
const stMap = ref({})
const loading = ref(false)
const searched = ref(false)

const filtered = computed(() =>
  query.seatType ? tickets.value.filter((t) => t.seatType === query.seatType) : tickets.value)

function seatName(t) { return seatTypes[t] || t }

async function load() {
  if (!query.rideDate) {
    ElMessage.warning('请选择日期（车次可留空，留空即查该日全部车次）')
    return
  }
  loading.value = true
  try {
    const params = { rideDate: query.rideDate }
    if (query.trainCode) params.trainCode = query.trainCode
    tickets.value = await request.get('/daily-train/ticket/list', { params })
    searched.value = true
  } finally {
    loading.value = false
  }
}

onMounted(() => {
  query.rideDate = new Date().toISOString().slice(0, 10)
  loadStationMap().then((m) => { stMap.value = m })
})
</script>
