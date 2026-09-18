<template>
  <div>
    <div class="top">
      <span class="ttl">槽车卸油入罐台账</span>
      <select v-model="statusFilter" class="m">
        <option value="">全部状态</option>
        <option value="待入罐">待入罐</option>
        <option value="已入罐">已入罐</option>
      </select>
      <select v-model="productFilter" class="m">
        <option value="">全部油品</option>
        <option v-for="p in products" :key="p" :value="p">{{ p }}</option>
      </select>
      <input v-model="keyword" class="m search" placeholder="车牌 / 单号" />
      <span class="grow" />
      <span class="add" @click="openCreate">＋ 槽车开单</span>
    </div>

    <!-- 安全员闸门：当天卸油口巡检停在待处理，一律不许入罐 -->
    <div class="gate" :class="gateClass">
      <span class="gate-ico">{{ gateIcon }}</span>
      <span class="gate-txt">
        <b>卸油口巡检（{{ today }}）：{{ gateText }}</b>
        <em v-if="gatePending">先去「罐区巡检」把这条异常标记已处理，才能点入罐</em>
        <em v-else-if="gateRow">巡检已闭环，允许入罐；入罐是否成功还要再看罐状态和罐容</em>
        <em v-else>今天还没有卸油口巡检记录，入罐前请先完成巡检</em>
      </span>
    </div>

    <div class="card">
      <div class="head">
        <span style="width:140px">单号 / 日期</span>
        <span style="width:110px">槽车牌</span>
        <span style="width:70px">油品</span>
        <span style="width:110px">计划升数</span>
        <span style="width:180px">所挂储罐</span>
        <span style="width:90px">状态</span>
        <span style="width:120px">入罐时间</span>
        <span style="width:80px">经办人</span>
        <span class="grow" />
        <span style="width:130px">操作</span>
      </div>

      <div v-for="u in shown" :key="u.id" class="drow" :class="{ done: u.status === '已入罐' }">
        <span class="mono dim" style="width:140px">
          {{ u.billNo }}
          <i class="sub">{{ u.billDate }}</i>
        </span>
        <span class="plate" style="width:110px">{{ u.plateNo }}</span>
        <span style="width:70px">
          <i class="prod" :class="'p-' + u.product">{{ u.product }}</i>
        </span>
        <span class="mono vol" style="width:110px">{{ u.plannedVolume }} 升</span>
        <span style="width:180px">
          <template v-if="tank(u)">
            <b class="mono">{{ tank(u).code }}</b>
            <i class="tankmeta">
              {{ tank(u).product }} · 库存 {{ tank(u).stock }}/{{ tank(u).capacity }}
              <em v-if="tank(u).status === '检修'" class="rep">· 检修</em>
            </i>
          </template>
          <span v-else class="dim">罐已删除</span>
        </span>
        <span style="width:90px">
          <i class="st" :class="u.status === '已入罐' ? 'st-in' : 'st-wait'">{{ u.status }}</i>
        </span>
        <span class="mono dim" style="width:120px">{{ u.enteredAt ? fmt(u.enteredAt) : '—' }}</span>
        <span class="dim" style="width:80px">{{ u.operator || '—' }}</span>
        <span class="grow" />
        <span class="acts" style="width:130px">
          <template v-if="u.status === '待入罐'">
            <span v-if="overflow(u)" class="tag-bad">超罐容</span>
            <span v-else-if="gatePending" class="tag-lock">巡检未闭环</span>
            <span v-else-if="tank(u) && tank(u).status === '检修'" class="tag-bad">罐检修</span>
            <span class="btn-in" @click="doEnter(u)">入罐</span>
            <span class="btn-edit" @click="openEdit(u)">改</span>
          </template>
          <span v-else class="dim-lock">升数·罐已锁定</span>
        </span>
      </div>

      <div v-if="!shown.length" class="none">没有符合条件的卸油单，点右上角给到站槽车开单</div>
    </div>

    <div class="legend">
      开单只做台账：库存不动，单停在「待入罐」；只有「入罐」成功才把升数加进罐库存，
      已入罐的单升数和所挂的罐都不能再改。
    </div>

    <el-dialog v-model="visible" :title="form.id ? '改卸油单' : '槽车卸油开单'" width="480px">
      <el-form label-width="100px">
        <el-form-item label="槽车车牌">
          <el-input v-model="form.plateNo" placeholder="如 鲁B·82301" />
        </el-form-item>
        <el-form-item label="油品">
          <el-select v-model="form.product" style="width:100%" @change="onProductChange">
            <el-option v-for="p in products" :key="p" :label="p" :value="p" />
          </el-select>
        </el-form-item>
        <el-form-item label="所挂储罐">
          <el-select v-model="form.tankId" style="width:100%" placeholder="选一口在用罐" @change="onTankChange">
            <el-option
              v-for="t in tankOptions"
              :key="t.id"
              :label="`${t.code}（${t.product} ${t.status}，库存 ${t.stock}/${t.capacity}）`"
              :value="t.id"
              :disabled="t.status !== '在用' || t.product !== form.product"
            />
          </el-select>
        </el-form-item>
        <el-form-item label="计划升数">
          <el-input-number v-model="form.plannedVolume" :min="1" :step="1000" />
        </el-form-item>
        <el-form-item label="经办人">
          <el-input v-model="form.operator" placeholder="如 王小明" />
        </el-form-item>
      </el-form>

      <div v-if="form.tankId && selectedTank" class="calc" :class="{ bad: formOverflow }">
        该罐此刻库存 {{ selectedTank.stock }} 升 + 计划 {{ form.plannedVolume || 0 }} 升
        = {{ selectedTank.stock + (form.plannedVolume || 0) }} 升，
        罐容 {{ selectedTank.capacity }} 升
        <b v-if="formOverflow">——超罐容，开不了单</b>
        <b v-else>——在罐容以内</b>
      </div>

      <template #footer>
        <el-button @click="visible = false">取消</el-button>
        <el-button type="primary" @click="save">{{ form.id ? '保存' : '开单' }}</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { ref, computed, onMounted } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { unloadingApi, tankApi, inspectionApi } from '../api'

