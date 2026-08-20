# How this submission was produced

Claude Code wrote essentially all of the code here — the entity and mapper changes, the service
layer, the Liquibase changesets, the tests, the Dockerfile and the CI workflow, as well as
`CHANGES.md`. My role was to direct the work rather than type it: I chose to review the existing
codebase before touching any of the tasks, set the order things were done in, made the calls
where more than one answer was defensible (the DTO naming, backfilling the sample data so it
satisfies the "one or more products" rule, leaving the `"order"` table name alone), and kept the
scope from creeping past what the brief actually asked for.

Two things are worth saying plainly. There were several points where Claude Code corrected a
misconception of mine — most usefully around JPA fetch strategies, where an approach I suggested
would not have had the effect I expected — and I changed position on the evidence rather than
pressing it. And I insisted throughout that claims be measured rather than asserted: the defects
described in `CHANGES.md` were reproduced against a real PostgreSQL instance, the performance
figures come from counting the statements the database actually received, and where a change
turned out not to help at the sample data size, the document says so rather than dressing it up.
