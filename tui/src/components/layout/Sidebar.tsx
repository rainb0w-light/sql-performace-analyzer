import React from 'react';
import { useTheme } from '../../theme';

interface SidebarProps {
  isConnected: boolean;
  status: 'idle' | 'busy' | 'error';
  sessionId: string;
}

export function Sidebar({ isConnected, status, sessionId }: SidebarProps) {
  const { colors } = useTheme();
  const statusText = status === 'idle' ? '就绪' : status === 'busy' ? '处理中' : '错误';
  const statusColor = status === 'idle' ? colors.success : status === 'busy' ? colors.warning : colors.error;

  // 截断过长的 session ID
  const displaySessionId = sessionId.length > 12 ? sessionId.substring(0, 10) + '...' : sessionId;

  return (
    <box
      style={{
        width: 24,
        flexDirection: 'column',
        paddingLeft: 1,
        paddingRight: 1,
      }}
    >
      {/* Status Section */}
      <box style={{
        border: true,
        borderColor: colors.border,
        paddingTop: 1,
        paddingBottom: 1,
        paddingLeft: 2,
        paddingRight: 2,
        marginBottom: 1,
      }}>
        <text style={{ fg: colors.textSecondary }}><strong>状态</strong></text>
      </box>

      <box style={{ paddingLeft: 2, paddingBottom: 1 }}>
        <text style={{ fg: colors.textSecondary }}>WebSocket:</text>
      </box>
      <box style={{ paddingLeft: 2, paddingBottom: 1 }}>
        <text style={{ fg: isConnected ? colors.success : colors.error }}>
          {isConnected ? '● 已连接' : '○ 未连接'}
        </text>
      </box>

      <box style={{ paddingLeft: 2, paddingBottom: 1 }}>
        <text style={{ fg: colors.textSecondary }}>状态:</text>
      </box>
      <box style={{ paddingLeft: 2, paddingBottom: 1 }}>
        <text style={{ fg: statusColor }}>{statusText}</text>
      </box>

      <box style={{ paddingLeft: 2, paddingBottom: 1 }}>
        <text style={{ fg: colors.textSecondary }}>Session:</text>
      </box>
      <box style={{ paddingLeft: 2, paddingBottom: 2 }}>
        <text style={{ fg: colors.text }}>{displaySessionId}</text>
      </box>
    </box>
  );
}
