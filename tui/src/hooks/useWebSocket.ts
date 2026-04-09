import { useEffect, useState, useCallback } from 'react';
import { Client, type Message } from '@stomp/stompjs';

const WS_URL = process.env.WS_URL || 'ws://localhost:18881/ws';

export interface WebSocketMessage {
  type: string;
  sessionId: string;
  payload?: unknown;
}

export interface WebSocketConfig {
  sessionId?: string;
  onMessage?: (message: WebSocketMessage) => void;
  onError?: (error: Error) => void;
  onConnect?: () => void;
  onDisconnect?: () => void;
}

export function useWebSocket(config: WebSocketConfig = {}) {
  const [isConnected, setIsConnected] = useState(false);
  const [error, setError] = useState<Error | null>(null);
  const [client, setClient] = useState<Client | null>(null);

  useEffect(() => {
    if (!config.sessionId) return;

    const stompClient = new Client({
      brokerURL: WS_URL,
      reconnectDelay: 5000,
      connectionTimeout: 10000,
      debug: () => {},
    });

    stompClient.onConnect = () => {
      setIsConnected(true);

      // 订阅会话更新消息
      stompClient.subscribe(`/topic/session/${config.sessionId}`, (message: Message) => {
        const data = JSON.parse(message.body);
        config.onMessage?.(data);
      });

      // 订阅全局响应消息
      stompClient.subscribe('/topic/response', (message: Message) => {
        const data = JSON.parse(message.body);
        config.onMessage?.(data);
      });

      config.onConnect?.();
    };

    stompClient.onDisconnect = () => {
      setIsConnected(false);
      config.onDisconnect?.();
    };

    stompClient.onStompError = (err: Message) => {
      const error = new Error(err.body || 'STOMP error');
      setError(error);
      config.onError?.(error);
    };

    stompClient.activate();
    setClient(stompClient);

    return () => {
      stompClient.deactivate();
      setClient(null);
    };
  }, [config.sessionId]);

  const sendCommand = useCallback((command: string) => {
    if (!client || !client.connected) {
      console.error('[WebSocket] 客户端未连接');
      return;
    }

    const message: WebSocketMessage = {
      type: 'TUI_COMMAND',
      sessionId: config.sessionId || 'default',
      payload: { command },
    };

    client.publish({
      destination: '/app/message',
      body: JSON.stringify(message),
    });
  }, [client, config.sessionId]);

  return { isConnected, error, sendCommand, client };
}
