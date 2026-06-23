# AI-assisted development

This project was built with AI assistance — deliberately and transparently. I'm being explicit about
*how*, because directing and reviewing AI well is itself an engineering skill, and it's the part I
want this repository to demonstrate. The AI executed; I architected and reviewed.

## Approach: a generator–critic loop under human review

I ran two AI roles, not one:

- A **generator** that wrote code, tests, and documentation.
- A **critic** — a second AI agent acting as architect/reviewer — that challenged every change against
  explicit acceptance criteria, with my own review on top.

These ran as two separate Claude sessions that I orchestrated, relaying decisions and review feedback
between them.

The work advanced in **9 phases** (setup → domain → application → persistence → web → webhook →
security → observability → docs). Each phase ended at a hard review gate: nothing moved forward with a
red build, a failing ArchUnit boundary rule, or an open design question.

Before writing any code, I ran a **structured decision interview** — about ten themes (domain model,
scope, state machine, retries, webhook contract, API, auth, security, observability, platform) — where
I made each architectural call and recorded the reasoning. Those decisions became the ADRs in
[`docs/adr/`](adr/).

## Architecture decisions I made (not the AI)

- **`Notification` is the only aggregate.** This service *delivers* events; it doesn't *own* them
  (separate bounded context), so there is no `Event` entity.
- **`Subscription` is first-class, behind a port** (its CRUD is out of scope); it holds the
  destination URL and a **per-subscription HMAC secret**.
- **Replay only from `FAILED`**, made deterministic by persisting the originating `subscription_id`
  and resolving the *current* subscription at replay time.
- **At-least-once delivery** with an **HMAC signature over `timestamp.body`** and an idempotency key
  (`event_id`) so receivers can deduplicate.
- **An SSRF guard on every delivery** — the webhook URL is attacker-influenced, so it's the headline
  risk for this system.
- **Tenant isolation enforced at the type level** — the client-facing repository port has no
  `findById` without a `ClientId`, so a cross-tenant read is structurally impossible.
- **Hexagonal architecture enforced by an ArchUnit test**, and **deliberately minimal observability**
  (I cut an over-engineered first version back to what the brief actually needs).

## Reviews that changed the code

The review gates weren't a formality — they caught real issues:

- **Anti-BOLA leak:** an internal `findForDelivery(id)` (no tenant) sat on the client-facing port,
  weakening the "structurally impossible cross-tenant read" guarantee. → segregated into a narrow,
  delivery-only port.
- **Replay race:** the atomic `FAILED → DELIVERING` claim couldn't be expressed with a plain `save`.
  → added an atomic `claimForReplay` (compare-and-set via `ConcurrentHashMap.compute`), verified with
  a 16-thread concurrency test.
- **Wrong status code:** an out-of-order date filter returned `500` instead of `400`. → added an
  `IllegalArgumentException → 400` handler.
- **Security depth:** confirmed production TLS certificate verification is never disabled (the
  trust-all path is test-only), and flagged gaps in the SSRF block-list.
- **Observability:** circuit-breaker metrics weren't published until an explicit Micrometer binder was
  added.
- **CI:** CodeQL reported "no source seen" because Gradle's incremental compile was cached. → forced a
  clean compile so the analyzer could trace the sources.
- **Stack honesty:** the bleeding-edge runtime was justified with an ADR and its dependency
  compatibility verified, instead of asserted.

## What the AI did vs. what I directed

| AI | Me |
|----|----|
| Wrote code, tests, scaffolding and doc drafts per phase | Designed the generator–critic workflow |
| Proposed designs and surfaced trade-offs | Made every architectural decision, with reasoning |
| Iterated until each phase's gates were green | Ran the review gates and caught the issues above |
| | Controlled scope, security posture, and Git hygiene |

## A note on this document

The architecture decisions, the per-phase reviews, and this write-up were produced in a separate
**architect/critic session** that drove the build through prepared prompts. The **build session** it
directed — the prompts received and the code produced — is captured, sanitized, in
[`docs/ai-session-transcript.md`](ai-session-transcript.md) (so the "generator" side of the loop is
inspectable; the architect side is summarized here). Including it is the point: the value is in how
the work was directed and reviewed.
