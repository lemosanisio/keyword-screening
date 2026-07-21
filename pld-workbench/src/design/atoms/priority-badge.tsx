import { Badge } from "@/components/ui/badge";

export function PriorityBadge({ priority }: { priority: string }) {
  const variant = priority === "CRITICAL" || priority === "HIGH" ? "destructive" : priority === "MEDIUM" ? "warning" : "outline";
  return <Badge variant={variant}>{priority}</Badge>;
}
