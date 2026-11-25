<!--
  StudentManage.vue —— 学生管理页面（管理员专用）
  功能：查看学生列表、添加学生、删除学生、重置密码
  所有操作通过弹窗确认后执行
-->
<template>
  <div>
    <!-- 顶部操作栏：添加按钮 + 总数提示 -->
    <div class="bar">
      <button class="btn-primary" @click="showAdd = true">+ 添加学生</button>
      <span class="tip">共 {{ total }} 名学生</span>
    </div>

    <!-- 学生列表表格 -->
    <table v-if="list.length">
      <thead>
        <tr><th>#</th><th>用户名</th><th>角色</th><th>操作</th></tr>
      </thead>
      <tbody>
        <tr v-for="(s, idx) in list" :key="s.id">
          <td>{{ (page - 1) * pageSize + idx + 1 }}</td>
          <td>{{ s.username }}</td>
          <td>{{ s.role }}</td>
          <td style="white-space:nowrap">
            <button class="btn-reset" @click="confirmReset(s)">重置密码</button>
            <button class="btn-del" @click="confirmDel(s)">删除</button>
          </td>
        </tr>
      </tbody>
    </table>
    <p v-else class="empty">暂无学生</p>

    <!-- 分页导航 -->
    <div v-if="totalPages > 1" class="pager">
      <button :disabled="page <= 1" @click="goPage(page - 1)">上一页</button>
      <span>第 {{ page }} / {{ totalPages }} 页 (共 {{ total }} 人)</span>
      <button :disabled="page >= totalPages" @click="goPage(page + 1)">下一页</button>
    </div>

    <!-- 添加学生弹窗 -->
    <div v-if="showAdd" class="modal" @click.self="closeAdd">
      <div class="modal-box">
        <h3>添加学生</h3>
        <label>用户名 <input v-model="form.username" placeholder="请输入用户名" /></label>
        <label>初始密码 <input v-model="form.password" placeholder="默认 123456" /></label>
        <p v-if="msg" :class="msgOk ? 'msg-success' : 'msg-error'">{{ msg }}</p>
        <div class="modal-btn">
          <button @click="closeAdd">取消</button>
          <button class="btn-primary" @click="add">确定</button>
        </div>
      </div>
    </div>

    <!-- 删除确认弹窗 -->
    <div v-if="showDel" class="modal" @click.self="showDel = false">
      <div class="modal-box">
        <h3>确认删除</h3>
        <p class="del-msg">确定要删除学生 <strong>{{ delTarget?.username }}</strong> 吗？此操作不可撤销。</p>
        <div class="modal-btn">
          <button @click="showDel = false">取消</button>
          <button class="btn-danger" @click="doDelete">确认删除</button>
        </div>
      </div>
    </div>

    <!-- 重置密码确认弹窗 -->
    <div v-if="showReset" class="modal" @click.self="showReset = false">
      <div class="modal-box">
        <h3>重置密码</h3>
        <p class="del-msg">确定要将 <strong>{{ resetTarget?.username }}</strong> 的密码重置为 123456 吗？学生下次登录需重新修改密码。</p>
        <div class="modal-btn">
          <button @click="showReset = false">取消</button>
          <button class="btn-primary" @click="doReset">确认重置</button>
        </div>
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
import { ref, computed, onMounted } from 'vue'
import { listStudents, addStudent, deleteStudent, resetStudentPassword } from '../api'

interface Student { id: number; username: string; role: string }

/** 学生列表数据 */
const list = ref<Student[]>([])
/** 当前页码 */
const page = ref(1)
const pageSize = 10
/** 学生总数 */
const total = ref(0)
/** 总页数（计算属性） */
const totalPages = computed(() => Math.ceil(total.value / pageSize) || 1)

/** 各弹窗显示/隐藏状态 */
const showAdd = ref(false)      // 添加弹窗
const showDel = ref(false)      // 删除确认弹窗
const showReset = ref(false)    // 重置密码确认弹窗
/** 待操作的目标学生 */
const delTarget = ref<Student | null>(null)
const resetTarget = ref<Student | null>(null)
/** 添加表单数据 */
const form = ref({ username: '', password: '' })
/** 操作结果提示 */
const msg = ref('')
const msgOk = ref(false)

