<template>
  <div class="page ai-page">
    <!-- 顶栏：会话标题 + 注意事项 -->
    <header class="ai-header">
      <div class="ai-title">{{ title }}</div>
      <button type="button" class="notice-btn" aria-label="查看注意事项" @click="noticeShow = true">
        <AppIcon name="info" size="lg" />
      </button>
    </header>

    <!-- 消息区 -->
    <div class="msg-list" ref="listEl" @scroll.passive="onListScroll">
      <div v-if="loading" class="list-loading">
        <div class="skeleton skeleton-bubble" style="width: 62%; height: 44px" />
        <div class="skeleton skeleton-bubble" style="width: 82%; height: 68px" />
      </div>
      <template v-else>
        <div v-if="hasMoreHistory" class="history-tip">
          {{ loadingHistory ? '加载更早消息…' : '上滑加载更早消息' }}
        </div>
        <div v-for="item in bubbles" :key="item.key">
          <div v-if="item.type === 'divider'" class="divider">
            <div class="divider-line">新会话</div>
            <div class="divider-tip">提问新问题</div>
          </div>
          <div v-else-if="item.type === 'user'" class="row-user" :style="{ marginTop: item.gap + 'px' }">
            <div class="bubble user-bubble">{{ item.text }}</div>
          </div>
          <div v-else class="row-ai" :style="{ marginTop: item.gap + 'px' }">
            <div class="bubble ai-bubble">
              <span v-if="item.round.streaming && !item.round.reply" class="thinking">思考中<span class="dots"><i /><i /><i /></span></span>
              <span v-else-if="item.round.errorText" class="muted">{{ item.round.errorText }}</span>
              <span v-else-if="item.round.failed" class="muted">（本轮回复失败）</span>
              <div v-else-if="item.round.reply" class="md" v-html="renderMd(item.round.reply)"></div>
              <div v-if="item.round.stopped || item.round.interrupted" class="bubble-note">
                {{ item.round.stopped ? '已停止展示，回复在后台完成' : '连接中断，回复在后台完成，刷新后可查看' }}
              </div>
            </div>
          </div>
        </div>
      </template>
    </div>

    <!-- 卡片行 + 输入区：页面高度已扣减全局 tabbar（fixed 不占流），随流布局即固定在 tabbar 之上 -->
    <div class="input-dock">
      <div v-if="cards.length" class="card-row">
        <button
          v-for="card in cards"
          :key="card.id"
          type="button"
          class="card-chip"
          :class="{ off: replying || disabled }"
          @click="sendCard(card)"
        >
          <AppIcon :name="cardIcon(card.icon)" size="sm" />
          <span>{{ card.cardName }}</span>
        </button>
      </div>
      <div v-if="overLimit" class="limit-tip">已超出内容上限</div>
      <div class="input-row">
        <textarea
          ref="inputEl"
          v-model="draft"
          class="chat-input"
          rows="1"
          :disabled="disabled"
          placeholder="输入问题，AI 为你解答"
          @keydown="onKeydown"
        ></textarea>
        <button
          type="button"
          class="send-btn"
          :class="{ stop: replying }"
          :disabled="!replying && (!canSend || disabled)"
          @click="replying ? stopReply() : send()"
        >{{ replying ? '停止' : '发送' }}</button>
      </div>
      <div v-if="disabled" class="disabled-tip">{{ disabledReason }}</div>
    </div>

    <!-- 注意事项：底部弹层 -->
    <van-popup v-model:show="noticeShow" position="bottom" round>
      <div class="sheet">
        <div class="sheet-grab" />
        <div class="sheet-title">注意事项</div>
        <div class="notice-list">
          <p v-for="(line, i) in NOTICE_LINES" :key="i" class="notice-line">{{ line }}</p>
        </div>
        <button type="button" class="btn neutral touch block" @click="noticeShow = false">知道了</button>
      </div>
    </van-popup>

    <!-- 预订单确认：底部弹层。载荷只含展示字段，确认/取消只发意图，
         操作由服务端按会话反查执行，结果文本以 AI 回复气泡回填 -->
    <van-popup :show="!!pending" position="bottom" round @update:show="(v) => { if (!v && !confirming) pending = null }">
      <div class="sheet">
        <div class="sheet-grab" />
        <div class="sheet-title">{{ pending?.type === 2 ? '退票确认' : '购票确认' }}</div>
        <div class="sheet-options confirm-body">{{ pending?.summary }}</div>
        <p v-if="pending?.expiresAt" class="confirm-expire">请在 {{ expireText }} 前确认，逾期自动作废</p>
        <div class="sheet-actions">
          <button type="button" class="btn quiet" :disabled="confirming" @click="act(false)">取消</button>
          <button type="button" class="btn neutral" :disabled="confirming" @click="act(true)">确认</button>
        </div>
      </div>
    </van-popup>
  </div>
