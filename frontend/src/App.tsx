import { useEffect, useState } from 'react';
import { Bot, Clock, Loader2, MessageSquare, Plus, Send, Sparkles, Trash2, Wrench } from 'lucide-react';

type ChatMessage = {
  role: 'system' | 'user' | 'assistant';
  content: string;
};

type ToolInfo = {
  name: string;
  title: string;
  description: string;
  example: string;
  outputFormat?: string;
};

type ChatResponse = {
  output?: string;
  model?: string;
  duration_ms?: number;
};

type AiConfig = {
  name: string;
  serverUrl: string;
  llmBaseUrl: string;
  model: string;
  useStreaming: boolean;
  answer: string;
  result: string;
  loading: boolean;
  error: string;
};

type EmbeddingResult = {
  loading: boolean;
  similarity: number | null;
  isSimilar: boolean | null;
  error: string;
};

type CallRecord = {
  id: number;
  model: string;
  baseUrl: string;
  systemPrompt: string;
  userQuestion: string;
  output: string;
  promptTokens: number;
  completionTokens: number;
  totalTokens: number;
  durationMs: number;
  streaming: boolean;
  success: boolean;
  errorMessage: string;
  createdAt: string;
};

type Conversation = {
  id: number;
  title: string;
  mode: string;
  createdAt: string;
  updatedAt: string;
};

type ConversationMessage = {
  id: number;
  conversationId: number;
  role: string;
  content: string;
  createdAt: string;
};

const defaultTools: ToolInfo[] = [
  { name: 'get_current_time', title: '当前时间', description: '获取当前日期和时间，让 AI 知道今天是什么日期、星期几。', example: '今天是几号？现在是什么时间？' },
  { name: 'get_weather', title: '天气查询', description: '查询城市天气，目前支持 Beijing、Shanghai、Tokyo，其他城市返回 mock 兜底数据。', example: '北京今天天气怎么样？' },
  { name: 'get_football_match_info', title: '俱乐部足球', description: '查询欧洲俱乐部联赛（英超/德甲/西甲等）球队状态，仅在明确要求预测比分时才含赔率。⚠️ 国家队请用下方工具。', example: '拜仁最近状态怎么样？ / 预测一下 Bayern Munich 对 Dortmund 的比分' },
  { name: 'get_world_cup_match_info', title: '国家队/世界杯', description: '查询国家队国际赛事（世界杯/欧洲杯/美洲杯等）状态，数据来源 api-football.com。仅在明确要求预测比分时才含赔率。⚠️ 俱乐部请用上方工具。', example: 'Brazil 最近状态怎么样？ / 预测一下 Brazil 对 Argentina 的世界杯比分' },
  { name: 'search_football_news', title: '网页搜索验证', description: '通过 DuckDuckGo 实时搜索网页，获取足球比赛的最新资讯和新闻报道，用于交叉验证 API 数据的时效性。', example: '搜索一下 Manchester United 的最新足球新闻' },
];

function extractSseText(buffer: string) {
  const parts = buffer.split(/\n\n/);
  const complete = parts.slice(0, -1);
  const remainder = parts[parts.length - 1] ?? '';
  const texts: string[] = [];
  for (const part of complete) {
    const lines = part.split(/\n/).map((line) => line.trim()).filter(Boolean);
    for (const line of lines) {
      if (!line.startsWith('data:')) continue;
      const payload = line.slice(5).trim();
      if (!payload || payload === '[DONE]' || payload === 'DONE') continue;
      try {
        const json = JSON.parse(payload) as { choices?: Array<{ delta?: { content?: string } }> };
        const delta = json.choices?.[0]?.delta?.content;
        if (delta) texts.push(delta);
      } catch {
        texts.push(payload);
      }
    }
  }
  return { text: texts.join(''), remainder };
}

const baseConfig = (name: string): AiConfig => ({
  name,
  serverUrl: 'http://localhost:8080',
  llmBaseUrl: 'https://api.deepseek.com/v1',
  apiKey: 'sk-2d2c031f11c94b06bdc456ca4aa2f44c',
  model: 'deepseek-chat',
  useStreaming: true,
  answer: '',
  result: '',
  loading: false,
  error: '',
});

type PromptMode = 'general' | 'weather' | 'football';

