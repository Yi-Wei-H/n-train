<template>
  <div class="page page-plain booking-page">
    <PageNav title="填写订单" />

    <div class="booking-body">
      <section class="card trip-card">
        <div class="trip-top">
          <span class="train-no">{{ $route.query.trainCode }}</span>
          <div class="grow">
            <div class="trip-route">{{ stationName(stMap, fromName) }} → {{ stationName(stMap, toName) }}</div>
            <div class="caption">{{ $route.query.rideDate }} · {{ $route.query.departureTime }} 发车</div>
          </div>
          <span class="chip action">{{ seatName }}</span>
        </div>
        <div class="trip-meta">
          <div>
            <span class="caption">单价</span>
            <b class="price">¥{{ price }}</b>
          </div>
          <div>
            <span class="caption">余票</span>
            <b class="num">{{ remaining }} 张</b>
          </div>
        </div>
      </section>

      <section class="card section-gap">
        <div class="card-head">
          <h2>选择乘车人</h2>
          <button type="button" class="btn sm quiet" @click="$router.push('/passengers')">管理</button>
        </div>
        <div v-if="passengers.length" class="passenger-list">
          <div
            v-for="p in passengers"
            :key="p.id"
            class="passenger-row"
            role="checkbox"
            :aria-checked="selectedIds.includes(p.id)"
            tabindex="0"
            @click="toggle(p)"
            @keydown.enter.prevent="toggle(p)"
          >
            <span class="check" :class="{ on: selectedIds.includes(p.id) }" aria-hidden="true">
              <AppIcon name="check" />
            </span>
            <div class="grow">
              <div class="passenger-name">
                {{ p.name }}
                <span class="chip info">{{ typeName(p.passengerType) }}</span>
              </div>
              <div class="caption mono">{{ maskIdCard(p.idCardNo) }}</div>
            </div>
          </div>
        </div>
        <div v-else class="empty">还没有乘车人</div>
      </section>

      <!-- 自选座（纯一等/二等座且余票 ≥20 才开放；抽象列位，最多两排） -->
      <section v-if="seatChoosable" class="card section-gap">
        <div class="card-head">
          <h2>自选座位</h2>
          <span class="caption">可不选，系统自动连座</span>
        </div>
        <div class="seat-pick-body">
          <div v-for="row in 2" :key="row" class="seat-pick-row">
            <span class="seat-row-label">{{ row }}排</span>
            <button
              v-for="col in choosableCols"
              :key="col"
              type="button"
              class="seat-pick"
              :class="{ on: chosenSet.has(col + row) }"
              :aria-pressed="chosenSet.has(col + row)"
              @click="toggleSeat(col + row)"
            >{{ col }}{{ row }}</button>
          </div>
          <p class="seat-tip">已选 {{ chosenSeats.length }} / {{ selectedIds.length }} 个座位（要么不选，要么选满）</p>
        </div>
      </section>
      <div v-else-if="selectedIds.length" class="notice neutral section-gap">
        <AppIcon name="seat" />
        <span class="grow">当前车票暂不支持在线选座，系统将自动为您分配座位</span>
      </div>
    </div>

    <div class="bottom-bar">
      <div class="bottom-total grow">
        <span class="caption">合计</span>
        <b class="price t-metric">¥{{ totalText }}</b>
      </div>
      <button class="btn solid touch" :disabled="!selectedIds.length" @click="submit()">提交订单</button>
    </div>

    <CaptchaDialog v-model:show="captchaShow" @verified="onCaptchaVerified" />
  </div>
</template>

<script setup>
import { computed, onMounted, ref } from 'vue'
import { useRouter, useRoute } from 'vue-router'
import { showConfirmDialog } from 'vant'
import { showSuccessToast, showToast } from '../utils/toast'
import request from '../utils/request'
import CaptchaDialog from '../components/CaptchaDialog.vue'
import PageNav from '../components/PageNav.vue'
import AppIcon from '../components/AppIcon.vue'
import { maskIdCard } from '../utils/mask'
import { loadStationMap, stationName } from '../utils/stations'

const router = useRouter()
const route = useRoute()
const passengers = ref([])
const selectedIds = ref([])
const captchaShow = ref(false)
const chosenSeats = ref([])
const stMap = ref({})

const seatName = computed(() => ({ 1: '商务座', 2: '一等座', 3: '二等座', 4: '硬卧', 5: '硬座' }[route.query.seatType] || route.query.seatType))
const fromName = computed(() => route.query.fromStation)
const toName = computed(() => route.query.toStation)
// route.query 里是电报码，展示层经 stationName 转站名
// 余票/单价初值来自查询页（缓存值），进页后经 /ticket/detail 实时刷新
const remaining = ref(Number(route.query.remaining || 0))
const price = ref(Number(route.query.price || 0))
const totalText = computed(() => (selectedIds.value.length * Number(price.value)).toFixed(2))

/** 一等座 ACDF / 二等座 ABCDF（与后端 SeatLayouts 一致） */
const choosableCols = computed(() =>
  Number(route.query.seatType) === 2 ? ['A', 'C', 'D', 'F'] : ['A', 'B', 'C', 'D', 'F'])

/** 选座开放条件：一等(2)/二等(3) 且余票 ≥ 20（旧版规则，按实时余票判断） */
const seatChoosable = computed(() => {
  const type = Number(route.query.seatType)
  return (type === 2 || type === 3) && remaining.value >= 20
})

