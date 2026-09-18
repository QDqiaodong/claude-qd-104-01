<template>
  <div>
    <div class="top">
      <select v-model="product" class="m">
        <option value="">全部油品</option>
        <option v-for="p in products" :key="p" :value="p">{{ p }}</option>
      </select>
      <select v-model="statusFilter" class="m">
        <option value="">全部状态</option>
        <option value="在用">在用</option>
        <option value="检修">检修</option>
      </select>
      <span class="grow" />
      <span class="add" @click="openCreate">＋ 新增储罐</span>
    </div>

    <div class="row">
      <div
        v-for="t in shown"
        :key="t.id"
        class="tank"
        :class="{ low: isLow(t), repair: t.status === '检修' }"
      >
        <div class="gauge">
          <div class="fill" :style="{ height: pct(t) + '%' }" />
          <div class="safe" :style="{ bottom: safePct(t) + '%' }">
            <i />
          </div>
          <span class="pctv">{{ pct(t) }}%</span>
        </div>

        <div class="meta">
          <div class="code">{{ t.code }}</div>
          <div class="product" :class="'p-' + t.product">{{ t.product }}</div>
          <div class="num">
            {{ t.stock }}<i>/{{ t.capacity }}</i>
          </div>
          <div class="safehint">安全线 {{ t.safeStock }} 升</div>
          <div class="st" :class="'st-' + t.status">{{ t.status }}</div>
          <span class="edit" @click="openEdit(t)">改</span>
        </div>
      </div>
    </div>

    <div class="legend">
      <span><i class="sw fill"></i>当前液位</span>
      <span><i class="sw safe"></i>安全线（低于它就该补油）</span>
      <span class="note">检修中的罐整体变灰</span>
    </div>

    <el-dialog v-model="visible" :title="form.id ? '编辑储罐' : '新增储罐'" width="450px">
      <el-form label-width="110px">
        <el-form-item label="罐号">
          <el-input v-model="form.code" :disabled="!!form.id" placeholder="如 T-05" />
        </el-form-item>
        <el-form-item label="油品">
          <el-select v-model="form.product" style="width:100%">
            <el-option v-for="p in products" :key="p" :label="p" :value="p" />
          </el-select>
        </el-form-item>
        <el-form-item label="罐容（升）">
          <el-input-number v-model="form.capacity" :min="1" :step="1000" />
        </el-form-item>
        <el-form-item label="当前库存（升）">
          <el-input-number v-model="form.stock" :min="0" :step="1000" />
        </el-form-item>
        <el-form-item label="安全线（升）">
          <el-input-number v-model="form.safeStock" :min="1" :step="500" />
        </el-form-item>
        <el-form-item label="状态">
          <el-select v-model="form.status" style="width:100%">
            <el-option label="在用" value="在用" />
            <el-option label="检修" value="检修" />
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
import { tankApi } from '../api'

const products = ['92#', '95#', '0#', '-10#']
const rows = ref([])
const product = ref('')
const statusFilter = ref('')
const visible = ref(false)
const form = ref({})

const shown = computed(() =>
  rows.value.filter(
    (t) => (!product.value || t.product === product.value) &&
      (!statusFilter.value || t.status === statusFilter.value)
  )
)

function pct(t) {
  if (!t.capacity) return 0
  return Math.min(100, Math.round((t.stock / t.capacity) * 100))
}

function safePct(t) {
  if (!t.capacity) return 0
  return Math.min(100, Math.round((t.safeStock / t.capacity) * 100))
}

function isLow(t) {
  return t.stock < t.safeStock
}

async function load() {
  try {
    rows.value = await tankApi.list({})
  } catch (e) {
    ElMessage.error(e.message)
  }
}

function openCreate() {
  form.value = { product: '92#', capacity: 30000, stock: 0, safeStock: 5000, status: '在用' }
  visible.value = true
}

function openEdit(row) {
  form.value = { ...row }
  visible.value = true
}

async function save() {
  try {
    if (form.value.id) {
      await tankApi.update(form.value.id, form.value)
    } else {
      await tankApi.create(form.value)
    }
    ElMessage.success('已保存')
    visible.value = false
    await load()
  } catch (e) {
    ElMessage.error(e.message)
  }
}

onMounted(load)
</script>

<style scoped>
.top {
  display: flex;
  align-items: center;
  gap: 12px;
  margin-bottom: 18px;
}
.m {
  border: 1px solid var(--el-border-color);
  border-radius: 4px;
  padding: 8px 10px;
  font-size: 13px;
  outline: none;
  background: #fff;
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
.row {
  display: flex;
  gap: 26px;
  flex-wrap: wrap;
  background: #fff;
  border: 1px solid #ebeef5;
  border-radius: 8px;
  padding: 24px 30px;
}
.tank {
  display: flex;
  gap: 14px;
}
.gauge {
  position: relative;
  width: 62px;
  height: 150px;
  border: 2px solid #dcdfe6;
  border-radius: 8px;
  background: #f7f8fa;
  overflow: hidden;
  display: flex;
  align-items: flex-end;
}
.fill {
  width: 100%;
  background: linear-gradient(180deg, var(--el-color-primary-light-3), var(--el-color-primary));
  transition: height 0.4s;
}
.tank.low .fill {
  background: linear-gradient(180deg, #f89898, #f56c6c);
}
.safe {
  position: absolute;
  left: 0;
  right: 0;
  height: 0;
  border-top: 1px dashed #e6a23c;
}
.safe i {
  position: absolute;
  right: 3px;
  top: -6px;
  font-size: 9px;
  color: #e6a23c;
}
.pctv {
  position: absolute;
  left: 0;
  right: 0;
  top: 6px;
  text-align: center;
  font-size: 11px;
  font-family: monospace;
  color: #606266;
}
.meta {
  position: relative;
  min-width: 108px;
  padding-top: 2px;
}
.code {
  font-family: monospace;
  font-size: 13px;
  font-weight: 600;
}
.product {
  display: inline-block;
  font-size: 11px;
  border-radius: 3px;
  padding: 1px 8px;
  color: #fff;
  margin: 5px 0 8px;
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
.num {
  font-family: monospace;
  font-size: 19px;
  line-height: 1;
  color: #303133;
}
.num i {
  font-style: normal;
  font-size: 11px;
  color: #a8abb2;
}
.safehint {
  font-size: 11px;
  color: #909399;
  margin-top: 5px;
}
.st {
  display: inline-block;
  margin-top: 6px;
  font-size: 11px;
  border-radius: 9px;
  padding: 1px 9px;
  background: var(--el-color-primary-light-9);
  color: var(--el-color-primary);
}
.st-检修 {
  background: #f4f4f5;
  color: #909399;
}
.tank.repair {
  opacity: 0.5;
}
.edit {
  position: absolute;
  right: 0;
  bottom: 0;
  font-size: 12px;
  color: var(--el-color-primary);
  cursor: pointer;
  opacity: 0;
  transition: opacity 0.15s;
}
.tank:hover .edit {
  opacity: 1;
}
.legend {
  display: flex;
  align-items: center;
  gap: 22px;
  margin-top: 14px;
  font-size: 12px;
  color: #909399;
}
.sw {
  display: inline-block;
  width: 14px;
  height: 9px;
  border-radius: 2px;
  margin-right: 6px;
  vertical-align: middle;
}
.sw.fill {
  background: var(--el-color-primary);
}
.sw.safe {
  background: transparent;
  border-top: 2px dashed #e6a23c;
  border-radius: 0;
  height: 0;
}
.legend .note {
  color: #c0c4cc;
}
</style>
