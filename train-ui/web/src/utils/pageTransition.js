import { ref } from 'vue'

// 页面切换动画方向：仅在进入/离开日期选择页时启用
export const pageTransition = ref('')

export function setPageTransition(name) {
  pageTransition.value = name
}
