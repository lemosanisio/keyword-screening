import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import * as React from "react";
import { DevActorProvider } from "@/features/auth-dev/dev-actor";

const queryClient = new QueryClient({
  defaultOptions: {
    queries: {
      refetchOnWindowFocus: false,
      retry: 1
    }
  }
});

export function Providers({ children }: { children: React.ReactNode }) {
  return (
    <QueryClientProvider client={queryClient}>
      <DevActorProvider>{children}</DevActorProvider>
    </QueryClientProvider>
  );
}
