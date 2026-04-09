<script setup lang="ts">
defineProps<{
  events: Array<{ id: string; type: string; content: string }>;
}>();
</script>

<template>
  <div class="chat-timeline">
    <p v-if="events.length === 0" class="chat-timeline__empty">
      等待第一条消息进入作战记录。
    </p>

    <article
      v-for="event in events"
      :key="event.id"
      class="chat-timeline__event"
      :data-type="event.type"
    >
      <p class="chat-timeline__type">{{ event.type }}</p>
      <p class="chat-timeline__content">{{ event.content }}</p>
    </article>
  </div>
</template>