const modePresets: Record<PromptMode, { prompt: string; question: string }> = {
  general: {
    prompt: '你是一个热心的 AI 助手，可以回答用户提出的各种问题。需要实时信息时可以调用工具获取数据。',
    question: '你好，请介绍一下你自己。',
  },
  weather: {
    prompt: '你是一个专业的天气和生活顾问。根据天气数据，告诉用户适合穿什么衣服、是否需要带伞、适合开车还是骑车、是否适合出门。用亲切的语气给出实用的生活建议。',
    question: '北京今天天气怎么样？适合出门吗？',
  },
  football: {
    prompt: '你是一个智能足球分析助手。收到用户问题后，按以下逻辑自主判断：\n\n1. 先分析用户意图：是想了解球队近期状态，还是想要预测比赛结果？\n2. 根据队名判断赛事类型：提到"巴西、法国、阿根廷、德国、英格兰"等国家队名称，或"世界杯、欧洲杯、美洲杯"等赛事 → 使用国家队工具；提到"曼联、皇马、拜仁、巴萨"等俱乐部名称，或"英超、德甲、西甲、欧冠"等联赛 → 使用俱乐部工具。\n3. 根据意图决定参数：要求预测比分、胜负、赔率 → need_prediction=true；仅问状态、近况、战绩 → need_prediction=false。\n4. 必须先调用 get_current_time 确认当前日期，再调用对应足球工具获取真实数据。\n5. 最后必须调用 search_football_news 进行网页搜索交叉验证，获取最新足球新闻。输出时必须标注：网页搜索是否执行、是否成功、各数据来源。严禁凭训练数据编造比赛结果。',
    question: '巴西在世界杯上最近状态怎么样？',
  },
};

const modeLabels: Record<PromptMode, string> = {
  general: '通用助手',
  weather: '天气专家',
  football: '足球分析师',
};

