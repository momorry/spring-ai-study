<template>
  <el-config-provider :locale="locale">
    <router-view />
  </el-config-provider>
</template>

<script setup>
import { ref } from 'vue'
import zhCn from 'element-plus/dist/locale/zh-cn.mjs'

const locale = ref(zhCn)
</script>

<style>
#app {
  font-family: 'Helvetica Neue', Helvetica, 'PingFang SC', 'Hiragino Sans GB',
    'Microsoft YaHei', '微软雅黑', Arial, sans-serif;
  -webkit-font-smoothing: antialiased;
  -moz-osx-font-smoothing: grayscale;
  height: 100vh;
  margin: 0;
  padding: 0;
}

body {
  margin: 0;
  padding: 0;
}

/* Element Plus 秋天主题色覆盖 */
:root {
  --el-color-primary: #FAFAF5;
  --el-color-primary-light-1: #F48C06;
  --el-color-primary-light-2: #FAA307;
  --el-color-primary-light-3: #FFBA08;
  --el-color-primary-light-4: #FFD166;
  --el-color-primary-light-5: #FFE6A3;
  --el-color-primary-light-6: #FFF3D6;
  --el-color-primary-light-7: #FFF8E8;
  --el-color-primary-light-8: #FFFBF2;
  --el-color-primary-light-9: #FFFEF9;
  --el-color-primary-dark-2: #DC2F02;
}
</style>
