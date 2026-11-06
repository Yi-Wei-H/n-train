<template>
  <div v-if="order" class="page page-plain detail-page">
    <PageNav title="订单详情" />

    <div class="detail-body">
      <section class="card status-card">
        <div class="status-top">
          <span class="chip" :class="statusChip(order.orderStatus)">{{ statusName(order.orderStatus) }}</span>
          <span class="caption">订单号 <span class="mono">{{ order.orderNo }}</span></span>
        </div>
        <div class="detail-trip">
          <span class="train-no">{{ order.trainCode }}</span>
          <div class="grow">
            <div class="trip-route">{{ order.fromName || order.fromStation }} → {{ order.toName || order.toStation }}</div>
            <div class="caption">{{ order.rideDate }} · {{ fmtTime(order.departureTime) }} 发车 · 到 {{ fmtTime(order.arrivalTime) }}</div>
          </div>
        </div>
        <div class="detail-amount">
          <span class="caption">订单金额</span>
          <span class="t-metric price">¥{{ order.totalAmount }}</span>
        </div>
        <div v-if="order.orderStatus === 0" class="notice action">
          <AppIcon name="clock" />
          <span class="grow">支付剩余 <b class="mono">{{ countdown }}</b>，超时订单将自动取消</span>
        </div>
      </section>

      <section class="card section-gap">
        <div class="card-head">
          <h2>乘车人明细</h2>
          <span v-if="order.orderStatus === 1" class="caption">勾选可退票</span>
        </div>
        <div class="item-list">
          <div v-for="item in order.items" :key="item.id" class="item-row">
            <button
              v-if="order.orderStatus === 1 && item.itemStatus === 1"
              type="button"
              class="check"
              :class="{ on: refundIds.includes(item.id) }"
              role="checkbox"
              :aria-checked="refundIds.includes(item.id)"
              :aria-label="`选择 ${item.passengerName} 退票`"
              @click="toggleRefund(item.id)"
            >
              <AppIcon name="check" />
            </button>
            <div class="grow">
              <div class="item-name">{{ item.passengerName }} · {{ item.seatNo || '未分配' }}座</div>
              <div class="caption">{{ itemName(item.itemStatus) }}</div>
            </div>
            <span class="price">¥{{ item.price }}</span>
          </div>
        </div>
      </section>

      <div class="detail-actions">
        <button
          v-if="order.orderStatus === 0"
          type="button"
          class="btn solid touch block"
          :disabled="paying"
          @click="pay"
        >{{ paying ? '支付中…' : `余额支付 ¥${order.totalAmount}` }}</button>
        <button v-if="order.orderStatus === 0" type="button" class="btn neutral touch block" @click="cancel">
          取消订单
        </button>
        <button
          v-if="order.orderStatus === 1"
          type="button"
          class="btn danger-plain touch block"
          :disabled="!refundIds.length"
          @click="refund"
        >退票（已勾选 {{ refundIds.length }} 张）</button>
        <button type="button" class="btn quiet touch block" @click="$router.push('/my-tickets')">
          我的车票
        </button>
      </div>
    </div>
  </div>
</template>

<script setup>
import { onMounted, onUnmounted, ref } from 'vue'
import { useRoute } from 'vue-router'
import { showConfirmDialog } from 'vant'
import { showSuccessToast, showToast } from '../utils/toast'
import request from '../utils/request'
import PageNav from '../components/PageNav.vue'
import AppIcon from '../components/AppIcon.vue'

const route = useRoute()
const orderId = route.params.id
const order = ref(null)
const refundIds = ref([])
const paying = ref(false)
const countdown = ref('15:00')
let timer = null

function statusName(s) { return { 0: '待支付', 1: '已支付（待出行）', 2: '已出行', 3: '已完成', 4: '已取消', 5: '退款中', 6: '已关闭' }[s] }
function statusChip(s) {
  if (s === 0) return 'action'
  if (s === 5) return 'danger'
  return 'outline'
}
function itemName(s) { return { 0: '待支付', 1: '已支付', 2: '已出行', 3: '退票中', 4: '已退票' }[s] }

