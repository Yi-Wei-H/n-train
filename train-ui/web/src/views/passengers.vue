<template>
  <div class="page page-plain passengers-page">
    <PageNav title="乘车人" />

    <div class="card-list">
      <article v-for="p in passengers" :key="p.id" class="passenger-card card">
        <div class="passenger-main">
          <span class="passenger-avatar"><AppIcon name="user" size="lg" /></span>
          <div class="grow">
            <div class="passenger-name">
              {{ p.name }}
              <span v-if="p.isSelf" class="chip action">本人</span>
            </div>
            <div class="caption">{{ typeName(p.passengerType) }}<span v-if="p.phone"> · {{ maskPhone(p.phone) }}</span></div>
            <div class="passenger-id mono">{{ maskIdCard(p.idCardNo) }}</div>
          </div>
          <div v-if="p.isSelf" class="self-lock">
            <AppIcon name="lock" size="sm" />
            <span>实名信息</span>
          </div>
          <div v-else class="passenger-ops">
            <button type="button" class="btn icon op" aria-label="编辑乘车人" @click="openEdit(p)">
              <AppIcon name="edit" size="sm" />
            </button>
            <button type="button" class="btn icon op danger-icon" aria-label="删除乘车人" @click="remove(p)">
              <AppIcon name="trash" size="sm" />
            </button>
          </div>
        </div>
      </article>

      <div v-if="!passengers.length" class="empty">
        <AppIcon name="users" size="xl" />
        <span>还没有乘车人</span>
      </div>
    </div>

    <div class="bottom-bar single">
      <button type="button" class="btn solid touch block" @click="openEdit(null)">
        <AppIcon name="plus" size="sm" />新增乘车人
      </button>
    </div>

    <van-popup v-model:show="dialog" position="bottom" round>
      <div class="sheet">
        <div class="sheet-grab" />
        <div class="sheet-title">{{ form.id ? '编辑乘车人' : '新增乘车人' }}</div>

        <div class="sheet-form">
          <label class="field">
            <span class="field-label">姓名</span>
            <input v-model="form.name" class="input" placeholder="与证件一致" maxlength="20" />
          </label>
          <label class="field">
            <span class="field-label">证件号</span>
            <input v-model="form.idCardNo" class="input" placeholder="身份证号" maxlength="18" />
          </label>
          <div class="field">
            <span class="field-label">类型</span>
            <div class="radio-row">
              <button
                v-for="t in types"
                :key="t.value"
                type="button"
                class="radio-pill"
                :class="{ on: form.passengerType === t.value }"
                :aria-pressed="form.passengerType === t.value"
                @click="form.passengerType = t.value"
              >{{ t.label }}</button>
            </div>
          </div>
          <label class="field">
            <span class="field-label">手机号</span>
            <input v-model="form.phone" class="input" type="tel" placeholder="选填" maxlength="11" />
          </label>
        </div>

        <div class="sheet-actions">
          <button type="button" class="btn neutral touch" @click="dialog = false">取消</button>
          <button type="button" class="btn solid touch" @click="save">保存</button>
        </div>
      </div>
    </van-popup>
  </div>
</template>

<script setup>
import { onMounted, reactive, ref } from 'vue'
import { showConfirmDialog } from 'vant'
import { showSuccessToast, showToast } from '../utils/toast'
import request from '../utils/request'
import { encrypt } from '../utils/aes'
import { maskPhone, maskIdCard } from '../utils/mask'
import PageNav from '../components/PageNav.vue'
import AppIcon from '../components/AppIcon.vue'

const passengers = ref([])
const dialog = ref(false)
const emptyForm = { id: null, name: '', idType: 1, idCardNo: '', phone: '', passengerType: 1 }
const form = reactive({ ...emptyForm })
const types = [
  { value: 1, label: '成人' },
  { value: 2, label: '儿童' },
  { value: 3, label: '学生' }
]

function typeName(t) { return { 1: '成人', 2: '儿童', 3: '学生' }[t] }

async function load() {
  passengers.value = await request.get('/passenger/list')
}

function openEdit(p) {
  if (p?.isSelf) {
    return showToast('本人乘车人信息不可修改')
  }
  Object.assign(form, emptyForm, p || {})
  dialog.value = true
}

async function save() {
  if (!form.name || !form.idCardNo) return showToast('姓名和证件号必填')
  // 姓名/证件/手机号 AES 传输加密（本人乘车人编辑时证件号不可改，服务端以 id 定位）
  await request.post('/passenger', {
    ...form,
    name: encrypt(form.name),
    idCardNo: encrypt(form.idCardNo),
    phone: form.phone ? encrypt(form.phone) : ''
  })
  showSuccessToast('已保存')
  dialog.value = false
  load()
}

async function remove(p) {
  try {
    await showConfirmDialog({ title: '删除乘车人', message: `确认删除 ${p.name}？` })
  } catch { return }
  await request.delete(`/passenger/${p.id}`)
  showSuccessToast('已删除')
  load()
}

onMounted(load)
</script>

<style scoped>
.passengers-page {
  padding-bottom: calc(80px + env(safe-area-inset-bottom));
}

.passenger-card {
  padding: 14px 16px;
}

.passenger-main {
  display: flex;
  align-items: center;
  gap: 12px;
}

.passenger-avatar {
  width: 40px;
  height: 40px;
  flex-shrink: 0;
  display: grid;
  place-items: center;
  border-radius: 50%;
  background: var(--action-soft);
  color: var(--action-text);
}

.passenger-name {
  display: flex;
  align-items: center;
  gap: 6px;
  font-size: 15px;
  line-height: 22px;
  font-weight: 600;
  color: var(--text-1);
}

.passenger-id {
  margin-top: 2px;
  color: var(--text-3);
}

.passenger-ops {
  display: flex;
  align-items: center;
  gap: 0;
  flex-shrink: 0;
}

.self-lock {
  display: inline-flex;
  align-items: center;
  gap: 4px;
  flex-shrink: 0;
  color: var(--text-3);
  font-size: 12px;
  line-height: 18px;
}

.self-lock svg.i {
  color: var(--text-3);
}

.op {
  width: 40px;
  height: 40px;
}

.danger-icon {
  color: var(--danger-text);
}

.sheet-form {
  display: flex;
  flex-direction: column;
  gap: 14px;
}

.radio-row {
  display: flex;
  gap: 8px;
}

.radio-pill {
  flex: 1;
  height: 44px;
  border: 1px solid var(--border-strong);
  border-radius: 8px;
  background: var(--bg-card);
  color: var(--text-2);
  font: 500 15px/22px var(--font-sans);
  cursor: pointer;
  transition: background-color var(--dur), border-color var(--dur), color var(--dur);
}

.radio-pill.on {
  border-color: var(--action-bg);
  background: var(--action-soft);
  color: var(--action-text);
}
</style>