/** 加载学生列表数据 */
async function load() {
  const r = await listStudents(page.value, pageSize)
  if (r.ok) { list.value = r.data; total.value = r.total }
}

onMounted(load)

/** 跳转到指定页码 */
function goPage(p: number) { page.value = p; load() }

/** 执行添加学生：密码为空时默认 123456 */
async function add() {
  const pwd = form.value.password || '123456'
  const res = await addStudent(form.value.username, pwd)
  msg.value = res.msg
  msgOk.value = res.ok
  if (res.ok) {
    form.value = { username: '', password: '' }
    load()
  }
}

/** 关闭添加弹窗并重置表单 */
function closeAdd() {
  showAdd.value = false
  msg.value = ''
  form.value = { username: '', password: '' }
}

/** 打开删除确认弹窗 */
function confirmDel(s: Student) {
  delTarget.value = s
  showDel.value = true
}

/** 执行删除学生 */
async function doDelete() {
  if (!delTarget.value) return
  await deleteStudent(delTarget.value.id)
  showDel.value = false
  delTarget.value = null
  load()
}

/** 打开重置密码确认弹窗 */
function confirmReset(s: Student) {
  resetTarget.value = s
  showReset.value = true
}

/** 执行重置密码 */
async function doReset() {
  if (!resetTarget.value) return
  await resetStudentPassword(resetTarget.value.id)
  showReset.value = false
  resetTarget.value = null
  load()
}
</script>

<style scoped>
.bar { display: flex; align-items: center; gap: 16px; margin-bottom: 16px }
.tip { color: #888; font-size: 14px }
.btn-primary { background: #4f46e5; color: #fff; border: none; padding: 8px 20px; border-radius: 6px; cursor: pointer; font-size: 14px }
.btn-reset { background: none; color: #3182ce; border: 1px solid #3182ce; padding: 4px 12px; border-radius: 4px; cursor: pointer; font-size: 13px; margin-right: 8px }
.btn-del { background: none; color: #e53e3e; border: 1px solid #e53e3e; padding: 4px 12px; border-radius: 4px; cursor: pointer; font-size: 13px }
.btn-danger { background: #e53e3e; color: #fff; border: none; padding: 8px 20px; border-radius: 6px; cursor: pointer; font-size: 14px }
table { width: 100%; background: #fff; border-radius: 8px; overflow: hidden; box-shadow: 0 1px 3px rgba(0, 0, 0, .1) }
th, td { padding: 10px 12px; text-align: left; font-size: 14px; border-bottom: 1px solid #f0f0f0 }
th { background: #fafafa; font-weight: 600 }
.empty { text-align: center; color: #999; padding: 40px }
.pager { display: flex; align-items: center; justify-content: center; gap: 16px; margin-top: 16px; font-size: 14px; color: #555 }
.pager button { padding: 6px 16px; border: 1px solid #ddd; border-radius: 6px; background: #fff; cursor: pointer; font-size: 13px }
.pager button:disabled { opacity: .4; cursor: not-allowed }
.pager button:hover:not(:disabled) { background: #f5f5f5 }
.modal { position: fixed; inset: 0; background: rgba(0, 0, 0, .4); display: flex; align-items: center; justify-content: center; z-index: 100 }
.modal-box { background: #fff; border-radius: 12px; padding: 24px; width: 400px }
.modal-box h3 { margin-bottom: 16px }
.modal-box label { display: block; margin-bottom: 10px; font-size: 14px }
.modal-box input { width: 100%; padding: 8px; border: 1px solid #ddd; border-radius: 6px; margin-top: 4px; font-size: 14px }
.modal-btn { display: flex; gap: 12px; justify-content: flex-end; margin-top: 16px }
.modal-btn button { padding: 8px 20px; border-radius: 6px; border: 1px solid #ddd; background: #fff; cursor: pointer }
.modal-btn .btn-primary { background: #4f46e5; color: #fff; border: none }
.modal-btn .btn-danger { background: #e53e3e; color: #fff; border: none }
.msg-success { color: #38a169; font-size: 13px; margin-bottom: 4px }
.msg-error { color: #e53e3e; font-size: 13px; margin-bottom: 4px }
.del-msg { font-size: 14px; color: #555; margin-bottom: 8px }
</style>
