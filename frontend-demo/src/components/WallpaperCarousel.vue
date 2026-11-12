<template>
  <div class="wallpaper-carousel">
    <div
      v-if="wallpapers.length"
      class="stage"
      :class="`fx-${effect}`"
      :style="{ '--enter-x': enterX }"
      @mouseenter="handleEnter"
      @mouseleave="handleLeave"
    >
      <!-- 上一层：转场时被新图盖住或一起动画退场 -->
      <img
        v-if="leavingIndex !== null"
        :src="wallpapers[leavingIndex].url"
        class="layer layer-leave"
        alt=""
      />
      <!-- 当前层：key 变化即重建元素，保证每次转场动画都从头播放 -->
      <img
        :key="`enter-${enterToken}`"
        :src="wallpapers[currentIndex].url"
        class="layer layer-enter"
        :alt="wallpapers[currentIndex].title"
      />
      <!-- 顶部渐变遮罩，保证右上角序号可读 -->
      <div class="stage-shade"></div>
      <!-- 白场 / 黑场：最顶层纯色遮罩，一闪而过形成“闪白 / 闪黑”转场 -->
      <div :key="`flash-${enterToken}`" class="fx-overlay"></div>

      <span class="stage-counter">
        {{ paddedCurrent }} <i>/</i> {{ paddedTotal }}
      </span>

      <button type="button" class="stage-nav nav-prev" aria-label="上一张" @click="step(-1)">
        <el-icon :size="22"><DArrowLeft /></el-icon>
      </button>
      <button type="button" class="stage-nav nav-next" aria-label="下一张" @click="step(1)">
        <el-icon :size="22"><DArrowRight /></el-icon>
      </button>
    </div>

    <div v-else class="stage stage-empty">
      <el-empty description="未在 src/images 目录找到壁纸资源" />
    </div>

    <!-- 缩略图导航 -->
    <div v-if="wallpapers.length > 1" class="thumb-bar">
      <button
        v-for="(item, index) in wallpapers"
        :key="item.url"
        type="button"
        class="thumb"
        :class="{ 'is-active': index === currentIndex }"
        :title="item.title"
        @click="jumpTo(index)"
      >
        <img :src="item.url" :alt="item.title" />
      </button>
    </div>
  </div>
</template>

<script setup>
import { computed, onBeforeUnmount, onMounted, ref } from 'vue'
import { DArrowLeft, DArrowRight } from '@element-plus/icons-vue'

// 轮播间隔与转场时长（转场在间隔内完成，切换节奏仍为 3 秒；后者需与样式里的 --fx 一致）
const INTERVAL = 3000
const FX_DURATION = 1100

/**
 * 可选转场特效。weight 越大出现概率越高：白场/黑场是本次要求的重点，权重最高。
 * fade 交叉淡化、slide 横向滑入、zoom 缩放推进、blur 焦点虚化、wipe 扫光揭幕。
 */
const EFFECTS = [
  { name: 'white', weight: 3 },
  { name: 'black', weight: 3 },
  { name: 'fade', weight: 2 },
  { name: 'slide', weight: 2 },
  { name: 'zoom', weight: 2 },
  { name: 'blur', weight: 2 },
  { name: 'wipe', weight: 1 }
]

const wallpaperModules = import.meta.glob('/src/images/*.{jpg,jpeg,png,webp}', {
  eager: true,
  query: '?url',
  import: 'default'
})

// 【哲风壁纸】光斑-动漫-可爱.jpg → 标题「光斑 · 动漫 · 可爱」，仅用于 alt 与缩略图提示
const parseWallpaper = (path, url) => {
  const base = path.slice(path.lastIndexOf('/') + 1).replace(/\.[^.]+$/, '')
  const matched = base.match(/^【(.+?)】(.*)$/)
  const rawTitle = matched ? matched[2] : base
  return {
    url,
    title: (rawTitle || base).split('-').filter(Boolean).join(' · ')
  }
}

const wallpapers = Object.entries(wallpaperModules)
  .sort(([a], [b]) => a.localeCompare(b))
  .map(([path, url]) => parseWallpaper(path, url))

const currentIndex = ref(0)
const leavingIndex = ref(null)
const effect = ref('fade')
const enterX = ref('100%')
const enterToken = ref(0)
const isPaused = ref(false)

let autoTimer = null
let settleTimer = null

const paddedCurrent = computed(() => String(currentIndex.value + 1).padStart(2, '0'))
const paddedTotal = computed(() => String(wallpapers.length).padStart(2, '0'))

