import { createWorkspaceApi, WorkspaceApiError } from "./api.js";
import { BLOCK_LABELS, EDITABLE_BLOCK_TYPES, createBlock, normalizeBlocks, serializeBlocks } from "./blocks.js";
const apiBase = document.querySelector('meta[name="numou-api-base"]')?.content || "/api/v1";
const api = createWorkspaceApi({ baseUrl: apiBase });
const $ = (selector) => document.querySelector(selector);
const state = { workspace:null, space:null, workspaces:[], spaces:[], page:null, revision:null, blocks:[], commentThreads:[], savedTitle:"", savedBlocks:"[]", dirty:false, view:"home", toastTimer:null };
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
function textToBlocks(text) {
  return String(text).split(/\n\s*\n/).filter((paragraph,index,array)=>paragraph.length||index<array.length-1)
    .map((paragraph,position)=>({id:crypto.randomUUID(),type:"PARAGRAPH",text:paragraph,position,attributes:{}}));
}
function createElement(tag, className, text) {
  const element=document.createElement(tag);
  if(className)element.className=className;
  if(text!==undefined)element.textContent=text;
  return element;
}
function renderBlockEditor() {
  const container=$("#block-editor");container.replaceChildren();
  state.blocks.forEach((block,index)=>{
    const card=createElement("article","block-card");card.dataset.blockId=block.id;
    const toolbar=createElement("div","block-toolbar");
    const typeLabel=createElement("span","block-type-label",BLOCK_LABELS[block.type]||block.type.replaceAll("_"," "));
    toolbar.append(typeLabel);
    const actions=createElement("div","block-actions");
    const up=createElement("button","icon-button","↑");up.type="button";up.title="Move block up";up.setAttribute("aria-label","Move block up");up.disabled=index===0;
    up.addEventListener("click",()=>moveBlock(index,-1));
    const down=createElement("button","icon-button","↓");down.type="button";down.title="Move block down";down.setAttribute("aria-label","Move block down");down.disabled=index===state.blocks.length-1;
    down.addEventListener("click",()=>moveBlock(index,1));
    const remove=createElement("button","icon-button block-remove","×");remove.type="button";remove.title="Remove block";remove.setAttribute("aria-label","Remove block");
    remove.addEventListener("click",()=>{state.blocks.splice(index,1);renderBlockEditor();markDirty();});
    actions.append(up,down,remove);toolbar.append(actions);card.append(toolbar);
    if(!EDITABLE_BLOCK_TYPES.includes(block.type)){
      const note=createElement("p","block-readonly",block.type==="FILE"?"File attachment block is preserved; file management is not available in this editor.":"Domain reference is preserved as an opaque domain-owned block.");
      card.append(note);
    } else if(block.type==="DIVIDER"){
      const line=createElement("hr","block-divider");card.append(line);
    } else {
      if(block.type==="TODO"||block.type==="CHECKLIST_ITEM"){
        const checked=document.createElement("input");checked.type="checkbox";checked.checked=block.attributes.checked==="true";checked.setAttribute("aria-label","Mark item complete");
        checked.addEventListener("change",()=>{block.attributes={...block.attributes,checked:String(checked.checked)};markDirty();});
        card.append(checked);
      }
      if(block.type==="CALLOUT"){
        const tone=document.createElement("select");tone.className="block-tone";tone.setAttribute("aria-label","Callout tone");
        for(const value of ["info","success","warning","danger","neutral"]){const option=document.createElement("option");option.value=value;option.textContent=value[0].toUpperCase()+value.slice(1);tone.append(option);}
        tone.value=block.attributes.tone||"info";tone.addEventListener("change",()=>{block.attributes={...block.attributes,tone:tone.value};markDirty();});card.append(tone);
      }
      if(block.type==="CODE"){
        const language=document.createElement("input");language.className="block-language";language.placeholder="Language (optional)";language.value=block.attributes.language||"";language.setAttribute("aria-label","Code language");
        language.addEventListener("input",()=>{block.attributes={...block.attributes,language:language.value};markDirty();});card.append(language);
      }
      const content=document.createElement(block.type==="HEADING"?"input":"textarea");
      content.className="block-content"+(block.type==="HEADING"?" block-heading":"");
      content.value=block.text;
      content.placeholder=({
        PARAGRAPH:"Write something…",HEADING:"Heading",BULLET:"List item",NUMBERED_ITEM:"List item",
        TODO:"To-do item",CHECKLIST_ITEM:"Checklist item",QUOTE:"Quote",CALLOUT:"Callout text",
        CODE:"Write or paste code…",TABLE:"Enter rows; separate columns with tabs",TOGGLE:"Toggle title and content…",
      })[block.type]||"Write something…";
      if(block.type==="HEADING")content.maxLength=200;
      if(block.type==="CODE"||block.type==="TABLE"||block.type==="TOGGLE")content.rows=5;
      else if(block.type!=="HEADING")content.rows=2;
      content.addEventListener("input",()=>{block.text=content.value;markDirty();});
      card.append(content);
    }
    container.append(card);
  });
}
function moveBlock(index,direction) {
  const target=index+direction;if(target<0||target>=state.blocks.length)return;
  [state.blocks[index],state.blocks[target]]=[state.blocks[target],state.blocks[index]];
  renderBlockEditor();markDirty();
}
function addBlock(type) {
  if(!EDITABLE_BLOCK_TYPES.includes(type))return;
  state.blocks.push(createBlock(type,state.blocks.length));renderBlockEditor();markDirty();
  const editors=$("#block-editor").querySelectorAll(".block-content");editors[editors.length-1]?.focus();
}
function renderEditor(page) {
  state.page=page;state.revision=Number(page.revision??page.currentRevision??0);
  state.savedTitle=safeText(page.title);state.blocks=normalizeBlocks(page.blocks);
  state.savedBlocks=JSON.stringify(serializeBlocks(state.blocks));state.dirty=false;
  $("#editor-title").value=state.savedTitle;renderBlockEditor();
  $("#editor-meta").textContent=`Revision ${state.revision} · Changes are saved when confirmed by the server.`;
  $("#history-panel").hidden=true;$("#save-button").disabled=true;$("#save-button").textContent="Save changes";showView("editor");void refreshCommentThreads();
}
function renderCommentThreads() {
  const container=$("#comment-threads");container.replaceChildren();
  const threads=state.commentThreads;
  $("#comments-count").textContent=`${threads.length} thread${threads.length===1?"":"s"}`;
  if(!threads.length){container.append(createElement("p","muted","No comments yet. Start a discussion on this page."));return;}
  for(const thread of threads){
    const card=createElement("article","comment-thread");card.dataset.threadId=thread.id;
    const header=createElement("div","comment-thread-header");
    const status=createElement("span",thread.resolved?"comment-status resolved":"comment-status",thread.resolved?"Resolved":"Open discussion");
    const action=createElement("button","button button-quiet",thread.resolved?"Reopen":"Resolve");action.type="button";
    action.addEventListener("click",async()=>{
      const pageId=state.page?.id;if(!pageId)return;action.disabled=true;
      const mutate=thread.resolved?api.reopenCommentThread:api.resolveCommentThread;
      await runAction(()=>mutate(pageId,thread.id,{expectedRevision:thread.revision}));
      await refreshCommentThreads();
    });
    header.append(status,action);card.append(header);
    for(const comment of thread.comments||[]){
      const entry=createElement("article",comment.parentCommentId?"comment-entry comment-reply":"comment-entry");
      const meta=createElement("div","comment-meta",(comment.parentCommentId?"Reply · ":"Comment · ")+safeText(comment.authorId).slice(0,8));
      const body=createElement("p","comment-body",safeText(comment.body));
      const timestamp=createElement("time","comment-time",comment.createdAt?new Date(comment.createdAt).toLocaleString():"");
      entry.append(meta,body,timestamp);card.append(entry);
    }
    const replyForm=createElement("form","comment-reply-form");
    const replyInput=createElement("textarea","","");replyInput.rows=2;replyInput.maxLength=10000;replyInput.required=true;
    replyInput.placeholder=thread.resolved?"Reopen this thread to reply…":"Write a reply…";replyInput.disabled=thread.resolved;
    const replyButton=createElement("button","button button-quiet","Reply");replyButton.type="submit";replyButton.disabled=thread.resolved;
    replyForm.append(replyInput,replyButton);
    replyForm.addEventListener("submit",async(event)=>{
      event.preventDefault();const parent=thread.comments?.[thread.comments.length-1];if(!parent)return;
      replyButton.disabled=true;
      const updated=await runAction(()=>api.replyToCommentThread(state.page.id,thread.id,{parentCommentId:parent.id,body:replyInput.value}));
      if(updated)await refreshCommentThreads();else replyButton.disabled=false;
    });
    card.append(replyForm);container.append(card);
  }
}
async function refreshCommentThreads() {
  const pageId=state.page?.id;if(!pageId)return;
  const threads=await runAction(()=>api.listCommentThreads(pageId));
  if(threads===null||state.page?.id!==pageId)return;
  state.commentThreads=Array.isArray(threads)?threads:[];
  renderCommentThreads();
}
async function refreshPages() { if(!state.space)return; const pages=await runAction(()=>api.listPages(state.space.id)); if(pages)renderPageNavigation(pages); }
async function openPage(pageId,discardDraft=false) { if(!pageId){showToast("The API response did not include a page ID.");return;} if(state.dirty&&!discardDraft&&!window.confirm("You have unsaved edits. Open another page and discard them?"))return; const page=await runAction(()=>api.getPage(pageId)); if(page)renderEditor(page); }
forms.workspace.addEventListener("submit",async(event)=>{event.preventDefault();const button=submitButton(forms.workspace);button.disabled=true;const created=await runAction(()=>api.createWorkspace({name:field(forms.workspace,"name").value.trim(),description:field(forms.workspace,"description").value.trim()||null}),"Workspace created.");button.disabled=false;if(!created)return;state.workspace=created;state.space=null;state.workspaces=[...state.workspaces,created];state.spaces=[];writeSelection(ACTIVE_WORKSPACE_KEY,created.id);writeSelection(ACTIVE_SPACE_KEY,null);renderWorkspace();field(forms.space,"name").focus();});
forms.space.addEventListener("submit",async(event)=>{event.preventDefault();if(!state.workspace)return;const button=submitButton(forms.space);button.disabled=true;const created=await runAction(()=>api.createSpace(state.workspace.id,{name:field(forms.space,"name").value.trim(),slug:field(forms.space,"slug").value.trim()}),"Space created.");button.disabled=false;if(!created)return;state.space=created;state.spaces=[...state.spaces,created];writeSelection(ACTIVE_SPACE_KEY,created.id);renderWorkspace();field(forms.page,"title").focus();await refreshPages();});
forms.page.addEventListener("submit",async(event)=>{event.preventDefault();if(!state.workspace||!state.space)return;const button=submitButton(forms.page);button.disabled=true;const title=field(forms.page,"title").value.trim();const content=field(forms.page,"content").value;const created=await runAction(()=>api.createPage(state.workspace.id,state.space.id,{title,blocks:content?textToBlocks(content):[]}),"Page created.");button.disabled=false;if(!created)return;field(forms.page,"title").value="";field(forms.page,"content").value="";await refreshPages();renderEditor(created);});
$("#editor-title").addEventListener("input",markDirty);
function markDirty(){state.dirty=$("#editor-title").value!==state.savedTitle||JSON.stringify(serializeBlocks(state.blocks))!==state.savedBlocks;$("#save-state").textContent=state.dirty?"Unsaved changes":"Saved";$("#save-button").disabled=!state.dirty;}
$("#add-block-button").addEventListener("click",()=>addBlock($("#new-block-type").value));
$("#save-button").addEventListener("click",async()=>{if(!state.page||!state.dirty)return;const button=$("#save-button");button.disabled=true;button.textContent="Saving…";const title=$("#editor-title").value.trim();if(!title){showToast("A page title is required.");button.disabled=false;button.textContent="Save changes";return;}const updated=await runAction(()=>api.updatePage(state.page.id,{expectedRevision:state.revision,title,blocks:serializeBlocks(state.blocks),changeSummary:"Updated in workspace web client"}));if(!updated){button.disabled=false;button.textContent="Save changes";return;}renderEditor(updated);showToast("Changes saved.");await refreshPages();});
$("#history-button").addEventListener("click",async()=>{if(!state.page)return;const history=await runAction(()=>api.getPageHistory(state.page.id));if(!history)return;const panel=$("#history-panel");panel.replaceChildren();const heading=document.createElement("h3");heading.textContent="Page history";panel.append(heading);if(!history.length){const empty=document.createElement("p");empty.className="muted";empty.textContent="No previous revisions are available.";panel.append(empty);}for(const revision of history){const row=document.createElement("div");row.className="history-row";const details=document.createElement("div");const label=document.createElement("strong");const number=revision.revision??revision.revisionNumber;label.textContent=`Revision ${number??"—"}`;const summary=document.createElement("p");summary.textContent=safeText(revision.changeSummary,"Saved page version");details.append(label,summary);row.append(details);const restore=document.createElement("button");restore.type="button";restore.className="button button-quiet";restore.textContent="Restore";restore.addEventListener("click",async()=>{const sourceRevision=Number(number);if(!Number.isFinite(sourceRevision)||!window.confirm(`Restore revision ${sourceRevision} as a new revision?`))return;const restored=await runAction(()=>api.restorePageRevision(state.page.id,{sourceRevision,expectedCurrentRevision:state.revision}),"Revision restored as a new version.");if(restored){renderEditor(restored);panel.hidden=true;await refreshPages();}});row.append(restore);panel.append(row);}panel.hidden=false;});
let mentionSuggestionSequence=0;
$("#comment-body").addEventListener("input",async()=>{
  const input=$("#comment-body"),panel=$("#mention-suggestions"),pageId=state.page?.id;
  const sequence=++mentionSuggestionSequence, cursor=input.selectionStart, before=input.value.slice(0,cursor);
  const match=before.match(/(?:^|\\s)@([A-Za-z0-9._-]{1,64})$/);
  if(!pageId||!match){panel.hidden=true;panel.replaceChildren();return;}
  const query=match[1];
  const members=await api.suggestMentions(pageId,query,8).catch(()=>[]);
  if(sequence!==mentionSuggestionSequence||state.page?.id!==pageId||input.selectionStart!==cursor)return;
  panel.replaceChildren();
  if(!Array.isArray(members)||!members.length){panel.hidden=true;return;}
  const tokenStart=cursor-match[0].length+match[0].lastIndexOf("@");
  for(const member of members){
    const option=document.createElement("button");option.type="button";option.className="mention-option";
    const name=document.createElement("strong");name.textContent=safeText(member.displayName);
    const handle=document.createElement("span");handle.textContent="@"+safeText(member.handle);
    option.append(name,handle);
    option.addEventListener("click",()=>{
      const cursor=input.selectionStart, tail=input.value.slice(cursor);
      input.value=input.value.slice(0,tokenStart)+"@"+member.handle+" "+tail;
      const next=tokenStart+member.handle.length+2;input.focus();input.setSelectionRange(next,next);
      panel.hidden=true;panel.replaceChildren();
    });
    panel.append(option);
  }
  panel.hidden=false;
});
$("#comment-form").addEventListener("submit",async(event)=>{
  event.preventDefault();const pageId=state.page?.id;if(!pageId)return;
  const body=$("#comment-body").value.trim();if(!body)return;
  const submit=$("#comment-form").querySelector('button[type="submit"]');submit.disabled=true;
  const created=await runAction(()=>api.createCommentThread(pageId,{body}));
  submit.disabled=false;
  if(created){$("#comment-body").value="";await refreshCommentThreads();}
});
$("#search-form").addEventListener("submit",async(event)=>{event.preventDefault();if(!state.workspace){showToast("Create or connect a workspace before searching.");return;}const results=await runAction(()=>api.searchPages(state.workspace.id,$("#search-query").value.trim(),state.space?.id));if(results)renderResults($("#search-results"),results,"No matching pages were found.");});
function renderResults(container,items,emptyMessage){container.replaceChildren();if(!Array.isArray(items)||items.length===0){const empty=document.createElement("p");empty.className="muted";empty.textContent=emptyMessage;container.append(empty);return;}for(const item of items){const card=document.createElement("article");card.className="result-card";const title=document.createElement("h3");title.textContent=safeText(item.title,"Untitled");const description=document.createElement("p");description.textContent=safeText(item.excerpt||item.description,"Open this page to view its content.");const open=document.createElement("button");open.type="button";open.textContent="Open page →";open.addEventListener("click",()=>openPage(item.id||item.pageId));card.append(title,description,open);container.append(card);}}
$("#workspace-switcher").addEventListener("change",event=>void switchWorkspace(event.target.value));$("#space-switcher").addEventListener("change",event=>switchSpace(event.target.value));
$("#nav-home").addEventListener("click",()=>showView("home"));$("#nav-search").addEventListener("click",()=>showView("search"));
$("#nav-templates").addEventListener("click",async()=>{showView("templates");if(!state.workspace){renderResults($("#template-results"),[],"Create a workspace to see its templates.");return;}const templates=await runAction(()=>api.listTemplates(state.workspace.id));if(!templates)return;const container=$("#template-results");container.replaceChildren();if(!templates.length){renderResults(container,[],"No templates are available in this workspace yet.");return;}for(const template of templates){const card=document.createElement("article");card.className="result-card";const title=document.createElement("h3");title.textContent=safeText(template.name,"Untitled template");const description=document.createElement("p");description.textContent=safeText(template.description,"Start a page from this template.");const create=document.createElement("button");create.type="button";create.textContent="Create page from template →";create.addEventListener("click",async()=>{if(!state.space){showToast("Create a space before using a template.");return;}const page=await runAction(()=>api.createPageFromTemplate(template.id,{spaceId:state.space.id,titleOverride:safeText(template.name,"New page")}),"Page created from template.");if(page){renderEditor(page);await refreshPages();}});card.append(title,description,create);container.append(card);}});
$("#new-workspace-shortcut").addEventListener("click",()=>{showView("home");field(forms.workspace,"name").focus();});
$("#refresh-button").addEventListener("click",async()=>{if(state.page&&state.view==="editor"){if(state.dirty&&!window.confirm("Reload from server and discard your unsaved edits?"))return;await openPage(state.page.id,true);}else await refreshPages();});
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
