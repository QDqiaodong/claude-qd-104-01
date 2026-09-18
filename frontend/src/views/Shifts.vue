<template>
  <div class="two">
    <section class="panel left">
      <div class="cap">
        当班中
        <span class="grow" />
        <span class="newlink" @click="openCreate">＋ 开一个班</span>
      </div>

      <div v-for="s in actives" :key="s.id" class="active">
        <div class="who">
          <span class="op">{{ s.operator }}</span>
          <span class="when">{{ s.shiftDate }} · {{ s.shiftType }}</span>
          <span class="guntag">{{ gunLabel(s.gunId) }}</span>
        </div>

        <div class="reads">
          <div class="read">
            <span>接班读数</span>
            <b class="mono">{{ s.startReading }}</b>
          </div>
          <div class="read">
            <span>交班读数</span>
            <b class="mono" :class="{ ready: endOf(s) !== null }">
              {{ endOf(s) === null ? '—' : endOf(s) }}
            </b>
          </div>
          <div class="read hl">
            <span>本班加油量</span>
            <b class="mono">{{ previewOf(s) === null ? '—' : previewOf(s) }} <i>升</i></b>
          </div>
        </div>

        <div class="hand">
          <div class="hrow">
            <label>交班时枪读数</label>
            <input v-model.number="ends[s.id]" type="number" :min="s.startReading" />
          </div>
          <div class="hrow">
            <label>本班收款（元）</label>
            <input v-model.number="amounts[s.id]" type="number" min="0" />
          </div>
          <div v-if="errs[s.id]" class="err">{{ errs[s.id] }}</div>
          <button class="main" @click="doHandover(s)">交　班</button>
          <div class="tip">
            加油量由服务端用「交班读数 − 接班读数」算，不用手填。
            点交班时这把枪已被改成维修/停用的，这次交班会被拒，班还停在当班中。
          </div>
        </div>
      </div>

      <div v-if="!actives.length" class="none">现在没有当班中的班次，点右上角开一个班</div>
    </section>

    <section class="panel right">
      <div class="cap">已交接</div>
      <div class="head">
        <span style="width:150px">班次</span>
        <span style="width:70px">枪号</span>
        <span style="width:160px">读数</span>
        <span style="width:100px">加油量</span>
        <span style="width:100px">收款</span>
        <span>当班人</span>
      </div>
      <div v-for="s in done" :key="s.id" class="drow">
        <span class="mono dim" style="width:150px">{{ s.shiftDate }} {{ s.shiftType }}</span>
        <span class="mono" style="width:70px">{{ gunLabel(s.gunId) }}</span>
        <span class="mono" style="width:160px">{{ s.startReading }} → {{ s.endReading }}</span>
        <span class="mono vol" style="width:100px">{{ s.volume }} 升</span>
        <span class="mono amt" style="width:100px">¥{{ s.amount }}</span>
        <span class="dim">{{ s.operator }}</span>
      </div>
      <div v-if="!done.length" class="none">还没有交过班的记录</div>
    </section>

    <el-dialog v-model="visible" title="开一个班" width="440px">
      <el-form label-width="96px">
        <el-form-item label="班次日期">
          <el-date-picker v-model="form.shiftDate" type="date" value-format="YYYY-MM-DD" style="width:100%" />
        </el-form-item>
        <el-form-item label="班次">
          <el-select v-model="form.shiftType" style="width:100%">
            <el-option label="白班" value="白班" />
            <el-option label="夜班" value="夜班" />
          </el-select>
        </el-form-item>
        <el-form-item label="用哪把枪">
          <el-select v-model="form.gunId" style="width:100%" placeholder="选一把当前可用的枪" @change="fillStart">
            <el-option
              v-for="g in openableGuns"
              :key="g.id"
              :label="`${g.code}（${g.product} · ${g.machineNo}）`"
              :value="g.id"
            />
          </el-select>
        </el-form-item>
        <div v-if="!openableGuns.length" class="tip nogun">
          现在没有能开班的枪：维修、停用或还挂着当班班的枪都开不了班。
        </div>
        <el-form-item label="接班读数">
          <el-input-number v-model="form.startReading" :min="0" :step="100" />
        </el-form-item>
        <div v-if="lastEnd !== null" class="tip chain">
          这把枪上一班交到 {{ lastEnd }}，接班读数已按它填好，可改。
        </div>
        <el-form-item label="当班人">
          <el-input v-model="form.operator" placeholder="如 王小明" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="visible = false">取消</el-button>
        <el-button type="primary" @click="open">开班</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { ref, computed, reactive, onMounted } from 'vue'
import { ElMessage } from 'element-plus'
import { shiftApi, gunApi } from '../api'

const rows = ref([])
const guns = ref([])
const visible = ref(false)
const form = ref({})
// 每个当班中的班各填各的交班读数、收款和报错，互不影响
const ends = reactive({})
const amounts = reactive({})
const errs = reactive({})

const actives = computed(() => rows.value.filter((s) => s.status === '当班中'))
const done = computed(() => rows.value.filter((s) => s.status === '已交接'))

// 能开班的枪：状态可用，而且这会儿没挂着当班中的班
const openableGuns = computed(() =>
  guns.value.filter((g) => g.status === '可用' && !actives.value.some((s) => s.gunId === g.id))
)

function gunLabel(id) {
  const g = guns.value.find((x) => x.id === id)
  return g ? g.code : `#${id}`
}

// 选中那把枪上一班交出去的读数，接班读数按它对齐
const lastEnd = computed(() => {
  if (!form.value.gunId) return null
  const hit = rows.value.find(
    (s) => s.gunId === form.value.gunId && s.status === '已交接' && s.endReading !== null
  )
  return hit ? hit.endReading : null
})