</template>

<script setup>
import { computed, nextTick, onBeforeUnmount, onMounted, reactive, ref, watch } from 'vue'
import { showToast } from '../utils/toast'
import agentApi from '../utils/agentApi'
import MarkdownIt from 'markdown-it'
import { postChatStream } from '../utils/aiSse'
import AppIcon from '../components/AppIcon.vue'

// 注意事项与欢迎文案为前端硬编码常量，随前端版本发布，不走接口
const NOTICE_LINES = [
  '· AI 助手可查询车次与余票、协助购票（确认后生成待支付订单）、查询与退订本人订单，也可日常问答。',
  '· 涉及购票、退票等操作，AI 会先给出确认摘要，经你确认后才执行。',
  '· AI 不会代你支付，也不会索要密码、验证码或完整证件号。',
  '· 超过 72 小时未对话会自动开启新会话，历史记录仍可查看。',
  '· 长期记忆仅保存你确认的内容，可随时查看、修改或删除。',
  '· 回复由 AI 生成，车次、余票等信息请以查询结果与订单页为准。'
]

const WELCOME_TEXT = `你好，我是铁旅通 AI 助手，可以帮你：
· 查询车次、余票与经停信息
· 协助购票（确认后生成待支付订单）
· 查询订单、协助退票
· 解答乘车与出行问题
点击下方快捷卡片，或直接输入开始～`

// 管理端维护的 icon 标识 → 图标名，未匹配回落 chat
const CARD_ICONS = {
  ticket: 'ticket',
  search: 'search',
  policy: 'info',
  buy: 'ticket',
  refund: 'refresh',
  remind: 'clock',
  calendar: 'calendar',
  station: 'location',
  help: 'info',
  service: 'chat'
}

const TITLE_PLACEHOLDER = '新会话'

// 距底部不足该值视为“贴底”，流式增量继续跟随滚动
const NEAR_BOTTOM_PX = 80
// 滚动到距顶部不足该值时，自动加载更早一页
const LOAD_OLDER_PX = 40

// AI 回复 Markdown 渲染：html=false 转义模型输出的原始标签（防注入），
// breaks 单换行即换行（对齐聊天排版），linkify 自动识别 URL
const md = new MarkdownIt({ html: false, breaks: true, linkify: true })
const mdLinkOpen = md.renderer.rules.link_open
md.renderer.rules.link_open = (tokens, idx, options, env, self) => {
  tokens[idx].attrSet('target', '_blank')
  tokens[idx].attrSet('rel', 'noopener')
  return mdLinkOpen ? mdLinkOpen(tokens, idx, options, env, self) : self.renderToken(tokens, idx, options)
}

/** 文本级缓存：流式轮每次 delta 全新文本必然重渲，历史轮命中缓存不再解析 */
const mdCache = new Map()
function renderMd(text) {
  let html = mdCache.get(text)
  if (html === undefined) {
    html = md.render(text)
    if (mdCache.size > 500) {
      mdCache.clear()
    }
    mdCache.set(text, html)
  }
  return html
}
const TITLE_POLL_INTERVAL = 2000
const TITLE_POLL_MAX = 10

