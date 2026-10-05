import {
	AdminProgramParticipant,
	Me,
	ProgramParticipant,
	ProgramParticipantSummary,
	SCData,
	SecurityEvent,
} from "../../utils/Variables";

export const Apies = {
	getAdminParticipants: async (): Promise<AdminProgramParticipant[] | null> => {
		const res = await fetch("/api/admin/participants");
		if (!res.ok) {
			console.error("Failed to fetch program participants, status: ", res.status);
			return null;
		}
		return res.json();
	},
	getMembers: async (): Promise<ProgramParticipantSummary[]> => {
		const res = await fetch("/api/members");
		if (!res.ok) {
			console.warn("Failed to fetch members, status: ", res.status);
			return [];
		}
		return await res.json();
	},
	addMember: async (email: string) => {
		const res = await fetch("/api/admin/member", {
			method: "POST",
			headers: { "Content-Type": "application/json" },
			body: JSON.stringify({ email: email }),
		});
		if (!res.ok)
			console.error("Failed to add member, with status code ", res.status);
		return res.status;
	},
	deleteMember: async (id: string) => {
		const res = await fetch(`/api/admin/member/${encodeURIComponent(id)}`, {
			method: "DELETE",
		});
		if (!res.ok)
			console.error("Failed to delete member, with status code ", res.status);
		return res.status;
	},
	updateParticipantStatus: async (id: string, active: boolean): Promise<number> => {
		const res = await fetch(
			`/api/admin/participants/${encodeURIComponent(id)}/status`,
			{
				method: "PUT",
				headers: { "Content-Type": "application/json" },
				body: JSON.stringify({ active }),
			},
		);
		if (!res.ok) {
			console.error("Failed to update participant status, status: ", res.status);
		}
		return res.status;
	},
	deleteParticipant: async (id: string, reason: string): Promise<number> => {
		const res = await fetch(
			`/api/admin/participants/${encodeURIComponent(id)}`,
			{
				method: "DELETE",
				headers: { "Content-Type": "application/json" },
				body: JSON.stringify({ confirmed: true, reason }),
			},
		);
		if (!res.ok) {
			console.error("Failed to delete participant, status: ", res.status);
		}
		return res.status;
	},
	addPoints: async (email: string, amount: number) => {
		const res = await fetch("/api/admin/points", {
			method: "POST",
			headers: { "Content-Type": "application/json" },
			body: JSON.stringify({ email: email, amount: amount }),
		});
		if (!res.ok)
			console.error("Failed to add points, with status code: ", res.status);
		return res.status;
	},
	joinProgram: async (): Promise<boolean> => {
		const res = await fetch("/api/enroll", {
			method: "POST",
			headers: { "Content-Type": "application/json" },
		});
		if (!res.ok) {
			console.error("Failed to join the program, with status code: ", res.status);
			return false;
		}
		return true;
	},
	validatePerson: async (): Promise<Me> => {
		const res = await fetch("/api/validate");
		if (!res.ok) {
			console.error("Failed to validate user, status: ", res.status);
			return { username: "", isAdmin: false, isParticipant: false, isActive: false };
		}

		return await res.json();
	},
	getSCData: async (): Promise<SCData[]> => {
		const res = await fetch("/api/admin/dashboard/members");
		if (!res.ok) {
			console.error("Failed to fetch SCData, with status: ", res.status);
			return [];
		}
		return res.json();
	},
	fetchMembership: async (): Promise<ProgramParticipant | null> => {
		const res = await fetch("/api/membership", {
			method: "GET",
			headers: { "Content-Type": "application/json" },
		});
		if (!res.ok) {
			console.error("Failed to fetch membership, with status: ", res.status);
			return null;
		}
		return res.json();
	},
	fetchEvents: async (): Promise<SecurityEvent[]> => {
		const res = await fetch("/api/events");
		if (!res.ok) {
			console.error("Failed to fetch events, with status: ", res.status);
			return [];
		}
		return res.json();
	},
	createEvent: async (event: SecurityEvent): Promise<Number> => {
		const response = await fetch("/api/events", {
			method: "POST",
			headers: { "Content-Type": "application/json" },
			body: JSON.stringify(event),
		});

		if (!response.ok) {
			console.error("Failed to create event, with status: ", response.status);
			return response.status;
		}

		return response.status;
	},
};
