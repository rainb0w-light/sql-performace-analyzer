interface BoxProps {
  children?: React.ReactNode;
  style?: Record<string, unknown>;
  flexDirection?: 'row' | 'column';
  justifyContent?: 'flex-start' | 'flex-end' | 'center' | 'space-between' | 'space-around';
  alignItems?: 'flex-start' | 'flex-end' | 'center' | 'stretch';
  padding?: number | [number, number];
  gap?: number;
  flex?: number;
  height?: string | number;
  width?: string | number;
  marginBottom?: number;
  marginTop?: number;
  marginLeft?: number;
  marginRight?: number;
}

export function Box({
  children,
  flexDirection = 'column',
  justifyContent,
  alignItems,
  padding,
  gap,
  flex,
  height,
  width,
  marginBottom,
  marginTop,
  marginLeft,
  marginRight,
  style = {},
}: BoxProps) {
  const boxStyle: Record<string, unknown> = {
    flexDirection,
    ...style,
  };

  if (justifyContent) boxStyle.justifyContent = justifyContent;
  if (alignItems) boxStyle.alignItems = alignItems;
  if (padding !== undefined) {
    boxStyle.padding = Array.isArray(padding) ? padding : [padding, padding];
  }
  if (gap !== undefined) boxStyle.gap = gap;
  if (flex !== undefined) boxStyle.flex = flex;
  if (height !== undefined) boxStyle.height = height;
  if (width !== undefined) boxStyle.width = width;
  if (marginBottom !== undefined) boxStyle.marginBottom = marginBottom;
  if (marginTop !== undefined) boxStyle.marginTop = marginTop;
  if (marginLeft !== undefined) boxStyle.marginLeft = marginLeft;
  if (marginRight !== undefined) boxStyle.marginRight = marginRight;

  return <box style={boxStyle}>{children}</box>;
}
