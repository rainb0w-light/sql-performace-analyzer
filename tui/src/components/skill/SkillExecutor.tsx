import { useState } from 'react';
import { Button } from '../ui/Button';

const SKILL_OPTIONS = [
  { name: 'analyze_sql', description: 'SQL 性能分析', requiresSql: true },
  { name: 'get_execution_plan', description: '获取执行计划', requiresSql: true },
  { name: 'get_table_structure', description: '获取表结构', requiresTable: true },
  { name: 'suggest_indexes', description: '索引建议', requiresSql: true },
  { name: 'explain_query', description: '查询解释', requiresSql: true },
];

interface SkillExecutorProps {
  sessionId?: string;
  onExecute?: (skillName: string, params: Record<string, unknown>) => void;
}

export function SkillExecutor({ sessionId, onExecute }: SkillExecutorProps) {
  const [sqlInput, setSqlInput] = useState('');
  const [selectedSkill, setSelectedSkill] = useState<string | null>(null);

  const handleExecute = (skillName: string) => {
    setSelectedSkill(skillName);
    onExecute?.(skillName, { sql: sqlInput });
  };

  return (
    <box style={{ height: '100%', gap: 1 }}>
      <text style={{ fg: 'cyan' }}>技能执行</text>

      <box style={{ border: true, borderStyle: 'single', borderColor: '#333', paddingTop: 1, paddingBottom: 1, paddingLeft: 1, paddingRight: 1 }}>
        <text style={{ fg: '#888' }}>SQL 输入:</text>
        <box
          style={{
            border: true,
            borderStyle: 'single',
            borderColor: '#444',
            paddingTop: 1,
            paddingBottom: 1,
            paddingLeft: 1,
            paddingRight: 1,
            marginTop: 1,
            height: 5,
          }}
        >
          <text>{sqlInput || '输入 SQL 语句...'}</text>
        </box>
      </box>

      <box style={{ flexDirection: 'column', gap: 1 }}>
        <text style={{ fg: '#888' }}>可选技能:</text>
        {SKILL_OPTIONS.map((skill) => (
          <box
            key={skill.name}
            style={{
              flexDirection: 'row',
              justifyContent: 'space-between',
              paddingTop: 1,
              paddingBottom: 1,
              paddingLeft: 1,
              paddingRight: 1,
              backgroundColor: selectedSkill === skill.name ? '#2a2a4a' : 'transparent',
            }}
          >
            <box style={{ flexDirection: 'column' }}>
              <text style={{ fg: 'cyan' }}>{skill.name}</text>
              <text style={{ fg: '#666' }}>{skill.description}</text>
            </box>
            <Button label="执行" onClick={() => handleExecute(skill.name)} variant="primary" />
          </box>
        ))}
      </box>
    </box>
  );
}
