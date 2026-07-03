/**
 * Auth composable — 用户状态 + 登录/登出
 */
export const useAuth = () => {
  const { login: apiLogin, getCurrentUser, logout: apiLogout, token } = useApi()

  const user = useState<{
    id: number
    username: string
    email: string
    avatarUrl: string
    role: string
    credits: number
  } | null>('auth-user', () => null)

  const isLoggedIn = computed(() => !!token.value)

  const login = async (username: string, password: string) => {
    const data = await apiLogin(username, password)
    user.value = data.user
    return data
  }

  const fetchUser = async () => {
    if (!token.value) return
    try {
      user.value = await getCurrentUser()
    } catch {
      // token 过期
      user.value = null
    }
  }

  const logout = () => {
    user.value = null
    apiLogout()
  }

  return { user, isLoggedIn, login, fetchUser, logout }
}