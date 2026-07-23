import { defineStore } from 'pinia'
import { ref } from 'vue'

type MenuItem = {
    name: string
    path?: string // 可能是纯目录，所以 path 可选
    children?: MenuItem[]
}

const AUTH_KEY = 'smartlab_auth'

export async function readJsonResponse(response: Response) {
    const text = await response.text()
    if (!text.trim()) return null
    try {
        return JSON.parse(text)
    } catch {
        return null
    }
}

export const useAuthStore = defineStore('auth', () => {
    const isAuthenticated = ref(false)
    const username = ref('')
    const roleName = ref('')
    const userBasicInfo = ref('') // ✨ 新增：基本信息（中文身份）
    const lab = ref('')
    const avatar = ref('')
    const token = ref('')
    const menus = ref<MenuItem[]>([])
    const permissions = ref<any[]>([])
    const authObjects = ref<string[]>([])
    const menuLoaded = ref(false)

    // ✨ 新增：判断用户是否有某个权限的方法
    const normalizePermission = (permission: any) => {
        if (!permission) return ''
        if (typeof permission === 'string') return permission
        if (permission.object && permission.action) return `${permission.object}:${permission.action}`
        return ''
    }

    const hasPermission = (permission: string) => {
        if (roleName.value === 'system_admin') return true
        const normalized = new Set([
            ...authObjects.value.map(normalizePermission),
            ...permissions.value.map(normalizePermission)
        ].filter(Boolean))
        return normalized.has(permission)
    }

    const visiblePaths = () => {
        const paths = new Set<string>()
        const walk = (items: MenuItem[]) => {
            items.forEach(item => {
                if (item.path) paths.add(item.path)
                if (item.children?.length) walk(item.children)
            })
        }
        walk(menus.value)
        return paths
    }

    const firstVisiblePath = () => {
        const paths = visiblePaths()
        return paths.values().next().value || '/home'
    }

    const clearAuth = () => {
        isAuthenticated.value = false
        username.value = ''
        roleName.value = ''
        userBasicInfo.value = ''
        lab.value = ''
        avatar.value = ''
        token.value = ''
        menus.value = []
        permissions.value = []
        authObjects.value = []
        menuLoaded.value = false
        localStorage.removeItem(AUTH_KEY)
    }

    const setAuth = (userData: any, persistMenus = true) => {
        isAuthenticated.value = true
        username.value = userData.username
        roleName.value = userData.roleName
        userBasicInfo.value = userData.userBasicInfo || userData.roleName // ✨ 存储基本信息
        lab.value = userData.lab || ''
        avatar.value = userData.avatar || ''
        token.value = userData.token || ''
        if (persistMenus) {
            menus.value = userData.menus || []
            menuLoaded.value = true
        } else {
            menus.value = []
            menuLoaded.value = false
        }
        permissions.value = userData.permissions || []
        authObjects.value = userData.authObjects || []

        localStorage.setItem(AUTH_KEY, JSON.stringify({
            username: username.value,
            roleName: roleName.value,
            userBasicInfo: userBasicInfo.value,
            lab: lab.value,
            avatar: avatar.value,
            token: token.value,
            permissions: permissions.value,
            authObjects: authObjects.value
        }))
    }

    const refreshProfile = async () => {
        if (!token.value) return null
        try {
            const res = await fetch('/api/user/profile', {
                headers: { Authorization: `Bearer ${token.value}` }
            })
            if (res.status === 401) {
                clearAuth()
                return null
            }
            const body = await readJsonResponse(res)
            if (res.ok && body?.success && body.data) {
                setAuth({ ...body.data, token: token.value })
                return body.data
            }
            return null
        } catch {
            return null
        }
    }

    const initAuth = () => {
        try {
            const raw = localStorage.getItem(AUTH_KEY)
            if (!raw) return
            const saved = JSON.parse(raw)
            if (saved?.username) {
                setAuth(saved, false)
            }
        } catch {
            clearAuth()
        }
    }

    initAuth()

    return {
        isAuthenticated,
        username,
        roleName,
        userBasicInfo,
        lab,
        avatar,
        token,
        menus,
        permissions,
        authObjects,
        menuLoaded,
        clearAuth,
        setAuth,
        initAuth,
        refreshProfile,
        hasPermission,
        visiblePaths,
        firstVisiblePath
    }
})
