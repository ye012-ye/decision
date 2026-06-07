<script setup lang="ts">
// Fixed, decorative gradient + drifting aurora blobs behind the app.
</script>

<template>
  <div class="aurora" aria-hidden="true">
    <span class="aurora__blob aurora__blob--1" />
    <span class="aurora__blob aurora__blob--2" />
    <span class="aurora__blob aurora__blob--3" />
  </div>
</template>

<style scoped>
.aurora {
  position: fixed;
  inset: 0;
  z-index: 0;
  overflow: hidden;
  background: var(--app-bg-gradient);
  pointer-events: none;
}
.aurora__blob {
  position: absolute;
  width: 46vmax;
  height: 46vmax;
  border-radius: 50%;
  filter: blur(80px);
  opacity: 0.9;
  will-change: transform;
}
.aurora__blob--1 {
  top: -12vmax;
  left: -8vmax;
  background: radial-gradient(circle at 30% 30%, var(--aurora-1), transparent 70%);
  animation: aurora-drift-1 24s ease-in-out infinite alternate;
}
.aurora__blob--2 {
  top: 18vmax;
  right: -14vmax;
  background: radial-gradient(circle at 50% 50%, var(--aurora-2), transparent 70%);
  animation: aurora-drift-2 30s ease-in-out infinite alternate;
}
.aurora__blob--3 {
  bottom: -16vmax;
  left: 28vmax;
  background: radial-gradient(circle at 50% 50%, var(--aurora-3), transparent 70%);
  animation: aurora-drift-3 28s ease-in-out infinite alternate;
}
@keyframes aurora-drift-1 { to { transform: translate3d(8vmax, 6vmax, 0) scale(1.15); } }
@keyframes aurora-drift-2 { to { transform: translate3d(-10vmax, 4vmax, 0) scale(1.1); } }
@keyframes aurora-drift-3 { to { transform: translate3d(6vmax, -8vmax, 0) scale(1.2); } }
</style>