const products = ['92#', '95#', '0#', '-10#']
const rows = ref([])
const tanks = ref([])
const inspections = ref([])
const statusFilter = ref('待入罐')
const productFilter = ref('')
const keyword = ref('')
const visible = ref(false)
const form = ref({})

const today = (() => {
  const d = new Date()
  return `${d.getFullYear()}-${String(d.getMonth() + 1).padStart(2, '0')}-${String(d.getDate()).padStart(2, '0')}`
})()

const shown = computed(() =>
  rows.value.filter(
    (u) =>
      (!statusFilter.value || u.status === statusFilter.value) &&
      (!productFilter.value || u.product === productFilter.value) &&
      (!keyword.value ||
        u.plateNo.includes(keyword.value.trim()) ||
        u.billNo.includes(keyword.value.trim()))
  )
)

function tank(u) {
  return tanks.value.find((t) => t.id === u.tankId) || null
}

const tankOptions = computed(() =>
  [...tanks.value].sort((a, b) => (a.status === b.status ? a.code.localeCompare(b.code) : a.status === '在用' ? -1 : 1))
)

const selectedTank = computed(() => tanks.value.find((t) => t.id === form.value.tankId) || null)

const formOverflow = computed(
  () =>
    selectedTank.value &&
    form.value.plannedVolume > 0 &&
    selectedTank.value.stock + form.value.plannedVolume > selectedTank.value.capacity
)

// 当天「卸油口」巡检闸门：只有停在待处理的异常才卡入罐
const gateRow = computed(
  () => inspections.value.find((i) => i.point === '卸油口' && i.inspectDate === today) || null
)
const gatePending = computed(() => gateRow.value && gateRow.value.status === '待处理')
const gateClass = computed(() => {
  if (!gateRow.value) return 'gate-none'
  return gatePending.value ? 'gate-bad' : 'gate-ok'
})
const gateIcon = computed(() => {
  if (!gateRow.value) return '○'
  return gatePending.value ? '!' : '✓'
})
const gateText = computed(() => {
  if (!gateRow.value) return '今天还没巡'
  const r = gateRow.value
  return `${r.result} · ${r.status}${r.issueDesc ? '（' + r.issueDesc + '）' : ''}`
})

function overflow(u) {
  const t = tank(u)
  return t && t.stock + u.plannedVolume > t.capacity
}

