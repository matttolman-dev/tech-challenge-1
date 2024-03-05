<template>
  <Menu />
  <Calculator v-if="!isNaN(account.balance)" />
  <v-skeleton-loader v-else-if="account.loading" type="card"></v-skeleton-loader>
  <Login v-else></Login>
</template>

<script setup>
  import Menu from "@/components/Menu.vue";
  import {useAccountStore} from "@/store/app";
  import Login from "@/components/Login.vue";

  const account = useAccountStore()

</script>
