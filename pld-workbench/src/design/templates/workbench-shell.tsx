import { Link, Outlet } from "react-router-dom";
import { ShieldCheck } from "lucide-react";
import { DevActorSwitcher } from "@/features/auth-dev/dev-actor";

export function WorkbenchShell() {
  return (
    <div className="min-h-screen bg-slate-100">
      <header className="border-b bg-background">
        <div className="mx-auto flex max-w-7xl items-center justify-between gap-4 px-4 py-3">
          <Link to="/" className="flex items-center gap-2 font-semibold">
            <ShieldCheck className="h-5 w-5" />
            PLD Workbench
          </Link>
          <nav className="flex items-center gap-4 text-sm">
            <Link className="text-muted-foreground hover:text-foreground" to="/queue">Fila</Link>
            <DevActorSwitcher />
          </nav>
        </div>
      </header>
      <main className="mx-auto max-w-7xl px-4 py-4">
        <div className="mb-4 rounded-lg border border-amber-200 bg-amber-50 px-3 py-2 text-xs text-amber-950">
          Ambiente exploratório: dados, entidades e fluxos são protótipos para aprendizado arquitetural.
        </div>
        <Outlet />
      </main>
    </div>
  );
}