function fmt(dt) {
  return dt.replace('T', ' ').slice(0, 16)
}

async function load() {
  try {
    const [billList, tankList, inspList] = await Promise.all([
      unloadingApi.list({}),
      tankApi.list({}),
      inspectionApi.list({})
    ])
    rows.value = billList
    tanks.value = tankList
    inspections.value = inspList
  } catch (e) {
    ElMessage.error(e.message)
  }
}

function openCreate() {
  form.value = { product: '92#', plannedVolume: 1000 }
  visible.value = true
}

function openEdit(u) {
  form.value = { ...u }
  visible.value = true
}

function onProductChange() {
  // 换油品后，原来挂的罐若装的不是这个油就清掉，逼着重新选对口罐
  const t = selectedTank.value
  if (t && t.product !== form.value.product) {
    form.value.tankId = undefined
  }
}

function onTankChange() {
  const t = selectedTank.value
  // 油品必须和罐正在装的油一致：选罐即把油品对齐到罐
  if (t && t.product !== form.value.product) {
    form.value.product = t.product
  }
}

async function save() {
  if (!form.value.plateNo || !form.value.plateNo.trim()) {
    ElMessage.warning('要填槽车车牌')
    return
  }
  if (!form.value.tankId) {
    ElMessage.warning('要挂一口在用、油品对口的储罐')
    return
  }
  if (!form.value.plannedVolume || form.value.plannedVolume <= 0) {
    ElMessage.warning('计划升数要大于 0')
    return
  }
  try {
    if (form.value.id) {
      await unloadingApi.update(form.value.id, form.value)
      ElMessage.success('已保存，库存仍未变动')
    } else {
      await unloadingApi.create(form.value)
      ElMessage.success('已开单，停在待入罐，库存未动')
    }
    visible.value = false
    await load()
  } catch (e) {
    ElMessage.error(e.message)
  }
}

async function doEnter(u) {
  const t = tank(u)
  const why = []
  if (gatePending.value) why.push('当天卸油口巡检还停在待处理')
  if (t && t.status === '检修') why.push(`${t.code} 已是检修罐`)
  if (overflow(u)) why.push('加上本单会超罐容')
  if (why.length) {
    ElMessage.error(`现在不能入罐：${why.join('；')}。服务端也会拦`)
    return
  }
  try {
    await ElMessageBox.confirm(
      `确认把槽车 ${u.plateNo} 的 ${u.plannedVolume} 升 ${u.product} 卸入 ${t.code}？` +
        `入罐成功后库存 ${t.stock} → ${t.stock + u.plannedVolume} 升，且单据不可再改。`,
      '卸油入罐',
      { confirmButtonText: '确认入罐', cancelButtonText: '取消', type: 'warning' }
    )
  } catch {
    return
  }
  try {
    await unloadingApi.enter(u.id)
    ElMessage.success('入罐成功，库存已加上')
    await load()
  } catch (e) {
    // 罐被改成检修、库存刚被别人加过超罐容、巡检闸门等：库存和单据都保持原样
    ElMessage.error(e.message)
    await load()
  }
}

onMounted(load)
</script>

<style scoped>
.top {
  display: flex;
  align-items: center;
  gap: 10px;
  margin-bottom: 14px;
  flex-wrap: wrap;
}
.ttl {
  font-size: 16px;
  font-weight: 700;
  letter-spacing: 1px;
}
.m {
  border: 1px solid var(--el-border-color);
  border-radius: 4px;
  padding: 7px 10px;
  font-size: 13px;
  outline: none;
  background: #fff;
}
.search {
  width: 150px;
  font-family: monospace;
}
.grow {
  flex: 1;
}
.add {
  font-size: 13px;
  color: var(--el-color-primary);
  cursor: pointer;
  user-select: none;
}
.add:hover {
  text-decoration: underline;
}