const loading = ref(true)
const title = ref(TITLE_PLACEHOLDER)
const conversationId = ref(null)
const cards = ref([])
const draft = ref('')
const replying = ref(false)
const disabled = ref(false)
const disabledReason = ref('')
const messages = ref([])
const noticeShow = ref(false)
// 预订单确认卡片（type/summary/expiresAt，由 SSE confirmation 事件或 current 恢复）
const pending = ref(null)
const confirming = ref(false)
const listEl = ref(null)
const inputEl = ref(null)
// 列表是否跟随底部（用户在底部附近为 true；上翻看历史时置 false，不打扰）
const stickToBottom = ref(true)
// 更早轮次：一页 10 轮，上滑到顶继续取
const hasMoreHistory = ref(false)
const loadingHistory = ref(false)

let abortController = null
let titlePollTimer = null
let seq = 0

const canSend = computed(() => draft.value.trim().length > 0)

// 确认截止时间：可解析则本地化展示，否则原样显示
const expireText = computed(() => {
  const d = new Date(pending.value?.expiresAt)
  return isNaN(d.getTime()) ? pending.value?.expiresAt : d.toLocaleString('zh-CN', { hour12: false })
})

// 输入上限：超出 200 字仅在输入框上方红字提示，不截断输入
const INPUT_MAX = 200
const overLimit = computed(() => draft.value.length > INPUT_MAX)

// 渲染单元：一轮拆用户/AI 两个气泡；同侧连续间距 8px、异侧 16px，分隔行重置
const bubbles = computed(() => {
  const list = []
  let prevSide = null
  const push = (item, side) => {
    item.gap = prevSide === side ? 8 : prevSide === null ? 0 : 16
    prevSide = side
    list.push(item)
  }
  for (const msg of messages.value) {
    if (msg.kind === 'divider') {
      list.push({ key: msg.key, type: 'divider' })
      prevSide = null
    } else if (msg.kind === 'welcome') {
      push({ key: msg.key, type: 'ai', round: { reply: WELCOME_TEXT } }, 'ai')
    } else {
      const userText = userTextOf(msg)
      if (userText) {
        push({ key: msg.key + '-u', type: 'user', text: userText }, 'user')
      }
      // 无任何可展示内容时不渲染 AI 侧（发送后 meta 到达前的瞬间）
      if (msg.streaming || msg.reply || msg.errorText || msg.failed || msg.stopped || msg.interrupted) {
        push({ key: msg.key + '-a', type: 'ai', round: msg }, 'ai')
      }
    }
  }
  return list
})

/** 服务端轮次 → 本地渲染项：首屏历史与上滑翻页共用 */
function toRound(m) {
  return {
    kind: 'round',
    key: 'h' + (m.id ?? seq++),
    // 保留服务端轮次 id：上滑翻页以最早一条为 beforeId
    id: m.id ?? null,
    userContent: m.userContent || '',
    cardId: m.cardId || null,
    reply: m.reply || '',
    failed: !m.reply,
    streaming: false
  }
}

// 卡片轮优先显示卡片主题，匹配不到 active 卡片回落用户原文（不展示隐藏提示词）
function userTextOf(msg) {
  if (msg.cardId) {
    const card = cards.value.find((c) => c.id === msg.cardId)
    if (card) {
      return card.cardName
    }
  }
  return msg.userContent || ''
}

function cardIcon(icon) {
  return CARD_ICONS[icon] || 'chat'
}

onMounted(async () => {
  const cardsReady = agentApi.get('/quick-card/active')
    .then((list) => {
      cards.value = [...(list || [])].sort((a, b) => (a.sortNo ?? 0) - (b.sortNo ?? 0))
    })
    .catch(() => {
      // 卡片加载失败：整行隐藏，不占位不报错
      cards.value = []
    })
  try {
    await Promise.all([loadCurrent(), cardsReady])
  } finally {
    loading.value = false
    // 历史渲染完成后定位到底部（聊天页惯例：进页即见最新消息）
    await scrollToBottomForced()
  }
})

