<template>
  <div class="page date-page">
    <PageNav title="选择出发日期" />

    <main class="date-body">
      <section class="card calendar-card">
        <div class="month-bar">
          <button
            type="button"
            class="month-nav"
            aria-label="上个月"
            :disabled="!canPrev"
            @click="shiftMonth(-1)"
          >
            <AppIcon name="chevron-left" size="lg" />
          </button>
          <span class="month-title">{{ monthLabel }}</span>
          <button
            type="button"
            class="month-nav"
            aria-label="下个月"
            :disabled="!canNext"
            @click="shiftMonth(1)"
          >
            <AppIcon name="chevron-right" size="lg" />
          </button>
        </div>

        <div class="weekdays" aria-hidden="true">
          <span v-for="w in WEEKDAYS" :key="w">{{ w }}</span>
        </div>

        <div class="days">
          <span v-for="(cell, i) in cells" :key="i" class="day-slot">
            <button
              v-if="cell"
              type="button"
              class="day"
              :class="{ selected: cell.value === selected, disabled: cell.disabled }"
              :disabled="cell.disabled"
              :aria-pressed="cell.value === selected"
              @click="choose(cell.value)"
            >{{ cell.day }}</button>
          </span>
        </div>
      </section>

      <div class="notice neutral date-note">
        <AppIcon name="info" size="sm" />
        <span class="grow">可购票日期为今天起 15 天内（至 {{ maxLabel }}）</span>
      </div>

      <div class="legend">
        <span><i class="dot selected" />已选</span>
        <span><i class="dot available" />可选</span>
        <span><i class="dot disabled" />不可购</span>
      </div>
    </main>
  </div>
</template>

<script setup>
import { computed, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import dayjs from 'dayjs'
import { setPickedDate } from '../utils/dateSelection'
import PageNav from '../components/PageNav.vue'
import AppIcon from '../components/AppIcon.vue'

const route = useRoute()
const router = useRouter()

const WEEKDAYS = ['日', '一', '二', '三', '四', '五', '六']
const today = dayjs().startOf('day')
const maxDate = today.add(14, 'day')
const maxLabel = maxDate.format('M月D日')

function initialDate() {
  const raw = typeof route.query.date === 'string' ? route.query.date : ''
  const date = dayjs(raw)
  if (!date.isValid() || date.isBefore(today) || date.isAfter(maxDate)) {
    return today
  }
  return date
}

const initial = initialDate()
const selected = ref(initial.format('YYYY-MM-DD'))
const viewMonth = ref(initial.startOf('month'))

const monthLabel = computed(() => viewMonth.value.format('YYYY年M月'))
const canPrev = computed(() => viewMonth.value.isAfter(today.startOf('month')))
const canNext = computed(() => viewMonth.value.isBefore(maxDate.startOf('month')))

const cells = computed(() => {
  const first = viewMonth.value.startOf('month')
  const offset = first.day()
  const total = first.daysInMonth()
  const list = []
  for (let i = 0; i < offset; i++) {
    list.push(null)
  }
  for (let day = 1; day <= total; day++) {
    const date = first.date(day)
    list.push({
      value: date.format('YYYY-MM-DD'),
      day,
      disabled: date.isBefore(today) || date.isAfter(maxDate)
    })
  }
  while (list.length % 7 !== 0) {
    list.push(null)
  }
  return list
})

function shiftMonth(step) {
  const next = viewMonth.value.add(step, 'month')
  if (next.isBefore(today.startOf('month')) || next.isAfter(maxDate.startOf('month'))) {
    return
  }
  viewMonth.value = next
}

function choose(value) {
  setPickedDate(value)
  // 与进入方向相反：页面从右向左进入，选择后向右退回
  if (window.history.length > 1) {
    router.back()
  } else {
    router.replace('/')
  }
}
</script>

<style scoped>
.date-body {
  padding: 12px 16px 0;
}

.calendar-card {
  padding: 14px 12px 16px;
}

.month-bar {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 0 2px 10px;
}

.month-title {
  font-size: 16px;
  line-height: 24px;
  font-weight: 600;
  color: var(--text-1);
}

.month-nav {
  width: 40px;
  height: 40px;
  display: grid;
  place-items: center;
  border: 0;
  border-radius: var(--r-control);
  background: transparent;
  color: var(--text-2);
  cursor: pointer;
  transition: background-color var(--dur), color var(--dur);
}

.month-nav:active:not(:disabled) {
  background: var(--bg-subtle);
}

.month-nav:disabled {
  color: var(--control-off);
  cursor: not-allowed;
}

.weekdays {
  display: grid;
  grid-template-columns: repeat(7, minmax(0, 1fr));
  gap: 4px;
  padding-bottom: 6px;
}

.weekdays span {
  text-align: center;
  font-size: 12px;
  line-height: 18px;
  color: var(--text-3);
}

.days {
  display: grid;
  grid-template-columns: repeat(7, minmax(0, 1fr));
  gap: 4px;
}

.day-slot {
  height: 48px;
}

.day {
  width: 100%;
  height: 100%;
  padding: 0;
  border: 0;
  border-radius: 8px;
  background: transparent;
  color: var(--text-2);
  font: 500 15px/22px var(--font-sans);
  font-variant-numeric: tabular-nums;
  cursor: pointer;
  transition: background-color var(--dur), color var(--dur), scale var(--dur);
}

.day:active:not(:disabled) {
  scale: .96;
  background: var(--bg-subtle);
}

.day.selected {
  background: var(--action-bg);
  color: var(--action-fg);
  font-weight: 600;
}

.day.disabled {
  color: var(--control-off);
  cursor: not-allowed;
}

.date-note {
  margin-top: 12px;
}

.legend {
  display: flex;
  align-items: center;
  gap: 18px;
  padding: 12px 4px;
  font-size: 12px;
  line-height: 18px;
  color: var(--text-2);
}

.legend .dot {
  display: inline-block;
  width: 10px;
  height: 10px;
  margin-right: 6px;
  border-radius: 3px;
  vertical-align: -1px;
}

.dot.selected {
  background: var(--action-bg);
}

.dot.available {
  background: var(--text-2);
}

.dot.disabled {
  background: var(--control-off);
}
</style>
