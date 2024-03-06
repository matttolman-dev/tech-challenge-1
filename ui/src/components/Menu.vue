<script setup>
import {useAccountStore, useAppStore} from "@/store/app";
const account = useAccountStore()
const app = useAppStore()

let loading = false

/**
 * Logout the user
 */
function logout() {
  loading = true
  fetch(`${app.root}/api/v1/auth/logout`, {
    credentials: "include",
    method: 'POST',
    headers: {
      'content-type': 'application/json'
    },
  })
    .finally(() => {
      loading = false
      account.requireLogin()
    })
}
</script>

<template>
<v-app-bar class="bg-primary">
  <v-app-bar-title>Calculator</v-app-bar-title>
  <template v-slot:append>
    <v-btn v-if="!isNaN(account.balance)" @click="logout()" class="mx-9" variant="outlined" :loading="loading">Logout</v-btn>
    <AccountLoader v-if="account.loading"></AccountLoader>
    <AccountBalance v-else-if="!isNaN(account.balance)"></AccountBalance>
  </template>
</v-app-bar>
</template>

<style scoped>

</style>
