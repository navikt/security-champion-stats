export type Member = { id: string; fullname: string, points: number, email: string }
export type Point  = { label: string; value: number }

async function json<T>(res: Response): Promise<T> {
  if (!res.ok) throw new Error(`${res.status} ${res.statusText}`)
  return res.json() as Promise<T>
}

export const Api = {
  getMembers: () => fetch('/api/members').then(res => json<Member[]>(res)),
  addMember:  (id: string, fullname: string) =>
    fetch('/api/members', {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({ id, fullname })
    }).then(res => json<{message:string}>(res)),
  addPoints:  (id: string, points: number) =>
    fetch(`/api/points?id=${encodeURIComponent(id)}`, {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({ points })
    }).then(res => json<{message:string}>(res)),
  deleteMember: (id: string) =>
    fetch(`/api/members/${encodeURIComponent(id)}`, { method: 'DELETE' })
      .then(res => res.text())
}