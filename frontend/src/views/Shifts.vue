<template>
  <div class="two">
    <section class="panel left">
      <div class="cap">
        当班中
        <span class="grow" />
        <span class="newlink" @click="openCreate">＋ 开一个班</span>
      </div>

      <div v-if="active" class="active">
        <div class="who">
          <span class="op">{{ active.operator }}</span>
          <span class="when">{{ active.shiftDate }} · {{ active.shiftType }}</span>
          <span class="guntag">{{ gunLabel(active.gunId) }}</span>
        </div>

        <div class="reads">
          <div class="read">
            <span>接班读数</span>
            <b class="mono">{{ active.startReading }}</b>
          </div>
          <div class="read">
            <span>交班读数</span>
            <b class="mono" :class="{ ready: endReading !== null }">
              {{ endReading === null ? '—' : endReading }}
            </b>
          </div>
          <div class="read hl">
            <span>本班加油量</span>
            <b class="mono">{{ preview === null ? '—' : preview }} <i>升</i></b>
          </div>
        </div>

        <div class="hand">
          <div class="hrow">
            <label>交班时枪读数</label>
            <input v-model.number="endReading" type="number" :min="active.startReading" />
          </div>
          <div class="hrow">
            <label>本班收款（元）</label>
            <input v-model.number="amount" type="number" min="0" />
          </div>
          <div v-if="err" class="err">{{ err }}</div>
          <button class="main" :disabled="!!err" @click="doHandover">交　班</button>
          <div class="tip">加油量由服务端用「交班读数 − 接班读数」算，不用手填。</div>
        </div>
      </div>

      <div v-else class="none">现在没有当班中的班次，点右上角开一个班</div>
    </section>

    <section class="panel right">
      <div class="cap">已交接</div>
      <div class="head">
        <span style="width:150px">班次</span>
        <span style="width:90px">油枪</span>
        <span style="width:170px">读数</span>
        <span style="width:110px">加油量</span>
        <span style="width:110px">收款</span>
        <span>当班人</span>
      </div>
      <div v-for="s in done" :key="s.id" class="drow">
        <span class="mono dim" style="width:150px">{{ s.shiftDate }} {{ s.shiftType }}</span>
        <span class="mono" style="width:90px">{{ gunLabel(s.gunId) }}</span>
        <span class="mono" style="width:170px">{{ s.startReading }} → {{ s.endReading }}</span>
        <span class="mono vol" style="width:110px">{{ s.volume }} 升</span>
        <span class="mono amt" style="width:110px">¥{{ s.amount }}</span>
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
        <el-form-item label="加油枪">
          <el-select v-model="form.gunId" style="width:100%" placeholder="选一把当前可用的枪">
            <el-option
              v-for="g in availGuns"
              :key="g.id"
              :label="`${g.code}（${g.product} · ${g.machineNo}）`"
              :value="g.id"
            />
          </el-select>
          <div v-if="!availGuns.length" class="gunhint">现在没有可用的枪，开不了班</div>
        </el-form-item>
        <el-form-item label="接班读数">
          <el-input-number v-model="form.startReading" :min="0" :step="100" />
        </el-form-item>
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
import { ref, computed, onMounted } from 'vue'
import { ElMessage } from 'element-plus'
import { shiftApi, gunApi } from '../api'

const rows = ref([])
const guns = ref([])
const visible = ref(false)
const form = ref({})
const endReading = ref(null)
const amount = ref(0)

const active = computed(() => rows.value.find((s) => s.status === '当班中') || null)
const done = computed(() => rows.value.filter((s) => s.status === '已交接'))
const availGuns = computed(() => guns.value.filter((g) => g.status === '可用'))

function gunLabel(id) {
  const hit = guns.value.find((g) => g.id === id)
  return hit ? hit.code : `#${id}`
}

const preview = computed(() => {
  if (!active.value || endReading.value === null || endReading.value === undefined) return null
  return Math.max(0, endReading.value - active.value.startReading)
})

const err = computed(() => {
  if (!active.value) return ''
  if (endReading.value === null || endReading.value === undefined || endReading.value === '') {
    return '还没填交班读数'
  }
  if (endReading.value < active.value.startReading) {
    return `交班读数不能小于接班读数 ${active.value.startReading}`
  }
  return ''
})

async function load() {
  try {
    const [shiftRows, gunRows] = await Promise.all([shiftApi.list({}), gunApi.list({})])
    rows.value = shiftRows
    guns.value = gunRows
    endReading.value = null
    amount.value = 0
  } catch (e) {
    ElMessage.error(e.message)
  }
}

async function openCreate() {
  try {
    guns.value = await gunApi.list({})
  } catch (e) {
    ElMessage.error(e.message)
    return
  }
  form.value = { shiftType: '白班', gunId: null }
  visible.value = true
}

async function open() {
  if (!form.value.gunId) {
    ElMessage.error('开班要选定一把加油枪')
    return
  }
  try {
    await shiftApi.create(form.value)
    ElMessage.success('已开班')
    visible.value = false
    await load()
  } catch (e) {
    ElMessage.error(e.message)
  }
}

async function doHandover() {
  try {
    await shiftApi.handover(active.value.id, endReading.value, amount.value)
    ElMessage.success('已交班')
    await load()
  } catch (e) {
    ElMessage.error(e.message)
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
  padding: 3px 9px;
}
.gunhint {
  font-size: 12px;
  color: #f56c6c;
  line-height: 1.6;
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