onBeforeUnmount(() => {
  stopTitlePolling()
  abortController?.abort()
})

async function loadCurrent() {
  try {
    applyCurrent(await agentApi.get('/ai/conversations/current'))
  } catch {
    // current 拉取失败（agentApi 已 toast）：回落欢迎态，发送链路仍可用
    applyCurrent(null)
  }
}

function applyCurrent(data) {
  disabled.value = !!data?.disabled
  disabledReason.value = data?.disabledReason || ''
  conversationId.value = data?.conversationId ?? null
  // 刷新/多设备恢复：镜像仍存在待确认单则恢复底部确认弹层
  pending.value = data?.pendingConfirm || null
  // 翻篇态顶栏只显示占位：顶栏对应当前（或待创建）会话，不回显旧会话标题
  title.value = data?.conversationId && !data?.archived ? data?.title || TITLE_PLACEHOLDER : TITLE_PLACEHOLDER
  const list = []
  for (const m of data?.messages || []) {
    list.push(toRound(m))
  }
  if (!list.length && !data?.conversationId && !data?.archived) {
    // 空欢迎态：欢迎消息仅本地渲染不落库，会话建立后由真实历史取代
    list.push({ kind: 'welcome', key: 'welcome' })
  }
  if (data?.archived) {
    // 翻篇：旧记录只读展示，末尾以“新会话”分隔行标记边界
    list.push({ kind: 'divider', key: 'd' + seq++ })
  }
  messages.value = list
  // 首屏只返回最近一页；还有更早则由上滑触发翻页
  hasMoreHistory.value = !!data?.hasMore
  if (data?.activeRequest) {
    // 其它设备/刷新前的回复仍在进行：恢复回复态，等待或刷新查看，不主动拉流重连
    replying.value = true
    messages.value.push(reactive({
      kind: 'round', key: 'r' + seq++, userContent: '', cardId: null,
      reply: '', streaming: true, recovered: true
    }))
  }
}

function onKeydown(e) {
  // Enter 发送、Shift+Enter 换行；输入法组词中的 Enter 不触发
  if (e.key === 'Enter' && !e.shiftKey && !e.isComposing) {
    e.preventDefault()
    send()
  }
}

async function send() {
  const text = draft.value.trim()
  if (!text || replying.value || disabled.value) {
    return
  }
  draft.value = ''
  await startChat({ content: text })
}

function sendCard(card) {
  if (disabled.value) {
    return
  }
  if (replying.value) {
    return showToast('回复进行中')
  }
  // 卡片点击即发送，只提交 cardId，不回传提示词
  startChat({ cardId: card.id })
}

async function startChat(payload) {
  // reactive 包装：流式期间对 streaming/reply 的修改必须驱动渲染（普通对象改不动视图）
  const round = reactive({
    kind: 'round',
    key: 'r' + seq++,
    userContent: payload.content || '',
    cardId: payload.cardId || null,
    reply: '',
    streaming: false
  })
  messages.value.push(round)
  replying.value = true
  abortController = new AbortController()
  // 发送即跟底：等本帧渲染出用户气泡再滚，首条回复到达前的等待期也能看到刚发的消息
  await scrollToBottomForced()
  try {
    await postChatStream({
      url: '/api/agent/ai/chat',
      // 任何重试必须换新 clientRequestId
      body: { ...payload, clientRequestId: uuid() },
      signal: abortController.signal,
      onMeta: (meta) => onMeta(round, meta),
      onDelta: (delta) => onDelta(round, delta),
      onTitle: onTitleEvent,
      onConfirmation: onConfirmationEvent,
      onError: (text) => onStreamError(round, text),
      onDone: (done) => onDoneEvent(round, done)
    })
    // 流关闭但未收到 done：服务端按断流语义继续聚合落库，重进页面由 current 恢复
    if (round.streaming) {
      round.streaming = false
      round.interrupted = true
    }
  } catch (e) {
    if (e.name === 'AbortError') {
      round.streaming = false
      round.stopped = true
    } else if (e.code === 47001 || e.code === 47002) {
      // 无权限 / 超日配额：进入禁用态，文案用服务端返回
      disabled.value = true
      disabledReason.value = e.message
    } else {
      // meta 前失败：不产生 AI 气泡，用户气泡保留，可手动重发（换新 clientRequestId）
      showToast(e.message || '请求失败')
    }
  } finally {
    replying.value = false
    abortController = null
  }
}

