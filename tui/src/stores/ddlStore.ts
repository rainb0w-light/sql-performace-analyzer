import { create } from 'zustand';

interface DdlOperation {
  type: 'CREATE' | 'ALTER' | 'DROP' | 'TRUNCATE';
  table: string;
  description: string;
  impact: string;
  ddl: string;
  rollback: string;
}

interface DdlState {
  pendingOperation: DdlOperation | null;
  isVisible: boolean;
  setPendingOperation: (operation: DdlOperation | null) => void;
  setVisible: (visible: boolean) => void;
}

export const useDdlStore = create<DdlState>((set) => ({
  pendingOperation: null,
  isVisible: false,
  setPendingOperation: (operation) => set({ pendingOperation: operation }),
  setVisible: (visible) => set({ isVisible: visible }),
}));
