<script setup>
import {useAccountStore, useAppStore, useHistoryStore} from "@/store/app";

const history = useHistoryStore()
const app = useAppStore()
const account = useAccountStore()

/**
 * Handles loading history
 */
function load() {
  history.loading = true
  let url = `${app.root}/api/v1/account/history?num=${history.pageSize}`
  if (history.cursor) {
    url += '&cursor=' + encodeURI(history.cursor)
  }

  if (history.search) {
    url += "&filter=" + encodeURI(history.search)
  }

  // Grabs our history
  fetch(url, {
    credentials: 'include'
  })
    .then(async res => {
      if (res.status === 200) {
        const json = await res.json()
        history.items = json.history
        history.error = null
        history.page = json.page
        history.length = json.pages
      } else if (res.status === 401 || res.status === 403) {
        account.needLogin()
      } else {
        history.error = 'Something went wrong'
      }
    })
    .catch(() => {
      history.error = 'Something went wrong'
    })
    .finally(() => {
      history.loading = false
    })
}

load()

/**
 * Handles search input
 * Debounces to only do search after 500ms of no input
 * @param value {string}
 */
function searchDebounced(value) {
  if (history.timeout) {
    clearTimeout(history.timeout)
  }
  history.search = value
  console.log(value)
  history.timeout = setTimeout(() => load(), 500)
}
</script>

<template>
  <v-form>
    <v-text-field @update:modelValue="searchDebounced" prepend-inner-icon="mdi-magnify" type="search" label="Search History" clearable />
  </v-form>
  <div v-if="history.loading">
    <v-list v-if="history.loading">
      <v-list-item
        v-for="n in 5"
        :key="n"
        title="">
        <template v-slot:default>
          <v-skeleton-loader type="list-item"></v-skeleton-loader>
        </template>
      </v-list-item>
    </v-list>
  </div>
  <div v-else>
    <v-list>
      <v-list-item
        v-for="n in history.items"
        :key="n.cursor"
        :value="n"
      >
        <v-list-item-subtitle>
          {{ n.time }} - {{ n.operation }}
          <span v-if="n.amount < 0">(</span>
          ${{ Math.floor(Math.abs(n.amount) / 100) }}.{{ Math.abs(n.amount) % 100 }}
          <span v-if="n.amount < 0">)</span>
        </v-list-item-subtitle>
        <v-list-item-title
          v-text="n.response?.res || ('(Added Funds - $' + Math.floor(n.amount / 100) + '.' + (Math.abs(n.amount) % 100) + ')')">
        </v-list-item-title>
      </v-list-item>
    </v-list>
    <div v-if="history.items.length === 0">
      No items found.
    </div>
    <v-pagination
      @update:modelValue="value => {history.page = value - 1; load()}"
      v-model="history.page"
      :length="history.length"
    ></v-pagination>
  </div>
  <v-alert class="bg-error mt-2" v-if="history.error">
    {{ history.error }}
  </v-alert>
</template>

<style scoped>

</style>
