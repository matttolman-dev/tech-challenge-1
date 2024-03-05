<script setup>
import {useAccountStore, useAppStore} from "@/store/app";

const account = useAccountStore()

const appStore = useAppStore()

function load() {
  account.startLoad()
  fetch(appStore.root + "/api/v1/account/balance", {
    credentials: "include"
  })
    .then(async res => {
      console.log(res)
      if (res.status == 401 || res.status == 403) {
        account.requireLogin()
      } else {
        const json = await res.json()
        account.setBalance(json.balance)
      }
    })
    .catch(err => account.setError(err))
    .finally(() => account.endLoad())
}

load()
</script>

<template>

</template>

<style scoped>

</style>
