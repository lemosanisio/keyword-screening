import { Badge } from "@/components/ui/badge";
import type { SourceExecution } from "@/api/types";

export function SourceStatusBadge({ status }: { status: SourceExecution["status"] }) {
  const variant = status === "UNAVAILABLE" || status === "ERROR" ? "destructive" : status === "PARTIAL" || status === "CONFLICT" || status === "EXPIRED" ? "warning" : "success";
  return <Badge variant={variant}>{status}</Badge>;
}