export default function App() {
  const [promptMode, setPromptMode] = useState<PromptMode>('general');
  const [systemPrompt, setSystemPrompt] = useState(modePresets.general.prompt);
  const [question, setQuestion] = useState(modePresets.general.question);
  const [tools, setTools] = useState<ToolInfo[]>(defaultTools);
  const [leftAi, setLeftAi] = useState<AiConfig>({ ...baseConfig('DeepSeek'), model: 'deepseek-chat' });
  const [rightAi, setRightAi] = useState<AiConfig>({ ...baseConfig('千问'), model: 'qwen-plus', apiKey: 'sk-019d423e803543128794ca5e89025539', llmBaseUrl: 'https://dashscope.aliyuncs.com/compatible-mode/v1' });
  const [embedding, setEmbedding] = useState<EmbeddingResult>({ loading: false, similarity: null, isSimilar: null, error: '' });
  const [records, setRecords] = useState<CallRecord[]>([]);
  const [conversations, setConversations] = useState<Conversation[]>([]);
  const [currentConversationId, setCurrentConversationId] = useState<number | null>(null);
  const [conversationMessages, setConversationMessages] = useState<ConversationMessage[]>([]);

  const fetchRecords = () => {
    fetch(`${leftAi.serverUrl}/agent/records`)
      .then((res) => (res.ok ? res.json() : Promise.reject(new Error('获取记录失败'))))
      .then((data) => setRecords(data as CallRecord[]))
      .catch(() => {});
  };

  const fetchConversations = () => {
    fetch(`${leftAi.serverUrl}/agent/conversations`)
      .then((res) => (res.ok ? res.json() : Promise.reject(new Error('获取对话列表失败'))))
      .then((data) => setConversations(data as Conversation[]))
      .catch(() => {});
  };

  const loadConversation = (convId: number) => {
    setCurrentConversationId(convId);
    fetch(`${leftAi.serverUrl}/agent/conversation/${convId}/messages`)
      .then((res) => (res.ok ? res.json() : Promise.reject(new Error('获取消息失败'))))
      .then((data) => setConversationMessages(data as ConversationMessage[]))
      .catch(() => {});
  };

  const newConversation = () => {
    const title = question.slice(0, 30) || '新对话';
    fetch(`${leftAi.serverUrl}/agent/conversation`, {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({ title, mode: promptMode }),
    })
      .then((res) => (res.ok ? res.json() : Promise.reject(new Error('创建对话失败'))))
      .then((data: Conversation) => {
        setCurrentConversationId(data.id);
        setConversationMessages([]);
        fetchConversations();
      })
      .catch(() => {});
  };

  const deleteConversation = (convId: number) => {
    fetch(`${leftAi.serverUrl}/agent/conversation/${convId}`, { method: 'DELETE' })
      .then((res) => {
        if (res.ok) {
          if (currentConversationId === convId) {
            setCurrentConversationId(null);
            setConversationMessages([]);
          }
          fetchConversations();
        }
      })
      .catch(() => {});
  };

  useEffect(() => {
    fetch(`${leftAi.serverUrl}/agent/tools`)
      .then((res) => (res.ok ? res.json() : Promise.reject(new Error('tools not available'))))
      .then((data: { tools?: ToolInfo[] }) => { if (data.tools?.length) setTools(data.tools); })
      .catch(() => setTools(defaultTools));
    fetchRecords();
    fetchConversations();
  }, [leftAi.serverUrl]);

  const useExample = (example: string) => setQuestion(example);
  const updateAi = (side: 'left' | 'right', patch: Partial<AiConfig>) => (side === 'left' ? setLeftAi : setRightAi)((prev) => ({ ...prev, ...patch }));

  const askOne = async (side: 'left' | 'right') => {
    const ai = side === 'left' ? leftAi : rightAi;
    updateAi(side, { loading: true, error: '', answer: '', result: '' });
    const messages: ChatMessage[] = [{ role: 'system', content: systemPrompt }, { role: 'user', content: question }];
    const startTime = Date.now();

    // 如果没有当前对话，先创建
    let convId = currentConversationId;
    if (convId === null) {
      try {
        const cres = await fetch(`${ai.serverUrl}/agent/conversation`, {
          method: 'POST',
          headers: { 'Content-Type': 'application/json' },
          body: JSON.stringify({ title: question.slice(0, 30), mode: promptMode }),
        });
        if (cres.ok) {
          const data: Conversation = await cres.json();
          convId = data.id;
          setCurrentConversationId(convId);
          fetchConversations();
        }
      } catch { /* 忽略 */ }
    }

    try {
      if (ai.useStreaming) {
        const url = `${ai.serverUrl}/agent/stream${convId ? `?conversationId=${convId}` : ''}`;
        const response = await fetch(url, {
          method: 'POST',
          headers: { 'Content-Type': 'application/json', 'X-API-Key': ai.apiKey, 'X-Base-Url': ai.llmBaseUrl },
          body: JSON.stringify({ model: ai.model, temperature: 0.7, stream: true, messages }),
        });
        if (!response.ok) throw new Error(await response.text());
        if (!response.body) throw new Error('当前浏览器不支持流式输出。');
        const reader = response.body.getReader();
        const decoder = new TextDecoder('utf-8');
        let buffer = '';
        let fullAnswer = '';
        while (true) {
          const { value, done } = await reader.read();
          if (done) break;
          buffer += decoder.decode(value, { stream: true });
          const parsed = extractSseText(buffer);
          fullAnswer += parsed.text;
          buffer = parsed.remainder;
          updateAi(side, { answer: fullAnswer });
        }
        if (buffer.trim()) {
          const parsed = extractSseText(buffer + '\n\n');
          fullAnswer += parsed.text;
          updateAi(side, { answer: fullAnswer });
        }
        updateAi(side, { result: `模型：${ai.model}\n输出方式：流式输出\n耗时：${Date.now() - startTime} ms` });
        if (convId) loadConversation(convId);
        return;
      }

      const response = await fetch(`${ai.serverUrl}/agent/chat`, {
        method: 'POST',
        headers: { 'Content-Type': 'application/json', 'X-API-Key': ai.apiKey, 'X-Base-Url': ai.llmBaseUrl },
        body: JSON.stringify({ model: ai.model, temperature: 0.7, stream: false, messages }),
      });
      const text = await response.text();
      if (!response.ok) throw new Error(text || `请求失败：${response.status}`);
      const data = JSON.parse(text) as ChatResponse;
      updateAi(side, { answer: data.output || '后端没有返回回答内容。', result: `模型：${data.model || ai.model}\n输出方式：堵塞输出\n耗时：${data.duration_ms ?? Date.now() - startTime} ms` });
    } catch (err) {
      updateAi(side, { error: err instanceof Error ? err.message : '未知错误', result: '' });
    } finally {
      updateAi(side, { loading: false });
    }
  };

  const askBoth = async () => Promise.all([askOne('left'), askOne('right')]);

  const compareEmbedding = async () => {
    const answerA = leftAi.answer;
    const answerB = rightAi.answer;
    if (!answerA || !answerB) return;

    setEmbedding({ loading: true, similarity: null, isSimilar: null, error: '' });

    try {
      const res = await fetch(`${rightAi.serverUrl}/agent/embedding/similarity`, {
        method: 'POST',
        headers: { 'Content-Type': 'application/json', 'X-API-Key': rightAi.apiKey, 'X-Base-Url': rightAi.llmBaseUrl },
        body: JSON.stringify({ text1: answerA, text2: answerB }),
      });
      if (!res.ok) throw new Error(await res.text());
      const data = await res.json();
      setEmbedding({ loading: false, similarity: data.similarity as number, isSimilar: data.is_similar as boolean, error: '' });
    } catch (err) {
      setEmbedding({ loading: false, similarity: null, isSimilar: null, error: err instanceof Error ? err.message : '未知错误' });
    }
  };

  return (
    <main style={{ display: 'flex', gap: 16, maxHeight: '100vh' }}>
      {/* ========== 左侧 Sidebar ========== */}
      <aside style={{ width: 260, flexShrink: 0, borderRight: '1px solid #e5e7eb', padding: '16px 12px', overflowY: 'auto', display: 'flex', flexDirection: 'column' }}>
        <button className="primary" style={{ marginBottom: 12, display: 'flex', alignItems: 'center', justifyContent: 'center', gap: 6 }}
          onClick={newConversation}>
          <Plus size={16} /> 新建对话
        </button>
        {conversations.length === 0 ? (
          <p style={{ color: '#999', fontSize: 13, textAlign: 'center', marginTop: 20 }}>暂无对话历史</p>
        ) : (
          conversations.map((conv) => (
            <div key={conv.id}
              style={{
                padding: '10px 12px', marginBottom: 4, borderRadius: 8, cursor: 'pointer',
                background: currentConversationId === conv.id ? '#e0f2fe' : 'transparent',
                border: currentConversationId === conv.id ? '1px solid #7dd3fc' : '1px solid transparent',
              }}
              onClick={() => loadConversation(conv.id)}
            >
              <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center' }}>
                <div style={{ fontSize: 14, fontWeight: 500, overflow: 'hidden', textOverflow: 'ellipsis', whiteSpace: 'nowrap', flex: 1 }}>
                  <MessageSquare size={12} style={{ marginRight: 6 }} />
                  {conv.title}
                </div>
                <button onClick={(e) => { e.stopPropagation(); deleteConversation(conv.id); }}
                  style={{ background: 'none', border: 'none', cursor: 'pointer', padding: '2px 4px', color: '#999' }}>
                  <Trash2 size={14} />
                </button>
              </div>
              <div style={{ fontSize: 11, color: '#999', marginTop: 4 }}>
                {modeLabels[conv.mode as PromptMode] || conv.mode} · {new Date(conv.updatedAt).toLocaleString('zh-CN')}
              </div>
            </div>
          ))
        )}
      </aside>

      {/* ========== 右侧：原有内容 ========== */}
      <div style={{ flex: 1, overflowY: 'auto', paddingRight: 8 }}>
      <section className="hero">
        <div className="logo"><Sparkles size={24} /></div>
        <div>
          <h1>双 AI 对话面板</h1>
          <p>左边和右边分别配置两个 AI，中间输入提示词和问题，可分别发送并保存各自回答。</p>
        </div>
      </section>

      {/* ===== 对话消息展示区 ===== */}
      {currentConversationId && conversationMessages.length > 0 && (
        <section className="panel" style={{ marginBottom: 16 }}>
          <div className="section-title"><MessageSquare size={18} /> 对话记录</div>
          <div style={{ maxHeight: 300, overflowY: 'auto' }}>
            {conversationMessages.map((msg) => (
              <div key={msg.id} style={{
                marginBottom: 8, padding: '8px 12px', borderRadius: 8,
                background: msg.role === 'user' ? '#eff6ff' : msg.role === 'assistant' ? '#f0fdf4' : '#f5f5f5',
                borderLeft: `3px solid ${msg.role === 'user' ? '#3b82f6' : msg.role === 'assistant' ? '#22c55e' : '#d4d4d4'}`,
              }}>
                <div style={{ fontSize: 11, color: '#999', marginBottom: 4 }}>
                  {msg.role === 'user' ? '用户' : msg.role === 'assistant' ? 'AI 助手' : msg.role}
                  {' · '}{new Date(msg.createdAt).toLocaleString('zh-CN')}
                </div>
                <div style={{ fontSize: 13, whiteSpace: 'pre-wrap', wordBreak: 'break-word' }}>{msg.content.slice(0, 500)}</div>
              </div>
            ))}
          </div>
        </section>
      )}

      <section className="compare-grid">
        <section className="panel ai-panel">
          <div className="section-title"><Bot size={18} /> DeepSeek</div>
          <label>后端地址<input value={leftAi.serverUrl} onChange={(e) => updateAi('left', { serverUrl: e.target.value })} /></label>
          <label>大模型 Base URL<input value={leftAi.llmBaseUrl} onChange={(e) => updateAi('left', { llmBaseUrl: e.target.value })} /></label>
          <label>模型<input value={leftAi.model} onChange={(e) => updateAi('left', { model: e.target.value })} /></label>
          <label className="toggle-row"><input type="checkbox" checked={leftAi.useStreaming} onChange={(e) => updateAi('left', { useStreaming: e.target.checked })} />流式输出</label>
          <button className="secondary" onClick={() => askOne('left')} disabled={leftAi.loading || !question.trim()}>{leftAi.loading ? <Loader2 className="spin" size={18} /> : <Send size={18} />}发送到 DeepSeek</button>
          {leftAi.error ? <div className="error">{leftAi.error}</div> : null}
          <div className="answer-box"><div className="answer-label">DeepSeek 回答</div><div className="answer">{leftAi.answer || '这里显示 DeepSeek 的回答。'}</div></div>
          <pre className="mini-result">{leftAi.result || 'DeepSeek 的结果会显示在这里。'}</pre>
        </section>

        <section className="panel middle-panel">
          <div className="section-title"><Send size={18} /> 提示词和问题</div>
          <div className="mode-selector">
            <button className={`mode-btn ${promptMode === 'general' ? 'active' : ''}`} onClick={() => { setPromptMode('general'); setSystemPrompt(modePresets.general.prompt); setQuestion(modePresets.general.question); }}>通用助手</button>
            <button className={`mode-btn ${promptMode === 'weather' ? 'active' : ''}`} onClick={() => { setPromptMode('weather'); setSystemPrompt(modePresets.weather.prompt); setQuestion(modePresets.weather.question); }}>天气专家</button>
            <button className={`mode-btn ${promptMode === 'football' ? 'active' : ''}`} onClick={() => { setPromptMode('football'); setSystemPrompt(modePresets.football.prompt); setQuestion(modePresets.football.question); }}>足球分析师</button>
          </div>
          <label>系统提示词<textarea rows={5} value={systemPrompt} onChange={(e) => setSystemPrompt(e.target.value)} /></label>
          <label>用户问题<textarea rows={7} value={question} onChange={(e) => setQuestion(e.target.value)} placeholder="例如：北京今天天气怎么样？" /></label>
          <div className="mode-selector" style={{ marginTop: 12 }}>
            <button className={`mode-btn ${leftAi.useStreaming ? 'active' : ''}`} onClick={() => { updateAi('left', { useStreaming: true }); updateAi('right', { useStreaming: true }); }}>
              流式输出
            </button>
            <button className={`mode-btn ${!leftAi.useStreaming ? 'active' : ''}`} onClick={() => { updateAi('left', { useStreaming: false }); updateAi('right', { useStreaming: false }); }}>
              阻塞输出
            </button>
          </div>
          <button className="primary" onClick={askBoth} disabled={!question.trim()}><Send size={18} /> 发送给两个 AI</button>

          <button className="primary" onClick={compareEmbedding}
            disabled={embedding.loading || !leftAi.answer || !rightAi.answer}
            style={{ marginTop: 12 }}>
            {embedding.loading ? <Loader2 className="spin" size={18} /> : <Sparkles size={18} />}
            对比两个 AI 回答的语义相似度
          </button>
          {embedding.similarity !== null && (
            <div style={{ marginTop: 12, padding: 12, background: '#f0f9ff', borderRadius: 8, fontSize: 14 }}>
              <div>语义相似度：<strong>{(embedding.similarity * 100).toFixed(1)}%</strong></div>
              <div style={{ marginTop: 4 }}>{embedding.isSimilar ? '✅ 两个模型看法一致' : '⚠️ 两个模型看法不同'}</div>
            </div>
          )}
          {embedding.error && <div className="error">{embedding.error}</div>}
        </section>

        <section className="panel ai-panel">
          <div className="section-title"><Bot size={18} /> 千问</div>
          <label>后端地址<input value={rightAi.serverUrl} onChange={(e) => updateAi('right', { serverUrl: e.target.value })} /></label>
          <label>大模型 Base URL<input value={rightAi.llmBaseUrl} onChange={(e) => updateAi('right', { llmBaseUrl: e.target.value })} /></label>
          <label>模型<input value={rightAi.model} onChange={(e) => updateAi('right', { model: e.target.value })} /></label>
          <label className="toggle-row"><input type="checkbox" checked={rightAi.useStreaming} onChange={(e) => updateAi('right', { useStreaming: e.target.checked })} />流式输出</label>
          <button className="secondary" onClick={() => askOne('right')} disabled={rightAi.loading || !question.trim()}>{rightAi.loading ? <Loader2 className="spin" size={18} /> : <Send size={18} />}发送到 千问</button>
          {rightAi.error ? <div className="error">{rightAi.error}</div> : null}
          <div className="answer-box"><div className="answer-label">千问 回答</div><div className="answer">{rightAi.answer || '这里显示 千问 的回答。'}</div></div>
          <pre className="mini-result">{rightAi.result || '千问 的结果会显示在这里。'}</pre>
        </section>
      </section>

      <section className="panel">
        <div className="section-title"><Clock size={18} /> 调用记录 <button onClick={fetchRecords} style={{ marginLeft: 12, fontSize: 13, padding: '4px 12px' }}>刷新</button></div>
        {records.length === 0 ? (
          <p style={{ color: '#999' }}>点击刷新获取调用记录。</p>
        ) : (
          <div style={{ overflowX: 'auto' }}>
            <table style={{ width: '100%', borderCollapse: 'collapse', fontSize: 13 }}>
              <thead>
                <tr style={{ background: '#f5f5f5', textAlign: 'left' }}>
                  <th style={{ padding: 8 }}>时间</th>
                  <th style={{ padding: 8 }}>模型</th>
                  <th style={{ padding: 8 }}>问题摘要</th>
                  <th style={{ padding: 8 }}>回答摘要</th>
                  <th style={{ padding: 8 }}>Token</th>
                  <th style={{ padding: 8 }}>耗时</th>
                  <th style={{ padding: 8 }}>方式</th>
                  <th style={{ padding: 8 }}>状态</th>
                </tr>
              </thead>
              <tbody>
                {records.map((r) => (
                  <tr key={r.id} style={{ borderBottom: '1px solid #eee' }}>
                    <td style={{ padding: 8, whiteSpace: 'nowrap' }}>{new Date(r.createdAt).toLocaleString('zh-CN')}</td>
                    <td style={{ padding: 8 }}>{r.model}</td>
                    <td style={{ padding: 8, maxWidth: 150, overflow: 'hidden', textOverflow: 'ellipsis', whiteSpace: 'nowrap' }}>{r.userQuestion}</td>
                    <td style={{ padding: 8, maxWidth: 200, overflow: 'hidden', textOverflow: 'ellipsis', whiteSpace: 'nowrap' }}>{r.output}</td>
                    <td style={{ padding: 8, whiteSpace: 'nowrap' }}>{r.totalTokens > 0 ? `${r.promptTokens}+${r.completionTokens}=${r.totalTokens}` : '-'}</td>
                    <td style={{ padding: 8, whiteSpace: 'nowrap' }}>{r.durationMs}ms</td>
                    <td style={{ padding: 8 }}>{r.streaming ? '流式' : '阻塞'}</td>
                    <td style={{ padding: 8 }}>{r.success ? '✅' : `❌ ${r.errorMessage}`}</td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
        )}
      </section>

      <section className="panel">
        <div className="section-title"><Wrench size={18} /> 可调用工具</div>
        <div className="tools">
          {tools.map((tool) => (
            <article className="tool-card" key={tool.name}>
              <div className="tool-name">{tool.title}</div>
              <code>{tool.name}</code>
              <p>{tool.description}</p>
              <button onClick={() => useExample(tool.example)}>使用示例：{tool.example}</button>
            </article>
          ))}
        </div>
      </section>
      </div>
    </main>
  );
}