// 加权随机，且不与上一次特效重复，避免连点时看不出转场变化
function pickEffect() {
  const candidates = EFFECTS.filter(item => item.name !== effect.value)
  const pool = candidates.length ? candidates : EFFECTS
  const total = pool.reduce((sum, item) => sum + item.weight, 0)
  let point = Math.random() * total
  for (const item of pool) {
    point -= item.weight
    if (point <= 0) return item.name
  }
  return pool[pool.length - 1].name
}

function clearTimers() {
  if (autoTimer) clearTimeout(autoTimer)
  if (settleTimer) clearTimeout(settleTimer)
  autoTimer = null
  settleTimer = null
}

function scheduleNext() {
  if (autoTimer) clearTimeout(autoTimer)
  if (isPaused.value || wallpapers.length < 2) return
  autoTimer = setTimeout(() => step(1), INTERVAL)
}

function goTo(target) {
  if (wallpapers.length < 2) return
  const next = (target + wallpapers.length) % wallpapers.length
  if (next === currentIndex.value) return

  effect.value = pickEffect()
  // 滑动/揭幕的方向随机，让每次转场都不完全一样
  enterX.value = Math.random() < 0.5 ? '100%' : '-100%'
  leavingIndex.value = currentIndex.value
  currentIndex.value = next
  enterToken.value += 1

  // 转场结束后卸掉上一层，再按 3 秒间隔安排下一次
  if (settleTimer) clearTimeout(settleTimer)
  settleTimer = setTimeout(() => {
    leavingIndex.value = null
  }, FX_DURATION)
  scheduleNext()
}

function step(offset) {
  goTo(currentIndex.value + offset)
}

function jumpTo(index) {
  goTo(index)
}

function handleEnter() {
  isPaused.value = true
  if (autoTimer) clearTimeout(autoTimer)
  autoTimer = null
}

function handleLeave() {
  isPaused.value = false
  scheduleNext()
}

// 页面切到后台时停掉轮播，回来再恢复，避免空转和动画错位
function handleVisibility() {
  if (document.hidden) {
    if (autoTimer) clearTimeout(autoTimer)
    autoTimer = null
  } else if (!isPaused.value) {
    scheduleNext()
  }
}

onMounted(() => {
  // 提前解码，避免首次切图时出现空白帧
  wallpapers.forEach(item => {
    const image = new Image()
    image.src = item.url
  })
  document.addEventListener('visibilitychange', handleVisibility)
  scheduleNext()
})

onBeforeUnmount(() => {
  clearTimers()
  document.removeEventListener('visibilitychange', handleVisibility)
})
</script>

<style scoped>
.wallpaper-carousel {
  height: 100%;
  padding: 12px;
  box-sizing: border-box;
  display: flex;
  flex-direction: column;
  gap: 12px;
  background: #F9E9CD;
}

.stage {
  --fx: 1100ms;
  --enter-x: 100%;
  position: relative;
  flex: 1;
  min-height: 0;
  overflow: hidden;
  border-radius: 16px;
  background: #2b2b2b;
  box-shadow: 0 8px 28px rgba(92, 74, 66, 0.18);
}

.stage-empty {
  display: flex;
  align-items: center;
  justify-content: center;
  background: #fff;
}

.layer {
  position: absolute;
  inset: 0;
  width: 100%;
  height: 100%;
  object-fit: cover;
  display: block;
}

.layer-leave {
  z-index: 1;
}

.layer-enter {
  z-index: 2;
  will-change: opacity, transform, filter;
}

/* 顶部渐变遮罩，保证右上角序号可读 */
.stage-shade {
  position: absolute;
  inset: 0;
  z-index: 3;
  pointer-events: none;
  background: linear-gradient(180deg, rgba(0, 0, 0, 0.3) 0%, rgba(0, 0, 0, 0) 22%);
}

.fx-overlay {
  position: absolute;
  inset: 0;
  z-index: 5;
  opacity: 0;
  pointer-events: none;
  background: #fff;
}

/* ---------- 转场特效 ---------- */

/* 交叉淡化：新图直接淡入盖住旧图 */
.fx-fade .layer-enter {
  animation: fx-fade-in var(--fx) ease both;
}

/* 白场 / 黑场：中途整屏被纯色覆盖，形成闪白、闪黑的电影感转场 */
.fx-white .fx-overlay,
.fx-black .fx-overlay {
  animation: fx-flash var(--fx) ease-in-out both;
}

.fx-white .fx-overlay {
  background: #ffffff;
}

.fx-black .fx-overlay {
  background: #000000;
}

.fx-white .layer-enter,
.fx-black .layer-enter {
  animation: fx-reveal var(--fx) linear both;
}