function onMeta(round, meta) {
  // meta 到达即建空 AI 气泡与三点 typing
  round.streaming = true
  if (meta?.conversationReset) {
    // 服务端已翻篇：旧记录不清空，本轮消息前插入“新会话”分隔行；
    // 列表里混着旧会话记录，不能再按新会话 id 向前翻页
    const idx = messages.value.indexOf(round)
    messages.value.splice(idx >= 0 ? idx : messages.value.length, 0, { kind: 'divider', key: 'd' + seq++ })
    hasMoreHistory.value = false
    title.value = TITLE_PLACEHOLDER
  }
  if (meta?.conversationId) {
    conversationId.value = meta.conversationId
  }
  if (meta?.title) {
    title.value = meta.title
  }
  scrollFollow()
}

function onDelta(round, delta) {
  if (!delta) {
    return
  }
  round.reply += delta
  scrollFollow()
}

function onTitleEvent(t) {
  if (t?.title) {
    title.value = t.title
  }
}

function onStreamError(round, text) {
  round.streaming = false
  // 后端已统一包装用户向文案（业务文案/厂家归类文案/系统繁忙），此处直接展示并覆盖已流出的增量
  round.errorText = text || '系统繁忙，请稍后重试'
  // 限流类同时 toast（提示稍后再试的行动指引）
  if (/频繁|繁忙|稍后再试/.test(round.errorText)) {
    showToast(round.errorText)
  }
  // 错误文案替换掉已流出内容后高度变化，跟随时重新贴底
  scrollFollow()
}

function onDoneEvent(round, done) {
  round.streaming = false
  if (done?.title) {
    title.value = done.title
  }
  // 标题仍未就绪：短轮询 current 兜底，拿到终态或超时即停，超时停留占位
  const settled = done?.titleStatus === 'SUCCESS' || done?.titleStatus === 'FAILED'
  if (!settled && title.value === TITLE_PLACEHOLDER && conversationId.value) {
    startTitlePolling()
  }
  scrollFollow()
}

// 购票/退票确认卡片：预检轮结束由服务端推送，底部弹层展示；载荷只含展示字段，不含操作 id
function onConfirmationEvent(c) {
  if (c?.summary) {
    pending.value = { type: c.type, summary: c.summary, expiresAt: c.expiresAt }
  }
}

// 确认/取消：只发意图与会话 id，操作由服务端按身份反查执行；结果文本以 AI 回复气泡回填会话
async function act(confirm) {
  if (confirming.value || !conversationId.value) {
    return
  }
  confirming.value = true
  try {
    const message = await agentApi.post('/ai/order', {
      conversationId: conversationId.value,
      action: confirm ? 'CONFIRM' : 'CANCEL'
    })
    pending.value = null
    if (message) {
      messages.value.push(reactive({
        kind: 'round', key: 'r' + seq++, userContent: '', cardId: null,
        reply: String(message), streaming: false
      }))
      scrollToBottomForced()
    }
  } catch (e) {
    // 请求层已统一 toast；失败保留弹层供重试
  } finally {
    confirming.value = false
  }
}

