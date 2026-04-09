import React, { useState, useEffect } from 'react';
import { Sidebar } from './components/layout/Sidebar';
import { CommandInput } from './components/input/CommandInput';
import { ChatHistory, type ChatMessage } from './components/chat/ChatHistory';
import { useSession } from './hooks/useSession';
import { useTerminalDimensions } from '@opentui/react';
import { ThemeProvider, useTheme } from './theme';
import { Spinner } from './components/ui/Spinner';

function AppContent() {
  const theme = useTheme();
  const { session, connectToSession, sendMessage, isConnected, error, sessionState } = useSession();
  const [status, setStatus] = useState<'idle' | 'busy' | 'error'>('idle');
  const [messages, setMessages] = useState<ChatMessage[]>([]);
  const [inputEnabled, setInputEnabled] = useState(true);

  const { width, height } = useTerminalDimensions();

  useEffect(() => {
    if (sessionState?.messages && sessionState.messages.length > 0) {
      const lastMsg = sessionState.messages[sessionState.messages.length - 1];
      const exists = messages.some(m => m.id === `ws-${lastMsg.timestamp}`);
      if (!exists) {
        const chatMsg: ChatMessage = {
          id: `ws-${lastMsg.timestamp}`,
          type: lastMsg.type === 'error' ? 'error' : 'response',
          content: lastMsg.content,
          timestamp: new Date(lastMsg.timestamp).toLocaleTimeString('zh-CN'),
          title: lastMsg.type,
        };
        setMessages((prev) => [...prev, chatMsg]);
      }
    }
  }, [sessionState?.messages]);

  React.useEffect(() => {
    if (session?.id) {
      connectToSession(session.id);
    }
  }, [session?.id]);

  const handleCommandSubmit = (command: string) => {
    const commandMsg: ChatMessage = {
      id: `cmd-${Date.now()}`,
      type: 'command',
      content: command,
      timestamp: new Date().toLocaleTimeString('zh-CN'),
      title: '命令',
    };
    setMessages((prev) => [...prev, commandMsg]);
    setStatus('busy');
    setInputEnabled(false);
    sendMessage('TUI_COMMAND', { command });
    setTimeout(() => {
      setStatus('idle');
      setInputEnabled(true);
    }, 1000);
  };

  const showSidebar = width > 100;

  return (
    <box style={{ height: '100%', width: '100%', flexDirection: 'column' }}>
      {/* Header */}
      <box style={{
        flexDirection: 'row',
        justifyContent: 'space-between',
        alignItems: 'center',
        paddingTop: 1,
        paddingBottom: 1,
        paddingLeft: 2,
        paddingRight: 2,
      }}>
        <box style={{ flexDirection: 'row', alignItems: 'center', gap: 3 }}>
          <text style={{ fg: theme.colors.primary }}><strong>GDB</strong></text>
          <text style={{ fg: theme.colors.textDisabled }}>|</text>
          <text style={{ fg: theme.colors.primaryLight }}>性能分析工具</text>
        </box>
        <box style={{ flexDirection: 'row', gap: 3, alignItems: 'center' }}>
          <text style={{ fg: isConnected ? theme.colors.success : theme.colors.error }}>
            {isConnected ? '● 已连接' : '○ 未连接'}
          </text>
          {status === 'busy' && <Spinner label="处理中" />}
        </box>
      </box>

      {/* Main Content Area */}
      <box style={{
        flexDirection: 'row',
        flexGrow: 1,
        minHeight: 0,
      }}>
        {/* Chat Area */}
        <box style={{
          width: showSidebar ? '80%' : '100%',
          flexDirection: 'column',
          paddingRight: showSidebar ? 1 : 0,
        }}>
          {/* Chat Header */}
          <box style={{
            paddingLeft: 2,
            paddingTop: 1,
            paddingBottom: 1,
          }}>
            <text style={{ fg: theme.colors.textSecondary }}>会话记录</text>
          </box>

          {/* Scrollable Chat History */}
          <scrollbox style={{
            flexGrow: 1,
            minHeight: 0,
          }}>
            <box style={{ paddingLeft: 2, paddingRight: 2, paddingTop: 1, paddingBottom: 1 }}>
              <ChatHistory messages={messages} />
            </box>
          </scrollbox>
        </box>

        {/* Sidebar */}
        {showSidebar && (
          <box style={{ width: '20%' }}>
            <Sidebar
              isConnected={isConnected}
              status={status}
              sessionId={session?.id || '-'}
            />
          </box>
        )}
      </box>

      {/* Input Area */}
      <box style={{
        flexDirection: 'column',
        paddingTop: 1,
        paddingBottom: 1,
        paddingLeft: 2,
        paddingRight: 2,
      }}>
        <box style={{ flexDirection: 'row', alignItems: 'center', gap: 2 }}>
          <text style={{ fg: theme.colors.primary }}>❯</text>
          <box style={{ flexGrow: 1 }}>
            <CommandInput
              onSubmit={handleCommandSubmit}
              disabled={!inputEnabled || status === 'busy'}
            />
          </box>
        </box>
      </box>

      {/* Error Toast */}
      {error && (
        <box style={{
          position: 'absolute',
          bottom: 4,
          left: 2,
          right: 2,
          backgroundColor: theme.colors.gray900,
          border: true,
          borderColor: theme.colors.error,
          paddingTop: 1,
          paddingBottom: 1,
          paddingLeft: 2,
          paddingRight: 2,
        }}>
          <text style={{ fg: theme.colors.error }}>✗ {error.message}</text>
        </box>
      )}
    </box>
  );
}

export function App() {
  return (
    <ThemeProvider>
      <AppContent />
    </ThemeProvider>
  );
}