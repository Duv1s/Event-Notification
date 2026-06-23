# 0007 — SSRF guard on every delivery

## Context
The webhook destination URL is client-controlled, which is a classic Server-Side Request Forgery
vector (it could point at internal services or the cloud metadata endpoint).

## Decision
`SsrfGuard` runs before **every** delivery: it allows only `https` on port 443, resolves DNS, and
rejects the target if any resolved address is loopback / any-local / link-local (incl.
`169.254.169.254`) / site-local (private) / multicast / IPv6 unique-local. IPv4-mapped IPv6 is
normalised by the JVM and caught by the same rules.

## Alternatives considered
- **Validate only at subscription-creation time:** DNS can rebind afterwards, so the URL must be
  re-validated on each send.

## Consequences
A residual TOCTOU remains (the IPs are validated just before the call, not pinned into the
connection); this is accepted for the in-memory demo and documented. Production would pin the
validated IP. Validation at subscription creation is still worthwhile and described in the design.