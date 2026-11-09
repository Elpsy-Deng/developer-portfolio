<script setup lang="ts">
export type Pair = { first: string; second: string }
defineProps<{ label: string; firstLabel: string; secondLabel: string; disabled?: boolean; url?: boolean }>()
const model = defineModel<Pair[]>({ required: true })
</script>
<template>
  <fieldset class="admin-pairs" :disabled="disabled"><legend>{{ label }}</legend>
    <div v-for="(row, index) in model" :key="index" class="admin-pair">
      <label>{{ firstLabel }} {{ index + 1 }}<input v-model="row.first" required maxlength="160" /></label>
      <label>{{ secondLabel }} {{ index + 1 }}<textarea v-if="!url" v-model="row.second" rows="3" required maxlength="5000" /><input v-else v-model="row.second" type="url" pattern="https://.*" required maxlength="2048" /></label>
      <button type="button" class="button button-outline" @click="model.splice(index, 1)">移除{{ label }} {{ index + 1 }}</button>
    </div>
    <button type="button" class="button button-outline" :disabled="model.length >= 30" @click="model.push({ first: '', second: '' })">新增{{ label }}</button>
  </fieldset>
</template>
