<script setup>
import {useAccountStore, useAddFundsStore, useAppStore, useCalcStore, useHistoryStore} from "@/store/app";

const account = useAccountStore()

const appStore = useAppStore()
const calc = useCalcStore()
const history = useHistoryStore()
const addFunds = useAddFundsStore()

/**
 * Loads the account information
 */
function load() {
  account.startLoad()
  fetch(appStore.root + "/api/v1/account/balance", {
    credentials: "include"
  })
    .then(async res => {
      if (res.status === 401 || res.status === 403) {
        account.requireLogin()
        // If we don't have permissions or our session expired then logout
        try {
          await fetch(appStore.root + "/api/v1/auth/logout", {
            credentials: "include",
            method: 'POSt'
          })
        }
        catch (e) {
          console.error('Clearing server session data failed!', e)
        }

        // And clear our in-memory session data
        account.requireLogin()
        calc.reset()
        history.reset()
        addFunds.reset()
      } else if (res.status === 200) {
        const json = await res.json()
        account.setBalance(json.balance)
      } else {
        console.error('Unknown status ' + res.status)
        account.setError('Something went wrong')
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
