export function hackerAlias(name: string | null, username: string): string {
	const firstName = (name || username.split("@")[0] || "OPERATIVE")
		.trim()
		.split(/\s+/)[0];
	const leet = firstName.replace(
		/[aeio]/gi,
		(letter) =>
			({ a: "4", e: "3", i: "1", o: "0" })[letter.toLowerCase()] ?? letter,
	);
	return `0x${leet.toUpperCase()}`;
}
