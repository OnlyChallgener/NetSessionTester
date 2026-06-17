import React, { useEffect, useMemo, useState } from 'react'
import { createRoot } from 'react-dom/client'
import './styles.css'

const DEFAULT_SETTINGS = {
  host: 'www.baidu.com',
  port: '80',
  mode: 'IPV4_THEN_IPV6',
  batchSize: '100',
  intervalMs: '500',
  timeoutMs: '3000',
  successLimit: '65535',
  failureLimit: '200',
  keepConnections: true,
  maskPrivacy: false
}

const emptyStats = protocol => ({
  protocol,
  phase: '待测试',
  addresses: [],
  active: 0,
  failure: 0,
  total: 0,
  added: 0,
  cps: 0,
  errors: {}
})

function callNative(name, payload) {
  const bridge = window.NativeBridge
  if (!bridge || typeof bridge[name] !== 'function') return null
  try {
    return payload === undefined ? bridge[name]() : bridge[name](JSON.stringify(payload))
  } catch (e) {
    console.error(e)
    return null
  }
}

function App() {
  const [tab, setTab] = useState('settings')
  const [settings, setSettings] = useState(DEFAULT_SETTINGS)
  const [status, setStatus] = useState('待测试')
  const [isAdding, setIsAdding] = useState(false)
  const [resolveResult, setResolveResult] = useState({ ipv4: [], ipv6: [] })
  const [ipv4Stats, setIpv4Stats] = useState(emptyStats('IPv4'))
  const [ipv6Stats, setIpv6Stats] = useState(emptyStats('IPv6'))
  const [logs, setLogs] = useState([])
  const [history, setHistory] = useState([])

  useEffect(() => {
    window.NativeEvents = {
      receive(event, payload) {
        if (event === 'state') applyState(payload)
        if (event === 'resolve') setResolveResult(payload)
        if (event === 'log') setLogs(prev => [...prev.slice(-80), payload])
        if (event === 'toast') toast(payload?.message || '')
      }
    }

    const raw = window.NativeBridge?.getInitialState?.()
    if (raw) applyState(JSON.parse(raw))
  }, [])

  useEffect(() => {
    callNative('saveSettings', settings)
  }, [settings])

  function applyState(data) {
    if (!data) return
    if (data.settings) setSettings(s => ({ ...s, ...data.settings }))
    setStatus(data.status || '待测试')
    setIsAdding(!!data.isAdding)
    if (data.ipv4Stats) setIpv4Stats(data.ipv4Stats)
    if (data.ipv6Stats) setIpv6Stats(data.ipv6Stats)
    if (data.logs) setLogs(data.logs)
    if (data.history) setHistory(data.history)
  }

  function toast(msg) {
    if (!msg) return
    const el = document.createElement('div')
    el.className = 'toast'
    el.textContent = msg
    document.body.appendChild(el)
    setTimeout(() => el.remove(), 1600)
  }

  function updateSetting(key, value) {
    setSettings(prev => ({ ...prev, [key]: value }))
  }

  function start() {
    setTab('test')
    callNative('startTest', settings)
  }

  const showIpv4 = settings.mode !== 'IPV6_ONLY'
  const showIpv6 = settings.mode !== 'IPV4_ONLY'

  return (
    <div className="app">
      <main className="page">
        {tab === 'settings' && (
          <SettingsPage
            settings={settings}
            updateSetting={updateSetting}
            resolveResult={resolveResult}
            onResolve={() => callNative('resolve', { host: settings.host })}
            onStart={start}
          />
        )}

        {tab === 'test' && (
          <TestPage
            settings={settings}
            status={status}
            isAdding={isAdding}
            ipv4Stats={ipv4Stats}
            ipv6Stats={ipv6Stats}
            showIpv4={showIpv4}
            showIpv6={showIpv6}
            logs={logs}
            onStart={start}
            onStop={() => callNative('stopAdding')}
            onRelease={() => callNative('release')}
            onExport={() => callNative('exportData')}
            onMore={() => setTab('logs')}
          />
        )}

        {tab === 'logs' && (
          <LogsPage
            logs={logs}
            history={history}
            onExport={() => callNative('exportData')}
            onClear={() => callNative('clearLogsHistory')}
          />
        )}
      </main>

      <nav className="bottom-nav">
        <button className={tab === 'settings' ? 'active' : ''} onClick={() => setTab('settings')}><span>⚙</span><b>设置</b></button>
        <button className={tab === 'test' ? 'active' : ''} onClick={() => setTab('test')}><span>▶</span><b>测试</b></button>
        <button className={tab === 'logs' ? 'active' : ''} onClick={() => setTab('logs')}><span>▣</span><b>日志</b></button>
      </nav>
    </div>
  )
}

