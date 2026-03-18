import { useState, useEffect } from 'react';

interface Session {
  id: string;
  name: string;
  status: 'idle' | 'active' | 'error';
}

export function useSession() {
  const [session, setSession] = useState<Session | null>(null);
  const [isConnected, setIsConnected] = useState(false);

  const connectToSession = (sessionId: string) => {
    console.log(`Connecting to session: ${sessionId}`);
    setIsConnected(true);
  };

  const disconnect = () => {
    setIsConnected(false);
  };

  return {
    session,
    isConnected,
    connectToSession,
    disconnect,
  };
}
