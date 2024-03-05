<script setup>
import {useAccountStore} from "@/store/app";
const account = useAccountStore()
</script>

<template>
<v-app-bar class="bg-primary">
  <template v-slot:prepend>
    <v-app-bar-nav-icon></v-app-bar-nav-icon>
  </template>
  <v-app-bar-title>Calculator</v-app-bar-title>
  <template v-slot:append>
    <AccountLoader v-if="account.loading"></AccountLoader>
    <AccountBalance v-else-if="!isNaN(account.balance)"></AccountBalance>
  </template>
</v-app-bar>
</template>

<style scoped>

</style>
