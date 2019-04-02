package com.template.flows

import co.paralleluniverse.fibers.Suspendable
import com.template.contracts.EUCContract
import com.template.states.EUCState
import net.corda.core.contracts.Command
import net.corda.core.contracts.requireThat
import net.corda.core.flows.*
import net.corda.core.identity.AbstractParty
import net.corda.core.identity.CordaX500Name
import net.corda.core.identity.Party
import net.corda.core.transactions.SignedTransaction
import net.corda.core.transactions.TransactionBuilder
import net.corda.core.utilities.ProgressTracker
import net.corda.core.utilities.ProgressTracker.Step

// *********
// * Flows *
// *********
object IssueFlow {
    @InitiatingFlow
    @StartableByRPC
    class Initiator(val eucState: EUCState) : FlowLogic<SignedTransaction>() {

        constructor(importer: AbstractParty,
                    exporter: AbstractParty,
                    endUser: AbstractParty,
                    itemDesc: String,
                    itemQty: Int,
                    totalValue: Double):
                this(EUCState(importer, exporter, endUser, itemDesc, itemQty, totalValue))

        /**
         * The progress tracker checkpoints each stage of the flow and outputs the specified messages when each
         * checkpoint is reached in the code. See the 'progressTracker.currentStep' expressions within the call() function.
         */
        companion object {
            object GENERATING_TRANSACTION : Step("Generating transaction based on a new EUC.")
            object VERIFYING_TRANSACTION : Step("Verifying contract constraints.")
            object SIGNING_TRANSACTION : Step("Signing transaction with our private key.")
            object GATHERING_SIGS : Step("Gathering the counterparty's signature.") {
                override fun childProgressTracker() = CollectSignaturesFlow.tracker()
            }

            object FINALISING_TRANSACTION : Step("Obtaining notary signature and recording transaction.") {
                override fun childProgressTracker() = FinalityFlow.tracker()
            }

            fun tracker() = ProgressTracker(
                    GENERATING_TRANSACTION,
                    VERIFYING_TRANSACTION,
                    SIGNING_TRANSACTION,
                    GATHERING_SIGS,
                    FINALISING_TRANSACTION
            )
        }

        override val progressTracker = tracker()

        @Suspendable
        override fun call(): SignedTransaction {
            // The importer should initiate the flow, so "ourIdentity" (i.e. the initiator) should be the importer.
            if (eucState.importer != ourIdentity)
                throw FlowException("Only the importer can initiate the flow.")

            // Obtain a reference to the notary we want to use.
            val notary = serviceHub.networkMapCache.notaryIdentities[0]

            // Stage 1.
            progressTracker.currentStep = GENERATING_TRANSACTION
            // Generate an unsigned transaction.
            // We will fetch the "Ministry of Defence" peer assuming that its name is "MOD" and that it has the same
            // city and country as the importer.
            val mod = CordaX500Name(organisation = "MOD", locality = ourIdentity.name.locality,
                    country = ourIdentity.name.country)
            val modParty = serviceHub.networkMapCache.getPeerByLegalName(mod)
            // We defined the list of required signers as the participants of this new states (i.e. importer, exporter,
            // end user) and the "Ministry of Defence" of the importing country.
            val requiredSigners = eucState.participants.plus(modParty)

            // Here we identify that this transaction is of type "Issue" and that it requires the earlier mentioned
            // signatures.
            val txCommand = Command(EUCContract.Commands.Issue(),
                    requiredSigners.map { it!!.owningKey })
            val txBuilder = TransactionBuilder(notary)
                    .addOutputState(eucState, EUCContract.ID)
                    .addCommand(txCommand)

            // Stage 2.
            progressTracker.currentStep = VERIFYING_TRANSACTION
            // Verify that the transaction is valid: This will call the EUC contract validation
            txBuilder.verify(serviceHub)

            // Stage 3.
            progressTracker.currentStep = SIGNING_TRANSACTION
            // Sign the transaction with our identity.
            val partSignedTx = serviceHub.signInitialTransaction(txBuilder)

            // Stage 4.
            progressTracker.currentStep = GATHERING_SIGS
            // Send the state to the counter parties, and receive it back with their signatures.
            val otherParties = requiredSigners.minus(ourIdentity)
            val otherPartiesSessions = otherParties.map { initiateFlow(it as Party) }
            val fullySignedTx = subFlow(CollectSignaturesFlow(partSignedTx, otherPartiesSessions,
                    GATHERING_SIGS.childProgressTracker()))

            // Stage 5.
            progressTracker.currentStep = FINALISING_TRANSACTION
            // Notarise and record the transaction in all parties' vaults.
            return subFlow(FinalityFlow(fullySignedTx, otherPartiesSessions, FINALISING_TRANSACTION.childProgressTracker()))
        }
    }

    @InitiatedBy(Initiator::class)
    class Responder(val counterpartySession: FlowSession) : FlowLogic<SignedTransaction>() {
        @Suspendable
        override fun call(): SignedTransaction {
            val signTransactionFlow = object : SignTransactionFlow(counterpartySession) {
                override fun checkTransaction(stx: SignedTransaction) = requireThat {
                    val output = stx.tx.outputs.single().data
                    "This must be an EUC transaction." using (output is EUCState)
                    val eucState = output as EUCState
                    // The below list mimics fetching a list of embargoed countries from a database.
                    val embargoedCountries = listOf("SO", "SD", "LY")
                    val eucCountries = listOf(eucState.importer, eucState.endUser).
                            map { it.nameOrNull()!!.country }
                    if (embargoedCountries.intersect(eucCountries).isNotEmpty())
                        throw FlowException("The importer and end user cannot be on the embargoed countries list.")
                }
            }
            val txId = subFlow(signTransactionFlow).id

            return subFlow(ReceiveFinalityFlow(counterpartySession, expectedTxId = txId))
        }
    }
}