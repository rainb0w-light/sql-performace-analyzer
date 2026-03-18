import { Button } from '../ui/Button';

interface IndexSuggestion {
  table: string;
  columns: string[];
  reason: string;
  expectedImprovement: string;
  ddl: string;
}

interface IndexSuggestionsProps {
  suggestions: IndexSuggestion[];
  onApply?: (suggestion: IndexSuggestion) => void;
  onApplyAll?: () => void;
}

export function IndexSuggestions({ suggestions, onApply, onApplyAll }: IndexSuggestionsProps) {
  if (suggestions.length === 0) {
    return (
      <box style={{ padding: [1, 1], backgroundColor: '#1a3a1a' }}>
        <text style={{ fg: 'green' }}>✓ 当前查询无需优化</text>
      </box>
    );
  }

  return (
    <box style={{ flexDirection: 'column', marginBottom: 2 }}>
      <box style={{ marginBottom: 1, flexDirection: 'row', justifyContent: 'space-between' }}>
        <text style={{ fg: 'cyan' }}>索引建议 ({suggestions.length})</text>
        {suggestions.length > 1 && (
          <Button label="应用所有" shortcut="A" onClick={onApplyAll || (() => {})} variant="primary" />
        )}
      </box>

      {suggestions.map((suggestion, i) => (
        <box
          key={i}
          style={{
            marginBottom: 1,
            padding: [1, 1],
            backgroundColor: '#222',
            border: 'single',
            borderColor: '#444',
          }}
        >
          <box style={{ marginBottom: 1, flexDirection: 'row', gap: 1 }}>
            <text style={{ fg: 'yellow' }}>💡</text>
            <text style={{ fg: 'yellow' }}>建议 {i + 1}</text>
          </box>

          <box style={{ flexDirection: 'column', gap: 1, marginBottom: 1 }}>
            <box style={{ flexDirection: 'row' }}>
              <text style={{ fg: '#666' }}>表名：</text>
              <text>{suggestion.table}</text>
            </box>
            <box style={{ flexDirection: 'row' }}>
              <text style={{ fg: '#666' }}>列：</text>
              <text style={{ fg: 'cyan' }}>{suggestion.columns.join(', ')}</text>
            </box>
            <box style={{ flexDirection: 'row' }}>
              <text style={{ fg: '#666' }}>理由：</text>
              <text style={{ fg: '#666' }}>{suggestion.reason}</text>
            </box>
            <box style={{ flexDirection: 'row' }}>
              <text style={{ fg: '#666' }}>预期收益：</text>
              <text style={{ fg: 'green' }}>{suggestion.expectedImprovement}</text>
            </box>
          </box>

          <box style={{ padding: 1, backgroundColor: '#1a1a1a', border: 'single', borderColor: '#333' }}>
            <text style={{ fg: 'green' }}>{suggestion.ddl}</text>
          </box>

          {onApply && (
            <box style={{ marginTop: 1 }}>
              <Button label="应用此建议" onClick={() => onApply(suggestion)} variant="primary" />
            </box>
          )}
        </box>
      ))}
    </box>
  );
}
