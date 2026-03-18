import { useState } from 'react';
import { Box } from '../ui/Box';
import { Text } from '../ui/Text';
import { Button } from '../ui/Button';

interface Session {
  id: string;
  name: string;
  status: 'idle' | 'active' | 'error';
  createdAt: string;
}

const MOCK_SESSIONS: Session[] = [
  { id: 'sess_001', name: '分析会话 1', status: 'idle', createdAt: '2024-01-15 10:30' },
  { id: 'sess_002', name: '分析会话 2', status: 'active', createdAt: '2024-01-15 11:45' },
];

interface SessionManagerProps {
  sessions?: Session[];
  onCreateSession?: () => void;
  onSelectSession?: (sessionId: string) => void;
  onCloseSession?: (sessionId: string) => void;
}

export function SessionManager({
  sessions = MOCK_SESSIONS,
  onCreateSession,
  onSelectSession,
  onCloseSession,
}: SessionManagerProps) {
  const [selectedId, setSelectedId] = useState<string | null>(null);

  const getStatusColor = (status: Session['status']) => {
    switch (status) {
      case 'idle':
        return 'green';
      case 'active':
        return 'yellow';
      case 'error':
        return 'red';
    }
  };

  return (
    <Box style={{ height: '100%' }}>
      <box style={{ flexDirection: 'row', justifyContent: 'space-between', marginBottom: 1 }}>
        <text style={{ fg: 'cyan', bold: true }}>会话管理</text>
        {onCreateSession && (
          <Button label="新建会话" onClick={onCreateSession} variant="primary" />
        )}
      </box>

      <box
        style={{
          flex: 1,
          border: 'single',
          borderColor: '#333',
          padding: [1, 1],
        }}
      >
        {sessions.length === 0 ? (
          <text style={{ fg: '#666' }}>暂无会话</text>
        ) : (
          sessions.map((session) => (
            <box
              key={session.id}
              style={{
                padding: [1, 1],
                backgroundColor: selectedId === session.id ? '#2a2a4a' : 'transparent',
                marginBottom: 1,
              }}
            >
              <box style={{ flexDirection: 'row', justifyContent: 'space-between' }}>
                <box style={{ flexDirection: 'row', gap: 1 }}>
                  <text style={{ fg: getStatusColor(session.status) }}>●</text>
                  <text>{session.name}</text>
                </box>
                <text style={{ fg: '#666' }}>{session.createdAt}</text>
              </box>
              <box style={{ flexDirection: 'row', gap: 1, marginTop: 1 }}>
                {onSelectSession && (
                  <Button
                    label="切换"
                    onClick={() => onSelectSession(session.id)}
                    variant="primary"
                  />
                )}
                {onCloseSession && (
                  <Button
                    label="关闭"
                    onClick={() => onCloseSession(session.id)}
                    variant="danger"
                  />
                )}
              </box>
            </box>
          ))
        )}
      </box>
    </Box>
  );
}
