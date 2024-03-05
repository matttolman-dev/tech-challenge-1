<script setup>
import {useAccountStore, useAddFundsStore, useAppStore} from "@/store/app";

const account = useAccountStore()
const addFunds = useAddFundsStore()
const app = useAppStore()

function num(value) {
  return +value > 0 ? null : 'Invalid value! Amount must be greater than zero!'
}

function submit() {
  addFunds.loading = true
  fetch(`${app.root}/api/v1/account/balance`, {
    method: 'PUT',
    credentials: 'include',
    headers: {
      'content-type': 'application/json',
    },
      body: JSON.stringify({
        amount: (+addFunds.amount) * 100
    })
  })
    .then(async res => {
      if (res.status == 401 || res.status == 403) {
        account.needLogin()
      }
      else if (res.status === 400) {
        addFunds.serverError = 'Invalid amount'
      }
      else if (res.status === 200) {
        addFunds.reset()
        account.setBalance(+(await res.json()).balance)
      }
    })
    .catch(e => {
      console.error(e)
      addFunds.serverError = 'Something went wrong!'
    })
    .finally(() => addFunds.loading = false)
}
</script>

<template>
  <v-dialog max-width="500" v-model="addFunds.dialog">
    <template v-slot:activator="{props: activatorProps}">
      <v-btn v-bind="activatorProps" variant="elevated" class="bg-secondary">
        ${{Math.floor(account.balance / 100)}}.{{account.balance % 100}}
      </v-btn>
    </template>
    <template v-slot:default="{ isActive }">
      <v-card title="Add Funds">
        <v-card-text>
          <v-form v-model="addFunds.form" @submit.prevent="submit">
            Current Funds: ${{Math.floor(account.balance / 100)}}.{{account.balance % 100}}
            <v-text-field :rules="[num]" v-model="addFunds.amount" type="number" label="Amount to add ($)" persistent-hint class="mt-2" />
            <v-container>
              <v-row>
                <v-col>
                  <v-btn :disabled="addFunds.loading" @click="addFunds.reset()" class="bg-grey-lighten-4" block>Cancel</v-btn>
                </v-col>
                <v-col>
                  <v-btn :loading="addFunds.loading" :disabled="!addFunds.form" type="submit" class="bg-primary" block>Add Funds</v-btn>
                </v-col>
              </v-row>
            </v-container>
          </v-form>
        </v-card-text>
      </v-card>
    </template>
  </v-dialog>
  <v-snackbar v-model="addFunds.success" multi-line class="bg-success">
    Funds Added!
    <template v-slot:actions>
      <v-btn
        color="red"
        variant="text"
        @click="snackbar = false"
      >
        Close
      </v-btn>
    </template>
  </v-snackbar>
</template>

<style scoped>

</style>
