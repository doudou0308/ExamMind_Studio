<!--
  QuestionAnalyze.vue —— 管理员"题库分析"页面
  功能：
    1. 题库知识 Wiki（LLM 生成的分类知识总结）
    2. 题目质量报告（各题正确率分析）
-->
<template>
  <div class="analyze-pg">
    <!-- 知识 Wiki 卡片区 -->
    <div class="card">
      <div class="card-title">
        题库知识 Wiki
        <button class="btn-wiki" :disabled="wikiLoading" @click="doRebuildWiki">
          {{ wikiLoading ? '生成中...' : wikiList.length ? '重新生成' : '生成 Wiki' }}
        </button>
      </div>
      <p v-if="!wikiList.length && !wikiLoading" class="card-empty">
        点击"生成 Wiki"，AI 将分析题库并生成知识点总结
      </p>
      <p v-if="wikiLoading" class="card-empty">AI 正在分析题库数据，请稍候...</p>
      <div v-if="wikiList.length" class="wiki-grid">
        <div v-for="w in wikiList" :key="w.category" class="wiki-card">
          <div class="wiki-card-header">
            <h4>{{ w.category }}</h4>
            <span v-if="w.weakQuestionIds" class="quality-badge bad">含待优化题</span>
          </div>
          <p class="wiki-summary">{{ w.summary }}</p>
          <p v-if="w.difficultyDist" class="wiki-meta">难度分布：{{ w.difficultyDist }}</p>
          <p v-if="w.relatedConcepts" class="wiki-meta">关联知识点：{{ w.relatedConcepts }}</p>
          <p v-if="w.weakQuestionIds" class="wiki-meta bad">待关注题目：{{ w.weakQuestionIds }}</p>
          <p class="wiki-time">更新于 {{ w.lastUpdated }}</p>
        </div>
      </div>
    </div>

  </div>
</template>

<script setup lang="ts">
import { ref, onMounted } from 'vue'
import { getWikiList, rebuildWiki } from '../api'

interface WikiEntry {
  category: string; summary: string; difficultyDist: string;
  weakQuestionIds: string; relatedConcepts: string; lastUpdated: string
}

const wikiList = ref<WikiEntry[]>([])
const wikiLoading = ref(false)

async function doRebuildWiki() {
  wikiLoading.value = true
  wikiList.value = []
  try {
    const r = await rebuildWiki()
    if (r && r.ok) wikiList.value = r.data || []
    else alert(r?.msg || '请求失败')
  } catch (e) { alert('请求异常：' + (e as Error).message) }
  wikiLoading.value = false
}

onMounted(() => {
  getWikiList().then(r => { if (r.ok) wikiList.value = r.data || [] })
})
</script>

<style scoped>
.analyze-pg { display: flex; flex-direction: column; gap: 20px; padding: 0 4px }

/* Wiki 卡片 */
.wiki-grid { display: grid; grid-template-columns: repeat(auto-fill, minmax(300px, 1fr)); gap: 14px; margin-top: 12px }
.wiki-card {
  border: 1px solid #e5e7eb; border-radius: 8px; padding: 16px; background: #fafafa; transition: .2s
}
.wiki-card:hover { box-shadow: 0 2px 8px rgba(0,0,0,.06) }
.wiki-card-header { display: flex; justify-content: space-between; align-items: center; margin-bottom: 8px }
.wiki-card-header h4 { font-size: 15px; color: #1f2937; margin: 0 }
.wiki-summary { font-size: 13px; color: #444; line-height: 1.6; margin-bottom: 8px }
.wiki-meta { font-size: 12px; color: #666; margin-bottom: 4px }
.wiki-meta.bad { color: #e53e3e }
.wiki-time { font-size: 11px; color: #aaa; margin-top: 8px }
.quality-badge { font-size: 11px; padding: 1px 6px; border-radius: 4px; font-weight: 500 }
.quality-badge.bad { background: #fef2f2; color: #e53e3e }

.btn-wiki {
  background: #4f46e5; color: #fff; border: none; padding: 5px 14px; border-radius: 5px;
  cursor: pointer; font-size: 12px; transition: .2s; margin-left: 12px
}
.btn-wiki:hover { background: #4338ca }
.btn-wiki:disabled { background: #a5b4fc; cursor: not-allowed }
.btn-wiki.sub { background: #6b7280; padding: 4px 12px }
.btn-wiki.sub:hover { background: #4b5563 }


</style>
