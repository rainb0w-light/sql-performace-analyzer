import { useTheme } from '../../theme';

interface ButtonProps {
  label: string;
  onClick: () => void;
  shortcut?: string;
  variant?: 'primary' | 'secondary' | 'danger';
  disabled?: boolean;
}

export function Button({ label, onClick, shortcut, variant = 'secondary', disabled = false }: ButtonProps) {
  const theme = useTheme();
  
  const variantStyles = {
    primary: {
      fg: theme.colors.primaryLight,
      bg: theme.colors.surface,
      border: theme.colors.primary,
    },
    secondary: {
      fg: theme.colors.textSecondary,
      bg: theme.colors.gray800,
      border: theme.colors.border,
    },
    danger: {
      fg: theme.colors.error,
      bg: theme.colors.gray800,
      border: theme.colors.error,
    },
  };

  // Get styles for current variant
  const currentStyle = variantStyles[variant];

  return (
    <box
      style={{
        paddingTop: 0,
        paddingBottom: 0,
        paddingLeft: 2,
        paddingRight: 2,
        backgroundColor: disabled ? theme.colors.gray900 : currentStyle.bg,
        border: true,
        borderStyle: 'single',
        borderColor: disabled ? theme.colors.gray700 : currentStyle.border,
      }}
    >
      <text
        style={{ fg: disabled ? theme.colors.textMuted : currentStyle.fg }}
      >{` ${label}${shortcut ? ` [${shortcut}]` : ''} `}</text>
    </box>
  );
}
