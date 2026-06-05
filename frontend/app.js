const API_BASE = 'http://127.0.0.1:8000';

// DOM refs
const tabs = document.querySelectorAll('.tab');
const pasteTab = document.getElementById('tab-paste');
const uploadTab = document.getElementById('tab-upload');
const novelText = document.getElementById('novel-text');
const novelTitle = document.getElementById('novel-title');
const chapterSep = document.getElementById('chapter-sep');
const convertBtn = document.getElementById('convert-btn');
const btnText = document.getElementById('btn-text');
const btnLoading = document.getElementById('btn-loading');
const errorMsg = document.getElementById('error-msg');
const outputPlaceholder = document.getElementById('output-placeholder');
const outputYaml = document.getElementById('output-yaml');
const outputActions = document.getElementById('output-actions');
const statsText = document.getElementById('stats-text');
const copyBtn = document.getElementById('copy-btn');
const downloadBtn = document.getElementById('download-btn');
const uploadArea = document.getElementById('upload-area');
const fileInput = document.getElementById('file-input');
const fileName = document.getElementById('file-name');

let activeTab = 'paste';
let uploadedFile = null;

// Tab switching
tabs.forEach(tab => {
  tab.addEventListener('click', () => {
    tabs.forEach(t => t.classList.remove('active'));
    tab.classList.add('active');
    activeTab = tab.dataset.tab;
    pasteTab.classList.toggle('hidden', activeTab !== 'paste');
    uploadTab.classList.toggle('hidden', activeTab !== 'upload');
  });
});

// File upload
uploadArea.addEventListener('dragover', e => {
  e.preventDefault();
  uploadArea.classList.add('drag-over');
});
uploadArea.addEventListener('dragleave', () => uploadArea.classList.remove('drag-over'));
uploadArea.addEventListener('drop', e => {
  e.preventDefault();
  uploadArea.classList.remove('drag-over');
  const file = e.dataTransfer.files[0];
  if (file) handleFile(file);
});
fileInput.addEventListener('change', () => {
  if (fileInput.files[0]) handleFile(fileInput.files[0]);
});

function handleFile(file) {
  if (!file.name.endsWith('.txt')) {
    showError('仅支持 .txt 格式文件');
    return;
  }
  uploadedFile = file;
  fileName.textContent = `已选择：${file.name}（${(file.size / 1024).toFixed(1)} KB）`;
  if (!novelTitle.value) {
    novelTitle.value = file.name.replace('.txt', '');
  }
}

// Convert
convertBtn.addEventListener('click', async () => {
  clearError();
  const title = novelTitle.value.trim();
  const sep = chapterSep.value.trim();

  setLoading(true);

  try {
    let response;

    if (activeTab === 'upload') {
      if (!uploadedFile) { showError('请先选择 .txt 文件'); setLoading(false); return; }
      const formData = new FormData();
      formData.append('file', uploadedFile);
      if (title) formData.append('novel_title', title);
      if (sep) formData.append('chapter_separator', sep);
      response = await fetch(`${API_BASE}/upload`, { method: 'POST', body: formData });
    } else {
      const text = novelText.value.trim();
      if (text.length < 100) { showError('请输入至少包含 3 个章节的小说文本（最少 100 字）'); setLoading(false); return; }
      response = await fetch(`${API_BASE}/convert`, {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({ novel_text: text, novel_title: title, chapter_separator: sep }),
      });
    }

    const data = await response.json();

    if (!response.ok) {
      showError(data.detail || '服务器错误，请检查后端是否已启动');
      return;
    }

    if (data.success) {
      showOutput(data.yaml_content, data.chapters_detected, data.characters_detected);
    } else {
      showError(data.error || '转换失败');
    }
  } catch (err) {
    if (err.name === 'TypeError' && err.message.includes('fetch')) {
      showError('无法连接后端服务，请确保已启动 backend/main.py（端口 8000）');
    } else {
      showError(`错误：${err.message}`);
    }
  } finally {
    setLoading(false);
  }
});

function showOutput(yaml, chapters, characters) {
  outputPlaceholder.classList.add('hidden');
  outputYaml.classList.remove('hidden');
  outputYaml.value = yaml;
  outputActions.classList.remove('hidden');
  statsText.textContent = `${chapters} 章节 · ${characters} 人物`;
}

function setLoading(loading) {
  convertBtn.disabled = loading;
  btnText.classList.toggle('hidden', loading);
  btnLoading.classList.toggle('hidden', !loading);
}

function showError(msg) {
  errorMsg.textContent = msg;
  errorMsg.classList.remove('hidden');
}

function clearError() {
  errorMsg.classList.add('hidden');
}

// Copy
copyBtn.addEventListener('click', async () => {
  await navigator.clipboard.writeText(outputYaml.value);
  const orig = copyBtn.textContent;
  copyBtn.textContent = '已复制 ✓';
  setTimeout(() => { copyBtn.textContent = orig; }, 1500);
});

// Download
downloadBtn.addEventListener('click', () => {
  const blob = new Blob([outputYaml.value], { type: 'text/yaml;charset=utf-8' });
  const url = URL.createObjectURL(blob);
  const a = document.createElement('a');
  a.href = url;
  const title = novelTitle.value.trim() || 'screenplay';
  a.download = `${title}_剧本.yaml`;
  a.click();
  URL.revokeObjectURL(url);
});
