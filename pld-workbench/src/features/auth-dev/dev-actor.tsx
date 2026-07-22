import * as React from "react";
import type { DevActor } from "@/api/types";
import { Select } from "@/components/ui/select";

const actors: DevActor[] = [
  { id: "analyst-1", role: "ANALYST", label: "Analista 1" },
  { id: "approver-1", role: "APPROVER", label: "Aprovador 1" },
  { id: "rule-admin-1", role: "RULE_ADMIN", label: "Admin Regras 1" },
  { id: "auditor-1", role: "AUDITOR", label: "Auditor 1" }
];

type DevActorContextValue = {
  actor: DevActor;
  setActorId: (actorId: string) => void;
};

const DevActorContext = React.createContext<DevActorContextValue | null>(null);

export function DevActorProvider({ children }: { children: React.ReactNode }) {
  const [actorId, setActorId] = React.useState(actors[0].id);
  const actor = actors.find((candidate) => candidate.id === actorId) ?? actors[0];

  return <DevActorContext.Provider value={{ actor, setActorId }}>{children}</DevActorContext.Provider>;
}

export function useDevActor() {
  const context = React.useContext(DevActorContext);
  if (!context) {
    throw new Error("useDevActor must be used inside DevActorProvider");
  }
  return context;
}

export function DevActorSwitcher() {
  const { actor, setActorId } = useDevActor();
  return (
    <div className="flex items-center gap-2 text-xs">
      <span className="text-muted-foreground">Ator dev</span>
      <Select aria-label="Ator dev" className="h-8 w-44" value={actor.id} onChange={(event) => setActorId(event.target.value)}>
        {actors.map((candidate) => (
          <option key={candidate.id} value={candidate.id}>
            {candidate.label} / {candidate.role}
          </option>
        ))}
      </Select>
    </div>
  );
}
