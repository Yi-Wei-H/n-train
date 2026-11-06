<template>
  <div class="page tabbed tickets-page">
    <header class="page-head">
      <h1 class="t-page">车票</h1>
      <p class="caption">查看行程、座位与退票</p>
    </header>

    <div class="tickets-filter">
      <SegTabs v-model="activeStatus" :tabs="tabs" @change="load" />
    </div>

    <div class="card-list">
      <article v-for="t in tickets" :key="t.id" class="ticket-card card">
        <div class="ticket-main">
          <span class="train-no">{{ t.trainCode }}</span>
          <div class="grow">
            <div class="ticket-route">{{ stationName(stMap, t.fromStation) }} → {{ stationName(stMap, t.toStation) }}</div>
            <div class="ticket-time">
              <span class="mono">{{ fmtTime(t.departureTime) }}</span>
              <span class="muted">·</span>
              <span>{{ t.rideDate }}</span>
            </div>
            <div class="caption">{{ t.passengerName }} · {{ t.carriageNo || '-' }}车 {{ t.seatNo || '-' }}座</div>
          </div>
          <div class="ticket-side">
            <span v-if="isBoughtByOther(t)" class="chip info">他人代购</span>
            <span class="chip" :class="statusChip(t.ticketStatus)">{{ statusName(t.ticketStatus) }}</span>
          </div>
        </div>
        <div v-if="t.ticketStatus === 1" class="ticket-actions">
          <button type="button" class="btn sm danger-plain" @click="refund(t)">退票</button>
        </div>
      </article>

      <div v-if="!tickets.length" class="empty">
        <AppIcon name="ticket" size="xl" />
        <span>暂无车票</span>
      </div>
    </div>
  </div>
</template>

<script setup>
import { onMounted, ref } from 'vue'
import { showConfirmDialog } from 'vant'
import { showSuccessToast } from '../utils/toast'
import request from '../utils/request'
import { useUserStore } from '../stores/user'
import { loadStationMap, stationName } from '../utils/stations'
import SegTabs from '../components/SegTabs.vue'
import AppIcon from '../components/AppIcon.vue'

const user = useUserStore()
const tabs = [
  { label: '全部', value: -1 },
  { label: '待出行', value: 1 },
  { label: '已出行', value: 2 },
  { label: '已退票', value: 3 }
]
const activeStatus = ref(-1)
const tickets = ref([])
const stMap = ref({})

function statusName(s) { return { 1: '待出行', 2: '已出行', 3: '已退票', 4: '已失效' }[s] }
function statusChip(s) {
  if (s === 1) return 'action'
  return 'outline'
}
/** 他人代购：票的购票人不是当前登录用户（自己买的和被人代买的都能看到） */
function isBoughtByOther(t) {
  return t.memberId && user.member && String(t.memberId) !== String(user.member.id)
}
/** 后端时间可能是 "HH:mm:ss" 或完整日期串，统一提取 HH:mm；日期单独展示 */
function fmtTime(t) {
  const m = String(t ?? '').match(/(\d{1,2}:\d{2})/)
  return m ? m[1] : ''
}

async function load() {
  const params = {}
  // 参数名与后端 MyTicketController 的 ticketStatus 对齐（曾用 status 导致过滤失效）
  if (activeStatus.value !== -1) params.ticketStatus = activeStatus.value
  tickets.value = await request.get('/my-ticket/list', { params })
}

/** 退票：本人买的走订单明细退款；他人代买的也可退自己的那张（退款回到购票人钱包） */
async function refund(t) {
  const refundTarget = isBoughtByOther(t) ? '票款将退回购票人账户' : '票款将退回您的钱包余额'
  try {
    await showConfirmDialog({
      title: '退票确认',
      message: `确认退掉 ${t.rideDate} ${t.trainCode} ${t.passengerName} 的车票？${refundTarget}。`
    })
  } catch { return }
  await request.post(`/order/${t.orderId}/refund`, { itemIds: [t.orderItemId] })
  showSuccessToast('已退票')
  load()
}

onMounted(async () => {
  loadStationMap().then((m) => { stMap.value = m })
  load()
})
</script>

<style scoped>
.page-head p {
  margin: 2px 0 0;
}

.tickets-filter {
  padding: 4px 16px 0;
}

.ticket-card {
  padding: 14px 16px;
}

.ticket-main {
  display: flex;
  align-items: flex-start;
  gap: 12px;
}

.train-no {
  width: 44px;
  height: 36px;
  flex-shrink: 0;
  display: grid;
  place-items: center;
  border: 1px solid var(--border);
  border-radius: var(--r-control);
  color: var(--action-text);
  font-family: var(--font-mono);
  font-size: 13px;
  font-weight: 700;
}

.ticket-route {
  font-size: 15px;
  line-height: 22px;
  font-weight: 600;
  color: var(--text-1);
}

.ticket-time {
  display: flex;
  align-items: center;
  gap: 4px;
  margin-top: 2px;
  font-size: 13px;
  line-height: 20px;
  color: var(--text-2);
}

.ticket-side {
  display: flex;
  flex-direction: column;
  align-items: flex-end;
  gap: 4px;
  flex-shrink: 0;
}

.ticket-actions {
  display: flex;
  justify-content: flex-end;
  margin-top: 12px;
  padding-top: 10px;
  border-top: 1px solid var(--border);
}
</style>
