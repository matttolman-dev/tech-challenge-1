<template>
  <Menu/>
  <div v-if="!isNaN(account.balance)">
    <v-responsive max-width="344" class="mx-auto mt-4">
      <Calculator/>
      <History/>
    </v-responsive>
  </div>
  <v-skeleton-loader v-else-if="account.loading" type="card"></v-skeleton-loader>
  <Login v-else></Login>
</template>

<script setup>
import Menu from "@/components/Menu.vue";
import {useAccountStore} from "@/store/app";
import Login from "@/components/Login.vue";
import Calculator from "@/components/Calculator.vue";
import History from "@/components/History.vue";

const account = useAccountStore()

</script>
