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
        <el-form-item>
          <el-button type="primary" @click="loadCarriages">查询车厢</el-button>
        </el-form-item>
        <el-form-item v-if="carriages.length" label="车厢">
          <el-select v-model="carriageKey" filterable style="width: 320px" placeholder="选择车厢" @change="onCarriageChange">
            <el-option
              v-for="c in carriages" :key="c.trainCode + '#' + c.carriageNo" :value="c.trainCode + '#' + c.carriageNo"
              :label="`${c.trainCode} · ${c.carriageNo} 车 · ${seatName(c.seatType)}（${c.seatCount} 座 / ${c.rowCount} 排）`"
            />
          </el-select>
        </el-form-item>
      </el-form>
    </el-card>

    <el-card class="table-card" shadow="never">
      <div v-if="seats.length" class="table-toolbar">
        <el-alert
          type="info"
          :closable="false"
          title="售票位图按站序排列，0 空闲、1 售出，与车次经停站顺序一致"
          style="flex: 1"
        />
      </div>
      <el-table :data="seats" v-loading="loading" max-height="640">
        <el-table-column prop="carriageNo" label="车厢" width="80" class-name="mono" />
        <el-table-column prop="rowNo" label="排" width="80" class-name="mono" />
        <el-table-column prop="colNo" label="列" width="80" class-name="mono" />
        <el-table-column label="席别" width="100" align="center">
          <template #default="{ row }">{{ seatName(row.seatType) }}</template>
        </el-table-column>
        <el-table-column prop="sell" label="售票位图" min-width="280">
          <template #default="{ row }">
            <span class="sell-map">{{ row.sell }}</span>
          </template>
        </el-table-column>
        <el-table-column label="全程状态" width="110" align="center">
          <template #default="{ row }">
            <el-tag :type="row.sell.includes('0') ? 'success' : 'info'">
              {{ row.sell.includes('0') ? '有余票' : '全程售出' }}
            </el-tag>
          </template>
        </el-table-column>
      </el-table>
      <el-empty v-if="searched && !seats.length" description="该车厢没有座位数据" />
    </el-card>
  </div>
</template>

<script setup>
import { onMounted, reactive, ref } from 'vue'
import { ElMessage } from 'element-plus'
import request from '../../utils/request'

const query = reactive({ rideDate: '', trainCode: '' })
const carriages = ref([])
const carriageKey = ref('')
const seats = ref([])
const loading = ref(false)
const searched = ref(false)

function seatName(t) { return { 1: '商务座', 2: '一等座', 3: '二等座', 4: '硬卧', 5: '硬座' }[t] || t }

async function loadCarriages() {
  if (!query.rideDate) {
    ElMessage.warning('请选择日期（车次可留空，留空即查该日全部车次）')
    return
  }
  carriages.value = await request.get('/daily-train/carriage/list', { params: { ...query } })
  seats.value = []
  carriageKey.value = ''
  if (!carriages.value.length) {
    searched.value = true
    ElMessage.warning('该日期没有每日车次数据（可能尚未生成）')
    return
  }
  carriageKey.value = carriages.value[0].trainCode + '#' + carriages.value[0].carriageNo
  await onCarriageChange()
}

async function onCarriageChange() {
  if (!carriageKey.value) return
  // 下拉 value 里同时带了车次号和车厢号（留空车次查询时同号车厢属于不同列车，必须成对传）
  const [trainCode, carriageNo] = carriageKey.value.split('#')
  if (!query.rideDate) {
    ElMessage.warning('请选择日期')
    return
  }
  loading.value = true
  try {
    seats.value = await request.get('/daily-train/seat/list', {
      params: { rideDate: query.rideDate, trainCode, carriageNo }
    })
    searched.value = true
  } finally {
    loading.value = false
  }
}

onMounted(() => {
  query.rideDate = new Date().toISOString().slice(0, 10)
})
</script>
