<template>
  <div>
    <div class="top">
      <span class="ttl">罐区巡检打卡</span>
      <input v-model="date" type="date" class="d" />
      <span class="progress">
        已巡 {{ checkedCount }} / {{ points.length }}
      </span>
      <span class="grow" />
      <span class="note">点「正常」直接登记；发现问题的点「异常」，写清是什么问题</span>
    </div>

    <div class="bar">
      <div class="bar-in" :style="{ width: barPct + '%' }" />
    </div>

    <div class="list">
      <div
        v-for="p in points"
        :key="p"
        class="item"
        :class="{ ok: rec(p) && rec(p).result === '正常', bad: rec(p) && rec(p).result === '异常' }"
      >
        <span class="mark">
          <i v-if="rec(p) && rec(p).result === '正常'">✓</i>
          <i v-else-if="rec(p)" class="x">!</i>
          <i v-else class="dot" />
        </span>
        <span class="point">{{ p }}</span>

        <template v-if="!rec(p)">
          <span class="grow" />
          <span class="act ok-act" @click="markNormal(p)">正常</span>
          <span class="act bad-act" @click="openIssue(p)">异常</span>
        </template>

        <template v-else>
          <span class="res">
            {{ rec(p).result }}
            <em v-if="rec(p).issueDesc">· {{ rec(p).issueDesc }}</em>
          </span>
          <span class="grow" />
          <span class="by">{{ rec(p).inspector }}</span>
          <span
            v-if="rec(p).result === '异常'"
            class="act"
            :class="rec(p).status === '待处理' ? 'warn-act' : 'done-act'"
            @click="rec(p).status === '待处理' && resolve(rec(p))"
          >
            {{ rec(p).status === '待处理' ? '标记已处理' : '已处理' }}
          </span>
        </template>
      </div>
    </div>

    <el-dialog v-model="visible" title="记录异常" width="450px">
      <div class="dlg-point">点位：<b>{{ issuePoint }}</b>　日期：<b>{{ date }}</b></div>
      <el-form label-width="86px">
        <el-form-item label="问题描述">
          <el-input v-model="issueDesc" type="textarea" placeholder="哪里不对、什么表现" />
        </el-form-item>
        <el-form-item label="巡检人">
          <el-input v-model="inspector" placeholder="如 王小明" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="visible = false">取消</el-button>
        <el-button type="primary" @click="saveIssue">提交</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { ref, computed, onMounted } from 'vue'
import { ElMessage } from 'element-plus'
import { inspectionApi } from '../api'

const BASE_POINTS = ['T-01罐区', 'T-02罐区', 'T-03罐区', 'T-04罐区', '卸油口', '消防器材箱', '配电房']
const rows = ref([])
const date = ref(new Date().toISOString().slice(0, 10))
const inspector = ref('')
const visible = ref(false)
const issuePoint = ref('')
const issueDesc = ref('')

const points = computed(() => {
  const extra = rows.value.map((r) => r.point).filter((p) => !BASE_POINTS.includes(p))
  return [...BASE_POINTS, ...new Set(extra)]
})

const dayRows = computed(() => rows.value.filter((r) => r.inspectDate === date.value))
const checkedCount = computed(() => dayRows.value.length)
const barPct = computed(() =>
  points.value.length ? Math.round((checkedCount.value / points.value.length) * 100) : 0
)

function rec(point) {
  return dayRows.value.find((r) => r.point === point) || null
}

async function load() {
  try {
    rows.value = await inspectionApi.list({})
  } catch (e) {
    ElMessage.error(e.message)
  }
}

async function markNormal(point) {
  if (!inspector.value) {
    ElMessage.warning('先在下面填一下巡检人')
    return
  }
  try {
    await inspectionApi.create({
      inspectDate: date.value,
      point,
      result: '正常',
      inspector: inspector.value
    })
    ElMessage.success(`${point} 已打卡`)
    await load()
  } catch (e) {
    ElMessage.error(e.message)
  }
}

function openIssue(point) {
  issuePoint.value = point
  issueDesc.value = ''
  visible.value = true
}

async function saveIssue() {
  if (!issueDesc.value.trim()) {
    ElMessage.warning('报异常要写清是什么问题')
    return
  }
  if (!inspector.value) {
    ElMessage.warning('先填一下巡检人')
    return
  }
  try {
    await inspectionApi.create({
      inspectDate: date.value,
      point: issuePoint.value,
      result: '异常',
      issueDesc: issueDesc.value,
      inspector: inspector.value
    })
    ElMessage.success('已记录异常')
    visible.value = false
    await load()
  } catch (e) {
    ElMessage.error(e.message)
  }
}

async function resolve(row) {
  try {
    await inspectionApi.resolve(row.id)
    ElMessage.success('已处理')
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
  align-items: baseline;
  gap: 14px;
  margin-bottom: 12px;
  flex-wrap: wrap;
}
.ttl {
  font-size: 16px;
  font-weight: 700;
  letter-spacing: 1px;
}
.d {
  border: 1px solid var(--el-border-color);
  border-radius: 4px;
  padding: 6px 10px;
  font-size: 13px;
  outline: none;
  font-family: monospace;
}
.progress {
  font-size: 13px;
  color: var(--el-color-primary);
  font-family: monospace;
}
.grow {
  flex: 1;
}
.note {
  font-size: 12px;
  color: #c0c4cc;
}
.bar {
  height: 5px;
  background: #eef1f5;
  border-radius: 3px;
  overflow: hidden;
  margin-bottom: 16px;
}
.bar-in {
  height: 100%;
  background: var(--el-color-primary);
  border-radius: 3px;
  transition: width 0.3s;
}
.list {
  background: #fff;
  border: 1px solid #ebeef5;
  border-radius: 8px;
  overflow: hidden;
}
.item {
  display: flex;
  align-items: center;
  gap: 14px;
  padding: 14px 18px;
  border-bottom: 1px solid #f5f7fa;
  font-size: 13px;
}
.item:last-child {
  border-bottom: none;
}
.item.ok {
  background: #fbfefb;
}
.item.bad {
  background: #fffafa;
}
.mark {
  width: 20px;
  text-align: center;
}
.mark i {
  font-style: normal;
  font-size: 13px;
  color: #67c23a;
  font-weight: 700;
}
.mark i.x {
  color: #f56c6c;
}
.mark i.dot {
  display: inline-block;
  width: 8px;
  height: 8px;
  border-radius: 50%;
  background: #dcdfe6;
}
.point {
  font-weight: 600;
  min-width: 120px;
}
.act {
  font-size: 12px;
  border-radius: 3px;
  padding: 3px 12px;
  cursor: pointer;
  user-select: none;
  border: 1px solid transparent;
}
.ok-act {
  color: #529b2e;
  background: #f0f9eb;
  border-color: #d1edc4;
}
.ok-act:hover {
  background: #e1f3d8;
}
.bad-act {
  color: #c45656;
  background: #fef0f0;
  border-color: #fbc4c4;
}
.bad-act:hover {
  background: #fde2e2;
}
.warn-act {
  color: #b88230;
  background: #fdf6ec;
  border-color: #f5dab1;
}
.done-act {
  color: #909399;
  background: #f4f4f5;
  cursor: default;
}
.res {
  color: #606266;
}
.res em {
  font-style: normal;
  color: #f56c6c;
  font-size: 12px;
}
.by {
  font-size: 12px;
  color: #a8abb2;
}
.dlg-point {
  font-size: 13px;
  color: #606266;
  margin-bottom: 14px;
}
.dlg-point b {
  color: var(--el-color-primary);
}
</style>
