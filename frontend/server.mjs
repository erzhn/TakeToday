import http from 'node:http';
import {readFile} from 'node:fs/promises';
import {fileURLToPath} from 'node:url';
const root=new URL('./',import.meta.url);
const backend=new URL(process.env.API_URL||'http://localhost:8080');
const types={'.html':'text/html; charset=utf-8','.css':'text/css; charset=utf-8','.js':'text/javascript; charset=utf-8'};
http.createServer(async(req,res)=>{
 if(req.url.startsWith('/api/')){
 const proxy=http.request(new URL(req.url,backend),{method:req.method,headers:{...req.headers,host:backend.host}},up=>{res.writeHead(up.statusCode,up.headers);up.pipe(res);});
 proxy.on('error',()=>{res.writeHead(503,{'Content-Type':'application/json; charset=utf-8'});res.end(JSON.stringify({message:'Сервер недоступен. Попробуйте позже.'}));});req.pipe(proxy);return;
 }
 const pathname=new URL(req.url,'http://localhost').pathname;
 const name=pathname==='/'?'index.html':pathname.slice(1);
 if(!['index.html','styles.css','app.js'].includes(name)){res.writeHead(404);res.end();return;}
 try{const body=await readFile(new URL(name,root));const ext=name.slice(name.lastIndexOf('.'));res.writeHead(200,{'Content-Type':types[ext],'X-Content-Type-Options':'nosniff','Content-Security-Policy':"default-src 'self'; style-src 'self'; script-src 'self'; img-src 'self'; connect-src 'self'; base-uri 'self'; frame-ancestors 'none'"});res.end(body);}catch{res.writeHead(404);res.end();}
}).listen(Number(process.env.FRONTEND_PORT||5173),'127.0.0.1',()=>console.log('TakeToday: http://localhost:5173'));
