import { createApp } from 'vue'
import { createPinia } from 'pinia'
import App from './App.vue'
import router from './router'
import { setAppError } from './composables/useAppError'
import './style.css'

const app = createApp(App)

// Without this, a render error tears down the component tree and the user is left
// looking at a blank page with nothing but a console message (#157).
app.config.errorHandler = (err, _instance, info) => {
  console.error('[factstore] unhandled error', err, info)
  setAppError(err, 'Something went wrong')
}

app.use(createPinia())
app.use(router)
app.mount('#app')
