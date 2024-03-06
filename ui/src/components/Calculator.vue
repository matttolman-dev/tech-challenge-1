<script setup>

import {useAccountStore, useAppStore, useCalcStore} from "@/store/app";

const calc = useCalcStore()
const app = useAppStore()
const account = useAccountStore()

/**
 * Trims zeroes from the start of a string
 * @param str {string}
 * @returns {string}
 */
function removeStartZeroes(str) {
  return str.replace(/^0+/, '')
}

/**
 * Handles evaluating the calculator
 * @returns {Promise<void>}
 */
function evaluate() {
  // If there is no operation, return
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

  // Error handling in case an operation isn't properly added in the future
  if (!endpoint) {
    console.error('Unknown operation', calc.op)
    return new Promise(resolve => resolve(calc.res))
  }

  calc.loading = true
  const x = calc.res || '0'
  const y = (calc.cur || '0')

  // Perform the evaluation
  return fetch(`${app.root}/api/v1/ops/${endpoint}`, {
    method: 'POST',
    credentials: 'include',
    headers: {
      'content-type': 'application/json'
    },
    body: JSON.stringify({
      // We always send x and y, even if the server ignores y for square root
      // It makes the code cleaner, and the server still functions just fine
      x: x,
      y: y
    })
  })
    .then(async res => {
      if (res.status === 402) {
        // 402 = payment required
        calc.error = 'Not enough funds!'
      } else if (res.status === 200) {
        // Success
        calc.op = ''
        calc.cur = ''
        calc.res = (await res.json()).res
      } else {
        // Unknown, so assume something bad happened
        // We aren't worried about 400 since our UI disallows incorrect params
        // If our UI's restricted input messes something up, it's a devs fault not a user's fault
        // (or the user was using developer tools to mess with things, at which point they can handle a 400 themselves)
        calc.error = 'Something went wrong'
      }
    })
    .catch(err => {
      console.error(err)
      calc.error = 'Something went wrong'
    })
    .finally(() => {
      // Always start an account load (handles expired sessions, updates the balance, etc.)
      account.startLoad()
      calc.loading = false
    })
}

/**
 * Generates a random string
 * @returns {Promise<void>}
 */
function random() {
  // Random string generation
  // This one is straightforward since we just hit an endpoint
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

/**
 * Click handler for a button
 * @param b {string}
 * @returns {Promise<void>}
 */
async function button(b) {
  if (b >= '0' && b <= '9') {
    // Handler for buttons

    // Clearing is for when we hit a button sequence that needs a new number
    // (e.g. 9, 2, +    this sequence wants a new numeric input)
    // In this case, we display the old number until we get a button input
    // At which point we clear the current display and start tracking the new number
    // We also take the current value and move it to the "result" register for operation use
    //  but only if there is nothing in our result register (aka. it is null)
    if (calc.clear) {
      if (calc.res === null) {
        calc.res = calc.cur || '0'
      }
      calc.cur = ''
      calc.clear = false
    }

    // Overwriting is for when we finished an input sequence, and we start a new one
    // (e.g. 9, 2, +, 3, =      finished sequence)
    // In this case, we still hold onto the result in case we do a continuation sequence
    // (e.g. +, 3      continuation sequence)
    // However, if we immediately input a number that means we want to do a new sequence
    // (e.g. 9, 2, +, 3, = (seq1), 1, 2, +, 8, = (seq2))
    // Since the overwrite flag is cleared after every press (except for presses that set it),
    //   we can simply check the flag and if it's set we clear the previous result
    if (calc.overwrite) {
      calc.res = null
    }

    // We don't want leading zeros, so we clear them out
    // We'll default to a '0' if the string is empty
    calc.cur = removeStartZeroes(calc.cur + b) || '0';
  } else if (b === 'back') {
    // This is if someone hits the backspace key to delete a character
    calc.cur = calc.cur.slice(0, -1)
  } else if (b === 'clear') {
    // This clears the calculator
    calc.reset()
  } else if (b.match(/[+/\-*]/)) {
    // When we hit an input button, we first try to evaluate whatever sequence we have
    // We then register our operation and ask for an input value
    // We don't handle the result and current registers as those are handled by
    //   the evaluate method and the number buttons
    // These are always used in "continuation sequences" where we ask for a number next
    //   and where we can continue an "end" sequence
    await evaluate()
    calc.op = b
    calc.clear = true
  } else if (b === '=') {
    // This will evaluate whatever we have in our stack
    //  (if there's nothing then it's a no op)
    // It also marks the "end" of a sequence
    await evaluate()
    calc.cur = ''
    calc.op = ''
    calc.clear = true
    calc.overwrite = true
    return
  } else if (b === 'sqrt') {
    // This will evaluate a square root
    // It also marks the "end" of a sequence
    await evaluate()
    calc.op = 'sqrt'
    await evaluate()
    calc.clear = true
    calc.overwrite = true
    return
  } else if (b === '.' && calc.cur.indexOf('.') < 0) {
    // Adds a decimal to a number (if one wasn't already present)
    calc.cur += '.'
  }
  calc.overwrite = false
}

/**
 * Keydown handler for shortcut keys
 * @param e {KeyboardEvent}
 * @returns {boolean}
 */
function keydown(e) {
  // We don't process keys when loading, and we only process keys that come from children of our calculator
  // (that way we aren't processing search queries)
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
  <v-container id="calc" class="border bg-grey-darken-2" tabindex="0">
    <v-row>
      <v-col class="bg-grey-lighten-3 text-right">
        {{ calc.cur || calc.res || '0' }}
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
