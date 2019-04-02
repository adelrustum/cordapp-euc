package com.template.contracts

import com.template.states.EUCState
import net.corda.core.contracts.CommandData
import net.corda.core.contracts.Contract
import net.corda.core.contracts.requireSingleCommand
import net.corda.core.contracts.requireThat
import net.corda.core.transactions.LedgerTransaction

// ************
// * Contract *
// ************
class EUCContract : Contract {
    companion object {
        // Used to identify our contract when building a transaction.
        const val ID = "com.template.contracts.EUCContract"
    }

    // A transaction is valid if the verify() function of the contract of all the transaction's input and output states
    // does not throw an exception.
    override fun verify(tx: LedgerTransaction) {
        // Verification logic goes here.
        // Require a single "Issue" command per transaction.
        val command = tx.commands.requireSingleCommand<Commands.Issue>()

        requireThat {
            // Constraints on the shape of the transaction.
            "No inputs should be consumed when issuing an EUC." using (tx.inputs.isEmpty())
            "There should be one output state of type EUCState." using (tx.outputs.size == 1)

            // EUC-specific constraints.
            val output = tx.outputsOfType<EUCState>().single()
            "The importer and exporter cannot be the same entity." using (output.importer != output.exporter)
            "The end user and exporter cannot be the same entity." using (output.endUser != output.exporter)
            "The importer and end user cannot be from different countries." using
                    (output.importer.nameOrNull()!!.country == output.endUser.nameOrNull()!!.country)
            "The importer and exporter cannot be from the same country." using (output.importer.nameOrNull()!!.country
                    != output.exporter.nameOrNull()!!.country)
            "The item quantity must be greater than zero." using (output.itemQty > 0)
            "The total EUC value must be greater than zero." using (output.totalValue > 0)

            // Constraints on the signers.
            val expectedSigners = listOf(output.importer.owningKey, output.exporter.owningKey,
                    output.endUser.owningKey)
            "The importer, exporter, and end user must all be signers." using
                    (command.signers.containsAll(expectedSigners))
        }
    }

    // Used to indicate the transaction's intent.
    interface Commands : CommandData {
        // Issue a new EUC.
        class Issue : Commands
    }
}