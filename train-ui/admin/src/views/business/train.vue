<template>
  <div class="page">
    <el-card class="search-card" shadow="never">
      <el-form inline>
        <el-form-item label="车次号">
          <el-input v-model="query.code" placeholder="G101" clearable style="width: 140px" />
        </el-form-item>
        <el-form-item>
          <el-button type="primary" @click="load">查询</el-button>
          <el-button type="primary" plain v-perm="'b:train:add'" @click="openAdd">
            <el-icon><Plus /></el-icon>新增车次
          </el-button>
        </el-form-item>
      </el-form>
    </el-card>

    <el-card class="table-card" shadow="never">
      <el-table :data="rows" v-loading="loading">
        <el-table-column prop="code" label="车次号" width="110" class-name="mono" />
        <el-table-column label="类型" width="100" align="center">
          <template #default="{ row }">{{ typeName(row.trainType) }}</template>
        </el-table-column>
        <el-table-column label="车厢" min-width="300">
          <template #default="{ row }">
            <el-tag v-for="c in row.carriages" :key="c.carriageNo" style="margin-right: 4px">
              {{ c.carriageNo }}号·{{ seatName(c.seatType) }}·{{ c.rowCount }}排
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column label="操作" width="150" align="center" fixed="right">
          <template #default="{ row }">
            <el-tooltip content="经停维护" placement="top" :show-after="0">
              <el-button
                class="row-action"
                text
                circle
                v-perm="'b:train:edit'"
                aria-label="经停维护"
                @click="openStops(row)"
              ><el-icon><Guide /></el-icon></el-button>
            </el-tooltip>
            <el-tooltip content="生成座位" placement="top" :show-after="0">
              <el-button
                class="row-action"
                text
                circle
                v-perm="'b:train:edit'"
                aria-label="生成座位"
                @click="genSeats(row)"
              ><el-icon><Suitcase /></el-icon></el-button>
            </el-tooltip>
            <el-tooltip content="编辑" placement="top" :show-after="0">
              <el-button
                class="row-action"
                text
                circle
                v-perm="'b:train:edit'"
                aria-label="编辑"
                @click="openEdit(row)"
              ><el-icon><Edit /></el-icon></el-button>
            </el-tooltip>
            <el-popconfirm title="删除车次及其经停/座位？" @confirm="remove(row)">
              <template #reference>
                <span class="row-action-wrap">
                  <el-tooltip content="删除" placement="top" :show-after="0">
                    <el-button
                      class="row-action is-danger"
                      text
                      circle
                      v-perm="'b:train:delete'"
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

  <!-- 车次 + 车厢模板 -->
  <el-dialog v-model="dialog" :title="form.id ? '编辑车次' : '新增车次'" width="700px" top="5vh" class="form-dialog" destroy-on-close>
    <el-form :model="form" label-width="80px">
      <el-form-item label="车次号"><el-input v-model="form.code" style="width: 160px" /></el-form-item>
      <el-form-item label="类型">
        <el-select v-model="form.trainType" style="width: 160px">
          <el-option :value="1" label="高铁" /><el-option :value="2" label="动车" />
          <el-option :value="3" label="普快" /><el-option :value="4" label="直达" />
        </el-select>
      </el-form-item>
      <el-form-item label="车厢模板">
        <el-table :data="form.carriages" size="small">
          <el-table-column label="车厢号" width="140">
            <template #default="{ row }"><el-input-number v-model="row.carriageNo" :min="1" controls-position="right" style="width: 100%" /></template>
          </el-table-column>
          <el-table-column label="席别" min-width="150" align="center">
            <template #default="{ row }">
              <el-select v-model="row.seatType" style="width: 100%">
                <el-option :value="1" label="商务座" /><el-option :value="2" label="一等座" />
                <el-option :value="3" label="二等座" /><el-option :value="5" label="硬座" />
              </el-select>
            </template>
          </el-table-column>
          <el-table-column label="排数" width="140">
            <template #default="{ row }"><el-input-number v-model="row.rowCount" :min="1" controls-position="right" style="width: 100%" /></template>
          </el-table-column>
          <el-table-column width="60" align="right">
            <template #default="{ $index }">
              <el-tooltip content="移除车厢" placement="top" :show-after="0">
                <el-button class="row-action is-danger" text circle aria-label="移除车厢" @click="form.carriages.splice($index, 1)">
                  <el-icon><Delete /></el-icon>
                </el-button>
              </el-tooltip>
            </template>
          </el-table-column>
        </el-table>
        <el-button plain style="margin-top: 8px" @click="form.carriages.push({ carriageNo: form.carriages.length + 1, seatType: 3, rowCount: 17 })">
          <el-icon><Plus /></el-icon>加车厢
        </el-button>
      </el-form-item>
    </el-form>
    <template #footer>
      <el-button @click="dialog = false">取消</el-button>
      <el-button type="primary" @click="save">保存</el-button>
    </template>
  </el-dialog>

  <!-- 经停维护 -->
  <el-dialog v-model="stopsDialog" :title="`经停维护 - ${current?.code || ''}`" width="760px" top="5vh" class="form-dialog" destroy-on-close>
    <el-table :data="stops" size="small">
      <el-table-column label="站" width="190">
        <template #default="{ row }">
          <el-select v-model="row.stationCode" filterable>
            <el-option v-for="s in stations" :key="s.code" :value="s.code" :label="s.name" />
          </el-select>
        </template>
      </el-table-column>
      <el-table-column label="到达" width="150">
        <template #default="{ row }"><el-time-picker v-model="row.arrivalTime" format="HH:mm" value-format="HH:mm:ss" /></template>
      </el-table-column>
      <el-table-column label="出发" width="150">
        <template #default="{ row }"><el-time-picker v-model="row.departureTime" format="HH:mm" value-format="HH:mm:ss" /></template>
      </el-table-column>
      <el-table-column label="距始发km" width="140">
        <template #default="{ row }"><el-input-number v-model="row.distanceKm" :min="0" controls-position="right" /></template>
      </el-table-column>
      <el-table-column width="60" align="right">
        <template #default="{ $index }">
          <el-tooltip content="移除经停" placement="top" :show-after="0">
            <el-button class="row-action is-danger" text circle aria-label="移除经停" @click="stops.splice($index, 1)">
              <el-icon><Delete /></el-icon>
            </el-button>
          </el-tooltip>
        </template>
      </el-table-column>
    </el-table>
    <el-button plain style="margin-top: 8px" @click="stops.push({ stationCode: '', arrivalTime: null, departureTime: null, distanceKm: 0 })">
      <el-icon><Plus /></el-icon>加经停
    </el-button>
    <template #footer>
      <el-button @click="stopsDialog = false">取消</el-button>
      <el-button type="primary" @click="saveStops">保存（按行序=站序）</el-button>
    </template>
  </el-dialog>