const chosenSet = computed(() => new Set(chosenSeats.value))

function toggle(p) {
  const i = selectedIds.value.indexOf(p.id)
  if (i >= 0) selectedIds.value.splice(i, 1)
  else selectedIds.value.push(p.id)
}

function toggleSeat(seat) {
  const i = chosenSeats.value.indexOf(seat)
  if (i >= 0) {
    chosenSeats.value.splice(i, 1)
  } else {
    if (chosenSeats.value.length >= selectedIds.value.length) {
      return showToast('所选座位数不能超过乘车人数')
    }
    chosenSeats.value.push(seat)
  }
}

function typeName(t) { return { 1: '成人', 2: '儿童', 3: '学生' }[t] }

async function submit(captchaParams) {
  if (selectedIds.value.length > 5) return showToast('单笔订单最多 5 位乘车人')
  if (chosenSeats.value.length > 0 && chosenSeats.value.length !== selectedIds.value.length) {
    return showToast('请选择与乘车人数相同的座位，或不选座由系统分配')
  }
  try {
    const order = await request.post('/order/create', {
      rideDate: route.query.rideDate,
      trainCode: route.query.trainCode,
      fromStation: route.query.fromStation,
      toStation: route.query.toStation,
      seatType: Number(route.query.seatType),
      passengerIds: selectedIds.value,
      chosenSeats: chosenSeats.value.length ? chosenSeats.value : undefined,
      ...(captchaParams || {})
    })
    if (order.seatDegraded) {
      // 自选座被抢已自动分配：弹窗告知实际座位以订单详情为准，确认后再进详情
      showConfirmDialog({
        title: '座位已调整',
        message: '所选座位刚被其他旅客购得，已为您自动分配同席别座位，请在 15 分钟内完成支付',
        confirmButtonText: '查看订单'
      }).finally(() => router.replace(`/orders/${order.id}`))
      return
    }
    showSuccessToast(`下单成功，订单号 ${order.orderNo}，请在 15 分钟内支付`)
    router.replace(`/orders/${order.id}`)
  } catch (e) {
    if (e?.code === 41006) {
      // 触发频控：弹验证码，验证后自动重提交
      captchaShow.value = true
      pendingCaptchaRetry = true
    }
  }
}

let pendingCaptchaRetry = false
function onCaptchaVerified(params) {
  if (pendingCaptchaRetry) {
    pendingCaptchaRetry = false
    submit(params)
  }
}

onMounted(async () => {
  loadStationMap().then((m) => { stMap.value = m })
  passengers.value = await request.get('/passenger/list')
  if (!passengers.value.length) {
    showToast('请先添加乘车人')
    router.push('/passengers')
  }
  refreshDetail()
})

/** 实时余票/单价刷新（绕过缓存的直查）：失败不阻断下单，沿用查询页数据 */
async function refreshDetail() {
  try {
    const list = await request.get('/ticket/detail', {
      params: {
        rideDate: route.query.rideDate,
        trainCode: route.query.trainCode,
        fromStation: route.query.fromStation,
        toStation: route.query.toStation
      }
    })
    const row = list.find((t) => Number(t.seatType) === Number(route.query.seatType))
    if (row) {
      remaining.value = row.remaining
      price.value = Number(row.price)
    }
  } catch (e) { /* 展示层尽力而为，权威校验在下单事务 */ }
}
</script>

<style scoped>
.booking-page {
  padding-bottom: calc(80px + env(safe-area-inset-bottom));
}

.booking-body {
  padding: 12px 16px 0;
}

.trip-card {
  padding: 14px 16px;
}

.trip-top {
  display: flex;
  align-items: center;
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

.trip-route {
  font-size: 15px;
  line-height: 22px;
  font-weight: 600;
  color: var(--text-1);
}

.trip-meta {
  display: flex;
  gap: 32px;
  margin-top: 12px;
  padding-top: 12px;
  border-top: 1px solid var(--border);
}

.trip-meta > div {
  display: flex;
  flex-direction: column;
  gap: 2px;
}

.trip-meta b {
  font-size: 15px;
  line-height: 22px;
}

.passenger-list {
  padding-bottom: 4px;
}

.passenger-row {
  display: flex;
  align-items: center;
  gap: 12px;
  min-height: 56px;
  padding: 8px 16px;
  border-top: 1px solid var(--border);
  cursor: pointer;
}

.passenger-name {
  display: flex;
  align-items: center;
  gap: 6px;
  font-size: 15px;
  line-height: 22px;
  font-weight: 500;
  color: var(--text-1);
}

.seat-pick-body {
  padding: 4px 16px 14px;
}

.seat-pick-row {
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 4px 0;
}

.seat-row-label {
  width: 40px;
  flex-shrink: 0;
  font-size: 13px;
  line-height: 20px;
  color: var(--text-3);
}

.seat-pick {
  width: 44px;
  height: 44px;
  border: 1px solid var(--border-strong);
  border-radius: var(--r-control);
  background: var(--bg-card);
  color: var(--text-1);
  font: 500 13px/20px var(--font-sans);
  cursor: pointer;
  transition: background-color var(--dur), border-color var(--dur), color var(--dur);
}

.seat-pick.on {
  border-color: var(--action-bg);
  background: var(--action-soft);
  color: var(--action-text);
}

.seat-tip {
  margin: 8px 0 0;
  font-size: 12px;
  line-height: 18px;
  color: var(--text-3);
}
</style>
