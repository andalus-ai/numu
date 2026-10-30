import { createWorkspaceApi, WorkspaceApiError } from "./api.js";
import { BLOCK_TYPES, blockTypeLabel, blocksFromPlainText, createBlock, moveBlock, normalizeBlocks, serializeBlocks } from "./blocks.js";
const apiBase = document.querySelector('meta[name="numou-api-base"]')?.content || "/api/v1";
const api = createWorkspaceApi({ baseUrl: apiBase });
const $ = (selector) => document.querySelector(selector);
const state = { workspace:null, space:null, workspaces:[], spaces:[], page:null, revision:null, editBlocks:[], savedTitle:"", savedBlocks:"[]", dirty:false, view:"home", toastTimer:null };
const ACTIVE_WORKSPACE_KEY = "numou.workspace.activeWorkspaceId";
const ACTIVE_SPACE_KEY = "numou.workspace.activeSpaceId";
function readSelection(key) { try { return localStorage.getItem(key); } catch { return null; } }
function writeSelection(key, value) { try { if(value) localStorage.setItem(key,value); else localStorage.removeItem(key); } catch { /* storage is optional */ } }
const forms = { workspace:$("#workspace-form"), space:$("#space-form"), page:$("#page-form") };
const submitButton = (form) => form.querySelector('button[type="submit"]');
const field = (form, name) => form.elements.namedItem(name);
const safeText = (value, fallback="") => typeof value === "string" ? value : fallback;
function showToast(message) { const toast=$("#toast"); toast.textContent=message; toast.hidden=false; clearTimeout(state.toastTimer); state.toastTimer=setTimeout(()=>{toast.hidden=true;},4800); }
function setStatus(message, connected=null) { $("#save-state").textContent=message; $("#connection-status").textContent=connected===true?"API connected":connected===false?"API unavailable":"Ready"; $("#connection-status").previousElementSibling?.classList.toggle("connected",connected===true); }
async function runAction(action, successMessage) { try { setStatus("Working…"); const result=await action(); setStatus("Ready",true); if(successMessage) showToast(successMessage); return result; } catch(error) { setStatus(error instanceof WorkspaceApiError&&error.code==="NETWORK_ERROR"?"Offline":"Needs attention",error instanceof WorkspaceApiError&&error.code!=="NETWORK_ERROR"); showToast(error instanceof WorkspaceApiError?error.message:"Something went wrong. Please try again."); return null; } }
function setFormEnabled(form, enabled) { for(const element of form.elements) element.disabled=!enabled; }
function renderWorkspace() { $("#workspace-summary").textContent=state.workspace?.name||"Create a workspace to begin."; $("#workspace-summary").classList.toggle("muted",!state.workspace); setFormEnabled(forms.space,Boolean(state.workspace)); setFormEnabled(forms.page,Boolean(state.workspace&&state.space)); if(state.space) field(forms.page,"title").placeholder=`A page in ${state.space.name}`; renderNavigationSwitchers(); }
function renderNavigationSwitchers() {
  const workspaceSelect=$("#workspace-switcher"), spaceSelect=$("#space-switcher");
  workspaceSelect.replaceChildren(); spaceSelect.replaceChildren();
  const workspacePlaceholder=document.createElement("option"); workspacePlaceholder.value=""; workspacePlaceholder.textContent=state.workspaces.length?"Select a workspace":"No workspace available"; workspaceSelect.append(workspacePlaceholder);
  for(const workspace of state.workspaces){const option=document.createElement("option");option.value=workspace.id;option.textContent=safeText(workspace.name,"Untitled workspace");workspaceSelect.append(option);}
  workspaceSelect.value=state.workspace?.id||""; workspaceSelect.disabled=state.workspaces.length<2;
  const spacePlaceholder=document.createElement("option");spacePlaceholder.value="";spacePlaceholder.textContent=state.workspace?(state.spaces.length?"Select a space":"No spaces yet"):"Select a workspace";spaceSelect.append(spacePlaceholder);
  for(const space of state.spaces){const option=document.createElement("option");option.value=space.id;option.textContent=safeText(space.name,"Untitled space");spaceSelect.append(option);}
  spaceSelect.value=state.space?.id||""; spaceSelect.disabled=!state.workspace||state.spaces.length===0;
}
async function switchWorkspace(workspaceId) {
  if(!workspaceId||workspaceId===state.workspace?.id)return;
  if(state.dirty&&!window.confirm("You have unsaved edits. Switch workspace and discard them?")){renderNavigationSwitchers();return;}
  const selected=state.workspaces.find(item=>item.id===workspaceId);if(!selected)return;
  const spaces=await runAction(()=>api.listSpaces(selected.id));if(spaces===null){renderNavigationSwitchers();return;}
  state.workspace=selected;state.spaces=Array.isArray(spaces)?spaces:[];state.space=state.spaces[0]||null;state.page=null;state.dirty=false;
  writeSelection(ACTIVE_WORKSPACE_KEY,selected.id);writeSelection(ACTIVE_SPACE_KEY,state.space?.id||null);
  renderWorkspace();renderPageNavigation([]);showView("home");if(state.space)await refreshPages();
}
function switchSpace(spaceId) {
  if(!spaceId||spaceId===state.space?.id)return;
  if(state.dirty&&!window.confirm("You have unsaved edits. Switch space and discard them?")){renderNavigationSwitchers();return;}
  const selected=state.spaces.find(item=>item.id===spaceId);if(!selected)return;
  state.space=selected;state.page=null;state.dirty=false;writeSelection(ACTIVE_SPACE_KEY,selected.id);renderWorkspace();renderPageNavigation([]);showView("home");void refreshPages();
}
function showView(view) { if(state.dirty&&state.view==="editor"&&view!=="editor"&&!window.confirm("You have unsaved edits. Leave this page without saving?")) return; state.view=view; $("#home-view").hidden=view!=="home"; $("#editor-view").hidden=view!=="editor"; $("#search-view").hidden=view!=="search"; $("#templates-view").hidden=view!=="templates"; for(const [id,target] of [["nav-home","home"],["nav-search","search"],["nav-templates","templates"]]) $("#"+id).classList.toggle("active",view===target); const crumbs=$("#breadcrumbs"); crumbs.replaceChildren(); const first=document.createElement("span"); first.textContent=state.workspace?.name||"Workspace"; const sep=document.createElement("span"); sep.className="crumb-separator"; sep.textContent="/"; const last=document.createElement("strong"); last.textContent=view==="editor"?(state.page?.title||"Untitled"):view==="search"?"Search":view==="templates"?"Templates":"Home"; crumbs.append(first,sep,last); }
function renderPageNavigation(pages) { const nav=$("#page-navigation"); nav.replaceChildren(); for(const page of Array.isArray(pages)?pages:[]) { const button=document.createElement("button"); button.type="button"; button.className="page-link"; button.textContent=safeText(page.title,"Untitled page"); button.addEventListener("click",()=>openPage(page.id)); nav.append(button); } }
function renderBlockEditor() {
  const container=$("#block-list"); container.replaceChildren();
  state.editBlocks.forEach((block,index)=>{
    const item=document.createElement("article"); item.className=`editor-block editor-block-${BLOCK_TYPES.includes(block.type)?block.type:"custom"}`;
    const header=document.createElement("div"); header.className="block-header";
    const label=document.createElement("span"); label.className="block-label"; label.textContent=blockTypeLabel(block.type); header.append(label);
    const actions=document.createElement("div"); actions.className="block-actions";
    const typeSelect=document.createElement("select"); typeSelect.className="block-type-select"; typeSelect.setAttribute("aria-label",`Block ${index+1} type`);
    if(!BLOCK_TYPES.includes(block.type)){const custom=document.createElement("option");custom.value=block.type;custom.textContent="Custom block";typeSelect.append(custom);}
    for(const type of BLOCK_TYPES){const option=document.createElement("option");option.value=type;option.textContent=blockTypeLabel(type);typeSelect.append(option);}
    typeSelect.value=block.type; typeSelect.addEventListener("change",()=>{state.editBlocks[index]={...state.editBlocks[index],type:typeSelect.value};renderBlockEditor();markDirty();});
    actions.append(typeSelect);
    for(const [labelText,delta] of [["Move up",-1],["Move down",1]]){const move=document.createElement("button");move.type="button";move.className="block-icon-button";move.textContent=delta<0?"↑":"↓";move.setAttribute("aria-label",labelText);move.title=labelText;move.disabled=index+delta<0||index+delta>=state.editBlocks.length;move.addEventListener("click",()=>{state.editBlocks=moveBlock(state.editBlocks,index,index+delta);renderBlockEditor();markDirty();});actions.append(move);}
    const remove=document.createElement("button");remove.type="button";remove.className="block-icon-button block-remove";remove.textContent="×";remove.setAttribute("aria-label",`Remove block ${index+1}`);remove.title="Remove block";remove.addEventListener("click",()=>{state.editBlocks.splice(index,1);state.editBlocks=normalizeBlocks(state.editBlocks);renderBlockEditor();markDirty();});actions.append(remove);
    header.append(actions); item.append(header);
    if(block.type==="divider"){const divider=document.createElement("hr");divider.className="document-divider";item.append(divider);}
    else {const input=document.createElement("textarea");input.className="block-input";input.value=block.text;input.rows=block.type==="heading-1"||block.type==="heading-2"?1:block.type==="code"?4:2;input.setAttribute("aria-label",`Block ${index+1} content`);input.placeholder=block.type==="heading-1"?"Heading":block.type==="heading-2"?"Subheading":block.type==="code"?"Write code…":"Start writing…";input.addEventListener("input",()=>{state.editBlocks[index]={...state.editBlocks[index],text:input.value};markDirty();});input.addEventListener("keydown",event=>{if((event.metaKey||event.ctrlKey)&&event.key==="Enter"){event.preventDefault();$("#save-button").click();}});item.append(input);}
    container.append(item);
  });
  if(!state.editBlocks.length){const empty=document.createElement("p");empty.className="block-empty";empty.textContent="This page is empty. Add a block to start writing.";container.append(empty);}
}
function renderEditor(page) { state.page=page; state.revision=Number(page.revision??page.currentRevision??0); state.savedTitle=safeText(page.title); state.editBlocks=serializeBlocks(normalizeBlocks(page.blocks)); state.savedBlocks=JSON.stringify(state.editBlocks); state.dirty=false; $("#editor-title").value=state.savedTitle; renderBlockEditor(); $("#editor-meta").textContent=`Revision ${state.revision} · Changes are saved when confirmed by the server.`; $("#history-panel").hidden=true; $("#save-button").disabled=true; $("#save-button").textContent="Save changes"; showView("editor"); }
async function refreshPages() { if(!state.space)return; const pages=await runAction(()=>api.listPages(state.space.id)); if(pages)renderPageNavigation(pages); }
async function openPage(pageId) { if(!pageId){showToast("The API response did not include a page ID.");return;} const page=await runAction(()=>api.getPage(pageId)); if(page)renderEditor(page); }
forms.workspace.addEventListener("submit",async(event)=>{event.preventDefault();const button=submitButton(forms.workspace);button.disabled=true;const created=await runAction(()=>api.createWorkspace({name:field(forms.workspace,"name").value.trim(),description:field(forms.workspace,"description").value.trim()||null}),"Workspace created.");button.disabled=false;if(!created)return;state.workspace=created;state.space=null;state.workspaces=[...state.workspaces,created];state.spaces=[];writeSelection(ACTIVE_WORKSPACE_KEY,created.id);writeSelection(ACTIVE_SPACE_KEY,null);renderWorkspace();field(forms.space,"name").focus();});
forms.space.addEventListener("submit",async(event)=>{event.preventDefault();if(!state.workspace)return;const button=submitButton(forms.space);button.disabled=true;const created=await runAction(()=>api.createSpace(state.workspace.id,{name:field(forms.space,"name").value.trim(),slug:field(forms.space,"slug").value.trim()}),"Space created.");button.disabled=false;if(!created)return;state.space=created;state.spaces=[...state.spaces,created];writeSelection(ACTIVE_SPACE_KEY,created.id);renderWorkspace();field(forms.page,"title").focus();await refreshPages();});
forms.page.addEventListener("submit",async(event)=>{event.preventDefault();if(!state.workspace||!state.space)return;const button=submitButton(forms.page);button.disabled=true;const title=field(forms.page,"title").value.trim();const content=field(forms.page,"content").value;const created=await runAction(()=>api.createPage(state.workspace.id,state.space.id,{title,blocks:content?blocksFromPlainText(content):[]}),"Page created.");button.disabled=false;if(!created)return;field(forms.page,"title").value="";field(forms.page,"content").value="";await refreshPages();renderEditor(created);});
$("#editor-title").addEventListener("input",markDirty);
function markDirty(){state.dirty=$("#editor-title").value!==state.savedTitle||JSON.stringify(serializeBlocks(state.editBlocks))!==state.savedBlocks;$("#save-state").textContent=state.dirty?"Unsaved changes":"Saved";$("#save-button").disabled=!state.dirty;}
$("#add-block-button").addEventListener("click",()=>{const type=$("#new-block-type").value;state.editBlocks.push(createBlock(type));state.editBlocks=normalizeBlocks(state.editBlocks);renderBlockEditor();markDirty();const inputs=$("#block-list").querySelectorAll(".block-input");inputs[inputs.length-1]?.focus();});
$("#save-button").addEventListener("click",async()=>{if(!state.page||!state.dirty)return;const button=$("#save-button");button.disabled=true;button.textContent="Saving…";const title=$("#editor-title").value.trim();if(!title){showToast("A page title is required.");button.disabled=false;button.textContent="Save changes";return;}const updated=await runAction(()=>api.updatePage(state.page.id,{expectedRevision:state.revision,title,blocks:serializeBlocks(state.editBlocks),changeSummary:"Updated in workspace web client"}));if(!updated){button.disabled=false;button.textContent="Save changes";return;}renderEditor(updated);showToast("Changes saved.");await refreshPages();});
$("#history-button").addEventListener("click",async()=>{if(!state.page)return;const history=await runAction(()=>api.getPageHistory(state.page.id));if(!history)return;const panel=$("#history-panel");panel.replaceChildren();const heading=document.createElement("h3");heading.textContent="Page history";panel.append(heading);if(!history.length){const empty=document.createElement("p");empty.className="muted";empty.textContent="No previous revisions are available.";panel.append(empty);}for(const revision of history){const row=document.createElement("div");row.className="history-row";const details=document.createElement("div");const label=document.createElement("strong");const number=revision.revision??revision.revisionNumber;label.textContent=`Revision ${number??"—"}`;const summary=document.createElement("p");summary.textContent=safeText(revision.changeSummary,"Saved page version");details.append(label,summary);row.append(details);const restore=document.createElement("button");restore.type="button";restore.className="button button-quiet";restore.textContent="Restore";restore.addEventListener("click",async()=>{const sourceRevision=Number(number);if(!Number.isFinite(sourceRevision)||!window.confirm(`Restore revision ${sourceRevision} as a new revision?`))return;const restored=await runAction(()=>api.restorePageRevision(state.page.id,{sourceRevision,expectedCurrentRevision:state.revision}),"Revision restored as a new version.");if(restored){renderEditor(restored);panel.hidden=true;await refreshPages();}});row.append(restore);panel.append(row);}panel.hidden=false;});
$("#search-form").addEventListener("submit",async(event)=>{event.preventDefault();if(!state.workspace){showToast("Create or connect a workspace before searching.");return;}const results=await runAction(()=>api.searchPages(state.workspace.id,$("#search-query").value.trim(),state.space?.id));if(results)renderResults($("#search-results"),results,"No matching pages were found.");});
function renderResults(container,items,emptyMessage){container.replaceChildren();if(!Array.isArray(items)||items.length===0){const empty=document.createElement("p");empty.className="muted";empty.textContent=emptyMessage;container.append(empty);return;}for(const item of items){const card=document.createElement("article");card.className="result-card";const title=document.createElement("h3");title.textContent=safeText(item.title,"Untitled");const description=document.createElement("p");description.textContent=safeText(item.excerpt||item.description,"Open this page to view its content.");const open=document.createElement("button");open.type="button";open.textContent="Open page →";open.addEventListener("click",()=>openPage(item.id||item.pageId));card.append(title,description,open);container.append(card);}}
$("#workspace-switcher").addEventListener("change",event=>void switchWorkspace(event.target.value));$("#space-switcher").addEventListener("change",event=>switchSpace(event.target.value));
$("#nav-home").addEventListener("click",()=>showView("home"));$("#nav-search").addEventListener("click",()=>showView("search"));
$("#nav-templates").addEventListener("click",async()=>{showView("templates");if(!state.workspace){renderResults($("#template-results"),[],"Create a workspace to see its templates.");return;}const templates=await runAction(()=>api.listTemplates(state.workspace.id));if(!templates)return;const container=$("#template-results");container.replaceChildren();if(!templates.length){renderResults(container,[],"No templates are available in this workspace yet.");return;}for(const template of templates){const card=document.createElement("article");card.className="result-card";const title=document.createElement("h3");title.textContent=safeText(template.name,"Untitled template");const description=document.createElement("p");description.textContent=safeText(template.description,"Start a page from this template.");const create=document.createElement("button");create.type="button";create.textContent="Create page from template →";create.addEventListener("click",async()=>{if(!state.space){showToast("Create a space before using a template.");return;}const page=await runAction(()=>api.createPageFromTemplate(template.id,{spaceId:state.space.id,titleOverride:safeText(template.name,"New page")}),"Page created from template.");if(page){renderEditor(page);await refreshPages();}});card.append(title,description,create);container.append(card);}});
$("#new-workspace-shortcut").addEventListener("click",()=>{showView("home");field(forms.workspace,"name").focus();});
$("#refresh-button").addEventListener("click",async()=>{if(state.page&&state.view==="editor"){if(state.dirty&&!window.confirm("Reload from server and discard your unsaved edits?"))return;await openPage(state.page.id);}else await refreshPages();});
window.addEventListener("beforeunload",(event)=>{if(state.dirty){event.preventDefault();event.returnValue="";}});
async function restoreNavigation() {
  const workspaces = await runAction(() => api.listWorkspaces());
  if (workspaces === null) { renderWorkspace(); return; }
  if (!Array.isArray(workspaces) || workspaces.length === 0) {
    state.workspaces = []; state.spaces = []; state.workspace = null; state.space = null;
    writeSelection(ACTIVE_WORKSPACE_KEY,null); writeSelection(ACTIVE_SPACE_KEY,null);
    renderWorkspace(); return;
  }
  state.workspaces = workspaces;
  const preferredWorkspace = readSelection(ACTIVE_WORKSPACE_KEY);
  state.workspace = workspaces.find(item => item.id === preferredWorkspace) || workspaces[0];
  writeSelection(ACTIVE_WORKSPACE_KEY,state.workspace.id);
  const spaces = await runAction(() => api.listSpaces(state.workspace.id));
  if (spaces === null) { renderWorkspace(); return; }
  if (!Array.isArray(spaces)) { state.space = null; writeSelection(ACTIVE_SPACE_KEY,null); renderWorkspace(); return; }
  state.spaces = spaces;
  const preferredSpace = readSelection(ACTIVE_SPACE_KEY);
  state.space = spaces.find(item => item.id === preferredSpace) || spaces[0] || null;
  writeSelection(ACTIVE_SPACE_KEY,state.space?.id || null);
  renderWorkspace();
  if (state.space) await refreshPages();
}
renderWorkspace();setStatus("Ready");void restoreNavigation();
