import http from './http'

export async function register(username, password) {
    const response = await http.post('/api/auth/register', { username, password })
    return response.data
}

export async function login(username, password) {
    const response = await http.post('/api/auth/login', { username, password })
    return response.data
}
