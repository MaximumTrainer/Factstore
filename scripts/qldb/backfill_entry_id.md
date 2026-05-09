# QLDB backfill: populate `entryId` on pre-migration documents

## Background

Documents inserted before the schema migration did not include an explicit `entryId`
field. The `AwsQldbLedger` previously projected `h.metadata.id AS metaId` from the
QLDB history function and used it as a fallback when `entryId` was absent.

This script backfills `entryId` so the fallback can be removed.

## Step 1 — Verify how many documents need backfilling

Run this PartiQL query via the AWS console or CLI:

```partiql
SELECT COUNT(*) AS missing FROM FactLedger WHERE entryId IS MISSING
```

If the result is 0, no backfill is needed — skip to Step 4.

## Step 2 — Backfill `entryId` from QLDB metadata

QLDB does not allow accessing `metadata.id` directly inside a DML UPDATE. The
backfill must be done in two PartiQL round-trips per document:

**2a.** Retrieve documents missing `entryId` together with their stable QLDB document
ID (the most recent history entry for each factId):

```partiql
SELECT h.metadata.id AS qldbDocId, h.data.factId
FROM history(FactLedger) AS h
WHERE h.data.entryId IS MISSING
  AND h.metadata.operation != 'DELETE'
ORDER BY h.metadata.version DESC
```

**2b.** For each row returned, run one UPDATE to stamp `entryId`:

```partiql
UPDATE FactLedger AS f
SET f.entryId = '<qldbDocId>'
WHERE f.factId = '<factId>'
  AND f.entryId IS MISSING
```

### Automated backfill runner

A ready-made Kotlin utility is provided at
`scripts/qldb/QldbEntryIdBackfill.kt` (runnable as a standalone `main`).
Configure `QLDB_LEDGER`, `AWS_REGION` as environment variables before running.

## Step 3 — Verify backfill is complete

```partiql
SELECT COUNT(*) AS missing FROM FactLedger WHERE entryId IS MISSING
```

The result **must be 0** before deploying the code that removes the fallback.

## Step 4 — Deploy the code change

The `metaId` fallback has been removed from `AwsQldbLedger.resolveEntryId` and the
`metaId` projection from the `getHistory` SELECT query (see issue #112). Deploy the
updated backend only after Steps 1–3 are complete in production.
