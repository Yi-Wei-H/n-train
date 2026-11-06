<template>
  <el-tooltip placement="bottom-end" effect="light" :show-after="0" popper-class="page-note-popper">
    <template #content>
      <div class="page-note"><slot /></div>
    </template>
    <button type="button" class="page-note-btn" aria-label="注意事项">
      <svg class="page-note-icon" viewBox="0 0 24 24" aria-hidden="true">
        <circle cx="12" cy="12" r="10" />
        <path d="M12 16v-4M12 8h.01" />
      </svg>
    </button>
  </el-tooltip>
</template>

<style scoped>
.page-note-btn {
  width: 32px;
  height: 32px;
  display: grid;
  place-items: center;
  border: 0;
  border-radius: var(--r-control);
  background: transparent;
  color: var(--text-2);
  cursor: help;
  transition: background-color var(--dur), color var(--dur);
}

.page-note-icon {
  width: 16px;
  height: 16px;
  fill: none;
  stroke: currentColor;
  stroke-width: 2;
  stroke-linecap: round;
  stroke-linejoin: round;
}

.page-note-btn:hover {
  background: var(--bg-subtle);
  color: var(--text-1);
}
</style>
