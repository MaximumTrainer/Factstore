/**
 * Standalone backfill utility: stamps `entryId` on FactLedger documents that
 * predate the schema migration (issue #112).
 *
 * Usage:
 *   QLDB_LEDGER=my-ledger AWS_REGION=eu-west-1 kotlinc -script QldbEntryIdBackfill.kt
 *
 * Or compile and run with Gradle after adding QLDB driver to the classpath.
 *
 * The script is idempotent — documents that already have `entryId` are skipped.
 */

import com.amazon.ion.IonText
import com.amazon.ion.IonStruct
import com.amazon.ion.system.IonSystemBuilder
import software.amazon.awssdk.regions.Region
import software.amazon.awssdk.services.qldbsession.QldbSessionClient
import software.amazon.qldb.QldbDriver
import software.amazon.qldb.RetryPolicy

fun main() {
    val ledgerName = System.getenv("QLDB_LEDGER")
        ?: error("QLDB_LEDGER environment variable not set")
    val region = System.getenv("AWS_REGION") ?: "eu-west-1"

    val ionSystem = IonSystemBuilder.standard().build()
    val driver = QldbDriver.builder()
        .ledger(ledgerName)
        .sessionClientBuilder(QldbSessionClient.builder().region(Region.of(region)))
        .transactionRetryPolicy(RetryPolicy.maxRetries(3))
        .build()

    // Step 1: count documents missing entryId
    var missingCount = 0L
    driver.execute { txn ->
        val result = txn.execute("SELECT COUNT(*) AS missing FROM FactLedger WHERE entryId IS MISSING")
        result.forEach { doc ->
            val struct = doc as? IonStruct ?: return@forEach
            missingCount = (struct.get("missing") as? com.amazon.ion.IonInt)?.longValue() ?: 0L
        }
    }
    println("Documents missing entryId: $missingCount")
    if (missingCount == 0L) {
        println("Nothing to backfill.")
        return
    }

    // Step 2: collect (qldbDocId, factId) pairs from history
    data class Row(val qldbDocId: String, val factId: String)
    val rows = mutableListOf<Row>()

    driver.execute { txn ->
        val result = txn.execute(
            "SELECT h.metadata.id AS qldbDocId, h.data.factId " +
            "FROM history(FactLedger) AS h " +
            "WHERE h.data.entryId IS MISSING " +
            "  AND h.metadata.operation != 'DELETE'"
        )
        result.forEach { doc ->
            val struct = doc as? IonStruct ?: return@forEach
            val docId = (struct.get("qldbDocId") as? IonText)?.stringValue() ?: return@forEach
            val factId = (struct.get("factId") as? IonText)?.stringValue() ?: return@forEach
            rows.add(Row(docId, factId))
        }
    }

    // Deduplicate: keep only the first (oldest) row per factId
    val deduped = rows.groupBy { it.factId }.mapValues { (_, v) -> v.first() }.values

    println("Backfilling ${deduped.size} document(s)...")
    var updated = 0
    deduped.forEach { (docId, factId) ->
        driver.execute { txn ->
            txn.execute(
                "UPDATE FactLedger AS f SET f.entryId = ? WHERE f.factId = ? AND f.entryId IS MISSING",
                ionSystem.newString(docId),
                ionSystem.newString(factId)
            )
        }
        updated++
        if (updated % 100 == 0) println("  ... $updated / ${deduped.size}")
    }

    // Step 3: verify
    var remaining = 0L
    driver.execute { txn ->
        val result = txn.execute("SELECT COUNT(*) AS missing FROM FactLedger WHERE entryId IS MISSING")
        result.forEach { doc ->
            val struct = doc as? IonStruct ?: return@forEach
            remaining = (struct.get("missing") as? com.amazon.ion.IonInt)?.longValue() ?: 0L
        }
    }
    println("Backfill complete. Documents still missing entryId: $remaining")
    check(remaining == 0L) { "Backfill incomplete — $remaining documents still missing entryId" }
}
