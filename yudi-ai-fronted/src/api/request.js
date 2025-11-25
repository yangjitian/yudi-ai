import axios from 'axios'
import JSONBig from 'json-bigint'
// unified error messaging is handled at call sites
import { useUserStore } from '@/stores/user'
import router from '@/router'

const jsonBigStringParser = JSONBig({ storeAsString: true })

const request = axios.create({
  baseURL: '/api',
  timeout: 60000,
  transformResponse: [
    (data) => {
      if (!data) return data
      try {
        return jsonBigStringParser.parse(data)
      } catch (error) {
        return data
      }
    }
  ]
})

request.interceptors.request.use(
  (config) => {
    const userStore = useUserStore()
    if (userStore.token) {
      config.headers.Authorization = `Bearer ${userStore.token}`
    }
    return config
  },
  (error) => {
    return Promise.reject(error)
  }
)

request.interceptors.response.use(
  (response) => {
    return response.data
  },
  (error) => {
    if (error.response) {
      const { status } = error.response
      
      if (status === 401) {
        const userStore = useUserStore()
        userStore.userLogout()
        router.push('/login')
      } else {
        
      }
    } else {
      
    }
    
    return Promise.reject(error)
  }
)

export default request