</template>

<script setup>
import { onMounted, reactive, ref } from 'vue'
import { ElMessage } from 'element-plus'
import request from '../../utils/request'

const typeName = (t) => ({ 1: '高铁', 2: '动车', 3: '普快', 4: '直达' }[t] || t)
const seatName = (t) => ({ 1: '商务座', 2: '一等座', 3: '二等座', 4: '硬卧', 5: '硬座' }[t] || t)

const rows = ref([])
const total = ref(0)
const loading = ref(false)
const dialog = ref(false)
const stopsDialog = ref(false)
const stations = ref([])
const current = ref(null)
const stops = ref([])
const query = reactive({ pageNum: 1, pageSize: 10, code: '' })
const emptyForm = { id: null, code: '', trainType: 1, carriages: [] }
const form = reactive({ ...emptyForm, carriages: [] })

async function load() {
  loading.value = true
  try {
    const page = await request.get('/train/page', { params: query })
    rows.value = page.list
    total.value = page.total
  } finally {
    loading.value = false
  }
}

function openAdd() {
  Object.assign(form, emptyForm, { carriages: [] })
  dialog.value = true
}

function openEdit(row) {
  request.get(`/train/${row.id}`).then((detail) => {
    Object.assign(form, emptyForm, detail, { carriages: detail.carriages || [] })
    dialog.value = true
  })
}

async function save() {
  if (form.id) await request.put('/train', form)
  else await request.post('/train', form)
  ElMessage.success('保存成功')
  dialog.value = false
  load()
}

async function openStops(row) {
  current.value = row
  if (!stations.value.length) {
    stations.value = await request.get('/station/list')
  }
  const list = await request.get('/train-station/list', { params: { trainId: row.id } })
  stops.value = list.map((s) => ({
    stationCode: s.stationCode, arrivalTime: s.arrivalTime,
    departureTime: s.departureTime, distanceKm: s.distanceKm
  }))
  stopsDialog.value = true
}

async function saveStops() {
  await request.post(`/train-station/replace`, stops.value, { params: { trainId: current.value.id } })
  ElMessage.success('已保存')
  stopsDialog.value = false
}

async function genSeats(row) {
  const count = await request.post(`/train/${row.id}/generate-seats`)
  ElMessage.success(`已生成 ${count} 个座位`)
}

async function remove(row) {
  await request.delete(`/train/${row.id}`)
  ElMessage.success('已删除')
  load()
}

onMounted(load)
</script>
