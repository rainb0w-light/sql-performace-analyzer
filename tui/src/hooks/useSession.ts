import { useState, useEffect, useCallback } from 'react';
import { useWebSocket, type WebSocketMessage } from './useWebSocket';

interface Session {
  id: string;
  name: string;
  status: 'idle' | 'active' | 'error';
}

interface SessionState {
  id: string;
  status: string;
  messages: Array<{ type: string; content: string; timestamp: number }>;
}

export function useSession() {
  const [session, setSession] = useState<Session | null>(null);
  const [sessionState, setSessionState] = useState<SessionState | null>(null);

  const handleSessionMessage = useCallback((message: WebSocketMessage) => {
    console.log('[useSession] 收到消息:', message.type);

    setSessionState((prev) => {
      if (!prev) return null;
      return {
        ...prev,
        messages: [
          ...prev.messages,
          {
            type: message.type,
            content: JSON.stringify(message.payload),
            timestamp: Date.now(),
          },
        ],
      };
    });
  }, []);

  const { isConnected, error, sendCommand, client } = useWebSocket({
    sessionId: session?.id,
    onMessage: handleSessionMessage,
    onConnect: () => {
      console.log('[useSession] 已连接到会话');
    },
    onDisconnect: () => {
      console.log('[useSession] 已断开连接');
    },
  });

  const connectToSession = (sessionId: string) => {
    console.log(`[useSession] 连接到会话：${sessionId}`);
    setSession({
      id: sessionId,
      name: `会话 ${sessionId.slice(-4)}`,
      status: 'active',
    });
    setSessionState({
      id: sessionId,
      status: 'connected',
      messages: [],
    });
  };

  const disconnect = () => {
    client?.deactivate();
    setSession(null);
    setSessionState(null);
  };

  const sendMessage = (type: string, payload?: unknown) => {
    if (!session?.id) {
      console.error('[useSession] 会话未连接');
      return;
    }

    const message: WebSocketMessage = {
      type,
      sessionId: session.id,
      payload,
    };

    client?.publish({
      destination: '/app/message',
      body: JSON.stringify(message),
    });
  };

  return {
    session,
    sessionState,
    isConnected,
    error,
    connectToSession,
    disconnect,
    sendCommand,
    sendMessage,
  };
}
