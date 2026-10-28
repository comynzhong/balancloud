<template>
  <div v-if="enabled" class="slider-captcha" :class="{ 'is-error': status === 'fail', 'is-success': status === 'success' }">
    <div ref="boardRef" class="sc-board" :style="{ aspectRatio: `${canvasWidth} / ${canvasHeight}` }">
      <img v-if="background" class="sc-bg" :src="background" alt="拼图背景" draggable="false" />
      <img
        v-if="piece"
        class="sc-piece"
        :src="piece"
        alt=""
        draggable="false"
        :style="{ left: `${(offset / canvasWidth) * 100}%` }"
      />
      <div v-if="!background" class="sc-placeholder">正在加载验证码…</div>
      <span v-if="status === 'success'" class="sc-badge">
        <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2.2" stroke-linecap="round" stroke-linejoin="round">
          <path d="M5 12.5l4.5 4.5L19 7.5" />
        </svg>
        验证通过
      </span>
    </div>

    <div class="sc-track">
      <div class="sc-fill" :style="{ width: `${offset}px` }"></div>
      <span class="sc-track-text">{{ trackText }}</span>
      <button
        type="button"
        class="sc-handle"
        :style="{ transform: `translateX(${handlePx}px)` }"
        :disabled="status === 'success' || status === 'checking' || !background"
        aria-label="拖动滑块完成拼图验证"
        @pointerdown="onPointerDown"
        @keydown="onKeyDown"
      >
        <svg v-if="status !== 'success'" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round">
          <path d="M8 7l-4 5 4 5M16 7l4 5-4 5" />
        </svg>
        <svg v-else viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2.2" stroke-linecap="round" stroke-linejoin="round">
          <path d="M5 12.5l4.5 4.5L19 7.5" />
        </svg>
      </button>
    </div>

    <div class="sc-foot">
      <span class="sc-msg">{{ message }}</span>
      <button type="button" class="sc-refresh" :disabled="loading" @click="refresh">刷新</button>
    </div>
  </div>
</template>

<script setup>
import { getSliderCaptcha, verifySliderCaptcha } from "@/api/login";

const emit = defineEmits(["verified", "enabled"]);

const enabled = ref(true);
const loading = ref(false);
const challengeId = ref("");
const background = ref("");
const piece = ref("");
const canvasWidth = ref(320);
const canvasHeight = ref(140);
const maxOffset = ref(263);
const boardWidth = ref(320);

const offset = ref(0);
const dragging = ref(false);
const status = ref("idle");
const message = ref("拖动滑块，让拼图对准缺口");
const boardRef = ref(null);

const scale = computed(() => (boardWidth.value > 0 ? boardWidth.value / canvasWidth.value : 1));
const handlePx = computed(() => Math.min(offset.value * scale.value, Math.max(0, boardWidth.value - 42)));
const trackText = computed(() => {
  if (status.value === "success") return "已完成安全验证";
  if (status.value === "checking") return "正在校验…";
  if (dragging.value) return "松开完成验证";
  return "拖动滑块完成拼图";
});

let dragStartClientX = 0;
let dragStartOffset = 0;
let resizeObserver;

onMounted(() => {
  if (typeof ResizeObserver !== "undefined") {
    resizeObserver = new ResizeObserver((entries) => {
      boardWidth.value = entries[0].contentRect.width;
    });
    if (boardRef.value) resizeObserver.observe(boardRef.value);
  }
  refresh();
});

onBeforeUnmount(() => {
  resizeObserver?.disconnect();
  stopDragging();
});

watch(
  () => enabled.value,
  (value) => {
    if (!value) return;
    nextTick(() => {
      if (boardRef.value) resizeObserver?.observe(boardRef.value);
    });
  }
);

function onPointerDown(event) {
  if (status.value === "success" || status.value === "checking" || !background.value) return;
  event.preventDefault();
  dragging.value = true;
  status.value = "dragging";
  message.value = "";
  dragStartClientX = event.clientX;
  dragStartOffset = offset.value;
  event.currentTarget.setPointerCapture?.(event.pointerId);
  window.addEventListener("pointermove", onPointerMove);
  window.addEventListener("pointerup", onPointerUp, { once: true });
  window.addEventListener("pointercancel", onPointerUp, { once: true });
}

function onPointerMove(event) {
  if (!dragging.value) return;
  const delta = (event.clientX - dragStartClientX) / scale.value;
  offset.value = clamp(dragStartOffset + delta, 0, maxOffset.value);
}

function onPointerUp() {
  if (!dragging.value) return;
  stopDragging();
  submit();
}

function stopDragging() {
  dragging.value = false;
  window.removeEventListener("pointermove", onPointerMove);
  window.removeEventListener("pointerup", onPointerUp);
  window.removeEventListener("pointercancel", onPointerUp);
}

function onKeyDown(event) {
  if (event.key === "ArrowLeft" || event.key === "ArrowRight") {
    event.preventDefault();
    const step = event.shiftKey ? 16 : 4;
    offset.value = clamp(offset.value + (event.key === "ArrowRight" ? step : -step), 0, maxOffset.value);
    status.value = "dragging";
    message.value = "按 Enter 提交验证";
    return;
  }
  if (event.key === "Enter") {
    event.preventDefault();
    if (status.value !== "success") submit();
  }
}

async function submit() {
  if (!background.value || status.value === "checking" || status.value === "success") return;
  status.value = "checking";
  try {
    const res = await verifySliderCaptcha(challengeId.value, Math.round(offset.value));
    if (res.verified) {
      status.value = "success";
      message.value = "";
      emit("verified", res.token);
      return;
    }
    status.value = "fail";
    message.value = res.msg || "验证失败，请重试";
    offset.value = 0;
    if (res.expired) {
      setTimeout(refresh, 700);
    }
  } catch (error) {
    status.value = "fail";
    message.value = "验证服务暂不可用，请点击刷新";
    offset.value = 0;
  }
}

