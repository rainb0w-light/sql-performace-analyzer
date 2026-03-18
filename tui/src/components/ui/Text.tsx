interface TextProps {
  children?: React.ReactNode;
  bold?: boolean;
  dim?: boolean;
  color?: string;
  style?: Record<string, unknown>;
}

export function Text({ children, bold, dim, color, style = {} }: TextProps) {
  const textStyle: Record<string, unknown> = {
    ...style,
  };

  if (bold) textStyle.bold = bold;
  if (dim) textStyle.dim = dim;
  if (color) textStyle.fg = color;

  return <text style={textStyle}>{children}</text>;
}
