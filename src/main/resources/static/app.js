'use strict';

const novelInput  = document.getElementById('novelInput');
const titleInput  = document.getElementById('titleInput');
const resumeInput = document.getElementById('resumeInput');
const convertBtn  = document.getElementById('convertBtn');
const yamlOutput  = document.getElementById('yamlOutput');
const charCount   = document.getElementById('charCount');
const copyBtn     = document.getElementById('copyBtn');
const downloadBtn = document.getElementById('downloadBtn');
const statusMsg   = document.getElementById('statusMsg');
const fileInput   = document.getElementById('fileInput');
const loading     = document.getElementById('loadingOverlay');
const loadingText = document.getElementById('loadingText');
const convIdBadge = document.getElementById('convIdBadge');
const convIdText  = document.getElementById('convIdText');

// ─── 字数统计 ──────────────────────────────────────────────────────────────────

novelInput.addEventListener('input', () => {
    charCount.textContent = novelInput.value.length + ' 字';
});

// ─── 文件上传 ──────────────────────────────────────────────────────────────────

fileInput.addEventListener('change', async () => {
    const file = fileInput.files[0];
    if (!file) return;
    const text = await file.text();
    novelInput.value = text;
    charCount.textContent = text.length + ' 字';
    if (!titleInput.value) titleInput.value = file.name.replace(/\.txt$/, '');
});

// ─── 转换 ──────────────────────────────────────────────────────────────────────

convertBtn.addEventListener('click', async () => {
    const text = novelInput.value.trim();
    if (!text) { showStatus('请先输入或上传小说文本', 'error'); return; }

    const isResume = resumeInput.value.trim().length > 0;
    loadingText.textContent = isResume
        ? '正在续传，跳过已完成的章节，继续处理剩余内容...'
        : 'AI 正在逐章改编，章节越多耗时越长，请耐心等候...';

    setLoading(true);
    hideStatus();
    convIdBadge.classList.add('hidden');

    try {
        const res = await fetch('/api/convert', {
            method: 'POST',
            headers: { 'Content-Type': 'application/json' },
            body: JSON.stringify({
                title: titleInput.value.trim() || '未命名',
                text,
                conversionId: resumeInput.value.trim() || null
            })
        });
        const data = await res.json();
        if (data.success) {
            yamlOutput.textContent = data.yaml;
            copyBtn.disabled = false;
            downloadBtn.disabled = false;
            showStatus('转换成功！', 'success');
            if (data.conversionId) {
                convIdText.textContent = data.conversionId;
                convIdBadge.classList.remove('hidden');
                resumeInput.value = '';
            }
            loadHistory();
        } else {
            showStatus(data.message || '转换失败', 'error');
            yamlOutput.textContent = '转换失败，请检查输入或联系管理员。';
        }
    } catch (err) {
        showStatus('网络错误：' + err.message, 'error');
    } finally {
        setLoading(false);
    }
});

// ─── 复制 / 下载 ───────────────────────────────────────────────────────────────

copyBtn.addEventListener('click', () => {
    navigator.clipboard.writeText(yamlOutput.textContent)
        .then(() => showStatus('已复制到剪贴板', 'success'))
        .catch(() => showStatus('复制失败，请手动选中复制', 'error'));
});

downloadBtn.addEventListener('click', () => {
    const blob = new Blob([yamlOutput.textContent], { type: 'text/yaml;charset=utf-8' });
    const url = URL.createObjectURL(blob);
    const a = document.createElement('a');
    a.href = url;
    a.download = (titleInput.value.trim() || 'script') + '.yaml';
    a.click();
    URL.revokeObjectURL(url);
});

function copyConvId() {
    navigator.clipboard.writeText(convIdText.textContent)
        .then(() => showStatus('转换ID已复制', 'success'))
        .catch(() => showStatus('复制失败', 'error'));
}

// ─── 历史记录 ──────────────────────────────────────────────────────────────────

