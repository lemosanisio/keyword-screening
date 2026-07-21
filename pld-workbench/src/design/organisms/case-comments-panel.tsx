import * as React from "react";
import type { CaseComment } from "@/api/types";
import { Button } from "@/components/ui/button";
import { Card, CardContent, CardHeader, CardTitle } from "@/components/ui/card";
import { Textarea } from "@/components/ui/textarea";
import { CommentItem } from "@/design/molecules/comment-item";

export function CaseCommentsPanel({ comments, onSubmit, busy }: { comments: CaseComment[]; onSubmit: (body: string) => void; busy: boolean }) {
  const [body, setBody] = React.useState("");
  return (
    <Card>
      <CardHeader>
        <CardTitle className="text-sm">Comentários</CardTitle>
      </CardHeader>
      <CardContent className="space-y-3">
        <Textarea value={body} onChange={(event) => setBody(event.target.value)} placeholder="Registrar observação imutável do caso" />
        <Button
          size="sm"
          disabled={busy || body.trim().length === 0}
          onClick={() => {
            onSubmit(body);
            setBody("");
          }}
        >
          Publicar comentário
        </Button>
        <div className="space-y-2">
          {comments.map((comment) => <CommentItem key={comment.commentId} comment={comment} />)}
        </div>
      </CardContent>
    </Card>
  );
}
