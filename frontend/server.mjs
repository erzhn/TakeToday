import http from 'node:http';
import {readFile} from 'node:fs/promises';
import {fileURLToPath} from 'node:url';
const root=new URL('../src/main/resources/static/',import.meta.url);
const backend=new URL(process.env.API_URL||'http://localhost:8080');
const types={'.html':'text/html; charset=utf-8','.css':'text/css; charset=utf-8','.js':'text/javascript; charset=utf-8','.woff2':'font/woff2','.svg':'image/svg+xml'};
http.createServer(async(req,res)=>{
 if(['/api/','/catalog','/offers/','/images/','/css/','/js/','/uploads/','/profile','/staff','/admin','/forgot-password'].some(path=>req.url.startsWith(path))){
 const proxy=http.request(new URL(req.url,backend),{method:req.method,headers:{...req.headers,host:backend.host}},up=>{res.writeHead(up.statusCode,up.headers);up.pipe(res);});
 proxy.on('error',()=>{res.writeHead(503,{'Content-Type':'application/json; charset=utf-8'});res.end(JSON.stringify({message:'Сервер недоступен. Попробуйте позже.'}));});req.pipe(proxy);return;
 }
 const pathname=new URL(req.url,'http://localhost').pathname;
 const name=pathname==='/'?'index.html':pathname.slice(1);
 // Страница входа использует общие стили и шрифты сайта: /css/*.css, /fonts/*.woff2, /favicon.svg
 if(!(['index.html','styles.css','app.js','favicon.svg'].includes(name)||/^(css\/[a-z-]+\.css|fonts\/[a-z0-9-]+\.woff2)$/.test(name))){res.writeHead(404);res.end();return;}
 try{const body=await readFile(new URL(name,root));const ext=name.slice(name.lastIndexOf('.'));res.writeHead(200,{'Content-Type':types[ext],'X-Content-Type-Options':'nosniff','Content-Security-Policy':"default-src 'self'; style-src 'self'; script-src 'self'; img-src 'self'; font-src 'self'; connect-src 'self'; base-uri 'self'; frame-ancestors 'none'"});res.end(body);}catch{res.writeHead(404);res.end();}
}).listen(Number(process.env.FRONTEND_PORT||5173),'127.0.0.1',()=>console.log('TakeToday: http://localhost:5173'));
