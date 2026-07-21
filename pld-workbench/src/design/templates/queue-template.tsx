import type { CaseQueueItem } from "@/api/types";
import { WorkQueueTable } from "@/design/organisms/work-queue-table";

export function QueueTemplate({ cases }: { cases: CaseQueueItem[] }) {
  const pendingApproval = cases.filter((item) => item.status === "PENDING_APPROVAL").length;
  return (
    <div className="space-y-4">
      <section className="grid gap-3 md:grid-cols-4">
        <Metric label="Abertos" value={cases.filter((item) => item.status === "OPEN").length} />
        <Metric label="Em análise" value={cases.filter((item) => item.status === "IN_ANALYSIS").length} />
        <Metric label="Aguardando aprovação" value={pendingApproval} />
        <Metric label="Total" value={cases.length} />
      </section>
      <WorkQueueTable cases={cases} />
    </div>
  );
}

function Metric({ label, value }: { label: string; value: number }) {
  return (
    <div className="rounded-xl border bg-card p-4 shadow-sm">
      <div className="text-xs uppercase text-muted-foreground">{label}</div>
      <div className="mt-1 font-mono text-2xl font-semibold">{value}</div>
    </div>
  );
}
