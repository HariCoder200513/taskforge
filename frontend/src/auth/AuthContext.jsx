import { createContext, useContext, useCallback, useEffect, useState } from 'react'
import { api, setAccessToken, setOnUnauthenticated, refreshSession } from '../api/client.js'

const AuthContext = createContext(null)

export function AuthProvider({ children }) {
  const [user, setUser] = useState(null)
  const [loading, setLoading] = useState(true)

  // On first load, try to restore a session from the refresh cookie.
  useEffect(() => {
    let active = true
    ;(async () => {
      const ok = await refreshSession()
      if (ok) {
        try {
          const me = await api.get('/auth/me')
          if (active) setUser(me)
        } catch {
          /* ignore — treated as logged out */
        }
      }
      if (active) setLoading(false)
    })()
    return () => { active = false }
  }, [])

  // When a call fails to refresh, drop the user so routes redirect to login.
  useEffect(() => {
    setOnUnauthenticated(() => setUser(null))
  }, [])

  const login = useCallback(async (email, password) => {
    const res = await api.post('/auth/login', { email, password }, { auth: false })
    setAccessToken(res.accessToken)
    setUser(res.user)
    return res.user
  }, [])

  const register = useCallback(async (email, password, name) => {
    return api.post('/auth/register', { email, password, name }, { auth: false })
  }, [])

  const logout = useCallback(async () => {
    try {
      await api.post('/auth/logout', undefined)
    } catch {
      /* best effort */
    }
    setAccessToken(null)
    setUser(null)
  }, [])

  const value = {
    user,
    loading,
    login,
    register,
    logout,
    isAuthenticated: !!user,
    roles: user?.roles ?? [],
    hasRole: (r) => (user?.roles ?? []).includes(r),
  }

  return <AuthContext.Provider value={value}>{children}</AuthContext.Provider>
}

export function useAuth() {
  const ctx = useContext(AuthContext)
  if (!ctx) throw new Error('useAuth must be used within AuthProvider')
  return ctx
}