async function loadHistory() {
    try {
        const res = await fetch('/api/history');
        const items = await res.json();
        renderHistory(items);
    } catch (err) {
        document.getElementById('historyList').innerHTML =
            '<div class="history-empty">加载历史记录失败</div>';
    }
}

function renderHistory(items) {
    const list = document.getElementById('historyList');
    if (!items || items.length === 0) {
        list.innerHTML = '<div class="history-empty">暂无历史记录</div>';
        return;
    }
    list.innerHTML = items.map(item => `
        <div class="history-item">
            <div class="history-item-info">
                <div class="history-item-title">${escapeHtml(item.title)}</div>
                <div class="history-item-meta">${item.createdAt} · ${item.totalChapters || '-'} 章 · ID: ${item.conversionId.substring(0, 8)}...</div>
            </div>
            <span class="badge ${badgeClass(item.status)}">${statusLabel(item.status)} ${progressText(item)}</span>
            <div class="history-actions">
                ${item.status === 'COMPLETED'
                    ? `<button class="action-btn" onclick="viewHistory(${item.id})">查看</button>`
                    : ''}
                ${item.status === 'IN_PROGRESS'
                    ? `<button class="action-btn resume-btn" onclick="resumeHistory('${item.conversionId}', '${escapeHtml(item.title)}')">续传</button>`
                    : ''}
                <button class="action-btn del-btn" onclick="deleteHistory(${item.id})">删除</button>
            </div>
        </div>
    `).join('');
}

async function viewHistory(id) {
    try {
        const res = await fetch(`/api/history/${id}`);
        const item = await res.json();
        yamlOutput.textContent = item.yamlResult;
        copyBtn.disabled = false;
        downloadBtn.disabled = false;
        titleInput.value = item.title;
        showStatus(`已加载历史记录「${item.title}」`, 'success');
        document.querySelector('.output-panel').scrollIntoView({ behavior: 'smooth' });
    } catch (err) {
        showStatus('加载历史记录失败', 'error');
    }
}

function resumeHistory(convId, title) {
    resumeInput.value = convId;
    titleInput.value = title;
    showStatus(`已填入续传ID，请重新上传小说文本后点击"AI 转换"`, 'success');
    document.querySelector('.input-panel').scrollIntoView({ behavior: 'smooth' });
    novelInput.focus();
}

async function deleteHistory(id) {
    if (!confirm('确认删除该条历史记录？')) return;
    try {
        await fetch(`/api/history/${id}`, { method: 'DELETE' });
        loadHistory();
    } catch (err) {
        showStatus('删除失败', 'error');
    }
}

// ─── 工具函数 ──────────────────────────────────────────────────────────────────

function setLoading(on) {
    loading.classList.toggle('hidden', !on);
    convertBtn.disabled = on;
}

function showStatus(msg, type) {
    statusMsg.textContent = msg;
    statusMsg.className = 'status-msg ' + type;
    statusMsg.classList.remove('hidden');
}

function hideStatus() { statusMsg.classList.add('hidden'); }

function escapeHtml(str) {
    return String(str)
        .replace(/&/g, '&amp;')
        .replace(/</g, '&lt;')
        .replace(/>/g, '&gt;')
        .replace(/"/g, '&quot;');
}

function badgeClass(status) {
    if (status === 'COMPLETED')  return 'badge-completed';
    if (status === 'IN_PROGRESS') return 'badge-progress';
    return 'badge-failed';
}

function statusLabel(status) {
    if (status === 'COMPLETED')  return '✓ 已完成';
    if (status === 'IN_PROGRESS') return '⏳ 进行中';
    return '✗ 失败';
}

function progressText(item) {
    if (item.status === 'IN_PROGRESS' && item.totalChapters) {
        return `${item.completedChapters}/${item.totalChapters}章`;
    }
    return '';
}

// 页面加载时拉取历史
loadHistory();
