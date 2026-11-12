<template>
  <div class="home-view">
    <!-- 首页改为壁纸轮播（资源取自 src/images），原首页内容已搬迁至「关于」页 -->
    <WallpaperCarousel />
  </div>
</template>

<script setup>
import WallpaperCarousel from '@/components/WallpaperCarousel.vue'
</script>

<style scoped>
.home-view {
  height: 100%;
  overflow: hidden;
}
</style>
