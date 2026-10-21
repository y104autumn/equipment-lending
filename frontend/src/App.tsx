import { useCallback, useEffect, useState } from 'react'
import type { FormEvent } from 'react'
import './App.css'

// 画面の構成・文言は Figma（fileKey KiodFcrn3lGjjFD0spnPsN・node 2:2「備品貸出申請（1画面）」）の
// get_design_context から起こした。API は要件定義書 §7。

type Status = 'REQUESTED' | 'APPROVED' | 'REJECTED' | 'LENT' | 'RETURNED'
type ActionPath = 'approve' | 'reject' | 'lend' | 'return'

type Equipment = { equipmentId: number; name: string }
type LendingRequest = {
  requestId: number
  applicantName: string
  equipmentId: number
  equipmentName: string
  startDate: string
  dueDate: string
  reason: string | null
  status: Status
  actions: ActionPath[]
}
type ApiError = { status: number; message: string; details: string[] }

const STATUS_LABEL: Record<Status, string> = {
  REQUESTED: '申請中',
  APPROVED: '承認済',
  LENT: '貸出中',
  RETURNED: '返却済',
  REJECTED: '却下',
}
// WHY: 絞り込みの並びは Figma 2:33 の順（すべて→申請中→承認済→貸出中→返却済→却下）。
const FILTERS: (Status | null)[] = [null, 'REQUESTED', 'APPROVED', 'LENT', 'RETURNED', 'REJECTED']

const ACTION_LABEL: Record<ActionPath, string> = {
  approve: '承認',
  reject: '却下',
  lend: '貸出',
  return: '返却',
}

/** 応答が 2xx でなければ ApiError を投げる。画面はサーバーのメッセージをそのまま出す。 */
async function call<T>(url: string, init?: RequestInit): Promise<T> {
  const res = await fetch(url, init)
  if (!res.ok) {
    const body: ApiError = await res
      .json()
      .catch(() => ({ status: res.status, message: `通信に失敗しました（${res.status}）`, details: [] }))
    throw body
  }
  return res.json() as Promise<T>
}

/** "2026-11-01" → "11/01"（Figma の一覧の期間の書き方）。 */
function monthDay(iso: string): string {
  const [, m, d] = iso.split('-')
  return `${m}/${d}`
}