function fillStart() {
  if (lastEnd.value !== null) {
    form.value.startReading = lastEnd.value
  }
}

function endOf(s) {
  const v = ends[s.id]
  return v === null || v === undefined || v === '' ? null : v
}

function previewOf(s) {
  const e = endOf(s)
  return e === null ? null : Math.max(0, e - s.startReading)
}

async function load() {
  try {
    const [shiftRows, gunRows] = await Promise.all([shiftApi.list({}), gunApi.list({})])
    rows.value = shiftRows
    guns.value = gunRows
    // 清掉已经交掉的班的输入框；还在当班中的班保留填到一半的读数和报错
    for (const k of Object.keys(ends)) {
      if (!shiftRows.some((s) => s.status === '当班中' && String(s.id) === String(k))) {
        delete ends[k]
        delete amounts[k]
        delete errs[k]
      }
    }
  } catch (e) {
    ElMessage.error(e.message)
  }
}

function openCreate() {
  form.value = { shiftType: '白班' }
  visible.value = true
}

async function open() {
  try {
    await shiftApi.create(form.value)
    ElMessage.success('已开班')
    visible.value = false
    await load()
  } catch (e) {
    ElMessage.error(e.message)
  }
}

async function doHandover(s) {
  const e = endOf(s)
  if (e === null) {
    errs[s.id] = '还没填交班读数'
    return
  }
  if (e < s.startReading) {
    errs[s.id] = `交班读数不能小于接班读数 ${s.startReading}`
    return
  }
  try {
    await shiftApi.handover(s.id, e, amounts[s.id])
    ElMessage.success('已交班')
    await load()
  } catch (ex) {
    // 交班被拒（比如这把枪刚被改成维修/停用）：班还停在当班中，报错留在这个班上
    await load()
    errs[s.id] = ex.message
  }
}

onMounted(load)
</script>

<style scoped>
.two {
  display: flex;
  gap: 16px;
  align-items: flex-start;
}
.panel {
  background: #fff;
  border: 1px solid #ebeef5;
  border-radius: 8px;
  padding: 16px 18px;
}
.left {
  flex: 1;
  min-width: 420px;
}
.right {
  flex: 1.1;
  min-width: 420px;
}
.cap {
  display: flex;
  align-items: center;
  font-size: 14px;
  font-weight: 600;
  margin-bottom: 14px;
}
.grow {
  flex: 1;
}
.newlink {
  font-size: 12px;
  font-weight: 400;
  color: var(--el-color-primary);
  cursor: pointer;
}
.active + .active {
  margin-top: 16px;
  padding-top: 14px;
  border-top: 1px dashed #ebeef5;
}
.who {
  display: flex;
  align-items: baseline;
  gap: 12px;
  padding-bottom: 12px;
  border-bottom: 1px solid #f2f3f5;
}
.op {
  font-size: 17px;
  font-weight: 700;
}
.when {
  font-size: 12px;
  color: #909399;
  font-family: monospace;
}
.guntag {
  margin-left: auto;
  font-size: 12px;
  font-family: monospace;
  font-weight: 700;
  color: var(--el-color-primary);
  background: var(--el-color-primary-light-9);
  border-radius: 4px;
  padding: 2px 8px;
}
.reads {
  display: flex;
  gap: 12px;
  margin: 16px 0;
}
.read {
  flex: 1;
  background: #fafbfc;
  border-radius: 6px;
  padding: 12px 14px;
}
.read span {
  display: block;
  font-size: 11px;
  color: #909399;
  margin-bottom: 6px;
}
.read b {
  font-size: 18px;
  color: #303133;
}
.read.hl {
  background: var(--el-color-primary-light-9);
}
.read.hl b {
  color: var(--el-color-primary);
}
.read b.ready {
  color: #303133;
}
.mono {
  font-family: monospace;
}
.read i {
  font-style: normal;
  font-size: 11px;
  color: #909399;
}
.hand {
  border-top: 1px solid #f2f3f5;
  padding-top: 14px;
}
.hrow {
  display: flex;
  align-items: center;
  gap: 12px;
  margin-bottom: 12px;
}
.hrow label {
  width: 116px;
  font-size: 13px;
  color: #606266;
}
.hrow input {
  flex: 1;
  border: 1px solid var(--el-border-color);
  border-radius: 4px;
  padding: 8px 11px;
  font-size: 13px;
  font-family: monospace;
  outline: none;
}
.hrow input:focus {
  border-color: var(--el-color-primary);
}
.err {
  font-size: 12px;
  color: #f56c6c;
  margin-bottom: 10px;
}
.main {
  width: 100%;
  border: none;
  background: var(--el-color-primary);
  color: #fff;
  border-radius: 4px;
  padding: 10px 0;
  font-size: 14px;
  letter-spacing: 4px;
  cursor: pointer;
}
.main:disabled {
  background: #c8d6e5;
  cursor: not-allowed;
}
.tip {
  font-size: 11px;
  color: #c0c4cc;
  margin-top: 9px;
}
.tip.nogun,
.tip.chain {
  margin: -8px 0 12px 96px;
}
.tip.chain {
  color: #e6a23c;
}
.none {
  text-align: center;
  color: #c0c4cc;
  font-size: 13px;
  padding: 46px 0;
}
.head,
.drow {
  display: flex;
  align-items: center;
  gap: 10px;
  font-size: 13px;
}
.head {
  font-size: 12px;
  color: #909399;
  padding: 7px 8px;
  background: #fafbfc;
  border-radius: 5px;
}
.drow {
  padding: 11px 8px;
  border-bottom: 1px solid #f5f7fa;
}
.dim {
  color: #a8abb2;
  font-size: 12px;
}
.vol {
  color: var(--el-color-primary);
  font-weight: 600;
}
.amt {
  color: #67c23a;
}
</style>