async function refresh() {
  if (loading.value) return;
  loading.value = true;
  status.value = "idle";
  message.value = "";
  offset.value = 0;
  try {
    const res = await getSliderCaptcha();
    const data = res.data;
    if (!data || data.enabled === false) {
      enabled.value = false;
      emit("enabled", false);
      return;
    }
    enabled.value = true;
    emit("enabled", true);
    challengeId.value = data.id;
    background.value = data.backgroundImage;
    piece.value = data.pieceImage;
    canvasWidth.value = data.canvasWidth || 320;
    canvasHeight.value = data.canvasHeight || 140;
    maxOffset.value = data.maxOffset || 263;
    message.value = "拖动滑块，让拼图对准缺口";
  } catch (error) {
    message.value = "验证码加载失败，请点击刷新";
  } finally {
    loading.value = false;
  }
}

function reset() {
  offset.value = 0;
  status.value = "idle";
  message.value = "";
  refresh();
}

function clamp(value, min, max) {
  return Math.min(Math.max(value, min), max);
}

defineExpose({ reset, refresh });
</script>

<style lang="scss" scoped>
.slider-captcha {
  margin: 2px 0 16px;
  user-select: none;
}

.sc-board {
  position: relative;
  width: 100%;
  max-width: 320px;
  margin: 0 auto;
  border-radius: 12px;
  overflow: hidden;
  background: #0a3b2d;
  box-shadow:
    0 0 0 1px rgba(16, 84, 64, 0.14),
    0 10px 22px rgba(9, 66, 47, 0.12);
  transition: box-shadow 0.2s ease;

  .sc-bg {
    display: block;
    width: 100%;
    height: 100%;
    object-fit: cover;
  }

  .sc-piece {
    position: absolute;
    top: 0;
    height: 100%;
    width: auto;
    pointer-events: none;
    filter: drop-shadow(0 3px 6px rgba(0, 0, 0, 0.32));
  }

  .sc-placeholder {
    position: absolute;
    inset: 0;
    display: grid;
    place-items: center;
    font-size: 12px;
    color: rgba(232, 255, 247, 0.68);
    background: linear-gradient(120deg, #0a3b2d 0%, #0d5b45 100%);
  }

  .sc-badge {
    position: absolute;
    top: 8px;
    right: 8px;
    display: inline-flex;
    align-items: center;
    gap: 5px;
    padding: 4px 9px;
    border-radius: 999px;
    background: rgba(6, 78, 58, 0.82);
    color: #eafff6;
    font-size: 11.5px;
    letter-spacing: 0.04em;

    svg {
      width: 13px;
      height: 13px;
    }
  }
}

.is-error .sc-board {
  animation: shake 0.4s ease;
}

.sc-track {
  position: relative;
  height: 42px;
  margin-top: 10px;
  border-radius: 10px;
  background: rgba(255, 255, 255, 0.9);
  box-shadow: inset 0 0 0 1px rgba(16, 84, 64, 0.14);
  overflow: hidden;
}

.sc-fill {
  position: absolute;
  top: 0;
  bottom: 0;
  left: 0;
  max-width: 100%;
  background: linear-gradient(90deg, rgba(18, 192, 124, 0.16), rgba(10, 154, 107, 0.32));
}

.sc-track-text {
  position: absolute;
  inset: 0;
  display: grid;
  place-items: center;
  font-size: 12px;
  letter-spacing: 0.02em;
  color: #7d968c;
  pointer-events: none;
}

.sc-handle {
  position: absolute;
  top: 0;
  left: 0;
  display: grid;
  place-items: center;
  width: 42px;
  height: 42px;
  padding: 0;
  border: none;
  border-radius: 10px;
  background: linear-gradient(145deg, #12c07c, #06825f);
  color: #fff;
  cursor: grab;
  touch-action: none;
  box-shadow: 0 6px 14px rgba(6, 110, 80, 0.28);
  transition: background 0.2s ease;

  &:active {
    cursor: grabbing;
  }

  &:focus-visible {
    outline: 2px solid rgba(18, 192, 124, 0.9);
    outline-offset: 2px;
  }

  &:disabled {
    cursor: default;
  }

  svg {
    width: 18px;
    height: 18px;
  }
}

.is-success .sc-handle {
  background: linear-gradient(145deg, #10b981, #047857);
}

.sc-foot {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  margin-top: 7px;
  min-height: 17px;
  font-size: 11.5px;

  .sc-msg {
    color: #6b8a80;
  }

  .is-error & .sc-msg {
    color: #c2543f;
  }  .sc-refresh {
    flex: none;
    padding: 2px 0;
    border: none;
    background: none;
    color: #0a9a6b;
    font-size: 11.5px;
    cursor: pointer;

    &:hover {
      text-decoration: underline;
    }

    &:disabled {
      color: #a9bdb5;
      cursor: default;
      text-decoration: none;
    }
  }
}

@keyframes shake {
  0%,
  100% {
    transform: translateX(0);
  }
  25% {
    transform: translateX(-5px);
  }
  50% {
    transform: translateX(4px);
  }
  75% {
    transform: translateX(-2px);
  }
}

@media (prefers-reduced-motion: reduce) {
  .is-error .sc-board {
    animation: none;
  }

  .sc-board .sc-piece {
    transition: none;
  }
}
</style>