function SettingsPage({ settings, updateSetting, resolveResult, onResolve, onStart }) {
  return (
    <>
      <Header title="宽带会话测试器" subtitle="TCP 会话保持 · IPv4 / IPv6 分别测试" />

      <Card>
        <SectionTitle icon="◎" title="目标设置" />
        <Label>地址</Label>
        <Input value={settings.host} onChange={v => updateSetting('host', v)} placeholder="www.baidu.com" />
        <Label>端口</Label>
        <div className="row">
          <Input className="grow" value={settings.port} onChange={v => updateSetting('port', onlyNumber(v))} placeholder="80" />
          <button className="outline-btn small" onClick={onResolve}>解析</button>
        </div>
        <SwitchRow title="打码" sub="隐藏 IP / 公网地址显示和导出" checked={settings.maskPrivacy} onChange={v => updateSetting('maskPrivacy', v)} />
        {(resolveResult?.ipv4?.length || resolveResult?.ipv6?.length || resolveResult?.error) ? (
          <div className="resolve-box">
            <p>IPv4：{formatList(resolveResult.ipv4, settings.maskPrivacy)}</p>
            <p>IPv6：{formatList(resolveResult.ipv6, settings.maskPrivacy)}</p>
            {resolveResult.error && <p className="red">错误：{resolveResult.error}</p>}
          </div>
        ) : null}
      </Card>

      <Card>
        <SectionTitle icon="∿" title="测试模式" />
        <div className="segmented">
          <button className={settings.mode === 'IPV4_ONLY' ? 'on' : ''} onClick={() => updateSetting('mode', 'IPV4_ONLY')}>仅 IPv4</button>
          <button className={settings.mode === 'IPV6_ONLY' ? 'on' : ''} onClick={() => updateSetting('mode', 'IPV6_ONLY')}>仅 IPv6</button>
          <button className={settings.mode === 'IPV4_THEN_IPV6' ? 'on' : ''} onClick={() => updateSetting('mode', 'IPV4_THEN_IPV6')}>分别测试</button>
        </div>
      </Card>

      <Card>
        <SectionTitle icon="≡" title="会话参数" />
        <div className="grid2">
          <Param label="新增" value={settings.batchSize} onChange={v => updateSetting('batchSize', onlyNumber(v))} />
          <Param label="间隔ms" value={settings.intervalMs} onChange={v => updateSetting('intervalMs', onlyNumber(v))} />
          <Param label="超时ms" value={settings.timeoutMs} onChange={v => updateSetting('timeoutMs', onlyNumber(v))} />
          <Param label="失败停" value={settings.failureLimit} onChange={v => updateSetting('failureLimit', onlyNumber(v))} />
        </div>
        <Param label="目标会话" value={settings.successLimit} onChange={v => updateSetting('successLimit', onlyNumber(v))} />
        <SwitchRow title="完成后保持连接" checked={settings.keepConnections} onChange={v => updateSetting('keepConnections', v)} />
        <p className="hint">默认：新增 100，间隔 500ms</p>
      </Card>

      <div className="action-row">
        <button className="primary-btn" onClick={onStart}>保存并测试</button>
        <button className="outline-btn" onClick={() => {
          Object.entries(DEFAULT_SETTINGS).forEach(([k, v]) => updateSetting(k, v))
        }}>恢复默认</button>
      </div>
    </>
  )
}

