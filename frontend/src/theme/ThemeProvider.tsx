import {
  useCallback,
  useEffect,
  useState,
  type ReactNode,
} from 'react'
import {
  STORAGE_KEY,
  ThemeContext,
  type Theme,
  type ResolvedTheme,
} from './ThemeContext'

export function ThemeProvider({ children }: { children: ReactNode }) {
  const [theme, setThemeState] = useState<Theme>(() => {
    try {
      const saved = localStorage.getItem(STORAGE_KEY)
      if (saved === 'light' || saved === 'dark' || saved === 'system') {
        return saved
      }
    } catch {
      // Fallback if localStorage unavailable
    }
    return 'light'
  })

  const [systemDark, setSystemDark] = useState<boolean>(() => {
    if (typeof window !== 'undefined' && window.matchMedia) {
      return window.matchMedia('(prefers-color-scheme: dark)').matches
    }
    return false
  })

  // Listen to OS system preference changes
  useEffect(() => {
    if (typeof window === 'undefined' || !window.matchMedia) return
    const mediaQuery = window.matchMedia('(prefers-color-scheme: dark)')
    const handler = (event: MediaQueryListEvent) => {
      setSystemDark(event.matches)
    }
    mediaQuery.addEventListener('change', handler)
    return () => mediaQuery.removeEventListener('change', handler)
  }, [])

  const resolvedTheme: ResolvedTheme =
    theme === 'system' ? (systemDark ? 'dark' : 'light') : theme

  // Apply theme to document data-theme attribute
  useEffect(() => {
    const root = document.documentElement
    root.setAttribute('data-theme', resolvedTheme)
    root.classList.remove('theme-light', 'theme-dark')
    root.classList.add(`theme-${resolvedTheme}`)
  }, [resolvedTheme])

  const setTheme = useCallback((nextTheme: Theme) => {
    setThemeState(nextTheme)
    try {
      localStorage.setItem(STORAGE_KEY, nextTheme)
    } catch {
      // Ignore localStorage write error
    }
  }, [])

  const toggleTheme = useCallback(() => {
    setTheme(resolvedTheme === 'light' ? 'dark' : 'light')
  }, [resolvedTheme, setTheme])

  return (
    <ThemeContext.Provider
      value={{
        theme,
        resolvedTheme,
        setTheme,
        toggleTheme,
      }}
    >
      {children}
    </ThemeContext.Provider>
  )
}

