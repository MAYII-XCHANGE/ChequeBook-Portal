import { BrowserRouter } from 'react-router-dom';
import { Toaster } from 'react-hot-toast';
import { AuthProvider } from './app/providers/AuthProvider';
import { AppRouter } from './app/routes/AppRouter';
import { NotificationProvider } from './hooks/useNotificationSummary';

const routerBasename = import.meta.env.BASE_URL.replace(/\/$/, '') || '/';

export default function App() {
  return (
    <BrowserRouter basename={routerBasename}>
      <AuthProvider>
        <NotificationProvider>
          <AppRouter />
          <Toaster
          position="top-right"
          gutter={12}
          containerStyle={{ top: 20, right: 20 }}
          toastOptions={{
            duration: 4500,
            style: {
              background: '#ffffff',
              color: '#172033',
              border: '1px solid #e2e8f0',
              borderRadius: '10px',
              boxShadow: '0 18px 45px -18px rgba(15, 23, 42, 0.35)',
              maxWidth: 'min(420px, calc(100vw - 32px))',
              padding: '14px 16px',
              fontSize: '14px',
              fontWeight: 600,
            },
            success: {
              iconTheme: { primary: '#059669', secondary: '#ffffff' },
            },
            error: {
              duration: 6000,
              iconTheme: { primary: '#dc2626', secondary: '#ffffff' },
            },
          }}
          />
        </NotificationProvider>
      </AuthProvider>
    </BrowserRouter>
  );
}
