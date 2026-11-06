<template>
  <div class="page page-plain orders-page">
    <PageNav title="我的订单" />
    <div class="orders-filter">
      <SegTabs v-model="activeStatus" :tabs="tabs" @change="load" />
    </div>

    <van-pull-refresh v-model="refreshing" @refresh="onRefresh">
      <van-list v-model:loading="loading" :finished="finished" @load="loadMore">
        <div class="card-list order-list">
          <button
            v-for="o in orders"
            :key="o.id"
            type="button"
            class="order-card card"
            @click="$router.push(`/orders/${o.id}`)"
          >
            <div class="order-top">
              <span class="order-code mono">{{ o.trainCode }}</span>
              <span class="order-route">{{ o.fromName || o.fromStation }} → {{ o.toName || o.toStation }}</span>
              <span class="chip" :class="statusChip(o.orderStatus)">{{ statusName(o.orderStatus) }}</span>
            </div>
            <div class="order-mid caption">
              {{ o.rideDate }} · {{ fmtTime(o.departureTime) }}<span v-if="o.arrivalTime"> – {{ fmtTime(o.arrivalTime) }}</span>
            </div>
            <div class="order-bottom">
              <span class="caption">订单金额</span>
              <span class="price">¥{{ o.totalAmount }}</span>
            </div>
          </button>
          <div v-if="finished && !orders.length" class="empty">
            <AppIcon name="ticket" size="xl" />
            <span>暂无订单</span>
          </div>
        </div>
      </van-list>
    </van-pull-refresh>
  </div>
</template>

<script setup>
import { ref } from 'vue'
import request from '../utils/request'
import PageNav from '../components/PageNav.vue'
import SegTabs from '../components/SegTabs.vue'
import AppIcon from '../components/AppIcon.vue'

// tab → 后端订单状态：全部 -1 特殊处理。
// 已完成分类同时收拢已取消/退款相关终态（4 已取消、5 退款中、6 已关闭）——仅前端分类归组，状态值本身不变。
const tabs = [
  { label: '全部', value: -1 },
  { label: '待支付', value: 0 },
  { label: '已支付', value: 1 },
  { label: '已出行', value: 2 },
  { label: '已完成', value: '3,4,5,6' }
]
const activeStatus = ref(-1)
const orders = ref([])
const loading = ref(false)
const refreshing = ref(false)
const finished = ref(false)
const pageNum = ref(1)
// 请求序号：tab 切换重置后，仍在途的旧响应直接丢弃，避免过期数据混进新列表
let reqSeq = 0

function statusName(s) { return { 0: '待支付', 1: '已支付', 2: '已出行', 3: '已完成', 4: '已取消', 5: '退款中', 6: '已关闭' }[s] }
function statusChip(s) {
  if (s === 1) return 'action'
  if (s === 5) return 'danger'
  return 'outline'
}

/** 后端时间统一 "yyyy-MM-dd HH:mm:ss"（空格分隔无 T），直接提取 HH:mm；兼容 ISO "T" 与纯 HH:mm:ss */
function fmtTime(t) {
  const m = String(t ?? '').match(/(\d{1,2}:\d{2})/)
  return m ? m[1] : ''
}

async function load() {
  // 先置 loading 再清列表：占用 van-list 的加载态，阻止它在重置完成后自动补发一次相同请求
  loading.value = true
  orders.value = []
  finished.value = false
  pageNum.value = 1
  await loadMore()
}

async function onRefresh() {
  try {
    await load()
  } finally {
    refreshing.value = false
  }
}

async function loadMore() {
  const seq = ++reqSeq
  const params = { pageNum: pageNum.value, pageSize: 10 }
  if (activeStatus.value !== -1) params.orderStatus = activeStatus.value
  try {
    const page = await request.get('/order/list', { params })
    if (seq !== reqSeq) return
    orders.value.push(...(page.list || []))
    pageNum.value += 1
    finished.value = orders.value.length >= page.total
  } finally {
    // 只有最新一次请求才能解除 loading，失败时也解除（van-list 可滚动重试）
    if (seq === reqSeq) loading.value = false
  }
}
</script>

<style scoped>
.orders-page {
  padding-bottom: calc(24px + env(safe-area-inset-bottom));
}

.orders-filter {
  padding: 12px 16px 4px;
}

.order-list {
  padding-top: 8px;
}

.order-card {
  display: block;
  width: 100%;
  padding: 14px 16px;
  text-align: left;
  cursor: pointer;
}

.order-card:active {
  background: var(--bg-subtle);
}

.order-top {
  display: flex;
  align-items: center;
  gap: 8px;
}

.order-code {
  color: var(--text-1);
  font-size: 13px;
  font-weight: 700;
}

.order-route {
  flex: 1;
  min-width: 0;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
  font-size: 15px;
  line-height: 22px;
  font-weight: 500;
  color: var(--text-1);
}

.order-mid {
  margin-top: 4px;
}

.order-bottom {
  display: flex;
  align-items: baseline;
  justify-content: space-between;
  margin-top: 10px;
  padding-top: 10px;
  border-top: 1px solid var(--border);
}

.order-bottom .price {
  font-size: 16px;
  line-height: 24px;
}
</style>