function startTitlePolling() {
  stopTitlePolling()
  const targetId = conversationId.value
  let polled = 0
  titlePollTimer = setInterval(async () => {
    polled++
    if (polled > TITLE_POLL_MAX) {
      return stopTitlePolling()
    }
    try {
      const data = await agentApi.get('/ai/conversations/current')
      if (data?.conversationId !== targetId) {
        // 会话已翻篇：新标题由新会话自己的事件负责
        return stopTitlePolling()
      }
      if (data?.title) {
        title.value = data.title
      }
      if (data?.titleStatus === 'SUCCESS' || data?.titleStatus === 'FAILED') {
        stopTitlePolling()
      }
    } catch {
      // 单次轮询失败静默跳过，下个周期再试
    }
  }, TITLE_POLL_INTERVAL)
}

function stopTitlePolling() {
  if (titlePollTimer) {
    clearInterval(titlePollTimer)
    titlePollTimer = null
  }
}

function stopReply() {
  if (abortController) {
    // 停止仅断本地流：服务端继续聚合、落完整回复，重进页面按服务端记录展示
    abortController.abort()
    return
  }
  // current 恢复的回复态无本地流可停：等待完成或刷新查看
  showToast('上一条回复进行中，可刷新查看结果')
}

function nearBottom() {
  const el = listEl.value
  if (!el) {
    return true
  }
  return el.scrollHeight - el.scrollTop - el.clientHeight < NEAR_BOTTOM_PX
}

// 跟随状态只在用户滚动时更新：若在内容增量之后再算距离，新气泡已把列表撑高，
// 会被误判成“用户上翻看历史”而放弃跟随
function onListScroll() {
  const el = listEl.value
  if (!el) {
    return
  }
  stickToBottom.value = nearBottom()
  // 上滑到顶继续取更早一页
  if (el.scrollTop < LOAD_OLDER_PX) {
    loadOlder()
  }
}

/** 最早一条服务端轮次的 id：翻页锚点（本地欢迎行没有 id） */
function firstHistoryId() {
  for (const item of messages.value) {
    if (item.kind === 'round' && item.id) {
      return item.id
    }
  }
  return null
}

/** 上滑翻页：前插一页更早轮次，渲染后补偿滚动位置，视觉上停留在原消息 */
async function loadOlder() {
  if (!hasMoreHistory.value || loadingHistory.value || !conversationId.value) {
    return
  }
  const el = listEl.value
  const beforeId = firstHistoryId()
  if (!el || !beforeId) {
    return
  }
  loadingHistory.value = true
  const prevHeight = el.scrollHeight
  const prevTop = el.scrollTop
  try {
    const data = await agentApi.get('/ai/conversations/messages', {
      params: { conversationId: conversationId.value, beforeId }
    })
    const older = (data?.messages || []).map(toRound)
    if (older.length) {
      messages.value.unshift(...older)
    }
    hasMoreHistory.value = !!data?.hasMore
    await nextTick()
    el.scrollTop = el.scrollHeight - prevHeight + prevTop
  } catch (e) {
    // 请求层已统一提示，恢复可重试状态即可
    hasMoreHistory.value = true
  } finally {
    loadingHistory.value = false
  }
}

async function scrollFollow() {
  if (!stickToBottom.value) {
    return
  }
  await nextTick()
  scrollBottom()
}

/** 发送/进页等主动动作：无视当前滚动位置，渲染落地后强制回到底部 */
async function scrollToBottomForced() {
  stickToBottom.value = true
  await nextTick()
  scrollBottom()
}

function scrollBottom() {
  const el = listEl.value
  if (el) {
    el.scrollTop = el.scrollHeight
  }
}

watch(draft, () => nextTick(autoGrow))

// 自适应高度 1-4 行，超出内部滚动
function autoGrow() {
  const el = inputEl.value
  if (!el) {
    return
  }
  el.style.height = 'auto'
  el.style.height = Math.min(el.scrollHeight, 96) + 'px'
}

function uuid() {
  if (crypto.randomUUID) {
    return crypto.randomUUID()
  }
  // 非 secure context（如局域网 http 调试机）回落随机拼装，格式同 UUIDv4
  return 'xxxxxxxx-xxxx-4xxx-yxxx-xxxxxxxxxxxx'.replace(/[xy]/g, (c) => {
    const r = (Math.random() * 16) | 0
    return (c === 'x' ? r : (r & 0x3) | 0x8).toString(16)
  })
}
</script>

