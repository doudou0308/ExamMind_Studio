/** 全局响应式状态管理（简易版，无需 Pinia/Vuex，靠 reactive 直接实现） */

import { reactive } from 'vue'

/** 用户基本信息接口 */
interface UserInfo {
  id: number               // 用户ID
  username: string         // 用户名
  role: string             // 角色：admin=管理员 / student=学生
  isFirstLogin?: boolean   // 是否首次登录（学生修改初始密码标记）
}

/** 全局共享状态：登录态、用户信息、Token */
export const store = reactive({
  loggedIn: false,                     // 是否已登录
  user: null as UserInfo | null,       // 当前用户信息
  token: '' as string,                 // 登录凭证（用于 API 请求头 Authorization）

  /** 登录：保存用户信息和 token，将 loggedIn 置为 true */
  login(user: UserInfo, token: string) {
    this.loggedIn = true
    this.user = user
    this.token = token
  },

  /** 登出：清空所有登录状态 */
  logout() {
    this.loggedIn = false
    this.user = null
    this.token = ''
  }
})
