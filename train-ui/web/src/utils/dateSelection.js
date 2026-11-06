import { ref } from 'vue'

// 日期选择页与首页之间的单向结果传递：选择后返回，首页重新挂载时取用
const pickedDate = ref(null)

export function setPickedDate(value) {
  pickedDate.value = value
}

export function takePickedDate() {
  const value = pickedDate.value
  pickedDate.value = null
  return value
}
