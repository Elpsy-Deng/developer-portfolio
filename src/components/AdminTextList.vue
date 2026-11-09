<script setup lang="ts">
import { computed } from 'vue'
const props = defineProps<{ label: string; id: string; disabled?: boolean }>()
const model = defineModel<string[]>({ required: true })
const text = computed({ get: () => model.value.join('\n'), set: (value: string) => { model.value = value.split('\n') } })
</script>
<template><div class="admin-field"><label :for="props.id">{{ label }}（每行一項）</label><textarea :id="props.id" v-model="text" rows="4" :disabled="disabled" maxlength="100000" /></div></template>