/** 后端时间统一 "yyyy-MM-dd HH:mm:ss"（空格分隔无 T），直接提取 HH:mm；兼容 ISO "T" 与纯 HH:mm:ss */
function fmtTime(t) {
  const m = String(t ?? '').match(/(\d{1,2}:\d{2})/)
  return m ? m[1] : ''
}

function toggleRefund(id) {
  const i = refundIds.value.indexOf(id)
  if (i >= 0) refundIds.value.splice(i, 1)
  else refundIds.value.push(id)
}

function startCountdown() {
  if (order.value.orderStatus !== 0) return
  // 空格日期串 iOS Safari 解析为 NaN，统一转 ISO "T" 再 parse
  const deadline = new Date(String(order.value.createdAt).replace(' ', 'T')).getTime() + 15 * 60 * 1000
  timer = setInterval(() => {
    const left = deadline - Date.now()
    if (left <= 0) {
      countdown.value = '已超时'
      clearInterval(timer)
      load()
      return
    }
    countdown.value = `${String(Math.floor(left / 60000)).padStart(2, '0')}:${String(Math.floor(left % 60000 / 1000)).padStart(2, '0')}`
  }, 1000)
}

async function load() {
  order.value = await request.get(`/order/${orderId}`)
}

async function pay() {
  try {
    await showConfirmDialog({ title: '确认支付', message: `将从余额扣除 ¥${order.value.totalAmount}` })
  } catch { return }
  paying.value = true
  try {
    const r = await request.post(`/order/${orderId}/pay`)
    showSuccessToast(`支付成功，余额 ¥${r.balanceAfter}`)
    load()
  } finally {
    paying.value = false
  }
}

async function cancel() {
  try {
    await showConfirmDialog({ title: '取消订单', message: '确认取消该订单？' })
  } catch { return }
  await request.post(`/order/${orderId}/cancel`)
  showSuccessToast('已取消')
  load()
}

async function refund() {
  if (!refundIds.value.length) return showToast('请先勾选要退票的乘车人')
  try {
    await showConfirmDialog({ title: '确认退票', message: `将退 ${refundIds.value.length} 张票，款项退回钱包` })
  } catch { return }
  const r = await request.post(`/order/${orderId}/refund`, { itemIds: refundIds.value })
  showSuccessToast(r.orderClosed ? `已全部退票，退款 ¥${r.refundedAmount}` : `退票成功，退款 ¥${r.refundedAmount}`)
  refundIds.value = []
  load()
}

onMounted(() => { load().then(startCountdown) })
onUnmounted(() => clearInterval(timer))
</script>

<style scoped>
.detail-page {
  padding-bottom: calc(24px + env(safe-area-inset-bottom));
}

.detail-body {
  padding: 12px 16px 0;
}

.status-card {
  padding: 14px 16px;
}

.status-top {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 8px;
}

.status-top .caption .mono {
  color: var(--text-2);
}

.detail-trip {
  display: flex;
  align-items: center;
  gap: 12px;
  margin-top: 14px;
  padding-top: 12px;
  border-top: 1px solid var(--border);
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

.detail-amount {
  display: flex;
  align-items: baseline;
  justify-content: space-between;
  margin-top: 12px;
  padding-top: 12px;
  border-top: 1px solid var(--border);
}

.status-card .notice {
  margin-top: 12px;
}

.item-list {
  padding-bottom: 4px;
}

.item-row {
  display: flex;
  align-items: center;
  gap: 12px;
  min-height: 52px;
  padding: 8px 16px;
  border-top: 1px solid var(--border);
}

.item-name {
  font-size: 15px;
  line-height: 22px;
  font-weight: 500;
  color: var(--text-1);
}

.item-row .price {
  font-size: 14px;
}

.detail-actions {
  display: flex;
  flex-direction: column;
  gap: 10px;
  margin-top: 20px;
}

.detail-actions .btn {
  margin: 0;
}
</style>