function TestPage({ settings, status, isAdding, ipv4Stats, ipv6Stats, showIpv4, showIpv6, logs, onStart, onStop, onRelease, onExport, onMore }) {
  const errors = mergeErrors([ipv4Stats, ipv6Stats])
  return (
    <>
      <Header title="宽带会话测试器" />
      <div className="chips">
        <Chip>{modeText(settings.mode)}</Chip>
        <Chip green>{isAdding ? '● 运行中' : status}</Chip>
        <Chip>◎ 目标 {settings.successLimit}</Chip>
      </div>

      <Card>
        <SectionTitle icon="∿" title="测试控制" />
        <p className={isAdding ? 'status green' : 'status'}>{isAdding ? '● 正在运行 · 已连接目标' : `状态：${status}`}</p>
        <div className="grid2">
          <button className="primary-btn" disabled={isAdding} onClick={onStart}>开始</button>
          <button className="danger-btn" disabled={!isAdding} onClick={onStop}>停止</button>
          <button className="outline-btn" onClick={onRelease}>释放</button>
          <button className="outline-btn" onClick={onExport}>导出</button>
        </div>
      </Card>

      {showIpv4 && <SessionCard title="IPv4 会话" stats={ipv4Stats} mask={settings.maskPrivacy} />}
      {showIpv6 && <SessionCard title="IPv6 会话" stats={ipv6Stats} mask={settings.maskPrivacy} />}

      {Object.keys(errors).length > 0 && (
        <Card>
          <div className="card-head">
            <SectionTitle icon="!" title="失败原因" />
            <button className="text-btn" onClick={onMore}>更多</button>
          </div>
          <div className="reason-row">
            {Object.entries(errors).slice(0, 6).map(([k, v]) => <span className="reason" key={k}>{k} {v}</span>)}
          </div>
        </Card>
      )}

      <RecentLogs logs={logs} onMore={onMore} />
    </>
  )
}

function LogsPage({ logs, history, onExport, onClear }) {
  return (
    <>
      <div className="top-line">
        <h1>日志与历史</h1>
        <button className="outline-btn compact" onClick={onClear}>清理</button>
      </div>
      <div className="chips">
        <Chip>全部</Chip>
        <Chip>运行日志</Chip>
        <Chip>检测历史</Chip>
        <Chip>失败原因</Chip>
      </div>

      <Card>
        <div className="grid2">
          <button className="outline-btn" onClick={onExport}>导出日志</button>
          <button className="outline-btn" onClick={onClear}>清理日志</button>
        </div>
      </Card>

      <RecentLogs logs={logs} />

      <h2 className="section-big">检测历史</h2>
      {history?.length ? history.map((item, idx) => <HistoryCard item={item} key={idx} />) : <Card><p>暂无历史记录</p></Card>}
    </>
  )
}

function SessionCard({ title, stats, mask }) {
  return (
    <Card>
      <div className="card-head">
        <SectionTitle icon="▮" title={title} />
        <span className="blue">{stats.phase || '待测试'}</span>
      </div>
      <div className="grid2">
        <Metric label="活动" value={stats.active || 0} blue />
        <Metric label="失败" value={stats.failure || 0} red />
      </div>
      <div className="grid3">
        <Metric label="总计" value={stats.total || 0} />
        <Metric label="新增" value={stats.added || 0} />
        <Metric label="CPS" value={`${stats.cps || 0}/s`} blue />
      </div>
      {stats.addresses?.length ? <p className="addr">地址：{formatList(stats.addresses, mask)}</p> : null}
    </Card>
  )
}

function RecentLogs({ logs, onMore }) {
  const shown = (logs || []).slice(-5)
  return (
    <Card>
      <div className="card-head">
        <SectionTitle icon="□" title="最近日志" />
        {onMore && <button className="text-btn" onClick={onMore}>更多</button>}
      </div>
      {shown.length ? shown.map((l, i) => <LogLine line={l} key={i} />) : <p className="hint">暂无日志</p>}
    </Card>
  )
}

