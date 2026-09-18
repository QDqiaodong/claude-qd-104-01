<template>
  <el-container style="min-height: 100vh">
    <el-aside width="216px" class="app-aside">
      <div class="app-brand">加油站 · 油枪与罐区巡检管理</div>
      <el-menu :default-active="route.path" router class="app-menu">
        <el-menu-item index="/tanks">储罐与库存</el-menu-item>
        <el-menu-item index="/unloadings">卸油入罐</el-menu-item>
        <el-menu-item index="/guns">油枪台账</el-menu-item>
        <el-menu-item index="/shifts">班次交接</el-menu-item>
        <el-menu-item index="/inspections">罐区巡检</el-menu-item>
      </el-menu>
    </el-aside>
    <el-container>
      <el-header class="app-header">
        <span class="app-crumb">{{ route.meta.title }}</span>
      </el-header>
      <el-main>
        <router-view />
      </el-main>
    </el-container>
  </el-container>
</template>

<script setup>
import { useRoute } from 'vue-router'
const route = useRoute()
</script>

<style>
.app-aside {
  display: flex;
  flex-direction: column;
  background: var(--el-color-primary);
}
.app-brand {
  padding: 20px 18px;
  font-size: 15px;
  font-weight: 600;
  line-height: 1.5;
  color: #fff;
}
.app-menu {
  flex: 1;
  border-right: none;
  background: transparent;
  --el-menu-text-color: rgba(255, 255, 255, 0.85);
  --el-menu-hover-text-color: #ffffff;
  --el-menu-active-color: #ffffff;
  --el-menu-hover-bg-color: rgba(255, 255, 255, 0.14);
}
.app-menu .el-menu-item.is-active {
  font-weight: 600;
  background: rgba(255, 255, 255, 0.22);
}
.app-header {
  display: flex;
  align-items: center;
  background: #ffffff;
  border-bottom: 1px solid var(--el-border-color-lighter);
}
.app-crumb {
  font-size: 15px;
  font-weight: 600;
  color: var(--el-color-primary-dark-2);
}
</style>
