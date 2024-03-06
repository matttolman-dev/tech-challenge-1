<script setup>
import {useAccountStore} from "@/store/app";

const account = useAccountStore()
</script>

<template>
<v-expansion-panels>
  <v-expansion-panel>
    <v-expansion-panel-title>
      History
    </v-expansion-panel-title>
    <v-expansion-panel-text v-if="!account.loading">
      <HistoryList />
    </v-expansion-panel-text>
  </v-expansion-panel>
</v-expansion-panels>
</template>

<style scoped>

</style>
