import { Link, Outlet, useLocation } from "react-router-dom";
import { ShieldCheck, Menu, X } from "lucide-react";
import { DevActorSwitcher } from "@/features/auth-dev/dev-actor";
import { GlobalSearch } from "@/design/molecules/global-search";
import * as React from "react";

const navItems = [
  { path: "/queue", label: "Fila de Casos", description: "Casos pendentes de análise" },
  { path: "/admin/rules", label: "Administração de Regras", description: "Catálogo, configurações, dry-run e ativação" },
];

const contextualPages = [
  { label: "Workspace do Caso", description: "Análise completa: evidências, decisões, dossiê, COAF", context: "Abrir via fila ou pesquisa (/cases/:id)" },
  { label: "Dossiê & Comunicação COAF", description: "Gerar dossiê, submeter comunicação regulatória", context: "Painel direito do workspace" },
  { label: "Matriz de Evidências", description: "Adapters (bureau, sanções, processos, mídia) com retry", context: "Seção central do workspace" },
  { label: "Detalhes de Evidência", description: "Sanções, processos judiciais, mídia negativa", context: "Seção central do workspace" },
  { label: "Decisões (Suspicion + Account)", description: "Dual approval, maker-checker", context: "Painel direito do workspace" },
  { label: "Timeline Regulatória", description: "Trilha de auditoria com ator/instante", context: "Seção central do workspace" },
  { label: "Relações PF/PJ", description: "Sócios, beneficiários finais, representantes", context: "Sidebar esquerda do workspace" },
  { label: "Sinais Transacionais (M7)", description: "Avaliação reproduzível: purpose, estado, fatos, explicação", context: "Sidebar esquerda do workspace" },
  { label: "Visão 360 da Parte", description: "Risco, segmentos, snapshot, validade", context: "Sidebar esquerda do workspace" },
];

export function WorkbenchShell() {
  const location = useLocation();
  const [menuOpen, setMenuOpen] = React.useState(false);

  return (
    <div className="min-h-screen bg-slate-100">
      <header className="border-b bg-background">
        <div className="mx-auto flex max-w-7xl items-center justify-between gap-4 px-4 py-3">
          <div className="flex items-center gap-3">
            <Link to="/" className="flex items-center gap-2 font-semibold">
              <ShieldCheck className="h-5 w-5" />
              PLD Workbench
            </Link>
            <button
              onClick={() => setMenuOpen(!menuOpen)}
              className="rounded p-1 text-muted-foreground hover:bg-muted hover:text-foreground"
              aria-label="Menu de navegação"
            >
              {menuOpen ? <X className="h-4 w-4" /> : <Menu className="h-4 w-4" />}
            </button>
          </div>
          <nav className="flex items-center gap-4 text-sm">
            {navItems.map((item) => (
              <Link
                key={item.path}
                className={`hidden sm:inline hover:text-foreground ${location.pathname === item.path || location.pathname.startsWith(item.path + "/") ? "text-foreground font-medium" : "text-muted-foreground"}`}
                to={item.path}
              >
                {item.label}
              </Link>
            ))}
            <GlobalSearch />
            <DevActorSwitcher />
          </nav>
        </div>
        {menuOpen && (
          <div className="border-t bg-background">
            <div className="mx-auto max-w-7xl px-4 py-4">
              <div className="mb-3">
                <h3 className="text-xs font-medium uppercase text-muted-foreground">Páginas navegáveis</h3>
                <div className="mt-2 grid gap-2 sm:grid-cols-2">
                  {navItems.map((item) => (
                    <Link
                      key={item.path}
                      to={item.path}
                      onClick={() => setMenuOpen(false)}
                      className={`rounded-lg border p-3 transition-colors hover:bg-muted ${location.pathname === item.path ? "border-primary bg-primary/5" : ""}`}
                    >
                      <div className="font-medium text-sm">{item.label}</div>
                      <div className="text-xs text-muted-foreground mt-0.5">{item.description}</div>
                      <div className="text-[10px] font-mono text-muted-foreground mt-1">{item.path}</div>
                    </Link>
                  ))}
                </div>
              </div>

              <div className="mb-3">
                <h3 className="text-xs font-medium uppercase text-muted-foreground">Seções contextuais (dentro do workspace)</h3>
                <div className="mt-2 grid gap-1.5 sm:grid-cols-2 lg:grid-cols-3">
                  {contextualPages.map((page) => (
                    <div key={page.label} className="rounded-lg border border-dashed p-2.5">
                      <div className="font-medium text-xs">{page.label}</div>
                      <div className="text-[10px] text-muted-foreground mt-0.5">{page.description}</div>
                      <div className="text-[10px] italic text-muted-foreground/70 mt-0.5">{page.context}</div>
                    </div>
                  ))}
                </div>
              </div>

              <div className="border-t pt-3">
                <h3 className="text-xs font-medium uppercase text-muted-foreground mb-2">Endpoints de desenvolvimento</h3>
                <div className="grid gap-1.5 sm:grid-cols-2 text-xs">
                  <div className="rounded bg-muted px-2.5 py-1.5">
                    <span className="font-mono text-[10px]">POST /v1/dev/onboarding/seed</span>
                    <div className="text-[10px] text-muted-foreground">Cria 3 parties com risco variado</div>
                  </div>
                  <div className="rounded bg-muted px-2.5 py-1.5">
                    <span className="font-mono text-[10px]">POST /v1/dev/onboarding/events</span>
                    <div className="text-[10px] text-muted-foreground">Publica evento de sistema mestre</div>
                  </div>
                  <div className="rounded bg-muted px-2.5 py-1.5">
                    <span className="font-mono text-[10px]">POST /v1/dev/scenarios/transaction-case</span>
                    <div className="text-[10px] text-muted-foreground">Cria caso com cenário de evidência</div>
                  </div>
                  <div className="rounded bg-muted px-2.5 py-1.5">
                    <span className="font-mono text-[10px]">POST :8080/v1/rules/keyword-screening/evaluate</span>
                    <div className="text-[10px] text-muted-foreground">Avalia transação no motor</div>
                  </div>
                </div>
              </div>
            </div>
          </div>
        )}
      </header>
      <main className="mx-auto max-w-7xl px-4 py-4">
        <div className="mb-4 rounded-lg border border-amber-200 bg-amber-50 px-3 py-2 text-xs text-amber-950">
          <span className="mr-2 rounded bg-amber-200 px-1.5 py-0.5 font-medium">DEV</span>
          Ambiente exploratório: dados, entidades e fluxos são protótipos para aprendizado arquitetural.
        </div>
        <Outlet />
      </main>
    </div>
  );
}
