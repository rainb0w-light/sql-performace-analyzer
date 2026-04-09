interface ExecutionPlanRow {
  id: number;
  selectType: string;
  table: string;
  type: string;
  possibleKeys: string[];
  key: string | null;
  keyLen: string | null;
  ref: string | null;
  rows: number;
  filtered: number;
  extra: string;
}

interface ExecutionPlanTableProps {
  plan: ExecutionPlanRow[];
}

export function ExecutionPlanTable({ plan }: ExecutionPlanTableProps) {
  const getTypeColor = (type: string) => {
    if (type === 'ALL') return 'red';
    if (type === 'ref' || type === 'eq_ref') return 'yellow';
    if (type === 'const' || type === 'primary') return 'green';
    return '#888';
  };

  if (plan.length === 0) {
    return (
      <box style={{ paddingTop: 1, paddingBottom: 1, paddingLeft: 1, paddingRight: 1 }}>
        <text style={{ fg: '#666' }}>暂无执行计划</text>
      </box>
    );
  }

  return (
    <box style={{ flexDirection: 'column' }}>
      <box style={{ flexDirection: 'row', marginBottom: 1 }}>
        <text style={{ fg: 'cyan' }}>执行计划</text>
      </box>

      <box
        style={{
          border: true,
          borderStyle: 'single',
          borderColor: '#333',
          paddingTop: 1,
          paddingBottom: 1,
          paddingLeft: 1,
          paddingRight: 1,
          flexDirection: 'column',
        }}
      >
        {plan.map((row, i) => (
          <box key={i} style={{ marginBottom: i < plan.length - 1 ? 1 : 0 }}>
            <box style={{ flexDirection: 'row', gap: 2 }}>
              <text style={{ fg: '#888' }}>{`[${row.id}]`}</text>
              <text style={{ fg: getTypeColor(row.type) }}>{row.type}</text>
              <text style={{ fg: 'cyan' }}>{row.table}</text>
              {row.key && (
                <text style={{ fg: 'green' }}>{`key: ${row.key}`}</text>
              )}
              <text style={{ fg: 'yellow' }}>{`rows: ${row.rows}`}</text>
            </box>
          </box>
        ))}
      </box>

      {plan.some((row) => row.type === 'ALL') && (
      <box
        style={{
          marginTop: 1,
          paddingTop: 1,
          paddingBottom: 1,
          paddingLeft: 1,
          paddingRight: 1,
          backgroundColor: '#3a1a1a',
          border: true,
          borderStyle: 'single',
          borderColor: 'red',
        }}
      >
          <text style={{ fg: 'red' }}>⚠ 检测到全表扫描，建议添加索引</text>
        </box>
      )}
    </box>
  );
}
