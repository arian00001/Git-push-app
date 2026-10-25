import React, { useEffect, useMemo, useState } from 'react';
import { createRoot } from 'react-dom/client';
import JSZip from 'jszip';
import {
  Home, Folder, MessageSquare, Network, Globe, Settings, UserRound, Plus,
  Upload, FileArchive, FileCode2, Sparkles, Pencil, PanelsTopLeft, Github,
  Rocket, Mail, ShieldCheck, Download, Search, Trash2, CheckCircle2,
  CircleAlert, ChevronDown, ChevronRight, ExternalLink, RefreshCw, X,
  FolderOpen, GitBranch, Eye, EyeOff, KeyRound, Smartphone, Terminal,
  CloudUpload, File, PackageCheck, Menu
} from 'lucide-react';
import './style.css';

const STORE = 'dss_settings_v1';
const readSettings = () => { try { return JSON.parse(localStorage.getItem(STORE) || '{}'); } catch { return {}; } };
const api = async (path, token, options={}) => {
  const res = await fetch(`https://api.github.com${path}`, {
    ...options, headers: { Accept:'application/vnd.github+json', ...(token ? {Authorization:`Bearer ${token}`} : {}), ...(options.body ? {'Content-Type':'application/json'} : {}), ...options.headers }
  });
  const data = res.status === 204 ? null : await res.json().catch(()=>({}));
  if (!res.ok) throw new Error(data.message || `GitHub API error (${res.status})`);
  return data;
};
const b64 = (str) => btoa(unescape(encodeURIComponent(str)));
const bytesToBase64 = (bytes) => { let binary = ''; const chunk = 0x8000; for (let i = 0; i < bytes.length; i += chunk) binary += String.fromCharCode(...bytes.subarray(i, i + chunk)); return btoa(binary); };
const App = () => {
  const [settings, setSettings] = useState(readSettings);
  const [tab, setTab] = useState('Workspace');
  const [showSettings, setShowSettings] = useState(false);
  const [showToken, setShowToken] = useState(false);
  const [notice, setNotice] = useState(null);
  const [busy, setBusy] = useState(false);
  const [owner, setOwner] = useState('');
  const [repos, setRepos] = useState([]);
  const [selected, setSelected] = useState('');
  const [files, setFiles] = useState([]);
  const [zipName, setZipName] = useState('');
  const [filter, setFilter] = useState('');
  const [branch, setBranch] = useState('main');
  const [newRepo, setNewRepo] = useState('');
  const [isPrivate, setIsPrivate] = useState(true);
  const [showCreate, setShowCreate] = useState(false);
  const [vercelStatus, setVercelStatus] = useState('');
  const [expanded, setExpanded] = useState(true);
  const [mobileNav, setMobileNav] = useState(false);
  const [logs, setLogs] = useState(['Workspace initialized', 'Waiting for a ZIP project…']);
  const log = (s) => setLogs(old => [`${new Date().toLocaleTimeString()}  ${s}`, ...old].slice(0,7));
  const toast = (type, text) => { setNotice({type,text}); setTimeout(()=>setNotice(null), 4200); };
  useEffect(()=>{ localStorage.setItem(STORE, JSON.stringify(settings)); }, [settings]);
  const selectedRepo = repos.find(r=>r.full_name===selected);
  const visibleFiles = useMemo(()=>files.filter(f=>f.path.toLowerCase().includes(filter.toLowerCase())).slice(0,120),[files,filter]);
  const saveSettings = async () => {
    if (!settings.githubToken?.trim()) { toast('error','Add a GitHub token first.'); return; }
    setBusy(true);
    try {
      const u = await api('/user', settings.githubToken.trim());
      setOwner(u.login);
      setSettings(s=>({...s, githubToken:s.githubToken.trim(), githubUsername:u.login}));
      log(`Authenticated as @${u.login}`);
      toast('success',`GitHub connected as @${u.login}`);
      setShowSettings(false);
      await loadRepos(u.login, settings.githubToken.trim());
    } catch(e) { toast('error',e.message); }
    finally { setBusy(false); }
  };
  const loadRepos = async (login=owner, token=settings.githubToken) => {
    if (!token) return toast('error','Save your GitHub token in Settings first.');
    setBusy(true);
    try {
      const u = login || (await api('/user',token)).login;
      const data = await api(`/users/${encodeURIComponent(u)}/repos?per_page=100&sort=updated`,token);
      setOwner(u); setRepos(data); log(`Loaded ${data.length} repositories`); toast('success',`Loaded ${data.length} repositories`);
    } catch(e) { toast('error',e.message); }
    finally { setBusy(false); }
  };
  const pickZip = async (event) => {
    const file = event.target.files?.[0]; if(!file) return;
    setBusy(true); setZipName(file.name);
    try {
      const zip = await JSZip.loadAsync(file);
      const all = [];
      for (const [path, item] of Object.entries(zip.files)) {
        if(item.dir) continue;
        const clean = path.replace(/^\/+/, '');
        if (!clean || clean.split('/').some(p=>p==='..') || clean.startsWith('.git/')) continue;
        const blob = await item.async('uint8array');
        all.push({path:clean, bytes:blob, size:blob.byteLength});
      }
      if(!all.length) throw new Error('No usable files found in this ZIP.');
      setFiles(all.sort((a,b)=>a.path.localeCompare(b.path)));
      log(`Imported ${all.length} files from ${file.name}`);
      toast('success',`ZIP unpacked: ${all.length} files`);
    } catch(e) { toast('error',`ZIP import failed: ${e.message}`); }
    finally { setBusy(false); event.target.value=''; }
  };
  const createRepo = async () => {
    if(!newRepo.trim()) return toast('error','Enter a repository name.');
    setBusy(true);
    try {
      const r = await api('/user/repos',settings.githubToken,{method:'POST',body:JSON.stringify({name:newRepo.trim(),private:isPrivate,auto_init:true,description:'Created with Dev Style Studio'})});
      setRepos(old=>[r,...old.filter(x=>x.full_name!==r.full_name)]); setSelected(r.full_name); setNewRepo(''); setShowCreate(false); log(`Created ${r.full_name}`); toast('success','Repository created.');
    } catch(e) { toast('error',e.message); }
    finally { setBusy(false); }
  };
  const deleteRepo = async () => {
    if(!selectedRepo) return;
    if(!confirm(`Permanently delete ${selectedRepo.full_name}? This cannot be undone.`)) return;
    setBusy(true);
    try { await api(`/repos/${selectedRepo.full_name}`,settings.githubToken,{method:'DELETE'}); setRepos(r=>r.filter(x=>x.full_name!==selected)); log(`Deleted ${selected}`); setSelected(''); toast('success','Repository deleted.'); }
    catch(e){toast('error',e.message)} finally{setBusy(false)}
  };
  const pushProject = async () => {
    if(!settings.githubToken) return toast('error','Connect GitHub in Settings first.');
    if(!selectedRepo) return toast('error','Select a repository first.');
    if(!files.length) return toast('error','Import a ZIP project first.');
    setBusy(true);
    try {
      const repo = await api(`/repos/${selectedRepo.full_name}`,settings.githubToken);
      const defaultBranch = repo.default_branch || branch || 'main';
      let sha = null;
      try { const ref=await api(`/repos/${selectedRepo.full_name}/git/ref/heads/${encodeURIComponent(defaultBranch)}`,settings.githubToken); sha=ref.object.sha; } catch {}
      const baseTree = sha ? (await api(`/repos/${selectedRepo.full_name}/git/commits/${sha}`,settings.githubToken)).tree.sha : null;
      const tree = [];
      for(let i=0;i<files.length;i++){
        const f=files[i];
        const blob=await api(`/repos/${selectedRepo.full_name}/git/blobs`,settings.githubToken,{method:'POST',body:JSON.stringify({content:bytesToBase64(f.bytes),encoding:'base64'})});
        tree.push({path:f.path,mode:'100644',type:'blob',sha:blob.sha});
        if(i%10===0) log(`Preparing file ${i+1}/${files.length}`);
      }
      const treeObj=await api(`/repos/${selectedRepo.full_name}/git/trees`,settings.githubToken,{method:'POST',body:JSON.stringify({base_tree:baseTree,tree})});
      const commit=await api(`/repos/${selectedRepo.full_name}/git/commits`,settings.githubToken,{method:'POST',body:JSON.stringify({message:`Upload project via Dev Style Studio (${new Date().toISOString().slice(0,10)})`,tree:treeObj.sha,...(sha?{parents:[sha]}:{})})});
      if(sha) await api(`/repos/${selectedRepo.full_name}/git/refs/heads/${encodeURIComponent(defaultBranch)}`,settings.githubToken,{method:'PATCH',body:JSON.stringify({sha:commit.sha,force:false})});
      else await api(`/repos/${selectedRepo.full_name}/git/refs`,settings.githubToken,{method:'POST',body:JSON.stringify({ref:`refs/heads/${defaultBranch}`,sha:commit.sha})});
      log(`Pushed ${files.length} files to ${selectedRepo.full_name}`);
      toast('success',`Push complete: ${files.length} files`);
    } catch(e){toast('error',`Push failed: ${e.message}`);log(`ERROR: ${e.message}`)}
    finally{setBusy(false)}
  };
  const connectVercel = async () => {
    if(!settings.vercelToken?.trim()) return toast('error','Add a Vercel token in Settings first.');
    setBusy(true);
    try {
      const r=await fetch('https://api.vercel.com/v9/projects?limit=1',{headers:{Authorization:`Bearer ${settings.vercelToken.trim()}`}});
      if(!r.ok) throw new Error((await r.json().catch(()=>({}))).error?.message || `Vercel API error (${r.status})`);
      setVercelStatus('Connected'); setSettings(s=>({...s,vercelToken:s.vercelToken.trim()})); toast('success','Vercel token verified.');
    }catch(e){setVercelStatus('Not connected');toast('error',e.message)}finally{setBusy(false)}
  };
  const nav = [
    {name:'Workspace',icon:Home},{name:'Projects',icon:Folder},{name:'GitHub',icon:Github},
    {name:'Deploy',icon:Rocket},{name:'Email Center',icon:Mail}
  ];
  return <div className="app-shell">
    <aside className={`sidebar ${mobileNav?'nav-open':''}`}>
      <div className="brand-mark"><span>///</span></div>
      <div className="nav-list">{nav.map(n=><button key={n.name} className={`nav-btn ${tab===n.name?'active':''}`} onClick={()=>{setTab(n.name);setMobileNav(false)}} title={n.name}><n.icon size={20}/><span>{n.name}</span></button>)}</div>
      <div className="sidebar-bottom"><button className="nav-btn" onClick={()=>setShowSettings(true)}><Settings size={20}/><span>Settings</span></button><div className="avatar">DS</div></div>
    </aside>
    {mobileNav&&<div className="nav-scrim" onClick={()=>setMobileNav(false)}/>}
    <main className="main-area">
      <header className="topbar"><button className="mobile-menu icon-btn" onClick={()=>setMobileNav(!mobileNav)}><Menu/></button><div><div className="eyebrow">MOBILE DEVELOPMENT WORKSPACE</div><h1><span>DEV</span> STYLE Studio</h1><p>Build, organize and publish your code — right from your phone.</p></div><button className="settings-top icon-btn" onClick={()=>setShowSettings(true)}><Settings size={19}/></button></header>
      <div className="content-grid">
        <section className="work-column">
          <div className="panel flow-panel">
            <div className="panel-heading"><div><span className="step-tag">YOUR WORKFLOW</span><h2>Project pipeline</h2></div><span className={`status ${settings.githubToken?'connected':''}`}><i/>{settings.githubToken?'GitHub token saved':'Not connected'}</span></div>
            <div className="flow-canvas">
              <div className="flow-row top-flow">
                <button className="flow-node lime" onClick={()=>document.getElementById('zip-input').click()}><Upload/><b>IMPORT & CREATE</b><small>ZIP project</small></button>
                <ChevronRight className="flow-arrow"/>
                <button className="flow-node lime" onClick={()=>document.getElementById('zip-input').click()}><FileArchive/><b>ZIP / SAF</b><small>Import files</small></button>
                <ChevronRight className="flow-arrow"/>
                <div className="flow-node pale"><FileCode2/><b>Project files</b><small>React · Kotlin · Java</small></div>
              </div>
              <div className="flow-down"><span>↓</span></div>
              <div className="core-box"><div className="core-label">CORE IDE WORKSPACE</div>
                <div className="core-row"><button className="flow-node ai-node" onClick={()=>toast('info','AI scanner is a UI placeholder in this starter build.')}><Sparkles/><b>AI ASSISTANT</b><small>Project scanner</small></button><button className="flow-node lime" onClick={()=>setTab('Projects')}><Pencil/><b>CODE PROJECT</b><small>Browse imported files</small></button><div className="preview-card"><PanelsTopLeft/><b>Project workspace</b><small>{files.length} files ready</small><div className="preview-pills"><span>ZIP</span><span>GitHub API</span><span>Android</span></div></div></div>
              </div>
              <div className="branch-lines"><span/><span/></div>
              <div className="bottom-modules"><button className="module-card" onClick={()=>setTab('GitHub')}><Github/><b>GitHub Manager</b><small>Repos · Push · Pull</small></button><button className="module-card" onClick={()=>setTab('Deploy')}><Rocket/><b>Deployment Adapter</b><small>Vercel · projects</small></button></div>
            </div>
            <div className="action-bar"><button className="primary-action" onClick={()=>document.getElementById('zip-input').click()}><Upload size={17}/> Import ZIP</button><button onClick={()=>setTab('Projects')}><Search size={17}/> Browse files</button><button onClick={pushProject} disabled={busy}><CloudUpload size={17}/> Push to GitHub</button><button onClick={()=>setShowSettings(true)}><Settings size={17}/> Settings</button><input id="zip-input" type="file" accept=".zip,application/zip" hidden onChange={pickZip}/></div>
          </div>
          <div className="lower-grid">
            <section className="panel repo-panel"><div className="panel-heading"><div><span className="step-tag">SOURCE CONTROL</span><h2>Repository manager</h2></div><button className="small-icon" onClick={()=>loadRepos()} disabled={busy}><RefreshCw size={16}/></button></div>
              <div className="repo-toolbar"><select value={selected} onChange={e=>setSelected(e.target.value)}><option value="">Select a repository…</option>{repos.map(r=><option key={r.id} value={r.full_name}>{r.full_name}{r.private?' · private':''}</option>)}</select><button className="square-btn" onClick={()=>setShowCreate(true)} title="Create repository"><Plus size={17}/></button></div>
              {selectedRepo?<div className="selected-repo"><div className="repo-icon"><Github/></div><div className="repo-meta"><b>{selectedRepo.full_name}</b><small>{selectedRepo.private?'Private':'Public'} · {selectedRepo.default_branch||'main'} branch</small></div><button className="small-icon danger" onClick={deleteRepo} title="Delete repository"><Trash2 size={16}/></button></div>:<div className="empty-state"><FolderOpen size={26}/><b>No repository selected</b><small>Connect GitHub, then select or create a repo.</small></div>}
              <div className="repo-buttons"><button onClick={()=>loadRepos()} disabled={busy}><RefreshCw size={15}/> Refresh repos</button><button className="primary-action" onClick={pushProject} disabled={busy}><CloudUpload size={15}/> Push project</button></div>
            </section>
            <section className="panel files-panel"><div className="panel-heading"><div><span className="step-tag">PROJECT CONTENTS</span><h2>Imported files <span className="count">{files.length}</span></h2></div><FileCode2 size={20}/></div><div className="file-search"><Search size={15}/><input value={filter} onChange={e=>setFilter(e.target.value)} placeholder="Filter file paths…"/></div>
              <div className="file-list">{visibleFiles.length?visibleFiles.map(f=><div className="file-row" key={f.path}><File size={15}/><span title={f.path}>{f.path}</span><small>{f.size<1024?`${f.size} B`:`${(f.size/1024).toFixed(1)} KB`}</small></div>):<div className="empty-state compact"><FileArchive size={24}/><b>{files.length?'No matching files':'Import a ZIP to begin'}</b><small>Files are unpacked locally in this workspace.</small></div>}</div>
              {files.length>120&&<small className="muted">Showing first 120 matching files.</small>}
            </section>
          </div>
        </section>
        <aside className="right-panel panel">
          <button className="right-title" onClick={()=>setExpanded(!expanded)}><ChevronDown className={expanded?'':'rotated'}/><span>Application modules</span><span className="module-count">05</span></button>
          {expanded&&<div className="module-list">
            <div className="module-item"><div className="module-icon"><GitBranch/></div><div><b>GitHub Repository Manager</b><small>List, create, delete and push project files.</small></div><CheckCircle2 className="module-check"/></div>
            <div className="module-item"><div className="module-icon"><Smartphone/></div><div><b>ZIP / SAF Import</b><small>Unpack a ZIP and inspect its file tree.</small></div><CheckCircle2 className="module-check"/></div>
            <div className="module-item"><div className="module-icon"><ShieldCheck/></div><div><b>Token settings</b><small>GitHub and Vercel API connectivity.</small></div><CheckCircle2 className="module-check"/></div>
            <div className="module-item"><div className="module-icon"><Rocket/></div><div><b>Vercel connection</b><small>{vercelStatus||'Token verification available in Settings.'}</small></div><span className={`dot-status ${vercelStatus==='Connected'?'ok':''}`}/></div>
            <div className="module-item"><div className="module-icon"><Terminal/></div><div><b>Build activity</b><small>Recent local workspace events.</small></div></div>
          </div>}
          <div className="activity"><div className="activity-head"><b>Activity log</b><button onClick={()=>setLogs([])} className="text-btn">Clear</button></div>{logs.map((l,i)=><div className="log-row" key={i}><span className="log-dot"/><span>{l}</span></div>)}</div>
          <div className="security-note"><ShieldCheck size={17}/><div><b>Token safety</b><p>Use a fine-grained token with only the repository permissions you need. Never share screenshots containing tokens.</p></div></div>
        </aside>
      </div>
      <footer><span>DEV STYLE STUDIO <i>•</i> Mobile-first developer workspace</span><span>Local workspace <b>●</b></span></footer>
    </main>
    {showSettings&&<div className="modal-backdrop" onClick={()=>setShowSettings(false)}><section className="modal" onClick={e=>e.stopPropagation()}><div className="modal-head"><div><span className="step-tag">ACCOUNT CONNECTIONS</span><h2>Settings & access</h2><p>Credentials stay in this app's local browser storage.</p></div><button className="icon-btn" onClick={()=>setShowSettings(false)}><X/></button></div>
      <label>GitHub username <input value={settings.githubUsername||owner} onChange={e=>setSettings(s=>({...s,githubUsername:e.target.value}))} placeholder="your-github-username"/></label>
      <label>GitHub access token <div className="token-field"><KeyRound size={16}/><input type={showToken?'text':'password'} value={settings.githubToken||''} onChange={e=>setSettings(s=>({...s,githubToken:e.target.value}))} placeholder="github_pat_…"/><button onClick={()=>setShowToken(!showToken)}>{showToken?<EyeOff size={16}/>:<Eye size={16}/>}</button></div></label>
      <div className="hint-box"><ShieldCheck size={17}/><span>Fine-grained token: grant access only to selected repositories and Contents (read/write). Repository administration is needed for create/delete actions.</span></div>
      <label>Vercel access token <div className="token-field"><KeyRound size={16}/><input type="password" value={settings.vercelToken||''} onChange={e=>setSettings(s=>({...s,vercelToken:e.target.value}))} placeholder="Vercel token"/></div></label>
      <label>Vercel team / scope (optional) <input value={settings.vercelScope||''} onChange={e=>setSettings(s=>({...s,vercelScope:e.target.value}))} placeholder="team_xxx or personal account"/></label>
      <div className="modal-actions"><button onClick={()=>{setSettings({});setOwner('');setRepos([]);setSelected('');localStorage.removeItem(STORE);toast('success','Saved credentials cleared.')}} className="secondary-btn">Clear saved tokens</button><button onClick={saveSettings} className="primary-action" disabled={busy}>{busy?'Checking…':'Save & connect GitHub'}</button><button onClick={connectVercel} className="secondary-btn" disabled={busy}>Verify Vercel</button></div>
      <p className="fineprint">Security note: this client-only starter stores tokens in WebView localStorage, which is not encrypted. Use a dedicated token, never commit it, and revoke it when finished. For production, replace this with Android Keystore-backed native storage.</p>
    </section></div>}
    {showCreate&&<div className="modal-backdrop" onClick={()=>setShowCreate(false)}><section className="modal small-modal" onClick={e=>e.stopPropagation()}><div className="modal-head"><div><span className="step-tag">NEW PROJECT REPOSITORY</span><h2>Create repository</h2></div><button className="icon-btn" onClick={()=>setShowCreate(false)}><X/></button></div><label>Repository name<input value={newRepo} onChange={e=>setNewRepo(e.target.value)} placeholder="my-android-project"/></label><label className="check-label"><input type="checkbox" checked={isPrivate} onChange={e=>setIsPrivate(e.target.checked)}/> Keep repository private</label><div className="modal-actions"><button className="secondary-btn" onClick={()=>setShowCreate(false)}>Cancel</button><button className="primary-action" onClick={createRepo} disabled={busy}>{busy?'Creating…':'Create repository'}</button></div></section></div>}
    {notice&&<div className={`toast ${notice.type}`}><span>{notice.type==='error'?<CircleAlert size={18}/>:<CheckCircle2 size={18}/>}</span>{notice.text}<button onClick={()=>setNotice(null)}><X size={15}/></button></div>}
  </div>;
};
createRoot(document.getElementById('root')).render(<App/>);