// Utilities
import { defineStore } from 'pinia'

export const useAppStore = defineStore('app', {
  state: () => ({
    root: "http://localhost:8080"
  }),
})

export const useLoginStore = defineStore('login', {
  state: () => ({
    form: false,
    email: null,
    password: null,
    passwordConfirm: null,
    createAccount: false,
    loggingIn: false,
    serverError: null
  }),
  actions: {
    toggleAccountCreate() {
      this.password = null
      this.passwordConfirm = null
      this.createAccount = !this.createAccount
    },
    reset() {
      this.form = false
      this.email = null
      this.password = null
      this.passwordConfirm = null
      this.createAccount = false
      this.loggingIn = false
      this.serverError = null
    }
  }
})

export const useAddFundsStore = defineStore('addFunds', {
  state: () => ({
    form: false,
    amount: null,
    loading: false,
    error: null,
    serverError: null,
    dialog: false,
    success: false
  }),
  actions: {
    reset() {
      this.form = false;
      this.amount = null
      this.loading = false
      this.error = null
      this.serverError = null
      this.dialog = false
    }
  }
})

export const useCalcStore = defineStore('calc', {
  state: () => ({
    res: 0,
    cur: '',
    op: '',
    loading: false,
    moreFunds: false,
    error: null,
    text: ''
  })
})

export const useAccountStore = defineStore('account', {
  state: () => ({
    balance: NaN,
    needLogin: false,
    loading: true,
    error: null,
  }),

  actions: {
    requireLogin() {
      this.needLogin = true
      this.balance = NaN
    },
    login() {
      this.needLogin = false
    },
    startLoad() {
      this.loading = true
    },
    setBalance(balance) {
      this.balance = balance
      this.error = null
    },
    endLoad() {
      this.loading = false
    },
    setError(err) {
      this.error = err
    }
  }
})

export const useHistoryStore = defineStore('history', {
  state: () => ({
    loading: true,
    items: [],
    cursor: null,
    next: true,
    start: 1,
    end: Number.MAX_SAFE_INTEGER,
    error: null,
    pageSize: 10,
    page: 1
  })
})
