import { type HTMLAttributes } from 'react'

export interface SkeletonProps extends HTMLAttributes<HTMLDivElement> {
  variant?: 'text' | 'circular' | 'rectangular'
  width?: string | number
  height?: string | number
}

export function Skeleton({
  variant = 'text',
  width,
  height,
  className = '',
  style,
  ...props
}: SkeletonProps) {
  const inlineStyle = {
    width: typeof width === 'number' ? `${width}px` : width,
    height: typeof height === 'number' ? `${height}px` : height,
    ...style,
  }

  return (
    <div
      aria-hidden="true"
      className={`horizon-skeleton horizon-skeleton-${variant} ${className}`}
      style={inlineStyle}
      {...props}
    />
  )
}

