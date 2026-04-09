import React from 'react';
import { useTheme } from '@/theme';

interface ChatBubbleProps {
  type: 'command' | 'response' | 'error' | 'info';
  content: string;
  timestamp?: string;
  title?: string;
}

export function ChatBubble({ type = 'response', content, timestamp, title }: ChatBubbleProps) {
  const theme = useTheme();

  const TYPE_COLORS: Record<string, string> = {
    command: theme.colors.command,
    response: theme.colors.response,
    error: theme.colors.error,
    info: theme.colors.highlight,
  };

  const TYPE_ICONS: Record<string, string> = {
    command: '❯',
    response: '●',
    error: '✗',
    info: 'ℹ',
  };

  const color = TYPE_COLORS[type];
  const icon = TYPE_ICONS[type];

  const borderColor = type === 'error' ? theme.colors.error :
                      type === 'command' ? theme.colors.command :
                      theme.colors.border;

  const bgColor = type === 'error' ? '#2a1a1a' : undefined;

  return (
    <box style={{
      paddingTop: 1,
      paddingBottom: 1,
      marginTop: 1,
      marginBottom: 1,
      border: true,
      borderStyle: 'single',
      borderColor,
      backgroundColor: bgColor,
      paddingLeft: 2,
      paddingRight: 2,
    }}>
      {/* Header line */}
      <box style={{ flexDirection: 'row', alignItems: 'center' }}>
        <text style={{ fg: color }}>{icon}</text>
        <text style={{ fg: color, marginLeft: 1 }}><strong>{title || type}</strong></text>
        {timestamp && (
          <text style={{ fg: theme.colors.textDisabled, marginLeft: 2 }}>{timestamp}</text>
        )}
      </box>

      {/* Content */}
      <box style={{
        marginTop: 1,
        paddingTop: 1,
      }}>
        <text style={{
          fg: type === 'error' ? theme.colors.error : theme.colors.text,
        }}>{content}</text>
      </box>
    </box>
  );
}