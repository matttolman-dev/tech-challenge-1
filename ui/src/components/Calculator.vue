<script setup>

import {useAccountStore, useAppStore, useCalcStore} from "@/store/app";

const calc = useCalcStore()
const app = useAppStore()
const account = useAccountStore()

function removeStartZeroes(str) {
  return str.replace(/^0+/, '')
}

function evaluate() {
  if (!calc.op) {
    return new Promise(resolve => resolve(calc.res))
  }

  let endpoint = ''
  switch (calc.op) {
    case '+':
      endpoint = 'add';
      break;
    case '-':
      endpoint = 'subtract';
      break;
    case '*':
      endpoint = 'multiply';
      break;
    case '/':
      endpoint = 'divide';
      break;
    case 'sqrt':
      endpoint = 'square-root';
      break;
  }

  if (!endpoint) {
    return new Promise(resolve => resolve(calc.res))
  }

  calc.loading = true
  const x = Number.isFinite(calc.res) ? calc.res : 0
  const y = +(calc.cur || '0')

  return fetch(`${app.root}/api/v1/ops/${endpoint}`, {
    method: 'POST',
    credentials: 'include',
    headers: {
      'content-type': 'application/json'
    },
    body: JSON.stringify({
      x: x,
      y: y
    })
  })
    .then(async res => {
      if (res.status === 402) {
        calc.error = 'Not enough funds!'
      } else if (res.status === 200) {
        calc.op = ''
        calc.cur = ''
        calc.res = +(await res.json()).res
        account.startLoad()
      } else {
        calc.error = 'Something went wrong'
      }
    })
    .catch(err => {
      console.error(err)
      calc.error = 'Something went wrong'
    })
    .finally(() => {
      account.startLoad()
      calc.loading = false
    })
}

function random() {
  calc.loading = true
  return fetch(`${app.root}/api/v1/ops/random-str`, {
    method: 'POST',
    credentials: 'include',
    headers: {
      'content-type': 'application/json'
    },
  })
    .then(async res => {
      if (res.status === 402) {
        calc.error = 'Not enough funds!'
      } else if (res.status === 200) {
        calc.text = (await res.json()).res
        account.startLoad()
      } else {
        calc.error = 'Something went wrong'
      }
    })
    .catch(err => {
      console.error(err)
      calc.error = 'Something went wrong'
    })
    .finally(() => {
      account.startLoad()
      calc.loading = false
    })
}

async function button(b) {
  if (b >= '0' && b <= '9') {
    calc.cur = removeStartZeroes(calc.cur + b);
  } else if (b === 'back') {
    calc.cur = calc.cur.slice(0, -1)
  } else if (b === 'clear') {
    calc.cur = ''
    calc.res = 0
  } else if (b.match(/[+/\-*]/)) {
    await evaluate()
    calc.op = b
  } else if (b === '=' && calc.op) {
    await evaluate()
  } else if (b === 'sqrt') {
    await evaluate()
    calc.op = 'sqrt'
    await evaluate()
  }
}

function keydown(e) {
  if (calc.loading || !document.querySelector('#calc').contains(e.target)) {
    return
  }

  switch (e.key) {
    case 'Backspace':
      button('back');
      break;
    case 'c':
      button('clear');
      break;
    case 's':
      button('sqrt');
      break;
    case '0':
    case '1':
    case '2':
    case '3':
    case '4':
    case '5':
    case '6':
    case '7':
    case '8':
    case '9':
    case '/':
    case '+':
    case '-':
    case '*':
    case '=':
      button(e.key);
      break;
    default:
      return;
  }
  e.preventDefault();
  return false;
}

window.onkeydown = keydown

</script>

<template>
  <v-container class="border bg-grey-darken-2" tabindex="0">
    <v-row>
      <v-col class="bg-grey-lighten-3 text-right">
        {{ calc.cur || calc.res }}
      </v-col>
    </v-row>
    <v-row>
      <v-col cols="6">
        <v-btn :loading="calc.loading" @click="button('clear')" class="bg-orange" block>C</v-btn>
      </v-col>
      <v-col>
        <v-btn :loading="calc.loading" @click="button('sqrt')" class="bg-yellow" block>&#x221A;</v-btn>
      </v-col>
      <v-col>
        <v-btn :loading="calc.loading" @click="button('/')" class="bg-yellow" block>&#x00f7;</v-btn>
      </v-col>
    </v-row>
    <v-row>
      <v-col>
        <v-btn :loading="calc.loading" @click="button('7')" class="bg-grey" block>7</v-btn>
      </v-col>
      <v-col>
        <v-btn :loading="calc.loading" @click="button('8')" class="bg-grey" block>8</v-btn>
      </v-col>
      <v-col>
        <v-btn :loading="calc.loading" @click="button('9')" class="bg-grey" block>9</v-btn>
      </v-col>
      <v-col>
        <v-btn :loading="calc.loading" @click="button('*')" class="bg-yellow" block>&#x00d7;</v-btn>
      </v-col>
    </v-row>
    <v-row>
      <v-col>
        <v-btn :loading="calc.loading" @click="button('4')" class="bg-grey" block>4</v-btn>
      </v-col>
      <v-col>
        <v-btn :loading="calc.loading" @click="button('5')" class="bg-grey" block>5</v-btn>
      </v-col>
      <v-col>
        <v-btn :loading="calc.loading" @click="button('6')" class="bg-grey" block>6</v-btn>
      </v-col>
      <v-col>
        <v-btn :loading="calc.loading" @click="button('-')" class="bg-yellow" block>&#x2212;</v-btn>
      </v-col>
    </v-row>
    <v-row>
      <v-col>
        <v-btn :loading="calc.loading" @click="button('3')" class="bg-grey" block>3</v-btn>
      </v-col>
      <v-col>
        <v-btn :loading="calc.loading" @click="button('2')" class="bg-grey" block>2</v-btn>
      </v-col>
      <v-col>
        <v-btn :loading="calc.loading" @click="button('1')" class="bg-grey" block>1</v-btn>
      </v-col>
      <v-col>
        <v-btn :loading="calc.loading" @click="button('+')" class="bg-yellow" block>&#x002b;</v-btn>
      </v-col>
    </v-row>
    <v-row>
      <v-col cols="6">
        <v-btn :loading="calc.loading" @click="button('0')" class="bg-grey" block>0</v-btn>
      </v-col>
      <v-col>
        <v-btn :loading="calc.loading" @click="button('.')" class="bg-grey pt-4" block>&#x2022;</v-btn>
      </v-col>
      <v-col>
        <v-btn :loading="calc.loading" @click="button('=')" class="bg-yellow" block>=</v-btn>
      </v-col>
    </v-row>
  </v-container>
  <v-alert closable class="bg-error mt-2 mb-2" v-if="calc.error" :text="calc.error"></v-alert>
  <v-divider class="mt-4 mb-4"/>
  <v-container>
    <v-row>
      <v-col>
        <v-btn :loading="calc.loading" @click="random()" class="bg-primary" block>
          Generate Random Text
        </v-btn>
      </v-col>
    </v-row>
    <v-row>
      <v-col>
        {{ calc.text }}
      </v-col>
    </v-row>
  </v-container>

</template>

<style scoped>

</style>
