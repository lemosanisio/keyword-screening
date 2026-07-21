import { Card, CardContent, CardHeader, CardTitle } from "@/components/ui/card";
import type { PartyResponse } from "@/api/types";

export function PartySummary({ party }: { party: PartyResponse }) {
  return (
    <Card>
      <CardHeader>
        <CardTitle className="text-sm">Parte analisada</CardTitle>
      </CardHeader>
      <CardContent className="space-y-2 text-sm">
        <div className="font-semibold">{party.currentSnapshot.officialName}</div>
        <div className="grid grid-cols-2 gap-2 text-xs text-muted-foreground">
          <span>Tipo</span>
          <span className="text-right text-foreground">{party.partyType}</span>
          <span>Party ID</span>
          <span className="text-right font-mono text-foreground">{party.partyId}</span>
          <span>Snapshot</span>
          <span className="text-right font-mono text-foreground">v{party.currentSnapshot.snapshotVersion}</span>
          <span>Fonte</span>
          <span className="text-right text-foreground">{party.currentSnapshot.sourceSystem}</span>
        </div>
      </CardContent>
    </Card>
  );
}
