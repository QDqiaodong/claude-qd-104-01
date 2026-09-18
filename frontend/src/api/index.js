import axios from 'axios'

const http = axios.create({ baseURL: '/api', timeout: 10000 })

http.interceptors.response.use(
  (res) => res.data,
  (err) => {
    const msg = err?.response?.data?.message || err.message || '请求失败'
    return Promise.reject(new Error(msg))
  }
)

export const tankApi = {
  list: (params) => http.get('/tanks', { params }),
  create: (data) => http.post('/tanks', data),
  update: (id, data) => http.put(`/tanks/${id}`, data)
}

export const gunApi = {
  list: (params) => http.get('/guns', { params }),
  create: (data) => http.post('/guns', data),
  update: (id, data) => http.put(`/guns/${id}`, data)
}

export const shiftApi = {
  list: (params) => http.get('/shifts', { params }),
  create: (data) => http.post('/shifts', data),
  update: (id, data) => http.put(`/shifts/${id}`, data),
  handover: (id, endReading, amount) =>
    http.post(`/shifts/${id}/handover`, null, { params: { endReading, amount } })
}

export const inspectionApi = {
  list: (params) => http.get('/inspections', { params }),
  create: (data) => http.post('/inspections', data),
  resolve: (id) => http.post(`/inspections/${id}/resolve`)
}

export const unloadingApi = {
  list: (params) => http.get('/unloadings', { params }),
  create: (data) => http.post('/unloadings', data),
  update: (id, data) => http.put(`/unloadings/${id}`, data),
  enter: (id) => http.post(`/unloadings/${id}/enter`)
}

export default http
