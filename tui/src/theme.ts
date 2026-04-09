import React, { createContext, useContext } from 'react';

// Base theme definitions for OpenTUI
export const baseColors = {
  primary: '#3b82f6',
  primaryLight: '#60a5fa',
  primaryDark: '#1d4ed8',
  success: '#10b981',
  warning: '#f59e0b',
  error: '#ef4444',
  info: '#3b82f6',
  background: '#0f172a',
  surface: '#1e293b',
  border: '#334155',
  borderLight: '#475569',
  text: '#e2e8f0',
  textSecondary: '#94a3b8',
  textDisabled: '#64748b',
  textMuted: '#475569',
  command: 'cyan',
  response: '#e0e0e0',
  highlight: '#88c0d0',
  white: '#ffffff',
  black: '#000000',
  gray100: '#f1f5f9',
  gray200: '#e2e8f0',
  gray300: '#cbd5e1',
  gray400: '#94a3b8',
  gray500: '#64748b',
  gray600: '#475569',
  gray700: '#334155',
  gray800: '#1e293b',
  gray900: '#0f172a',
} as const;

export const baseSpacing = {
  xs: 1,
  sm: 2,
  md: 3,
  lg: 4,
  xl: 5,
  '2xl': 6,
} as const;

export const baseBorders = {
  default: 'single',
  strong: 'double',
  rounded: 'rounded',
} as const;

// Type definitions
export type ThemeColors = typeof baseColors;
export type ThemeSpacing = typeof baseSpacing;
export type ThemeBorders = typeof baseBorders;

// Enhanced theme interface optimized for OpenTUI components
export interface Theme {
  colors: ThemeColors;
  spacing: ThemeSpacing;
  borders: ThemeBorders;
  // Additional OpenTUI-specific properties
  componentStyles: {
    button: {
      primary: {
        bgColor?: string;
        fgColor: string;
        borderColor: string;
      };
      secondary: {
        bgColor?: string;
        fgColor: string;
        borderColor: string;
      };
    };
    input: {
      bgColor: string;
      fgColor: string;
      borderColor: string;
    };
    card: {
      bgColor: string;
      borderColor: string;
    };
    sidebar: {
      bgColor: string;
      borderColor: string;
      textColor: string;
    };
  };
}

// Default theme with OpenTUI-specific component styling
export const defaultTheme: Theme = {
  colors: baseColors,
  spacing: baseSpacing,
  borders: baseBorders,
  componentStyles: {
    button: {
      primary: {
        bgColor: baseColors.primary,
        fgColor: baseColors.white,
        borderColor: baseColors.primaryDark,
      },
      secondary: {
        bgColor: baseColors.surface,
        fgColor: baseColors.text,
        borderColor: baseColors.border,
      },
    },
    input: {
      bgColor: baseColors.surface,
      fgColor: baseColors.text,
      borderColor: baseColors.border,
    },
    card: {
      bgColor: baseColors.surface,
      borderColor: baseColors.border,
    },
    sidebar: {
      bgColor: baseColors.gray900,
      borderColor: baseColors.border,
      textColor: baseColors.text,
    },
  },
};

// React Context for theme
const ThemeContext = createContext<Theme>(defaultTheme);

// Custom hook to use theme
export const useTheme = () => {
  const context = useContext(ThemeContext);
  if (!context) {
    throw new Error('useTheme must be used within a ThemeProvider');
  }
  return context;
};

// For compatibility with OpenTUI's JSX in preserve mode, we create the context provider
// and export it, but don't embed JSX directly in the ts file
export function ThemeProvider({ children, theme }: { children: React.ReactNode; theme?: Partial<Theme> }) {
  const mergedTheme = {
    ...defaultTheme,
    ...theme,
    componentStyles: {
      ...defaultTheme.componentStyles,
      ...(theme?.componentStyles || {}),
    },
  };

  return React.createElement(
    ThemeContext.Provider,
    { value: mergedTheme },
    children
  );
}

// Export the theme as constant as well for backwards compatibility
export const theme = defaultTheme;