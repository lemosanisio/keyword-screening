const port = Number(process.env.PORT ?? 5173);

const server = Bun.serve({
  port,
  async fetch(request) {
    const url = new URL(request.url);
    const pathname = url.pathname === "/" ? "/index.html" : url.pathname;
    const file = Bun.file(`dist${pathname}`);

    if (await file.exists()) {
      return new Response(file);
    }

    return new Response(Bun.file("dist/index.html"));
  }
});

console.log(`PLD Workbench disponível em http://localhost:${server.port}`);
