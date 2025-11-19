import React from 'react'
import type { Member } from '../api'
import '../style/terminal.css';

export function MembersTable({
  members, onDelete,
}: { members: Member[]; onDelete: (id: string) => void }) {
    return (
        <table className="table" role="table" aria-label="Members">
            <thead>
            <tr>
                <th style={{width: 80}}>ID</th>
                <th>Fullname</th>
                <th className="right" style={{width: 120}}>Points</th>
                <th style={{width: 120}}>Actions</th>
            </tr>
            </thead>
            <tbody>
            {members.map((m) => (
                <tr key={m.id}>
                    <td>{m.id}</td>
                    <td>{m.fullname}</td>
                    <td className="td-num">{m.points.toLocaleString()}</td>
                    <td>
                        <button className="btn btn-danger" onClick={() => onDelete(m.id)}>
                            Delete
                        </button>
                    </td>
                </tr>
            ))}
            {members.length === 0 && (
                <tr>
                    <td colSpan={4} className="mono-muted">
                        No members
                    </td>
                </tr>
            )}
            </tbody>
        </table>
    );
}