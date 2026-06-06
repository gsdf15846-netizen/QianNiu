'use strict';

const novelInput  = document.getElementById('novelInput');
const titleInput  = document.getElementById('titleInput');
const convertBtn  = document.getElementById('convertBtn');
const yamlOutput  = document.getElementById('yamlOutput');
const charCount   = document.getElementById('charCount');
const copyBtn     = document.getElementById('copyBtn');
const downloadBtn = document.getElementById('downloadBtn');
const statusMsg   = document.getElementById('statusMsg');
const fileInput   = document.getElementById('fileInput');
const loading     = document.getElementById('loadingOverlay');

novelInput.addEventListener('input', () => {
    charCount.textContent = novelInput.value.length + ' 字';
});

fileInput.addEventListener('change', async () => {
    const file = fileInput.files[0];
    if (!file) return;
    const text = await file.text();
    novelInput.value = text;
    charCount.textContent = text.length + ' 字';
    if (!titleInput.value) {
        titleInput.value = file.name.replace(/\.txt$/, '');
    }
});

convertBtn.addEventListener('click', async () => {
    const text = novelInput.value.trim();
    if (!text) {
        showStatus('请先输入或上传小说文本', 'error');
        return;
    }

    setLoading(true);
    hideStatus();

    try {
        const res = await fetch('/api/convert', {
            method: 'POST',
            headers: { 'Content-Type': 'application/json' },
            body: JSON.stringify({
                title: titleInput.value.trim() || '未命名',
                text
            })
        });
        const data = await res.json();
        if (data.success) {
            yamlOutput.textContent = data.yaml;
            copyBtn.disabled = false;
            downloadBtn.disabled = false;
            showStatus('转换成功！', 'success');
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

function setLoading(on) {
    loading.classList.toggle('hidden', !on);
    convertBtn.disabled = on;
}

function showStatus(msg, type) {
    statusMsg.textContent = msg;
    statusMsg.className = 'status-msg ' + type;
    statusMsg.classList.remove('hidden');
}

function hideStatus() {
    statusMsg.classList.add('hidden');
}
