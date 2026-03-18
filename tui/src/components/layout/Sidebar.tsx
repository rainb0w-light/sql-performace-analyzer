import { useState } from 'react';

const MENU_ITEMS = [
  { key: 'session', label: 'Session', shortcut: 'F1' },
  { key: 'sql_analysis', label: 'SQL Analysis', shortcut: 'F2' },
  { key: 'table_analysis', label: 'Table Analysis', shortcut: 'F3' },
  { key: 'mybatis', label: 'MyBatis', shortcut: 'F4' },
  { key: 'ddl', label: 'DDL', shortcut: 'F5' },
  { key: 'knowledge', label: 'Knowledge', shortcut: 'F6' },
  { key: 'datasource', label: 'Datasource', shortcut: 'F7' },
  { key: 'settings', label: 'Settings', shortcut: 'F8' },
];

interface SidebarProps {
  activeMenu?: string;
  onMenuChange?: (menu: string) => void;
}

export function Sidebar({ activeMenu = 'session', onMenuChange }: SidebarProps) {
  const [selectedIndex, setSelectedIndex] = useState(0);

  return (
    <box
      style={{
        width: 25,
        borderRight: 'single',
        borderColor: '#333',
        backgroundColor: '#151520',
      }}
    >
      {MENU_ITEMS.map((item, index) => (
        <box
          key={item.key}
          style={{
            padding: [0, 2],
            backgroundColor: activeMenu === item.key ? '#2a2a4a' : 'transparent',
          }}
        >
          <text style={{ fg: activeMenu === item.key ? 'cyan' : '#666' }}>
            {item.label}
          </text>
          <text style={{ fg: '#444' }}>{` ${item.shortcut}`}</text>
        </box>
      ))}
    </box>
  );
}