<style scoped>
.ai-page {
  display: flex;
  flex-direction: column;
  /* 移动端浏览器地址栏/工具栏收放时 100vh 会大于可视高度，优先用动态视口单位 */
  height: calc(100vh - var(--tabbar-h) - env(safe-area-inset-bottom));
  height: calc(100dvh - var(--tabbar-h) - env(safe-area-inset-bottom));
  min-height: 0;
  padding-bottom: 0;
  background: var(--bg-page);
}

/* 顶栏：品牌渐变底，与首页页头同源 */
.ai-header {
  position: sticky;
  top: 0;
  z-index: 10;
  display: flex;
  align-items: center;
  flex-shrink: 0;
  height: var(--nav-h);
  background: linear-gradient(180deg, var(--brand-1), var(--brand-2));
  color: #fff;
}

.ai-title {
  position: absolute;
  left: 50%;
  max-width: 60%;
  transform: translateX(-50%);
  font-size: 15px;
  line-height: 22px;
  font-weight: 600;
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
}

.notice-btn {
  width: 44px;
  height: 44px;
  display: grid;
  place-items: center;
  margin-left: auto;
  border: 0;
  background: transparent;
  color: #fff;
  cursor: pointer;
}

/* 消息区：独立滚动；min-height:0 解除 flex 子项的内容高度下限 */
.msg-list {
  flex: 1;
  min-height: 0;
  overflow-y: auto;
  padding: 12px 0 16px;
}

.list-loading {
  display: flex;
  flex-direction: column;
  gap: 10px;
  padding: 4px 16px;
}

.skeleton-bubble {
  border-radius: var(--r-panel);
}

.history-tip {
  padding: 0 0 12px;
  text-align: center;
  font-size: 12px;
  line-height: 18px;
  color: var(--text-3);
}

.row-user {
  display: flex;
  justify-content: flex-end;
  padding: 0 16px;
}

.row-ai {
  display: flex;
  align-items: flex-start;
  padding: 0 16px;
}

.bubble {
  max-width: 78%;
  padding: 10px 12px;
  border-radius: var(--r-panel);
  font-size: 15px;
  line-height: 24px;
  white-space: pre-wrap;
  word-break: break-word;
}

.user-bubble {
  background: var(--action-bg);
  color: var(--action-fg);
}

.ai-bubble {
  border: 1px solid var(--border);
  background: var(--bg-card);
  color: var(--text-1);
  white-space: normal;
}

/* 气泡内 Markdown：字号间距收敛到聊天比例，表格/代码块横向滚动 */
.md :deep(p) { margin: 0 0 6px; }
.md :deep(p:last-child) { margin-bottom: 0; }
.md :deep(ul), .md :deep(ol) { margin: 6px 0; padding-left: 18px; }
.md :deep(li) { margin: 2px 0; }
.md :deep(h1), .md :deep(h2), .md :deep(h3), .md :deep(h4) { margin: 8px 0 6px; font-size: 15px; }
.md :deep(code) { padding: 1px 5px; border-radius: 4px; background: var(--bg-subtle); font-size: 13px; }
.md :deep(pre) { margin: 6px 0; padding: 10px; border-radius: 8px; background: var(--bg-subtle); overflow-x: auto; }
.md :deep(pre code) { padding: 0; background: none; }
.md :deep(table) { display: block; margin: 6px 0; border-collapse: collapse; overflow-x: auto; max-width: 100%; }
.md :deep(th), .md :deep(td) { border: 1px solid var(--border); padding: 4px 8px; font-size: 13px; }
.md :deep(blockquote) { margin: 6px 0; padding: 2px 10px; border-left: 3px solid var(--border); color: var(--text-2); }
.md :deep(a) { color: var(--action-text); word-break: break-all; }
.md :deep(hr) { margin: 8px 0; border: none; border-top: 1px solid var(--border); }

