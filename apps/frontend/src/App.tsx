import React, { useEffect, useState } from 'react'
import { Api, Member } from './api'
import { MembersTable } from './components/MembersTable'
import '../src/style/terminal.css'

export function App() {
  const [members, setMembers] = useState<Member[]>([])

  const reload = async () => {
    const [ms] = await Promise.all([Api.getMembers()])
    setMembers(ms)
  }

  useEffect(() => { void reload() }, [])

  const addMember = async () => {
    const id = `id-${Date.now()}`
    const fullname = 'New Person'
    const email = "email"
    await Api.addMember(id, fullname)
    await reload()
  }

  const addPoints = async () => {
    if (members.length === 0) return
    await Api.addPoints(members[0].email, 3)
    await reload()
  }

  const deleteMember = async (id: string) => {
    await Api.deleteMember(id)
    await reload()
  }

  return (
    <div className="container">
      <div className="toolbar">
        <button className="btn" onClick={reload}>Reload data</button>
        <button className="btn" onClick={addMember}>Add member</button>
        <button className="btn" onClick={addPoints}>Add points</button>
      </div>
      <div className="dashboard">
          <section className="card">
              <div className="card-header">
              <div className="card-body">
                  <MembersTable members={members} onDelete={deleteMember} />
              </div>
              </div>
          </section>
      </div>
    </div>
  )
}