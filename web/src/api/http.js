import axios from 'axios'

const http = axios.create()

http.interceptors.request.use(config => {
    const token = localStorage.getItem('token')
    if (token) {
        config.headers.Authorization = `Bearer ${token}`
    }
    return config
})

http.interceptors.response.use(
    response => {
        const data = response.data
        if (data && data.code !== undefined && data.code !== 200) {
            return Promise.reject(new Error(data.message || '请求失败'))
        }
        response.data = data.data !== undefined ? data.data : data
        return response
    },
    error => {
        const data = error.response?.data
        if (data && data.message) {
            return Promise.reject(new Error(data.message))
        }
        if (error.response?.status === 401) {
            localStorage.removeItem('token')
            localStorage.removeItem('username')
            window.location.reload()
        }
        return Promise.reject(new Error(error.message || '网络错误'))
    }
)

export default http
