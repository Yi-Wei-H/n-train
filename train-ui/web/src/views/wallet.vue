<template>
  <div class="page page-plain wallet-page">
    <PageNav title="我的钱包" />

    <div class="wallet-body">
      <section class="card balance-card">
        <div class="balance-top">
          <span class="balance-icon"><AppIcon name="wallet" size="lg" /></span>
          <span class="caption">可用余额（上限 ¥1000）</span>
        </div>
        <div class="balance-amount">¥{{ wallet?.balance || '0.00' }}</div>
        <div class="balance-quota">
          <span class="caption">可充额度</span>
          <span class="mono">¥{{ wallet?.rechargeQuota || '0.00' }}</span>
        </div>
        <div class="quota">
          <i :style="{ width: balancePct + '%', background: 'var(--action-bg)' }" />
        </div>
      </section>

      <section class="card section-gap">
        <div class="card-head">
          <h2>充值</h2>
          <span class="caption">即充即到账</span>
        </div>
        <div class="tier-grid">
          <button
            v-for="t in tiers"
            :key="t"
            type="button"
            class="tier-btn"
            @click="recharge(t)"
          >¥{{ t }}</button>
        </div>
      </section>

      <section class="card section-gap">
        <div class="card-head">
          <h2>流水</h2>
        </div>
        <div v-if="flows.length" class="flow-list">
          <div v-for="f in flows" :key="f.flowNo" class="flow-row">
            <div class="grow">
              <div class="flow-name">{{ flowName(f.flowType) }}</div>
              <div class="caption">{{ f.bizNo || f.remark }} · {{ f.createdAt }}</div>
            </div>
            <div class="flow-amt">
              <span class="num">{{ f.flowType === 2 ? '-' : '+' }}{{ f.amount }}</span>
              <span class="caption">余 {{ f.balanceAfter }}</span>
            </div>
          </div>
        </div>
        <div v-else class="empty">暂无流水</div>
      </section>
    </div>
  </div>
</template>

<script setup>
import { computed, onMounted, ref } from 'vue'
import { showSuccessToast } from '../utils/toast'
import request from '../utils/request'
import PageNav from '../components/PageNav.vue'
import AppIcon from '../components/AppIcon.vue'

const wallet = ref(null)
const tiers = ref([])
const flows = ref([])

const balancePct = computed(() => {
  const balance = Number(wallet.value?.balance || 0)
  return Math.min(100, Math.max(0, balance / 10))
})

function flowName(t) { return { 1: '充值', 2: '支付', 3: '退款' }[t] }

async function load() {
  wallet.value = await request.get('/wallet')
  tiers.value = await request.get('/wallet/tiers')
  const page = await request.get('/wallet/flows', { params: { pageNum: 1, pageSize: 20 } })
  flows.value = page.list || []
}

async function recharge(amount) {
  try {
    wallet.value = await request.post('/wallet/recharge', { amount })
    showSuccessToast(`充值成功，余额 ¥${wallet.value.balance}`)
    load()
  } catch {
    // 错误提示由 request 拦截器统一弹出，这里只消费拒绝
  }
}

onMounted(load)
</script>

<style scoped>
.wallet-body {
  padding: 12px 16px 0;
}

.balance-card {
  padding: 16px;
}

.balance-top {
  display: flex;
  align-items: center;
  gap: 8px;
}

.balance-icon {
  width: 32px;
  height: 32px;
  display: grid;
  place-items: center;
  border-radius: 50%;
  background: var(--action-soft);
  color: var(--action-text);
}

.balance-amount {
  margin-top: 10px;
  font-size: 24px;
  line-height: 32px;
  font-weight: 600;
  font-variant-numeric: tabular-nums;
  color: var(--text-1);
}

.balance-quota {
  display: flex;
  align-items: baseline;
  justify-content: space-between;
  margin-top: 2px;
}

.quota {
  height: 6px;
  margin-top: 10px;
  overflow: hidden;
  border-radius: 9999px;
  background: var(--bg-subtle);
}

.quota i {
  display: block;
  height: 100%;
  border-radius: inherit;
}

.tier-grid {
  display: grid;
  grid-template-columns: repeat(3, minmax(0, 1fr));
  gap: 8px;
  padding: 2px 16px 16px;
}

.tier-btn {
  height: 44px;
  border: 1px solid var(--border-strong);
  border-radius: 8px;
  background: var(--bg-card);
  color: var(--text-1);
  font: 600 15px/22px var(--font-sans);
  font-variant-numeric: tabular-nums;
  cursor: pointer;
  transition: background-color var(--dur), border-color var(--dur), color var(--dur), scale var(--dur);
}

.tier-btn:active {
  scale: .96;
  border-color: var(--action-bg);
  background: var(--action-soft);
  color: var(--action-text);
}

.flow-list {
  padding-bottom: 4px;
}

.flow-row {
  display: flex;
  align-items: center;
  gap: 12px;
  min-height: 56px;
  padding: 10px 16px;
  border-top: 1px solid var(--border);
}

.flow-name {
  font-size: 15px;
  line-height: 22px;
  font-weight: 500;
  color: var(--text-1);
}

.flow-amt {
  display: flex;
  flex-direction: column;
  align-items: flex-end;
  gap: 2px;
  font-size: 15px;
  line-height: 22px;
  font-weight: 600;
  font-variant-numeric: tabular-nums;
  color: var(--text-1);
}
</style>
