<template>
  <div class="page tabbed home-page">
    <!-- 品牌渐变页头 + 查票卡 -->
    <header class="home-header">
      <div class="brand-row">
        <span class="logo-mini"><img :src="logoBlue" alt="" /></span>
        <span class="brand-name">铁旅通票务系统</span>
      </div>

      <section class="query-card">
        <div class="station-row">
          <button type="button" class="station" @click="pickFrom = true">
            <span class="st-label">出发站</span>
            <span class="st-name">{{ from?.name || '请选择' }}</span>
          </button>
          <button type="button" class="swap-btn" aria-label="交换出发与到达站" @click="swapStations">
            <AppIcon name="swap" size="lg" />
          </button>
          <button type="button" class="station right" @click="pickTo = true">
            <span class="st-label">到达站</span>
            <span class="st-name">{{ to?.name || '请选择' }}</span>
          </button>
        </div>
        <button type="button" class="date-row" @click="openDatePicker">
          <span class="date-label">出发日期</span>
          <span class="date-value">{{ dateLabel }}<AppIcon name="calendar" size="sm" /></span>
        </button>
        <button class="btn solid touch block search-btn" :disabled="loading" @click="search">
          {{ loading ? '查询中…' : '查询车票' }}
        </button>
      </section>
    </header>

    <main class="home-body">
      <!-- 结果列表 -->
      <template v-if="tickets.length">
        <div class="results-head">共 {{ trainCount }} 个车次</div>
        <article v-for="(group, code) in grouped" :key="code" class="train-card">
          <div class="train-head">
            <span class="train-no">{{ code }}</span>
            <div class="train-main">
              <div class="train-route">{{ group[0].fromName || from?.name }} → {{ group[0].toName || to?.name }}</div>
              <div class="train-time">
                <span class="mono">{{ group[0].departureTime }}</span>
                <span class="train-arrow">—</span>
                <span class="mono">{{ group[0].arrivalTime }}</span>
              </div>
            </div>
            <button type="button" class="btn sm quiet" @click="showStops(code)">经停</button>
          </div>
          <div v-for="t in group" :key="t.seatType" class="seat-line">
            <span class="seat-name">{{ seatName(t.seatType) }}</span>
            <span class="seat-price">¥{{ t.price }}</span>
            <span class="seat-left" :class="{ out: t.remaining <= 0 }">
              {{ t.remaining > 0 ? `余 ${t.remaining} 张` : '无票' }}
            </span>
            <button
              type="button"
              class="btn sm danger-plain"
              :disabled="t.remaining <= 0"
              @click="goBooking(t, group[0])"
            >预订</button>
          </div>
        </article>
      </template>

      <div v-else-if="searched && !loading" class="empty">
        <AppIcon name="search" size="xl" />
        <span>没有查询到车票</span>
      </div>

      <!-- 未查询时的快捷入口 -->
      <section v-else-if="!loading" class="card quick-card">
        <button type="button" class="quick-row" @click="$router.push('/my-tickets')">
          <span class="quick-icon"><AppIcon name="ticket" size="lg" /></span>
          <span class="grow">
            <span class="quick-title">我的车票</span>
            <span class="caption">查看行程与退票</span>
          </span>
          <AppIcon name="chevron-right" class="muted" />
        </button>
        <button type="button" class="quick-row" @click="$router.push('/orders')">
          <span class="quick-icon"><AppIcon name="list" size="lg" /></span>
          <span class="grow">
            <span class="quick-title">我的订单</span>
            <span class="caption">待支付与历史订单</span>
          </span>
          <AppIcon name="chevron-right" class="muted" />
        </button>
      </section>
    </main>

    <StationPicker v-model:show="pickFrom" title="选择出发站" :stations="stations" @select="(s) => { from = s; pickFrom = false }" />
    <StationPicker v-model:show="pickTo" title="选择到达站" :stations="stations" @select="(s) => { to = s; pickTo = false }" />

    <!-- 经停站时刻表 -->
    <van-popup v-model:show="stopsShow" position="bottom" round>
      <div class="sheet">
        <div class="sheet-grab" />
        <div class="sheet-title">{{ stopsTrainCode }} 经停时刻表</div>
        <div class="sheet-options">
          <div v-for="s in stops" :key="s.id" class="stop-row">
            <div class="grow">
              <div class="t-strong">{{ s.stationName }}</div>
              <div class="caption">第 {{ s.sort }} 站 · 里程 {{ s.distanceKm ?? '-' }} km</div>
            </div>
            <div class="stop-times mono">
              <span>到 {{ s.arrivalTime || '-' }}</span>
              <span>发 {{ s.departureTime || '-' }}</span>
            </div>
          </div>
        </div>
      </div>
    </van-popup>
  </div>
</template>

