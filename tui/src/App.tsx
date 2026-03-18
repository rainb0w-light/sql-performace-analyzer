import { Box } from './components/ui/Box';
import { Header } from './components/layout/Header';
import { Sidebar } from './components/layout/Sidebar';
import { StatusBar } from './components/layout/StatusBar';
import { SessionManager } from './components/session/SessionManager';
import { SkillExecutor } from './components/skill/SkillExecutor';
import { DdlConfirmationDialog } from './components/ddl/DdlConfirmationDialog';
import { useSession } from './hooks/useSession';
import { useEffect } from 'react';

export function App() {
  const { session, connectToSession } = useSession();

  useEffect(() => {
    if (session?.id) {
      connectToSession(session.id);
    }
  }, [session?.id]);

  return (
    <Box style={{ height: '100%', flexDirection: 'column' }}>
      <Header />
      <Box style={{ flex: 1, flexDirection: 'row' }}>
        <Sidebar />
        <Box style={{ flex: 1, padding: 1 }}>
          <SessionManager />
        </Box>
      </Box>
      <StatusBar />
      <DdlConfirmationDialog />
    </Box>
  );
}
