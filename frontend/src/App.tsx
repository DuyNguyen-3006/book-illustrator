import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import { BrowserRouter } from "react-router-dom";
import { AppRoutes } from "@/router";
import { ToastProvider } from "@/shared/components/ToastProvider";

const queryClient = new QueryClient({
  defaultOptions: {
    queries: {
      // Pipeline steps are paid API calls; a failed one is surfaced to the user and
      // retried by them, never automatically (CLAUDE.md §2.2).
      retry: false,
      refetchOnWindowFocus: true,
    },
    mutations: { retry: false },
  },
});

export default function App() {
  return (
    <QueryClientProvider client={queryClient}>
      <ToastProvider>
        <BrowserRouter>
          <AppRoutes />
        </BrowserRouter>
      </ToastProvider>
    </QueryClientProvider>
  );
}