function LogLine({ line }) {
  return (
    <div className="log-line">
      <span className="time">{line.timeText}</span>
      <span className={`tag ${line.level?.toLowerCase()}`}>{tagText(line.level)}</span>
      <span className="log-text">{line.text}</span>
    </div>
  )
}

function HistoryCard({ item }) {
  const stats = item.ipv4Stats?.total ? item.ipv4Stats : item.ipv6Stats
  const protocol = item.mode === 'IPV4_THEN_IPV6' ? '分别测试' : item.mode === 'IPV6_ONLY' ? 'IPv6 完成' : 'IPv4 完成'
  return (
    <Card>
      <div className="card-head">
        <span className="muted">{item.timeText}</span>
        <Chip green>{protocol}</Chip>
      </div>
      <div className="grid2">
        <Mini label="目标" value={item.host} />
        <Mini label="端口" value={item.port} />
        <Mini label="活动" value={stats?.active || 0} />
        <Mini label="失败" value={stats?.failure || 0} />
        <Mini label="总计" value={stats?.total || 0} />
        <Mini label="CPS" value={`${stats?.cps || 0}/s`} />
      </div>
      {stats?.errors && <div className="reason-row">{Object.entries(stats.errors).slice(0, 4).map(([k, v]) => <span className="reason" key={k}>{k} {v}</span>)}</div>}
    </Card>
  )
}

function Header({ title, subtitle }) {
  return <header className="header"><h1>{title}</h1>{subtitle && <p>{subtitle}</p>}</header>
}

function Card({ children }) {
  return <section className="card">{children}</section>
}

function SectionTitle({ icon, title }) {
  return <div className="section-title"><span>{icon}</span><h2>{title}</h2></div>
}

function Label({ children }) { return <label className="label">{children}</label> }

function Input({ value, onChange, placeholder, className = '' }) {
  return <input className={`input ${className}`} value={value} placeholder={placeholder} onChange={e => onChange(e.target.value)} />
}

function Param({ label, value, onChange }) {
  return <label className="param"><span>{label}</span><input value={value} onChange={e => onChange(e.target.value)} /></label>
}

function SwitchRow({ title, sub, checked, onChange }) {
  return <div className="switch-row"><div><b>{title}</b>{sub && <p>{sub}</p>}</div><input type="checkbox" checked={!!checked} onChange={e => onChange(e.target.checked)} /></div>
}

function Metric({ label, value, blue, red }) {
  return <div className="metric"><span>{label}</span><b className={red ? 'red' : blue ? 'blue' : ''}>{value}</b></div>
}

function Mini({ label, value }) {
  return <div className="mini"><span>{label}</span><b>{value}</b></div>
}

function Chip({ children, green }) {
  return <span className={green ? 'chip green-chip' : 'chip'}>{children}</span>
}

function onlyNumber(v) { return String(v || '').replace(/\D/g, '') }
function tagText(level) { return ({ STAT: '统计', SUCCESS: '成功', WARN: '告警', ERROR: '错误', INFO: '信息' }[level] || '信息') }
function modeText(mode) { return mode === 'IPV4_ONLY' ? 'IPv4' : mode === 'IPV6_ONLY' ? 'IPv6' : '分别测试' }
function formatList(list, mask) {
  if (!list?.length) return '未解析'
  return list.map(x => mask ? maskIp(x) : x).join(' / ')
}
function maskIp(ip) {
  if (!ip) return ''
  if (ip.includes(':')) {
    const parts = ip.split(':').filter(Boolean)
    return parts.length >= 2 ? `${parts[0]}:${parts[1]}:****` : '****'
  }
  const p = ip.split('.')
  return p.length === 4 ? `${p[0]}.${p[1]}.*.*` : '****'
}
function mergeErrors(statsList) {
  const out = {}
  statsList.forEach(s => {
    Object.entries(s?.errors || {}).forEach(([k, v]) => out[k] = (out[k] || 0) + v)
  })
  return out
}

createRoot(document.getElementById('root')).render(<App />)