.fx-white .layer-leave,
.fx-black .layer-leave {
  animation: fx-hide var(--fx) linear both;
}

/* 横向滑入：方向由 --enter-x 随机决定，旧图轻微反向位移做视差 */
.fx-slide .layer-enter {
  animation: fx-slide-in var(--fx) cubic-bezier(0.22, 0.61, 0.36, 1) both;
}

.fx-slide .layer-leave {
  animation: fx-slide-out var(--fx) cubic-bezier(0.22, 0.61, 0.36, 1) both;
}

/* 缩放推进 */
.fx-zoom .layer-enter {
  animation: fx-zoom-in var(--fx) ease both;
}

.fx-zoom .layer-leave {
  animation: fx-zoom-out var(--fx) ease both;
}

/* 焦点虚化 */
.fx-blur .layer-enter {
  animation: fx-blur-in var(--fx) ease both;
}

.fx-blur .layer-leave {
  animation: fx-blur-out var(--fx) ease both;
}

/* 扫光揭幕 */
.fx-wipe .layer-enter {
  animation: fx-wipe-in var(--fx) cubic-bezier(0.65, 0.05, 0.36, 1) both;
}

@keyframes fx-fade-in {
  from { opacity: 0; }
  to { opacity: 1; }
}

@keyframes fx-flash {
  0% { opacity: 0; }
  42%, 56% { opacity: 1; }
  100% { opacity: 0; }
}

@keyframes fx-reveal {
  0%, 46% { opacity: 0; }
  70%, 100% { opacity: 1; }
}

@keyframes fx-hide {
  0% { opacity: 1; }
  46%, 100% { opacity: 0; }
}

@keyframes fx-slide-in {
  from { transform: translateX(var(--enter-x)); }
  to { transform: translateX(0); }
}

@keyframes fx-slide-out {
  from { transform: translateX(0); }
  to { transform: translateX(calc(var(--enter-x) * -0.18)); }
}

@keyframes fx-zoom-in {
  from { opacity: 0; transform: scale(1.18); }
  to { opacity: 1; transform: scale(1); }
}

@keyframes fx-zoom-out {
  from { opacity: 1; transform: scale(1); }
  to { opacity: 0; transform: scale(0.94); }
}

@keyframes fx-blur-in {
  from { opacity: 0; filter: blur(16px); transform: scale(1.04); }
  to { opacity: 1; filter: blur(0); transform: scale(1); }
}

@keyframes fx-blur-out {
  from { opacity: 1; filter: blur(0); }
  to { opacity: 0; filter: blur(10px); }
}

@keyframes fx-wipe-in {
  from { clip-path: inset(0 0 0 100%); }
  to { clip-path: inset(0 0 0 0); }
}

/* ---------- 舞台上的控件 ---------- */

.stage-counter {
  position: absolute;
  right: 28px;
  top: 24px;
  z-index: 4;
  font-size: 15px;
  letter-spacing: 2px;
  color: #fff;
  text-shadow: 0 2px 10px rgba(0, 0, 0, 0.55);
  pointer-events: none;
}

.stage-counter i {
  font-style: normal;
  opacity: 0.6;
}

.stage-nav {
  position: absolute;
  top: 50%;
  transform: translateY(-50%);
  z-index: 4;
  width: 42px;
  height: 42px;
  display: flex;
  align-items: center;
  justify-content: center;
  padding: 0;
  border: none;
  border-radius: 50%;
  background: rgba(0, 0, 0, 0.28);
  color: #fff;
  cursor: pointer;
  opacity: 0;
  transition: all 0.25s ease;
  user-select: none;
  -webkit-user-select: none;
}

.stage:hover .stage-nav {
  opacity: 1;
}

.stage-nav:hover {
  background: #F5B199;
}

.nav-prev {
  left: 18px;
}

.nav-next {
  right: 18px;
}

.thumb-bar {
  flex-shrink: 0;
  height: 68px;
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 10px;
}

.thumb {
  width: 104px;
  height: 62px;
  padding: 0;
  border: 2px solid transparent;
  border-radius: 10px;
  overflow: hidden;
  background: none;
  cursor: pointer;
  opacity: 0.6;
  transition: all 0.25s ease;
  user-select: none;
  -webkit-user-select: none;
}

.thumb img {
  width: 100%;
  height: 100%;
  object-fit: cover;
  display: block;
}

.thumb:hover {
  opacity: 0.9;
}

.thumb.is-active {
  opacity: 1;
  border-color: #F5B199;
  box-shadow: 0 4px 12px rgba(244, 140, 6, 0.28);
}
</style>
