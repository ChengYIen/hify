<script setup lang="ts">
defineProps<{
  title: string
  description?: string
}>()
</script>

<template>
  <div class="page-header">
    <div class="page-header-left">
      <h1 class="page-title">{{ title }}</h1>
      <p v-if="description" class="page-desc">{{ description }}</p>
    </div>
    <div v-if="$slots.actions" class="page-header-right">
      <slot name="actions" />
    </div>
  </div>
</template>

<style scoped>
.page-header {
  display:         flex;
  align-items:     flex-start;
  justify-content: space-between;
  gap:             var(--hify-section-gap);
  margin-bottom:   var(--hify-section-gap);
  padding-bottom:  var(--hify-section-gap);
  border-bottom:   1px solid var(--hify-border-light);
}

.page-header-left {
  flex: 1;
  min-width: 0;
}

.page-title {
  margin:      0;
  font-size:   var(--hify-font-size-xl);
  font-weight: 700;
  color:       var(--hify-text-primary);
  line-height: var(--hify-line-height-tight);
  letter-spacing: -0.3px;
}

.page-desc {
  margin:      6px 0 0;
  font-size:   var(--hify-font-size-base);
  color:       var(--hify-text-tertiary);
  line-height: var(--hify-line-height-base);
}

.page-header-right {
  display:     flex;
  align-items: center;
  gap:         12px;
  flex-shrink: 0;
  padding-top: 2px;  /* 和标题基线对齐 */
}
</style>
