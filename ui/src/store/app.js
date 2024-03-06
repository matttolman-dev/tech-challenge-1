// Utilities
import { defineStore } from 'pinia'

export const useAppStore = defineStore('app', {
  state: () => ({
    // For local development (in standalone vue) uncomment this line and comment the next line
    // root: "http://localhost:8080",
    root: ""
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
    res: null,
    cur: '',
    op: '',
    clear: false,
    overwrite: false,
    loading: false,
    moreFunds: false,
    error: null,
    text: ''
  }),
  actions: {
    reset() {
      this.cur = ''
      this.res = null
      this.op = ''
      this.clear = false
      this.overwrite = false
      this.loading = false
      this.moreFunds = false
      this.error = false
      this.text = ''
    },
    clearCalc() {
      this.cur = ''
      this.res = null
      this.op = ''
      this.clear = false
      this.overwrite = false
    }
  }
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
      this.loading = true
      this.error = null
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
    error: null,
    search: null,
    length: 1,
    pageSize: 10,
    page: 1,
    timeout: null
  }),
  actions: {
    reset() {
      this.loading = true
      this.items = []
      this.error = null
      this.search = null
      this.length = 1
      this.pageSize = 10
      this.page = 1
      if (this.timeout != null) {
        clearTimeout(this.timeout)
      }
      this.timeout = null
    }
  }
})