<script setup>
import { computed, onMounted, ref, watch } from 'vue'
import { useRouter } from 'vue-router'
import { showToast } from '../utils/toast'
import dayjs from 'dayjs'
import request from '../utils/request'
import { takePickedDate } from '../utils/dateSelection'
import StationPicker from '../components/StationPicker.vue'
import AppIcon from '../components/AppIcon.vue'
import logoBlue from '../assets/logo/logo-blue-256.png'

const router = useRouter()
const stations = ref([])
const from = ref(null)
const to = ref(null)
const rideDate = ref(dayjs().add(1, 'day').format('YYYY-MM-DD'))
const pickFrom = ref(false)
const pickTo = ref(false)
const tickets = ref([])
const searched = ref(false)
const loading = ref(false)
const stopsShow = ref(false)
const stopsTrainCode = ref('')
const stops = ref([])

const WEEK = ['周日', '周一', '周二', '周三', '周四', '周五', '周六']

const dateLabel = computed(() => {
  const d = dayjs(rideDate.value)
  const diff = d.startOf('day').diff(dayjs().startOf('day'), 'day')
  const prefix = diff === 0 ? '今天 · ' : diff === 1 ? '明天 · ' : ''
  return `${prefix}${d.format('MM月DD日')} ${WEEK[d.day()]}`
})

const grouped = computed(() => {
  const map = {}
  for (const t of tickets.value) {
    (map[t.trainCode] = map[t.trainCode] || []).push(t)
  }
  return map
})
const trainCount = computed(() => Object.keys(grouped.value).length)

function swapStations() {
  const tmp = from.value
  from.value = to.value
  to.value = tmp
}

function openDatePicker() {
  router.push({ name: 'datePicker', query: { date: rideDate.value } })
}

async function showStops(code) {
  stopsTrainCode.value = code
  stops.value = await request.get('/ticket/train-stops', { params: { trainCode: code } })
  stopsShow.value = true
}

async function search() {
  if (!from.value || !to.value) return showToast('请选择出发/到达站')
  if (from.value.code === to.value.code) return showToast('出发到达不能相同')
  loading.value = true
  try {
    tickets.value = await request.get('/ticket/query', {
      params: { rideDate: rideDate.value, fromStation: from.value.code, toStation: to.value.code }
    })
    searched.value = true
  } finally {
    loading.value = false
  }
}

function goBooking(ticket, head) {
  router.push({
    path: '/booking',
    query: {
      rideDate: rideDate.value,
      trainCode: ticket.trainCode,
      fromStation: ticket.fromStation,
      toStation: ticket.toStation,
      seatType: ticket.seatType,
      price: ticket.price,
      remaining: ticket.remaining,
      departureTime: head.departureTime,
      arrivalTime: head.arrivalTime
    }
  })
}

function seatName(t) { return { 1: '商务座', 2: '一等座', 3: '二等座', 4: '硬卧', 5: '硬座' }[t] || t }

const QUERY_KEY = 'ntrain-web-query'

function saveQuery() {
  localStorage.setItem(QUERY_KEY, JSON.stringify({
    from: from.value?.code, to: to.value?.code, rideDate: rideDate.value
  }))
}

onMounted(async () => {
  stations.value = await request.get('/ticket/stations')
  // 恢复上次查询条件（选过的站和日期刷新后不丢）
  let saved = null
  try {
    saved = JSON.parse(localStorage.getItem(QUERY_KEY) || 'null')
  } catch { saved = null }
  const byCode = (code, fallback) => stations.value.find((s) => s.code === code) || fallback
  from.value = byCode(saved?.from, stations.value.find((s) => s.code === 'VNP') || stations.value[0])
  to.value = byCode(saved?.to, stations.value.find((s) => s.code === 'SHH') || stations.value[1])
  if (saved?.rideDate && dayjs(saved.rideDate).isAfter(dayjs().subtract(1, 'day'))) {
    rideDate.value = saved.rideDate
  }
  // 日期选择页返回后应用选择结果（页面重新挂载时读取）
  const picked = takePickedDate()
  if (picked && dayjs(picked).isValid() && !dayjs(picked).isBefore(dayjs(), 'day')) {
    rideDate.value = picked
  }
})

// 站/日期变化即保存，下次进入自动恢复
watch([from, to, rideDate], () => {
  if (from.value && to.value) saveQuery()
})
</script>

<style scoped>
.home-header {
  padding: 16px 16px 20px;
  background: linear-gradient(180deg, var(--brand-1), var(--brand-2));
}

.brand-row {
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 0 2px 14px;
  color: #fff;
}

.logo-mini {
  width: 30px;
  height: 30px;
  display: flex;
  align-items: center;
  justify-content: center;
  border-radius: 50%;
  background: #fff;
  flex-shrink: 0;
}

.logo-mini img {
  width: 24px;
  height: 24px;
  object-fit: contain;
}

.brand-name {
  font-size: 17px;
  line-height: 24px;
  font-weight: 600;
  letter-spacing: .5px;
}

.query-card {
  padding: 16px 12px;
  background: var(--bg-card);
  border: 1px solid var(--border);
  border-radius: var(--r-panel);
  box-shadow: var(--shadow-sm);
}

