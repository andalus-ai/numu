import { createServer } from "node:http";
import { readFile } from "node:fs/promises";
import { extname, resolve, sep } from "node:path";
import { fileURLToPath } from "node:url";
const root = resolve(fileURLToPath(new URL("../", import.meta.url)));
const port = Number(process.env.PORT || 4173);
const mime = { ".html":"text/html; charset=utf-8", ".css":"text/css; charset=utf-8", ".js":"text/javascript; charset=utf-8", ".json":"application/json; charset=utf-8" };
createServer(async (request, response) => {
  if (request.method !== "GET" && request.method !== "HEAD") { response.writeHead(405,{Allow:"GET, HEAD"}).end(); return; }
  let pathname;
  try { pathname = decodeURIComponent(new URL(request.url || "/", "http://localhost").pathname); } catch { response.writeHead(400).end(); return; }
  const requested = pathname === "/" ? "index.html" : pathname.replace(/^\/+/, "");
  const file = resolve(root, requested);
  if (file !== root && !file.startsWith(root + sep)) { response.writeHead(403).end(); return; }
  try {
    const data = await readFile(file);
    response.writeHead(200, {"Content-Type":mime[extname(file)] || "application/octet-stream","X-Content-Type-Options":"nosniff","Referrer-Policy":"same-origin","Cache-Control":"no-store"});
    response.end(request.method === "HEAD" ? undefined : data);
  } catch { response.writeHead(404,{"Content-Type":"text/plain; charset=utf-8"}).end("Not found"); }
}).listen(port,"127.0.0.1",()=>process.stdout.write(`Numou Workspace UI preview: http://127.0.0.1:${port}\n`));