function App() {
  const [equipment, setEquipment] = useState<Equipment[]>([])
  const [requests, setRequests] = useState<LendingRequest[]>([])
  const [filter, setFilter] = useState<Status | null>(null)
  const [listError, setListError] = useState<string | null>(null)
  const [busyId, setBusyId] = useState<number | null>(null)

  const [applicantName, setApplicantName] = useState('')
  const [equipmentId, setEquipmentId] = useState('')
  const [startDate, setStartDate] = useState('')
  const [dueDate, setDueDate] = useState('')
  const [reason, setReason] = useState('')
  const [formErrors, setFormErrors] = useState<string[]>([])
  const [submitting, setSubmitting] = useState(false)

  const loadRequests = useCallback(async () => {
    try {
      const q = filter ? `?status=${filter}` : ''
      setRequests(await call<LendingRequest[]>(`/api/requests${q}`))
      setListError(null)
    } catch (e) {
      setListError((e as ApiError).message ?? '一覧を読み込めませんでした')
    }
  }, [filter])

  useEffect(() => {
    call<Equipment[]>('/api/equipment')
      .then(setEquipment)
      .catch((e: ApiError) => setListError(e.message ?? '備品を読み込めませんでした'))
  }, [])

  useEffect(() => {
    // WHY: 絞り込みを変えた時と初回に、サーバーから一覧を取り直す（状態の正はサーバー）。
    loadRequests()
  }, [loadRequests])

  async function submit(e: FormEvent) {
    e.preventDefault()
    setSubmitting(true)
    try {
      await call<LendingRequest>('/api/requests', {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({
          applicantName,
          // WHY: 未選択・未入力は null で送り、必須の判定はサーバーの検査（400）に任せる＝決まりを 1 箇所にする。
          equipmentId: equipmentId ? Number(equipmentId) : null,
          startDate: startDate || null,
          dueDate: dueDate || null,
          reason: reason || null,
        }),
      })
      setFormErrors([])
      setApplicantName('')
      setEquipmentId('')
      setStartDate('')
      setDueDate('')
      setReason('')
      await loadRequests()
    } catch (err) {
      const apiErr = err as ApiError
      setFormErrors(apiErr.details?.length ? apiErr.details : [apiErr.message])
    } finally {
      setSubmitting(false)
    }
  }

  async function act(id: number, action: ActionPath) {
    setBusyId(id)
    try {
      await call<LendingRequest>(`/api/requests/${id}/${action}`, { method: 'POST' })
      await loadRequests()
    } catch (err) {
      // WHY: 409（他の人が先に操作した）でも一覧を取り直して、今の状態のボタンに直す。
      setListError((err as ApiError).message)
      await loadRequests().catch(() => {})
    } finally {
      setBusyId(null)
    }
  }

  return (
    <>
      <header className="header">
        <h1>備品貸出申請</h1>
      </header>

      <main className="body">
        <form className="form" onSubmit={submit} noValidate>
          <h2>申請フォーム</h2>

          <div className="field">
            <label htmlFor="applicantName">申請者名 *</label>
            <input
              id="applicantName"
              value={applicantName}
              maxLength={50}
              placeholder="山田 太郎"
              onChange={(e) => setApplicantName(e.target.value)}
            />
          </div>

          <div className="field">
            <label htmlFor="equipmentId">備品 *</label>
            <select id="equipmentId" value={equipmentId} onChange={(e) => setEquipmentId(e.target.value)}>
              <option value="">選択してください</option>
              {equipment.map((eq) => (
                <option key={eq.equipmentId} value={eq.equipmentId}>
                  {eq.name}
                </option>
              ))}
            </select>
          </div>

          <div className="period">
            <div className="field">
              <label htmlFor="startDate">開始日 *</label>
              <input id="startDate" type="date" value={startDate} onChange={(e) => setStartDate(e.target.value)} />
            </div>
            <div className="field">
              <label htmlFor="dueDate">返却予定日 *</label>
              <input id="dueDate" type="date" value={dueDate} onChange={(e) => setDueDate(e.target.value)} />
            </div>
          </div>

          <div className="field">
            <label htmlFor="reason">理由（任意・200 字まで）</label>
            <textarea
              id="reason"
              value={reason}
              maxLength={200}
              placeholder="出張先での顧客向けデモに使うため"
              onChange={(e) => setReason(e.target.value)}
            />
          </div>

          <p className="note">* は必須。返却予定日は開始日以降。</p>

          {formErrors.length > 0 && (
            <ul className="errors" role="alert">
              {formErrors.map((m) => (
                <li key={m}>{m}</li>
              ))}
            </ul>
          )}

          <button type="submit" className="btn btn-submit" disabled={submitting}>
            申請する
          </button>
        </form>

        <section className="list-area">
          <div className="filter">
            <span className="filter-label">状態で絞る:</span>
            {FILTERS.map((s) => (
              <button
                key={s ?? 'ALL'}
                type="button"
                className="chip"
                aria-pressed={filter === s}
                onClick={() => setFilter(s)}
              >
                {s ? STATUS_LABEL[s] : 'すべて'}
              </button>
            ))}
          </div>

          <div className="table" role="table">
            <div className="row row-head" role="row">
              <div className="col col-id">申請番号</div>
              <div className="col col-name">申請者</div>
              <div className="col col-equipment">備品</div>
              <div className="col col-period">期間</div>
              <div className="col col-status">状態</div>
              <div className="col col-actions">操作</div>
            </div>
            {requests.length === 0 && <div className="row row-empty">申請はありません</div>}
            {requests.map((r) => (
              <div className="row" role="row" key={r.requestId}>
                <div className="col col-id">#{r.requestId}</div>
                <div className="col col-name" title={r.applicantName}>
                  {r.applicantName}
                </div>
                <div className="col col-equipment" title={r.equipmentName}>
                  {r.equipmentName}
                </div>
                <div className="col col-period">
                  {monthDay(r.startDate)}〜{monthDay(r.dueDate)}
                </div>
                <div className="col col-status">
                  <span className={`badge badge-${r.status}`}>{STATUS_LABEL[r.status]}</span>
                </div>
                <div className="col col-actions">
                  {/* WHY: 押せる操作はサーバーが actions で返す（状態の表の正は Status.java 1 箇所）。画面は判定しない。 */}
                  {r.actions.length === 0 && <span className="none">—</span>}
                  {r.actions.map((a) => (
                    <button
                      key={a}
                      type="button"
                      className={`btn btn-${a}`}
                      disabled={busyId === r.requestId}
                      onClick={() => act(r.requestId, a)}
                    >
                      {ACTION_LABEL[a]}
                    </button>
                  ))}
                </div>
              </div>
            ))}
          </div>

          {listError && (
            <p className="message" role="alert">
              {listError}
            </p>
          )}
          <p className="note">操作ボタンは状態に応じて表示（申請中→承認／却下、承認済→貸出、貸出中→返却）。</p>
        </section>
      </main>
    </>
  )
}

export default App
