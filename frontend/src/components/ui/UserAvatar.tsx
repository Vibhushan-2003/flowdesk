import { type HTMLAttributes } from 'react'

export interface UserAvatarProps extends HTMLAttributes<HTMLDivElement> {
  name?: string | null
  email?: string | null
  size?: 'sm' | 'md' | 'lg'
}

function getInitials(name?: string | null, email?: string | null): string {
  if (name && name.trim()) {
    const parts = name.trim().split(/\s+/)
    if (parts.length >= 2) {
      return `${parts[0][0]}${parts[1][0]}`.toUpperCase()
    }
    return parts[0].slice(0, 2).toUpperCase()
  }
  if (email && email.trim()) {
    return email.slice(0, 2).toUpperCase()
  }
  return 'FD'
}

export function UserAvatar({
  name,
  email,
  size = 'md',
  className = '',
  ...props
}: UserAvatarProps) {
  const initials = getInitials(name, email)

  return (
    <div
      className={`horizon-avatar horizon-avatar--${size} horizon-avatar-${size} ${className}`}
      title={name || email || 'User'}
      aria-label={name || email || 'User Avatar'}
      {...props}
    >
      <span className="horizon-avatar-initials horizon-avatar__initials">{initials}</span>
    </div>
  )
}
