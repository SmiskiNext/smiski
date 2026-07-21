import { forwardRef, type ButtonHTMLAttributes, type ReactNode } from 'react';
import { cn } from './cn';
import { buttonClassName, type ButtonSize, type ButtonVariant } from './buttonStyles';

export type { ButtonSize, ButtonVariant } from './buttonStyles';

export interface ButtonProps extends ButtonHTMLAttributes<HTMLButtonElement> {
  variant?: ButtonVariant;
  size?: ButtonSize;
  isLoading?: boolean;
  leadingIcon?: ReactNode;
}

export const Button = forwardRef<HTMLButtonElement, ButtonProps>(function Button(
  {
    variant = 'secondary',
    size = 'md',
    isLoading,
    leadingIcon,
    className,
    children,
    disabled,
    type = 'button',
    ...props
  },
  ref,
) {
  return (
    <button
      ref={ref}
      type={type}
      className={cn(buttonClassName(variant, size), className)}
      disabled={disabled || isLoading}
      {...props}
    >
      {isLoading ? (
        <span
          className="size-4 animate-spin rounded-full border-2 border-current border-r-transparent"
          aria-hidden="true"
        />
      ) : (
        leadingIcon
      )}
      {children}
    </button>
  );
});
