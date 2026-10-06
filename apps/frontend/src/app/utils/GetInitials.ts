export function getInitials(fullname: string): string {
	const [lastName, firstNames] = fullname.split(",");
	const orderedName = firstNames === undefined ? fullname : `${firstNames} ${lastName}`;
	const parts = orderedName.trim().split(/\s+/).filter(Boolean);
	const first = parts[0]?.[0] ?? "";
	const last = parts.length > 1 ? parts[parts.length - 1][0] : "";
	return (first + last).toUpperCase();
}