.gate {
  display: flex;
  align-items: flex-start;
  gap: 12px;
  border-radius: 8px;
  padding: 12px 16px;
  margin-bottom: 14px;
  border: 1px solid;
}
.gate-ico {
  width: 22px;
  height: 22px;
  border-radius: 50%;
  display: inline-flex;
  align-items: center;
  justify-content: center;
  font-style: normal;
  font-weight: 700;
  font-size: 13px;
  flex: none;
}
.gate-txt {
  font-size: 13px;
  line-height: 1.7;
}
.gate-txt em {
  display: block;
  font-style: normal;
  font-size: 12px;
}
.gate-bad {
  background: #fef0f0;
  border-color: #fbc4c4;
}
.gate-bad .gate-ico {
  background: #f56c6c;
  color: #fff;
}
.gate-bad b {
  color: #c45656;
}
.gate-bad em {
  color: #c45656;
}
.gate-ok {
  background: #f0f9eb;
  border-color: #c2e7b0;
}
.gate-ok .gate-ico {
  background: #67c23a;
  color: #fff;
}
.gate-ok b {
  color: #529b2e;
}
.gate-ok em {
  color: #7a9e6b;
}
.gate-none {
  background: #fdf6ec;
  border-color: #f5dab1;
}
.gate-none .gate-ico {
  background: #e6a23c;
  color: #fff;
}
.gate-none b {
  color: #b88230;
}
.gate-none em {
  color: #b88230;
}

.card {
  background: #fff;
  border: 1px solid #ebeef5;
  border-radius: 8px;
  overflow: hidden;
}
.head,
.drow {
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 12px 14px;
  font-size: 13px;
}
.head {
  font-size: 12px;
  color: #909399;
  background: #fafbfc;
}
.drow {
  border-bottom: 1px solid #f5f7fa;
}
.drow:last-child {
  border-bottom: none;
}
.drow.done {
  background: #fcfcfd;
}
.mono {
  font-family: monospace;
}
.dim {
  color: #a8abb2;
  font-size: 12px;
}
.sub {
  display: block;
  font-style: normal;
  font-size: 11px;
  color: #c0c4cc;
}
.plate {
  font-weight: 600;
  font-family: monospace;
}
.prod {
  font-style: normal;
  font-size: 11px;
  border-radius: 3px;
  padding: 1px 8px;
  color: #fff;
}
.p-92# {
  background: #409eff;
}
.p-95# {
  background: #67c23a;
}
.p-0# {
  background: #e6a23c;
}
.p--10# {
  background: #7c5cf5;
}
.vol {
  color: var(--el-color-primary);
  font-weight: 600;
}
.tankmeta {
  display: block;
  font-style: normal;
  font-size: 11px;
  color: #909399;
}
.tankmeta .rep {
  color: #f56c6c;
  font-style: normal;
}
.st {
  font-style: normal;
  font-size: 11px;
  border-radius: 9px;
  padding: 1px 9px;
}
.st-wait {
  background: #fdf6ec;
  color: #b88230;
}
.st-in {
  background: #f0f9eb;
  color: #529b2e;
}
.acts {
  display: flex;
  align-items: center;
  gap: 8px;
  justify-content: flex-end;
}
.btn-in {
  font-size: 12px;
  border-radius: 3px;
  padding: 3px 14px;
  cursor: pointer;
  color: #529b2e;
  background: #f0f9eb;
  border: 1px solid #d1edc4;
  user-select: none;
}
.btn-in:hover {
  background: #e1f3d8;
}
.btn-edit {
  font-size: 12px;
  color: var(--el-color-primary);
  cursor: pointer;
  user-select: none;
}
.btn-edit:hover {
  text-decoration: underline;
}
.tag-bad,
.tag-lock {
  font-size: 11px;
  border-radius: 3px;
  padding: 2px 8px;
}
.tag-bad {
  color: #c45656;
  background: #fef0f0;
}
.tag-lock {
  color: #b88230;
  background: #fdf6ec;
}
.dim-lock {
  font-size: 11px;
  color: #c0c4cc;
}
.none {
  text-align: center;
  color: #c0c4cc;
  font-size: 13px;
  padding: 48px 0;
}
.legend {
  margin-top: 12px;
  font-size: 12px;
  color: #909399;
}
.calc {
  margin: 4px 0 0;
  background: #f0f9eb;
  border: 1px solid #d1edc4;
  color: #529b2e;
  border-radius: 6px;
  padding: 10px 14px;
  font-size: 12px;
  line-height: 1.8;
}
.calc b {
  display: block;
}
.calc.bad {
  background: #fef0f0;
  border-color: #fbc4c4;
  color: #c45656;
}
</style>
