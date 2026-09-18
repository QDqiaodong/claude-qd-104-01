<template>
  <div>
    <div class="top">
      <span class="ttl">加油机 · 油枪</span>
      <span class="hint">每台加油机一个格子，里面是它挂的枪</span>
      <span class="grow" />
      <select v-model="statusFilter" class="m">
        <option value="">全部状态</option>
        <option value="可用">可用</option>
        <option value="停用">停用</option>
        <option value="维修">维修</option>
      </select>
      <span class="add" @click="openCreate">＋ 挂一把新枪</span>
    </div>

    <div class="machines">
      <div v-for="m in machines" :key="m.no" class="machine">
        <div class="mhead">
          <span class="mno">{{ m.no }}</span>
          <span class="mcnt">{{ m.guns.length }} 把枪</span>
          <span class="mok">{{ m.guns.filter((g) => g.status === '可用').length }} 把可用</span>
        </div>
        <div class="mbody">
          <div
            v-for="g in m.guns"
            :key="g.id"
            class="gun"
            :class="'g-' + g.status"
            @click="openEdit(g)"
          >
            <div class="gcode">{{ g.code }}</div>
            <div class="gprod">{{ g.product }}</div>
            <div class="gtank">{{ tankLabel(g.tankId) }}</div>
            <div class="gst">{{ g.status }}</div>
          </div>
        </div>
      </div>
      <div v-if="!machines.length" class="none">没有符合条件的油枪</div>
    </div>

    <div class="legend">
      <span><i class="d ok"></i>可用</span>
      <span><i class="d off"></i>停用</span>
      <span><i class="d fix"></i>维修</span>
      <span class="grow" />
      <span class="note">点任意一把枪可以改它的机号、油品、所连储罐和状态</span>
    </div>

    <el-dialog v-model="visible" :title="form.id ? '编辑油枪' : '挂一把新枪'" width="450px">
      <el-form label-width="106px">
        <el-form-item label="枪号">
          <el-input v-model="form.code" :disabled="!!form.id" placeholder="如 G-06" />
        </el-form-item>
        <el-form-item label="加油机号">
          <el-input v-model="form.machineNo" placeholder="如 3号机" />
        </el-form-item>
        <el-form-item label="油品">
          <el-select v-model="form.product" style="width:100%">
            <el-option v-for="p in products" :key="p" :label="p" :value="p" />
          </el-select>
        </el-form-item>
        <el-form-item label="所连储罐">
          <el-select v-model="form.tankId" style="width:100%">
            <el-option
              v-for="t in tanks"
              :key="t.id"
              :label="`${t.code}（${t.product} ${t.status}）`"
              :value="t.id"
            />
          </el-select>
        </el-form-item>
        <el-form-item label="状态">
          <el-select v-model="form.status" style="width:100%">
            <el-option label="可用" value="可用" />
            <el-option label="停用" value="停用" />
            <el-option label="维修" value="维修" />
          </el-select>
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="visible = false">取消</el-button>
        <el-button type="primary" @click="save">保存</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { ref, computed, onMounted } from 'vue'
import { ElMessage } from 'element-plus'
import { gunApi, tankApi } from '../api'

const products = ['92#', '95#', '0#', '-10#']
const rows = ref([])
const tanks = ref([])
const statusFilter = ref('')
const visible = ref(false)
const form = ref({})

const machines = computed(() => {
  const list = rows.value.filter((g) => !statusFilter.value || g.status === statusFilter.value)
  const map = new Map()
  for (const g of list) {
    if (!map.has(g.machineNo)) map.set(g.machineNo, [])
    map.get(g.machineNo).push(g)
  }
  return [...map.entries()].map(([no, guns]) => ({ no, guns }))
})

function tankLabel(id) {
  const hit = tanks.value.find((t) => t.id === id)
  return hit ? `${hit.code} ${hit.product}` : id
}

async function load() {
  try {
    rows.value = await gunApi.list({})
  } catch (e) {
    ElMessage.error(e.message)
  }
}

async function loadTanks() {
  try {
    tanks.value = await tankApi.list({})
  } catch (e) {
    ElMessage.error(e.message)
  }
}

function openCreate() {
  form.value = { product: '92#', status: '可用' }
  visible.value = true
}

function openEdit(row) {
  form.value = { ...row }
  visible.value = true
}

async function save() {
  try {
    if (form.value.id) {
      await gunApi.update(form.value.id, form.value)
    } else {
      await gunApi.create(form.value)
    }
    ElMessage.success('已保存')
    visible.value = false
    await load()
  } catch (e) {
    ElMessage.error(e.message)
  }
}

onMounted(async () => {
  await loadTanks()
  await load()
})
</script>

<style scoped>
.top {
  display: flex;
  align-items: baseline;
  gap: 14px;
  margin-bottom: 16px;
}
.ttl {
  font-size: 16px;
  font-weight: 700;
  letter-spacing: 1px;
}
.hint {
  font-size: 12px;
  color: #909399;
}
.grow {
  flex: 1;
}
.m {
  border: 1px solid var(--el-border-color);
  border-radius: 4px;
  padding: 6px 10px;
  font-size: 12px;
  outline: none;
  background: #fff;
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
.machines {
  display: flex;
  gap: 16px;
  flex-wrap: wrap;
}
.machine {
  flex: 1;
  min-width: 290px;
  border: 1px solid #ebeef5;
  border-radius: 8px;
  overflow: hidden;
  background: #fff;
}
.mhead {
  display: flex;
  align-items: center;
  gap: 12px;
  padding: 11px 15px;
  background: #fafbfc;
  border-bottom: 1px solid #ebeef5;
}
.mno {
  font-size: 14px;
  font-weight: 700;
}
.mcnt,
.mok {
  font-size: 12px;
  color: #909399;
}
.mok {
  margin-left: auto;
  color: #67c23a;
}
.mbody {
  display: flex;
  gap: 10px;
  flex-wrap: wrap;
  padding: 14px 15px;
}
.gun {
  width: 124px;
  border: 1px solid #e4e7ed;
  border-radius: 7px;
  padding: 10px 12px;
  cursor: pointer;
  transition: all 0.15s;
  background: #fff;
}
.gun:hover {
  border-color: var(--el-color-primary);
  box-shadow: 0 3px 10px rgba(0, 0, 0, 0.06);
}
.gun.g-停用 {
  opacity: 0.5;
  background: #fafafa;
}
.gun.g-维修 {
  border-color: #fbc4c4;
  background: #fffafa;
}
.gcode {
  font-family: monospace;
  font-size: 13px;
  font-weight: 700;
}
.gprod {
  font-size: 17px;
  font-weight: 700;
  margin: 5px 0 3px;
  color: var(--el-color-primary);
}
.gun.g-维修 .gprod {
  color: #f56c6c;
}
.gtank {
  font-size: 11px;
  color: #909399;
  font-family: monospace;
}
.gst {
  margin-top: 7px;
  font-size: 11px;
  color: #909399;
}
.none {
  color: #c0c4cc;
  font-size: 13px;
  padding: 40px;
}
.legend {
  display: flex;
  align-items: center;
  gap: 20px;
  margin-top: 14px;
  font-size: 12px;
  color: #909399;
}
.legend .grow {
  flex: 1;
}
.d {
  display: inline-block;
  width: 9px;
  height: 9px;
  border-radius: 2px;
  margin-right: 6px;
}
.d.ok {
  background: var(--el-color-primary);
}
.d.off {
  background: #c0c4cc;
}
.d.fix {
  background: #f56c6c;
}
.note {
  color: #c0c4cc;
}
</style>
