<template>
  <section class="preferences-page">
    <header class="page-heading"><h1>{{ t('个性化') }}</h1></header>
    <el-card shadow="never" class="glass-panel appearance-card">
      <div class="preference-row">
        <div>
          <h2 id="theme-heading">{{ t('界面外观') }}</h2>
          <p>{{ t(theme === 'night' ? '夜晚模式' : '白天模式') }}</p>
        </div>
        <button class="day-night" type="button" role="switch" :aria-checked="theme === 'night'"
                :aria-label="t('夜晚模式')" @click="theme = theme === 'night' ? 'day' : 'night'">
          <span class="day-night__thumb" aria-hidden="true" />
          <el-icon class="day-night__sun" aria-hidden="true"><Sunny /></el-icon>
          <el-icon class="day-night__moon" aria-hidden="true"><Moon /></el-icon>
        </button>
      </div>
      <div class="preference-row">
        <div>
          <h2 id="material-heading">{{ t('界面质感') }}</h2>
          <p>{{ t('应用于整个工作台') }}</p>
        </div>
        <el-radio-group v-model="material" aria-labelledby="material-heading">
          <el-radio-button value="glass">{{ t('磨砂玻璃') }}</el-radio-button>
          <el-radio-button value="solid">{{ t('实色界面') }}</el-radio-button>
        </el-radio-group>
      </div>
    </el-card>
  </section>
</template>

<script setup>
import { Moon, Sunny } from '@element-plus/icons-vue'
import { material, theme, t } from '../preferences.js'
</script>

<style scoped>
.preferences-page { display: grid; max-width: 800px; gap: 20px; }
.preferences-page :deep(.el-card__body) { padding-block: 0; }
.preference-row { display: flex; justify-content: space-between; align-items: center; flex-wrap: wrap; gap: 16px 24px; padding: 24px 0; }
.preference-row + .preference-row { border-top: 1px solid var(--border); }
.preference-row h2 { font-size: 17px; }
.preference-row p { margin-top: 4px; color: var(--text-3); }
.day-night {
  position: relative;
  display: flex;
  align-items: center;
  justify-content: space-around;
  width: 84px;
  height: 40px;
  padding: 0;
  border: 1px solid var(--border-strong);
  border-radius: 20px;
  background: var(--theme-switch-fill);
  color: var(--text-3);
  cursor: pointer;
}
.day-night__thumb {
  position: absolute;
  inset: 3px auto auto 4px;
  width: 32px;
  height: 32px;
  border: 1px solid var(--accent-border);
  border-radius: 50%;
  background: var(--surface);
  box-shadow: 0 2px 4px var(--theme-switch-shadow);
  transition: transform .28s var(--ease-out), background-color .24s var(--ease-out);
}
.day-night .el-icon { font-size: 16px; }
.day-night__sun, .day-night[aria-checked="true"] .day-night__moon { color: var(--accent); }
.day-night[aria-checked="true"] .day-night__sun { color: var(--text-3); }
.day-night[aria-checked="true"] .day-night__thumb { transform: translateX(42px); background: var(--accent-soft); }
@media (prefers-reduced-motion: reduce) { .day-night__thumb { transition: none; } }
</style>
