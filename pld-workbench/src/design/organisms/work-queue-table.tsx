import { Link } from "react-router-dom";
import type { CaseQueueItem } from "@/api/types";
import { Button } from "@/components/ui/button";
import { Card, CardContent, CardHeader, CardTitle } from "@/components/ui/card";
import { PriorityBadge } from "@/design/atoms/priority-badge";
import { StatusBadge } from "@/design/atoms/status-badge";
import { VersionTag } from "@/design/atoms/version-tag";

export function WorkQueueTable({ cases }: { cases: CaseQueueItem[] }) {
  return (
    <Card>
      <CardHeader>
        <CardTitle>Fila única</CardTitle>
      </CardHeader>
      <CardContent>
        <div className="overflow-hidden rounded-lg border">
          <table className="w-full text-left text-sm">
            <thead className="bg-muted text-xs uppercase text-muted-foreground">
              <tr>
                <th className="px-3 py-2">Prioridade</th>
                <th className="px-3 py-2">Caso</th>
                <th className="px-3 py-2">Origem</th>
                <th className="px-3 py-2">Status</th>
                <th className="px-3 py-2">Responsável</th>
                <th className="px-3 py-2 text-right">Fontes</th>
                <th className="px-3 py-2 text-right">Ação</th>
              </tr>
            </thead>
            <tbody>
              {cases.map((item) => (
                <tr key={item.caseId} className="border-t bg-card hover:bg-accent/40">
                  <td className="px-3 py-2"><PriorityBadge priority={item.priority} /></td>
                  <td className="px-3 py-2">
                    <div className="font-mono text-xs">{item.caseId}</div>
                    <div className="mt-1 flex items-center gap-2 text-xs text-muted-foreground">
                      <span>{item.reasonCode}</span>
                      <VersionTag version={item.version} />
                    </div>
                  </td>
                  <td className="px-3 py-2">{item.origin}</td>
                  <td className="px-3 py-2"><StatusBadge status={item.status} /></td>
                  <td className="px-3 py-2">{item.assignedActorId ?? "-"}</td>
                  <td className="px-3 py-2 text-right font-mono">{item.sourceCount}</td>
                  <td className="px-3 py-2 text-right">
                    <Button asChild size="sm" variant="outline">
                      <Link to={`/cases/${item.caseId}`}>Abrir</Link>
                    </Button>
                  </td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>
      </CardContent>
    </Card>
  );
}