.station-row {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 4px 6px 14px;
}

.station {
  flex: 1;
  min-width: 0;
  display: flex;
  flex-direction: column;
  gap: 4px;
  padding: 0;
  border: 0;
  background: transparent;
  color: var(--text-1);
  text-align: left;
  cursor: pointer;
}

.station.right {
  align-items: flex-end;
  text-align: right;
}

.st-label {
  font-size: 12px;
  line-height: 18px;
  color: var(--text-3);
}

.st-name {
  max-width: 100%;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
  font-size: 18px;
  line-height: 26px;
  font-weight: 600;
  color: var(--text-1);
}

.station.right .st-name {
  color: var(--action-text);
}

.swap-btn {
  width: 40px;
  height: 40px;
  flex-shrink: 0;
  display: grid;
  place-items: center;
  margin: 0 4px;
  border: 1px solid var(--border);
  border-radius: 50%;
  background: var(--bg-subtle);
  color: var(--action-text);
  cursor: pointer;
  transition: background-color var(--dur);
}

.swap-btn:active {
  background: var(--action-soft);
}

.date-row {
  display: flex;
  align-items: center;
  justify-content: space-between;
  width: 100%;
  height: 44px;
  padding: 0 6px;
  border: 0;
  border-top: 1px solid var(--border);
  background: transparent;
  cursor: pointer;
}

.date-label {
  font-size: 13px;
  line-height: 20px;
  color: var(--text-2);
}

.date-value {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  font-size: 15px;
  line-height: 22px;
  font-weight: 500;
  color: var(--text-1);
}

.date-value svg.i {
  color: var(--text-3);
}

.search-btn {
  margin-top: 10px;
}

.home-body {
  padding-top: 12px;
}

.results-head {
  padding: 0 16px 8px;
  font-size: 13px;
  line-height: 20px;
  color: var(--text-2);
}

.train-card {
  margin: 0 16px 8px;
  overflow: hidden;
  background: var(--bg-card);
  border: 1px solid var(--border);
  border-radius: var(--r-panel);
}

.train-head {
  display: flex;
  align-items: center;
  gap: 12px;
  padding: 12px 14px;
  border-bottom: 1px solid var(--border);
}

.train-no {
  width: 44px;
  height: 36px;
  flex-shrink: 0;
  display: grid;
  place-items: center;
  border: 1px solid var(--border);
  border-radius: var(--r-control);
  background: var(--bg-card);
  color: var(--action-text);
  font-family: var(--font-mono);
  font-size: 13px;
  font-weight: 700;
}

.train-main {
  flex: 1;
  min-width: 0;
}

.train-route {
  font-size: 15px;
  line-height: 22px;
  font-weight: 600;
  color: var(--text-1);
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
}

.train-time {
  display: flex;
  align-items: center;
  gap: 6px;
  margin-top: 2px;
  color: var(--text-1);
}

.train-time .mono {
  font-size: 13px;
}

.train-arrow {
  color: var(--text-3);
  font-size: 12px;
}

.seat-line {
  display: flex;
  align-items: center;
  gap: 10px;
  min-height: 48px;
  padding: 8px 14px;
}

.seat-line + .seat-line {
  border-top: 1px solid var(--border);
}

.seat-name {
  font-size: 15px;
  line-height: 22px;
  color: var(--text-1);
}

.seat-price {
  flex: 1;
  color: var(--price);
  font-size: 15px;
  line-height: 22px;
  font-weight: 600;
  font-variant-numeric: tabular-nums;
}

.seat-left {
  min-width: 68px;
  text-align: right;
  font-size: 13px;
  line-height: 20px;
  color: var(--text-2);
}

.seat-left.out {
  color: var(--text-3);
}

.quick-card {
  margin: 4px 16px 0;
  overflow: hidden;
}

.quick-row {
  display: flex;
  align-items: center;
  gap: 12px;
  width: 100%;
  min-height: 56px;
  padding: 10px 16px;
  border: 0;
  background: transparent;
  text-align: left;
  cursor: pointer;
}

.quick-row + .quick-row {
  border-top: 1px solid var(--border);
}

.quick-icon {
  width: 36px;
  height: 36px;
  flex-shrink: 0;
  display: grid;
  place-items: center;
  border-radius: 50%;
  background: var(--action-soft);
  color: var(--action-text);
}

.quick-title {
  display: block;
  font-size: 15px;
  line-height: 22px;
  font-weight: 500;
  color: var(--text-1);
}

.quick-row .caption {
  display: block;
}

.stop-row {
  display: flex;
  align-items: center;
  gap: 12px;
  min-height: 56px;
  padding: 8px 0;
  border-bottom: 1px solid var(--border);
}

.stop-row:last-child {
  border-bottom: 0;
}

.stop-times {
  display: flex;
  flex-direction: column;
  align-items: flex-end;
  gap: 2px;
  color: var(--text-2);
}
</style>