.bubble-note {
  margin-top: 6px;
  font-size: 12px;
  line-height: 18px;
  color: var(--text-3);
}

/* 回复等待动画：思考中 + 三点逐个循环亮起 */
.thinking {
  display: inline-flex;
  align-items: center;
  color: var(--text-3);
  font-size: 14px;
}

.thinking .dots {
  display: inline-flex;
  align-items: center;
  margin-left: 2px;
}

.thinking .dots i {
  width: 5px;
  height: 5px;
  margin-left: 3px;
  border-radius: 50%;
  background: currentColor;
  animation: thinking-blink 1.2s infinite;
}

.thinking .dots i:nth-child(2) {
  animation-delay: .2s;
}

.thinking .dots i:nth-child(3) {
  animation-delay: .4s;
}

@keyframes thinking-blink {
  0%, 60%, 100% { opacity: .2; }
  30% { opacity: 1; }
}

/* 翻篇分隔行 */
.divider {
  margin: 20px 0;
  text-align: center;
}

.divider-line {
  font-size: 12px;
  line-height: 18px;
  color: var(--text-3);
}

.divider-tip {
  margin-top: 2px;
  font-size: 11px;
  line-height: 16px;
  color: var(--text-3);
}

/* 确认弹层正文与截止提示 */
.confirm-body {
  font-size: 14px;
  line-height: 22px;
  color: var(--text-1);
  white-space: pre-wrap;
  word-break: break-word;
}

.confirm-expire {
  margin-top: 10px;
  font-size: 12px;
  line-height: 18px;
  color: var(--text-3);
}

/* 卡片行 + 输入区 */
.input-dock {
  flex-shrink: 0;
  background: var(--bg-card);
  border-top: 1px solid var(--border);
}

.card-row {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
  padding: 8px 16px 0;
}

.card-chip {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  height: 36px;
  padding: 0 14px;
  border: 1px solid var(--border);
  border-radius: 9999px;
  background: var(--bg-card);
  color: var(--text-1);
  font: 400 13px/20px var(--font-sans);
  white-space: nowrap;
  cursor: pointer;
}

.card-chip svg.i {
  color: var(--action-text);
}

.card-chip.off {
  color: var(--text-3);
}

.card-chip.off svg.i {
  color: var(--text-3);
}

.input-row {
  display: flex;
  align-items: flex-end;
  gap: 8px;
  padding: 8px 12px 8px 16px;
}

.chat-input {
  flex: 1;
  max-height: 96px;
  padding: 7px 0;
  border: 0;
  background: transparent;
  font: 400 16px/24px var(--font-sans);
  color: var(--text-1);
  resize: none;
  outline: none;
  overflow-y: auto;
}

.chat-input::placeholder {
  color: var(--text-3);
}

.chat-input:disabled {
  color: var(--text-3);
}

.send-btn {
  flex-shrink: 0;
  height: 36px;
  padding: 0 18px;
  border: 0;
  border-radius: 9999px;
  background: var(--action-bg);
  color: var(--action-fg);
  font: 600 14px/20px var(--font-sans);
  cursor: pointer;
  transition: background-color var(--dur), scale var(--dur);
}

.send-btn:active {
  scale: .96;
}

.send-btn:disabled {
  opacity: .45;
}

.send-btn.stop {
  background: var(--text-3);
}

.disabled-tip {
  padding: 0 16px 10px;
  font-size: 12px;
  line-height: 18px;
  color: var(--text-3);
}

.limit-tip {
  padding: 4px 16px 0;
  font-size: 12px;
  line-height: 18px;
  color: var(--danger-text);
}

.notice-list {
  max-height: 46vh;
  margin-bottom: 16px;
  overflow-y: auto;
}

.notice-line {
  margin: 0 0 8px;
  font-size: 13px;
  line-height: 22px;
  color: var(--text-2);
}

.notice-line:last-child {
  margin-bottom: 0;
}
</style>
