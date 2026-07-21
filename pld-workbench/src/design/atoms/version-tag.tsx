export function VersionTag({ version }: { version: number }) {
  return <span className="rounded border bg-muted px-1.5 py-0.5 font-mono text-[11px] text-muted-foreground">v{version}</span>;
}
