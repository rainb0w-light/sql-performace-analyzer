interface ButtonProps {
  label: string;
  onClick: () => void;
  shortcut?: string;
  variant?: 'primary' | 'secondary' | 'danger';
  disabled?: boolean;
}

export function Button({ label, onClick, shortcut, variant = 'secondary', disabled = false }: ButtonProps) {
  const colors: Record<string, string> = {
    primary: 'cyan',
    secondary: '#666',
    danger: 'red',
  };

  const bgColor: Record<string, string> = {
    primary: '#1a3a4a',
    secondary: '#2a2a2a',
    danger: '#3a1a1a',
  };

  return (
    <box
      style={{
        padding: [0, 2],
        backgroundColor: disabled ? '#1a1a1a' : bgColor[variant],
        border: 'single',
        borderColor: disabled ? '#333' : colors[variant],
        contentAlign: 'center',
      }}
    >
      <text
        style={{ fg: disabled ? '#444' : colors[variant], dim: disabled }}
      >{` ${label}${shortcut ? ` [${shortcut}]` : ''} `}</text>
    </box>
  );
}
