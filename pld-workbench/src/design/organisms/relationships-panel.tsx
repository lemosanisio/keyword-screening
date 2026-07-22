import { useQuery } from "@tanstack/react-query";
import { requestJson } from "@/api/http";
import { Card, CardContent, CardHeader, CardTitle } from "@/components/ui/card";

type Relationship = {
  relationshipId: string;
  fromPartyId: string;
  toPartyId: string;
  type: string;
  participationPercentage?: number | null;
  startDate?: string | null;
  endDate?: string | null;
  sourceSystem: string;
};

const typeColors: Record<string, string> = {
  ULTIMATE_BENEFICIAL_OWNER: "bg-red-100 text-red-800",
  SHAREHOLDER: "bg-blue-100 text-blue-800",
  DIRECTOR: "bg-purple-100 text-purple-800",
  LEGAL_REPRESENTATIVE: "bg-amber-100 text-amber-800",
};

export function RelationshipsPanel({ partyId }: { partyId: string }) {
  const query = useQuery({
    queryKey: ["relationships", partyId],
    queryFn: () => requestJson<Relationship[]>(`/v1/parties/${partyId}/relationships`),
  });

  if (query.isLoading) return null;
  const relationships = query.data ?? [];
  if (relationships.length === 0) return null;

  return (
    <Card>
      <CardHeader>
        <CardTitle className="text-sm">Relações</CardTitle>
      </CardHeader>
      <CardContent className="space-y-2">
        {relationships.map((rel) => (
          <div key={rel.relationshipId} className="rounded border p-2 text-xs">
            <div className="flex items-center justify-between">
              <span className={`rounded px-1.5 py-0.5 text-[10px] font-medium ${typeColors[rel.type] ?? "bg-muted"}`}>
                {rel.type}
              </span>
              {rel.participationPercentage != null && (
                <span className="font-mono">{rel.participationPercentage}%</span>
              )}
            </div>
            <div className="mt-1 text-muted-foreground">
              {rel.fromPartyId === partyId ? (
                <span>→ <span className="font-mono text-foreground">{rel.toPartyId}</span></span>
              ) : (
                <span>← <span className="font-mono text-foreground">{rel.fromPartyId}</span></span>
              )}
            </div>
            {rel.startDate && (
              <div className="mt-0.5 text-[10px] text-muted-foreground">
                {rel.startDate}{rel.endDate ? ` — ${rel.endDate}` : " — vigente"}
              </div>
            )}
          </div>
        ))}
      </CardContent>
    </Card>
  );
}
