import type { PropsWithChildren } from 'react';

interface AlertProps extends PropsWithChildren {
  tone?: 'info' | 'success' | 'warning' | 'error';
  title?: string;
}

export function Alert({ children, title, tone = 'info' }: AlertProps) {
  return (
    <div
      className={`alert alert--${tone}`}
      role={tone === 'error' ? 'alert' : 'status'}
      aria-live={tone === 'error' ? 'assertive' : 'polite'}
    >
      {title && <strong className="alert__title">{title}</strong>}
      <div>{children}</div>
    </div>
  );
}
