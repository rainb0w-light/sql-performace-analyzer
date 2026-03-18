import { useEffect, useState } from 'react';
import { Client, type Message } from '@stomp/stompjs';

const WS_URL = process.env.WS_URL || 'ws://localhost:8080/ws';

export interface WebSocketConfig {
  sessionId?: string;
  onMessage?: (message: unknown) => void;
  onError?: (error: Error) => void;
  onConnect?: () => void;
  onDisconnect?: () => void;
}

export function useWebSocket(config: WebSocketConfig = {}) {
  const [isConnected, setIsConnected] = useState(false);
  const [error, setError] = useState<Error | null>(null);

  useEffect(() => {
    if (!config.sessionId) return;

    const client = new Client({
      brokerURL: WS_URL,
      reconnectDelay: 5000,
      connectionTimeout: 10000,
      onConnect: () => {
        setIsConnected(true);
        client.subscribe(`/topic/session/${config.sessionId}`, (message: Message) => {
          config.onMessage?.(JSON.parse(message.body));
        });
        config.onConnect?.();
      },
      onDisconnect: () => {
        setIsConnected(false);
        config.onDisconnect?.();
      },
      onError: (err: Message) => {
        const error = new Error(err.body || 'WebSocket error');
        setError(error);
        config.onError?.(error);
      },
    });

    client.activate();

    return () => {
      client.deactivate();
    };
  }, [config.sessionId]);

  return { isConnected, error };
}
