<script setup>
import Joi from 'joi'
import {useAccountStore, useAppStore, useLoginStore} from "@/store/app"

const account = useAccountStore()
const app = useAppStore()

const loginData = useLoginStore()

function emailValidate(value) {
  loginData.serverError = null
  return Joi.object({Email: Joi.string().required().email({tlds: {allow: false}})}).validate({Email: value}).error?.message
}


function passwordValidate(value) {
  loginData.serverError = null
  return Joi.object({Password: Joi.string().min(8).required()}).validate({Password: value}).error?.message
}

function confirmValidate(value) {
  loginData.serverError = null
  if (value !== loginData.password) {
    return 'Passwords must match!'
  }
  return null
}

function onSubmit() {
  if (!loginData.form || emailValidate(loginData.email) || passwordValidate(loginData.password)) {
    return
  }

  loginData.loggingIn = true
  let promise = null

  if (loginData.createAccount) {
    promise = fetch(`${app.root}/api/v1/auth/signup`, {
      credentials: "include",
      method: 'POST',
      headers: {
        'content-type': 'application/json'
      },
      body: JSON.stringify({
        username: loginData.email,
        password: loginData.password,
        'password-confirm': loginData.passwordConfirm,
      })
    })
  } else {
    promise = fetch(`${app.root}/api/v1/auth/login`, {
      credentials: "include",
      method: 'POST',
      headers: {
        'content-type': 'application/json'
      },
      body: JSON.stringify({
        username: loginData.email,
        password: loginData.password
      })
    })
  }

  promise
    .then(res => {
      switch (res.status) {
        case 200:
        case 204: {
          loginData.reset()
          account.startLoad()
          return
        }
        case 401:
        case 403: {
          loginData.serverError = 'Invalid credentials'
          return;
        }
        case 429: {
          loginData.serverError = 'Too many attempts, your account is locked for 30 minnutes'
          return
        }
        default: {
          loginData.serverError = 'Something went wrong'
          return
        }
      }
    })
    .catch(() => loginData.serverError = 'Could not connect to server')
    .finally(() => loginData.loggingIn = false)
}

</script>

<template>
  <v-container>
    <v-row align="center" style="height: 80em; max-height: calc(100vh - 80px)">
      <v-col>
        <v-form v-model="loginData.form" @submit.prevent="onSubmit">
          <v-responsive class="mx-auto" max-width="344">
            <v-text-field v-model="loginData.email" clearable :readonly="loginData.loggingIn" :rules="[emailValidate]"
                          label="Email" persistent-hint type="email"></v-text-field>
            <v-text-field v-model="loginData.password" clearable :readonly="loginData.loggingIn"
                          :rules="[passwordValidate]" label="Password" persistent-hint type="password"></v-text-field>
            <v-text-field v-if="loginData.createAccount" v-model="loginData.passwordConfirm" clearable
                          :readonly="loginData.loggingIn" :rules="[passwordValidate, confirmValidate]" label="Confirm Password"
                          persistent-hint type="password"></v-text-field>
            <v-alert class="bg-error mb-2" v-if="loginData.serverError" :text="loginData.serverError"></v-alert>
            <v-btn :loading="loginData.loggingIn" type="submit" size="large" :disabled="!loginData.form"
                   :variant="'elevated'" class="ml-auto bg-secondary" on>
              {{ loginData.createAccount ? 'Create Account' : 'Login' }}
            </v-btn>
            <v-divider class="mt-4 mb-4"></v-divider>
          </v-responsive>
        </v-form>
        <v-responsive class="mx-auto" style="max-width: fit-content">
          <v-btn @click="loginData.toggleAccountCreate()" variant="text" class="text-secondary mt-2 mx-auto">
            {{ loginData.createAccount ? 'Login' : 'Create an account' }}
          </v-btn>
        </v-responsive>
      </v-col>
    </v-row>
  </v-container>
</template>

<style scoped>

</style>
