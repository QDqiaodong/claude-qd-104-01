import { createRouter, createWebHistory } from 'vue-router'
import Tanks from '../views/Tanks.vue'
import Unloadings from '../views/Unloadings.vue'
import Guns from '../views/Guns.vue'
import Shifts from '../views/Shifts.vue'
import Inspections from '../views/Inspections.vue'

const routes = [
  { path: '/', redirect: '/tanks' },
  { path: '/tanks', component: Tanks, meta: { title: '储罐与库存' } },
  { path: '/unloadings', component: Unloadings, meta: { title: '卸油入罐台账' } },
  { path: '/guns', component: Guns, meta: { title: '油枪台账' } },
  { path: '/shifts', component: Shifts, meta: { title: '班次交接' } },
  { path: '/inspections', component: Inspections, meta: { title: '罐区巡检' } }
]

export default createRouter({
  history: createWebHistory(),
  routes
})
