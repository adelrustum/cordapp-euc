package com.template.contracts

import com.template.states.EUCState
import net.corda.core.identity.CordaX500Name
import net.corda.testing.core.TestIdentity
import net.corda.testing.node.MockServices
import net.corda.testing.node.ledger
import org.junit.Test

class ContractTests {
    private val ledgerServices = MockServices()
    private val importer = TestIdentity(CordaX500Name("Importer", "Brussels", "BE")).party
    private val exporter = TestIdentity(CordaX500Name("Exporter", "Stockholm", "SE")).party
    // exporter2 is same country as importer.
    private val exporter2 = TestIdentity(CordaX500Name("Exporter2", "Brussels", "BE")).party
    private val endUser = TestIdentity(CordaX500Name("End User", "Brussels", "BE")).party
    private val itemDesc = "AK-47"
    private val itemQty = 100
    private val totalValue = 100000.0
    private val eucState = EUCState(importer, exporter, endUser, itemDesc, itemQty, totalValue)

    @Test
    fun `No inputs should be consumed when issuing an EUC`() {
        ledgerServices.ledger {
            transaction {
                input(EUCContract.ID, eucState)
                command(eucState.participants.map { it.owningKey }, EUCContract.Commands.Issue())
                `fails with`("No inputs should be consumed when issuing an EUC.")
            }
        }
    }

    @Test
    fun `There should be one output state of type EUCState`() {
        ledgerServices.ledger {
            transaction {
                output(EUCContract.ID, eucState)
                command(eucState.participants.map { it.owningKey }, EUCContract.Commands.Issue())
                verifies()
            }
        }
    }

    @Test
    fun `The importer and exporter cannot be the same entity`() {
        ledgerServices.ledger {
            transaction {
                output(EUCContract.ID, EUCState(importer, importer, endUser, itemDesc, itemQty, totalValue))
                command(eucState.participants.map { it.owningKey }, EUCContract.Commands.Issue())
                `fails with`("The importer and exporter cannot be the same entity.")
            }
        }
    }

    @Test
    fun `The importer and exporter cannot be from the same country`() {
        ledgerServices.ledger {
            transaction {
                output(EUCContract.ID, EUCState(importer, exporter2, endUser, itemDesc, itemQty, totalValue))
                command(eucState.participants.map { it.owningKey }, EUCContract.Commands.Issue())
                `fails with`("The importer and exporter cannot be from the same country.")
            }
        }
    }

    @Test
    fun `The importer, exporter, and end user must all be signers`() {
        ledgerServices.ledger {
            transaction {
                output(EUCContract.ID, EUCState(importer, exporter, endUser, itemDesc, itemQty, totalValue))
                // We are missing the end user signature here
                command(listOf(importer, exporter).map { it.owningKey }, EUCContract.Commands.Issue())
                `fails with`("The importer, exporter, and end user must all be signers.")
            }
        }
    }
}