import { Sun, Moon, Laptop } from 'lucide-react'
import { useTheme } from '../../theme/useTheme'

interface ThemeSwitcherProps {
  variant?: 'toggle' | 'segmented'
  className?: string
}

export function ThemeSwitcher({ variant = 'toggle', className = '' }: ThemeSwitcherProps) {
  const { theme, resolvedTheme, setTheme, toggleTheme } = useTheme()

  if (variant === 'segmented') {
    return (
      <div
        className={`theme-segmented-control ${className}`}
        role="radiogroup"
        aria-label="Color theme selection"
      >
        <button
          type="button"
          role="radio"
          aria-checked={theme === 'light'}
          className={`theme-segment-btn ${theme === 'light' ? 'is-active' : ''}`}
          onClick={() => setTheme('light')}
          title="Light theme"
        >
          <Sun size={14} aria-hidden="true" />
          <span>Light</span>
        </button>

        <button
          type="button"
          role="radio"
          aria-checked={theme === 'dark'}
          className={`theme-segment-btn ${theme === 'dark' ? 'is-active' : ''}`}
          onClick={() => setTheme('dark')}
          title="Dark theme"
        >
          <Moon size={14} aria-hidden="true" />
          <span>Dark</span>
        </button>

        <button
          type="button"
          role="radio"
          aria-checked={theme === 'system'}
          className={`theme-segment-btn ${theme === 'system' ? 'is-active' : ''}`}
          onClick={() => setTheme('system')}
          title="Match system appearance"
        >
          <Laptop size={14} aria-hidden="true" />
          <span>Auto</span>
        </button>
      </div>
    )
  }

  return (
    <button
      type="button"
      className={`theme-toggle-btn ${className}`}
      onClick={toggleTheme}
      aria-label={`Switch to ${resolvedTheme === 'dark' ? 'light' : 'dark'} mode`}
      title={`Switch to ${resolvedTheme === 'dark' ? 'light' : 'dark'} mode`}
    >
      {resolvedTheme === 'dark' ? (
        <Sun size={18} className="theme-toggle-icon" aria-hidden="true" />
      ) : (
        <Moon size={18} className="theme-toggle-icon" aria-hidden="true" />
      )}
    </button>
  )
}

