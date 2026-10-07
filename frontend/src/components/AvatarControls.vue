<script setup lang="ts">
const props = defineProps<{
  position: { x: number; y: number; zoom: number }
  saving: boolean
  message: string
  error: string
}>()

const emit = defineEmits<{
  save: []
  change: [value: Partial<{ x: number; y: number; zoom: number }>]
}>()

function update(field: 'x' | 'y' | 'zoom', event: Event) {
  const value = Number((event.target as HTMLInputElement).value)
  emit('change', { [field]: value })
}
</script>

<template>
  <div class="editor">
    <label><span>Horizontal <output>{{ props.position.x }}%</output></span><input :value="props.position.x" type="range" min="0" max="100" step="1" @input="update('x', $event)" /></label>
    <label><span>Vertical <output>{{ props.position.y }}%</output></span><input :value="props.position.y" type="range" min="0" max="100" step="1" @input="update('y', $event)" /></label>
    <label><span>Zoom <output>{{ Number(props.position.zoom).toFixed(2) }}x</output></span><input :value="props.position.zoom" type="range" min="1" max="2" step="0.01" @input="update('zoom', $event)" /></label>
    <button class="save" type="button" :disabled="props.saving" @click="emit('save')">{{ props.saving ? 'Salvando…' : 'Salvar no Supabase' }}</button>
    <p v-if="props.message" class="success">{{ props.message }}</p>
    <p v-if="props.error" class="error">{{ props.error }}</p>
  </div>
</template>
