const PALETTE = ["#639c2d", "#4f7d24", "#92ba6c", "#63a33e", "#697267", "#b17a32", "#bc4b39", "#415035", "#6f7d63", "#b1ce96"];
const DARK_PALETTE = ["#8fd04d", "#75bd35", "#a9db75", "#8fcf59", "#98a595", "#d4a75c", "#e07865", "#d8e6d0", "#aebea4", "#c6e8a6"];
const ID_FIELDS = ["id", "object_id", "code", "key"];
const NAME_FIELDS = ["name", "label", "title", "displayName"];
const TYPE_FIELDS = ["type", "object_type", "category", "kind"];
const STATUS_FIELDS = ["status", "state"];
const SOURCE_FIELDS = ["source", "from", "src"];
const TARGET_FIELDS = ["target", "to", "dst"];
const RELATION_FIELDS = ["relation", "relationName", "edge_name", "type", "label"];

function firstField(obj, configured, candidates) {
  if (configured && obj && Object.prototype.hasOwnProperty.call(obj, configured)) return configured;
  for (const key of candidates) if (obj && Object.prototype.hasOwnProperty.call(obj, key)) return key;
  return undefined;
}
function esc(value) {
  return String(value ?? "").replace(/&/g, "&amp;").replace(/</g, "&lt;").replace(/>/g, "&gt;").replace(/"/g, "&quot;").replace(/'/g, "&#39;");
}
function jsonForScript(value) { return JSON.stringify(value).replace(/</g, "\\u003c").replace(/\u2028/g, "\\u2028").replace(/\u2029/g, "\\u2029"); }
function errorResult(message, meta = {}) {
  return { html: `<!doctype html><html><body style="font:14px sans-serif;padding:20px;color:#bc4b39">${esc(message)}</body></html>`, meta: { skillId: "network-graph", chartType: "network-graph", error: true, warnings: [], ...meta } };
}
function normalizeInput(input) {
  const data = input?.data;
  const rawNodes = Array.isArray(data) ? [] : (Array.isArray(data?.nodes) ? data.nodes : []);
  const rawLinks = Array.isArray(data) ? data : (Array.isArray(data?.links) ? data.links : Array.isArray(data?.edges) ? data.edges : Array.isArray(data?.relations) ? data.relations : []);
  const warnings = [];
  const nodeMap = new Map();
  for (const [i, raw] of rawNodes.entries()) {
    if (!raw || typeof raw !== "object") continue;
    const idKey = firstField(raw, input?.idField, ID_FIELDS);
    const nameKey = firstField(raw, input?.nameField, NAME_FIELDS);
    if (!idKey || raw[idKey] == null || String(raw[idKey]).trim() === "") { warnings.push(`Node at index ${i} skipped: missing ID`); continue; }
    const id = String(raw[idKey]);
    if (nodeMap.has(id)) { warnings.push(`Duplicate node ID ignored: ${id}`); continue; }
    const typeKey = firstField(raw, input?.typeField, TYPE_FIELDS);
    const statusKey = firstField(raw, input?.statusField, STATUS_FIELDS);
    nodeMap.set(id, { id, name: String(nameKey && raw[nameKey] != null ? raw[nameKey] : id), type: typeKey ? String(raw[typeKey] ?? "Other") : "Other", status: statusKey ? String(raw[statusKey] ?? "") : "", isAnchor: Boolean(raw.isAnchor), x: Number.isFinite(Number(raw.x)) ? Number(raw.x) : undefined, y: Number.isFinite(Number(raw.y)) ? Number(raw.y) : undefined, symbolSize: Number.isFinite(Number(raw.symbolSize)) ? Math.max(8, Math.min(48, Number(raw.symbolSize))) : undefined, properties: raw });
  }
  const links = [];
  const seenLinks = new Set();
  for (const [i, raw] of rawLinks.entries()) {
    if (!raw || typeof raw !== "object") continue;
    const sourceKey = firstField(raw, input?.sourceField, SOURCE_FIELDS);
    const targetKey = firstField(raw, input?.targetField, TARGET_FIELDS);
    if (!sourceKey || !targetKey || raw[sourceKey] == null || raw[targetKey] == null) { warnings.push(`Link at index ${i} skipped: missing source or target`); continue; }
    const source = String(raw[sourceKey]); const target = String(raw[targetKey]);
    if (!nodeMap.has(source)) nodeMap.set(source, { id: source, name: String(raw.sourceName ?? raw.fromName ?? source), type: "Other", status: "", isAnchor: false, properties: {} });
    if (!nodeMap.has(target)) nodeMap.set(target, { id: target, name: String(raw.targetName ?? raw.toName ?? target), type: "Other", status: "", isAnchor: false, properties: {} });
    const relKey = firstField(raw, input?.relationField, RELATION_FIELDS);
    const relation = relKey && raw[relKey] != null ? String(raw[relKey]) : "";
    const key = `${source}\u0000${target}\u0000${relation}`;
    if (seenLinks.has(key)) { warnings.push(`Duplicate link ignored: ${source} -> ${target}`); continue; }
    seenLinks.add(key);
    const weight = Number(raw.weight ?? raw.value);
    links.push({ source, target, relation, weight: Number.isFinite(weight) && weight > 0 ? weight : 1, properties: raw });
  }
  return { nodeMap, links, warnings };
}
function generateHtml({ title, nodes, links, layout, directed, showRelationLabels, width, height, dark }) {
  const palette = dark ? DARK_PALETTE : PALETTE;
  const safeTitle = esc(title);
  const serializedNodes = jsonForScript(nodes);
  const serializedLinks = jsonForScript(links);
  const serializedConfig = jsonForScript({ layout, directed, showRelationLabels, dark });
  return `<!doctype html><html lang="en"><head><meta charset="utf-8"><meta name="viewport" content="width=device-width,initial-scale=1"><style>
  *{box-sizing:border-box}html,body{margin:0;width:100%;height:100%;font-family:Inter,-apple-system,BlinkMacSystemFont,"Segoe UI",sans-serif;background:${dark ? "#121b10" : "#ffffff"};color:${dark ? "#f2f8ee" : "#25321f"}}.shell{height:100%;display:flex;flex-direction:column}.title{font-size:15px;font-weight:650;padding:12px 16px;border-bottom:1px solid ${dark ? "#3b5632" : "#e1ead9"}}#chart{flex:1;width:100%;outline:none}.hint{font-size:11px;color:${dark ? "#aebea4" : "#6f7d63"};padding:5px 12px}
  </style><script src="/api/echart/echarts.min.js"></script></head><body><div class="shell"><div class="title">${safeTitle}</div><div id="chart" role="img" aria-label="Interactive network graph"></div><div class="hint">Drag nodes to explore · Scroll to zoom · Click a node to highlight its neighbors</div></div><script>
  const nodes=${serializedNodes}; const links=${serializedLinks}; const cfg=${serializedConfig};
  const root=document.getElementById('chart');
  if(!window.echarts){root.textContent='ECharts resource failed to load.';throw new Error('ECharts resource failed to load');}
  const chart=echarts.init(root, null, {renderer:'canvas'});
  const types=[...new Set(nodes.map(n=>n.type||'Other'))];
  const categories=types.map((name,i)=>({name,itemStyle:{color:${JSON.stringify(palette)}[i%${palette.length}]}}));
  const nodeData=nodes.map(n=>({...n,category:Math.max(0,types.indexOf(n.type||'Other')),symbolSize:n.symbolSize||Math.min(34,Math.max(13,10+Math.sqrt(n.degree||0)*4)),itemStyle:{borderColor:n.isAnchor?(cfg.dark?'#8fd04d':'#639c2d'):(n.statusColor||'transparent'),borderWidth:n.isAnchor?4:(n.statusColor?3:1),shadowBlur:n.isAnchor?12:0,shadowColor:cfg.dark?'rgba(143,208,77,0.26)':'rgba(99,156,45,0.24)'},label:{show:n.isAnchor||nodes.length<=35,fontSize:n.isAnchor?13:11,fontWeight:n.isAnchor?700:400}}));
  const series={type:'graph',data:nodeData,links:links.map(l=>({...l,lineStyle:{width:Math.min(5,1+Math.log2(Math.max(1,l.weight))),opacity:.65},label:{show:cfg.showRelationLabels,formatter:l.relation||'',fontSize:9}})),categories,layout:cfg.layout,roam:true,draggable:true,focusNodeAdjacency:true,selectedMode:'single',scaleLimit:{min:.15,max:8},edgeSymbol:cfg.directed?['none','arrow']:['none','none'],edgeSymbolSize:8,edgeLabel:{show:cfg.showRelationLabels,formatter:p=>p.data.relation||'',fontSize:9,color:${JSON.stringify(dark ? '#aebea4' : '#6f7d63')}},label:{position:'right',color:${JSON.stringify(dark ? '#f2f8ee' : '#3b5632')},overflow:'truncate',width:120},lineStyle:{color:'source',curveness:.12},emphasis:{focus:'adjacency',lineStyle:{width:3,opacity:1}},blur:{itemStyle:{opacity:.2},lineStyle:{opacity:.08}}};
  if(cfg.layout==='none' && nodeData.some(n=>!Number.isFinite(n.x)||!Number.isFinite(n.y))) series.layout='force';
  if(series.layout==='force') series.force={repulsion:Math.max(120,Math.min(700, nodes.length*8)),edgeLength:[55,150],gravity:.08,friction:.6};
  chart.setOption({animation:true,backgroundColor:'transparent',tooltip:{trigger:'item',confine:true,backgroundColor:cfg.dark?'#21331c':'#ffffff',borderColor:cfg.dark?'#3b5632':'#e1ead9',textStyle:{color:cfg.dark?'#f2f8ee':'#25321f'},formatter:p=>{const d=p.data||{};if(p.dataType==='edge'){return '<b>'+esc(d.source)+'</b> → <b>'+esc(d.target)+'</b><br>'+esc(d.relation||'relationship');}let out='<b>'+esc(d.name||d.id)+'</b><br>ID: '+esc(d.id)+'<br>Type: '+esc(d.type||'Other');if(d.status)out+='<br>Status: '+esc(d.status);for(const [k,v] of Object.entries(d.properties||{})){if(['id','name','label','type','status','x','y'].includes(k)||v==null||typeof v==='object')continue;out+='<br>'+esc(k)+': '+esc(v)}return out}},legend:[{type:'scroll',data:types,top:8,left:8,textStyle:{color:${JSON.stringify(dark ? '#d8e6d0' : '#415035')}}}],series:[series]});
  function esc(s){return String(s??'').replace(/[&<>"']/g,c=>({'&':'&amp;','<':'&lt;','>':'&gt;','"':'&quot;',"'":'&#39;'}[c]));}
  chart.on('click',p=>{if(p.dataType!=='node')return;const id=p.data.id;const neighbors=new Set([id]);links.forEach(l=>{if(l.source===id)neighbors.add(l.target);if(l.target===id)neighbors.add(l.source)});chart.dispatchAction({type:'downplay',seriesIndex:0});nodeData.forEach((n,i)=>chart.dispatchAction({type:neighbors.has(n.id)?'highlight':'downplay',seriesIndex:0,dataIndex:i}));});
  new ResizeObserver(()=>chart.resize()).observe(root);
  </script></body></html>`;
}
export async function execute(input) {
  const chartType = input?.chartType || "network-graph";
  const title = String(input?.title || "Relationship Network").trim();
  if (chartType !== "network-graph") return errorResult("network-graph only supports chartType: network-graph", { chartType });
  if (!input?.data) return errorResult("Missing required parameter: data", { missingParams: ["data"] });
  const { nodeMap, links, warnings } = normalizeInput(input);
  if (!nodeMap.size) return errorResult("No valid nodes or relationship endpoints were found", { warnings });
  const maxNodes = Math.max(10, Math.min(300, Number.isFinite(Number(input.maxNodes)) ? Math.floor(Number(input.maxNodes)) : 150));
  const anchors = new Set([...(Array.isArray(input.anchorIds) ? input.anchorIds : []), ...(input.anchorId != null ? [input.anchorId] : [])].map(String));
  for (const n of nodeMap.values()) if (n.isAnchor) anchors.add(n.id);
  const degree = new Map([...nodeMap.keys()].map(id => [id, 0]));
  for (const l of links) { degree.set(l.source, (degree.get(l.source)||0)+1); degree.set(l.target, (degree.get(l.target)||0)+1); }
  const allNodes = [...nodeMap.values()].map(n => ({ ...n, isAnchor: n.isAnchor || anchors.has(n.id), degree: degree.get(n.id)||0 }));
  const anchorReach = new Set(anchors);
  for (const l of links) if (anchorReach.has(l.source) || anchorReach.has(l.target)) { anchorReach.add(l.source); anchorReach.add(l.target); }
  allNodes.sort((a,b) => Number(b.isAnchor)-Number(a.isAnchor) || Number(anchorReach.has(b.id))-Number(anchorReach.has(a.id)) || b.degree-a.degree || a.name.localeCompare(b.name));
  const truncated = allNodes.length > maxNodes;
  const selected = allNodes.slice(0, maxNodes);
  const selectedIds = new Set(selected.map(n=>n.id));
  if (truncated) warnings.push(`Node limit applied: rendered ${selected.length} of ${allNodes.length} nodes`);
  const renderLinks = links.filter(l=>selectedIds.has(l.source)&&selectedIds.has(l.target));
  const types = [...new Set(selected.map(n=>n.type||"Other"))];
  const nodes = selected.map(n => ({ id:n.id, name:n.name, type:n.type||"Other", status:n.status, isAnchor:n.isAnchor, degree:n.degree, x:n.x, y:n.y, symbolSize:n.symbolSize, properties:n.properties, statusColor: /risk|danger|critical|recalled|defective|error/i.test(n.status) ? (input.theme === "dark" ? "#e07865" : "#bc4b39") : /warning|affected|pending/i.test(n.status) ? (input.theme === "dark" ? "#d4a75c" : "#b17a32") : undefined }));
  const layoutInput = ["force","circular","none"].includes(input.layout) ? input.layout : "force";
  const layout = layoutInput === "none" && nodes.every(n=>Number.isFinite(n.x)&&Number.isFinite(n.y)) ? "none" : (layoutInput === "none" ? "force" : layoutInput);
  const html = generateHtml({ title, nodes, links:renderLinks, layout, directed:input.directed !== false, showRelationLabels:input.showRelationLabels === true, width:Math.max(360,Math.min(2400,Number(input.width)||800)), dark:input.theme === "dark" });
  return { html, meta: { skillId:"network-graph", chartType, title, dataRows:renderLinks.length, nodeCount:nodes.length, originalNodeCount:allNodes.length, linkCount:renderLinks.length, originalLinkCount:links.length, truncated, warnings, layout } };
}
