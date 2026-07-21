import type { CaseComment } from "@/api/types";

export function CommentItem({ comment }: { comment: CaseComment }) {
  return (
    <div className="rounded-lg border bg-background p-3 text-sm">
      <div className="mb-1 flex items-center justify-between gap-2 text-xs text-muted-foreground">
        <span>{comment.createdByActorId} / {comment.createdByActorRole}</span>
        <time>{new Date(comment.createdAt).toLocaleString("pt-BR")}</time>
      </div>
      <p>{comment.body}</p>
    </div>
  );
}
