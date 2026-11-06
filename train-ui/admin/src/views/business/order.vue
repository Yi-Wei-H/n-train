<template>
  <div class="page">
    <el-card class="search-card" shadow="never">
      <el-form inline>
        <el-form-item label="订单状态">
          <el-select v-model="query.orderStatus" placeholder="全部" clearable style="width: 140px">
            <el-option v-for="(name, code) in STATUS_NAME" :key="code" :value="Number(code)" :label="name" />
          </el-select>
        </el-form-item>
        <el-form-item label="车次">
          <el-input v-model="query.trainCode" clearable style="width: 120px" />
        </el-form-item>
        <el-form-item>
          <el-button type="primary" @click="load">查询</el-button>
        </el-form-item>
      </el-form>
    </el-card>

    <el-card class="table-card" shadow="never">
      <el-table :data="rows" v-loading="loading">
        <el-table-column prop="orderNo" label="订单号" min-width="170" show-overflow-tooltip class-name="mono" />
        <el-table-column prop="memberId" label="会员 ID" width="150" show-overflow-tooltip class-name="mono" />
        <el-table-column prop="rideDate" label="乘车日" width="100" class-name="mono" />
        <el-table-column prop="trainCode" label="车次" width="80" class-name="mono" />
        <el-table-column label="区间" min-width="140">
          <template #default="{ row }">{{ row.fromName || row.fromStation }} → {{ row.toName || row.toStation }}</template>
        </el-table-column>
        <el-table-column label="金额" width="90" align="right">
          <template #default="{ row }"><span class="money">¥{{ row.totalAmount }}</span></template>
        </el-table-column>
        <el-table-column label="状态" width="90" align="center">
          <template #default="{ row }">
            <el-tag :type="STATUS_TYPE[row.orderStatus]">{{ STATUS_NAME[row.orderStatus] }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="createdAt" label="下单时间" width="170" class-name="mono" />
        <el-table-column label="操作" width="100" align="center" fixed="right">
          <template #default="{ row }">
            <el-tooltip content="订单明细" placement="top" :show-after="0">
              <el-button class="row-action" text circle aria-label="订单明细" @click="openDetail(row)">
                <el-icon><View /></el-icon>
              </el-button>
            </el-tooltip>
            <el-tooltip v-if="row.orderStatus === 1" content="标记发车" placement="top" :show-after="0">
              <el-button
                class="row-action"
                text
                circle
                v-perm="'b:order:travel'"
                aria-label="标记发车"
                @click="traveled(row)"
              ><el-icon><VideoPlay /></el-icon></el-button>
            </el-tooltip>
            <el-tooltip v-if="row.orderStatus === 2" content="完成订单" placement="top" :show-after="0">
              <el-button
                class="row-action"
                text
                circle
                v-perm="'b:order:travel'"
                aria-label="完成订单"
                @click="complete(row)"
              ><el-icon><CircleCheck /></el-icon></el-button>
            </el-tooltip>
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

  <el-drawer v-model="detailDrawer" title="订单明细" size="620px">
    <el-descriptions v-if="detail" :column="2" border size="small">
      <el-descriptions-item label="订单号">{{ detail.orderNo }}</el-descriptions-item>
      <el-descriptions-item label="金额">¥{{ detail.totalAmount }}</el-descriptions-item>
      <el-descriptions-item label="日期">{{ detail.rideDate }}</el-descriptions-item>
      <el-descriptions-item label="车次">{{ detail.trainCode }}</el-descriptions-item>
      <el-descriptions-item label="发车">{{ detail.departureTime }}</el-descriptions-item>
      <el-descriptions-item label="到达">{{ detail.arrivalTime }}</el-descriptions-item>
    </el-descriptions>
    <el-table v-if="detail" :data="detail.items || []" size="small" style="margin-top: 12px">
      <el-table-column prop="passengerName" label="乘车人" width="90" />
      <el-table-column label="席别/座位" width="130">
        <template #default="{ row }">{{ row.seatType }}席 {{ row.carriageNo || '-' }}车{{ row.seatNo || '-' }}</template>
      </el-table-column>
      <el-table-column label="票价" width="90" align="right">
        <template #default="{ row }"><span class="money">¥{{ row.price }}</span></template>
      </el-table-column>
      <el-table-column label="状态" width="90" align="center">
        <template #default="{ row }">{{ ITEM_NAME[row.itemStatus] }}</template>
      </el-table-column>
      <el-table-column label="实退" width="90" align="right">
        <template #default="{ row }">{{ row.refundAmount }}</template>
      </el-table-column>
    </el-table>
  </el-drawer>
</template>

<script setup>
import { onMounted, reactive, ref } from 'vue'
import { ElMessage } from 'element-plus'
import request from '../../utils/request'

const STATUS_NAME = { 0: '待支付', 1: '已支付', 2: '已出行', 3: '已完成', 4: '已取消', 5: '退款中', 6: '已关闭' }
const STATUS_TYPE = { 0: 'warning', 1: 'primary', 2: 'success', 3: 'success', 4: 'info', 5: 'danger', 6: 'info' }
const ITEM_NAME = { 0: '待支付', 1: '已支付', 2: '已出行', 3: '退票中', 4: '已退票' }

const rows = ref([])
const total = ref(0)
const loading = ref(false)
const detailDrawer = ref(false)
const detail = ref(null)
const query = reactive({ pageNum: 1, pageSize: 10, orderStatus: null, trainCode: '' })

async function load() {
  loading.value = true
  try {
    const params = { ...query }
    if (params.orderStatus === null) delete params.orderStatus
    if (!params.trainCode) delete params.trainCode
    const page = await request.get('/order/page', { params })
    rows.value = page.list
    total.value = page.total
  } finally {
    loading.value = false
  }
}

async function openDetail(row) {
  detail.value = await request.get(`/order/${row.id}`)
  detailDrawer.value = true
}

async function traveled(row) {
  await request.post(`/order/${row.id}/traveled`)
  ElMessage.success('已标记发车')
  load()
}

async function complete(row) {
  await request.post(`/order/${row.id}/complete`)
  ElMessage.success('已完成')
  load()
}

onMounted(load)
</script>
