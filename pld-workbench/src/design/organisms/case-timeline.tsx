import type { TimelineEntry } from "@/api/types";
import { Card, CardContent, CardHeader, CardTitle } from "@/components/ui/card";

export function CaseTimeline({ entries }: { entries: TimelineEntry[] }) {
  return (
    <Card>
      <CardHeader>
        <CardTitle className="text-sm">Timeline regulatória</CardTitle>
      </CardHeader>
      <CardContent className="space-y-3">
        {entries.map((entry) => (
          <div key={entry.timelineEntryId} className="relative border-l pl-4 text-sm">
            <div className="absolute -left-1.5 top-1.5 h-3 w-3 rounded-full border bg-background" />
            <div className="font-medium">{entry.entryType}</div>
            <div className="text-xs text-muted-foreground">{entry.summaryCode}</div>
            <div className="mt-1 flex flex-wrap gap-2 text-[11px] text-muted-foreground">
              <span>{entry.actorId}</span>
              <span>{new Date(entry.recordedAt).toLocaleString("pt-BR")}</span>
              {entry.objectType && <span>{entry.objectType}:{entry.objectId}</span>}
            </div>
          </div>
        ))}
      </CardContent>
    </Card>
  );
}
