import { Box } from '../ui/Box';
import { Button } from '../ui/Button';

interface DdlOperation {
  type: 'CREATE' | 'ALTER' | 'DROP' | 'TRUNCATE';
  table: string;
  description: string;
  impact: string;
  ddl: string;
  rollback: string;
}

interface DdlConfirmationDialogProps {
  operation?: DdlOperation | null;
  onConfirm?: () => void;
  onDeny?: () => void;
  visible?: boolean;
}

export function DdlConfirmationDialog({
  operation,
  onConfirm,
  onDeny,
  visible = false,
}: DdlConfirmationDialogProps) {
  if (!visible || !operation) return null;

  return (
    <box
      style={{
        position: 'absolute',
        top: '50%',
        left: '50%',
        width: 80,
        height: 20,
        transform: 'translate(-50%, -50%)',
        border: 'double',
        borderColor: 'cyan',
        backgroundColor: '#1a1a2e',
        padding: [1, 2],
      }}
    >
      <box style={{ marginBottom: 1 }}>
        <text style={{ fg: 'cyan', bold: true }}>确认 DDL 操作</text>
      </box>

      <box style={{ flexDirection: 'column', gap: 1, marginBottom: 1 }}>
        <box style={{ flexDirection: 'row' }}>
          <text style={{ fg: '#888' }}>类型：</text>
          <text style={{ fg: 'yellow' }}>{operation.type}</text>
        </box>
        <box style={{ flexDirection: 'row' }}>
          <text style={{ fg: '#888' }}>表名：</text>
          <text>{operation.table}</text>
        </box>
        <box style={{ flexDirection: 'row' }}>
          <text style={{ fg: '#888' }}>描述：</text>
          <text>{operation.description}</text>
        </box>
        <box style={{ flexDirection: 'row' }}>
          <text style={{ fg: '#888' }}>影响：</text>
          <text style={{ fg: 'red' }}>{operation.impact}</text>
        </box>
      </box>

      <box
        style={{
          padding: [1, 1],
          backgroundColor: '#111',
          border: 'single',
          borderColor: '#333',
          marginBottom: 1,
        }}
      >
        <text style={{ fg: 'green' }}>{operation.ddl}</text>
      </box>

      <box style={{ flexDirection: 'row', gap: 2, justifyContent: 'flex-end' }}>
        <Button label="拒绝 [N]" onClick={onDeny || (() => {})} variant="danger" />
        <Button label="确认 [Y]" onClick={onConfirm || (() => {})} variant="primary" />
      </box>
    </box>
  );
}
