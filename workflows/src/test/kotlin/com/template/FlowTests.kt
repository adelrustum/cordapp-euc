package com.template

import com.template.flows.IssueFlow
import com.template.states.EUCState
import net.corda.core.flows.FlowException
import net.corda.core.identity.CordaX500Name
import net.corda.core.node.services.queryBy
import net.corda.core.utilities.getOrThrow
import net.corda.testing.core.singleIdentity
import net.corda.testing.node.MockNetwork
import net.corda.testing.node.MockNetworkParameters
import net.corda.testing.node.StartedMockNode
import net.corda.testing.node.TestCordapp
import org.junit.After
import org.junit.Before
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class FlowTests {
    private val network = MockNetwork(MockNetworkParameters(cordappsForAllNodes = listOf(
        TestCordapp.findCordapp("com.template.contracts"),
        TestCordapp.findCordapp("com.template.flows")
    )))
    private lateinit var importer: StartedMockNode
    private lateinit var exporter: StartedMockNode
    private lateinit var endUser: StartedMockNode
    private lateinit var mod: StartedMockNode
    private val itemDesc = "AK-47"
    private val itemQty = 100
    private val totalValue = 100000.0
    private lateinit var eucState: EUCState
    // Data for embargo
    private lateinit var embImporter: StartedMockNode
    private lateinit var embEndUser: StartedMockNode
    private lateinit var embMOD: StartedMockNode
    private lateinit var embEUCState: EUCState


    @Before
    fun setup() {
        importer = network.createPartyNode(CordaX500Name("Importer", "Brussels", "BE"))
        exporter = network.createPartyNode(CordaX500Name("Exporter", "Stockholm", "SE"))
        endUser = network.createPartyNode(CordaX500Name("End User", "Brussels", "BE"))
        mod = network.createPartyNode(CordaX500Name("MOD", "Brussels", "BE"))
        eucState = EUCState(importer.info.singleIdentity(), exporter.info.singleIdentity(), endUser.info.singleIdentity()
                , itemDesc, itemQty, totalValue)

        // Data for embargo
        embImporter = network.createPartyNode(CordaX500Name("emb Importer", "Mogadishu", "SO"))
        embEndUser = network.createPartyNode(CordaX500Name("emb End User", "Mogadishu", "SO"))
        embMOD = network.createPartyNode(CordaX500Name("MOD", "Mogadishu", "SO"))
        embEUCState = EUCState(embImporter.info.singleIdentity(), exporter.info.singleIdentity(),
                embEndUser.info.singleIdentity(), itemDesc, itemQty, totalValue)

        listOf(exporter, endUser, mod, embImporter, embEndUser).forEach {
            it.registerInitiatedFlow(IssueFlow.Responder::class.java)
        }
    }

    @After
    fun tearDown() = network.stopNodes()

    @Test
    fun `ISSUE flow can only be initiated by the importer`() {
        val flow = IssueFlow.Initiator(eucState)
        // Here, the exporter is initiating the flow
        val future = exporter.startFlow(flow)
        network.runNetwork()
        // The flow should throw the following exception: net.corda.core.flows.FlowException
        // Failed requirement: Only the importer can initiate the flow.
        assertFailsWith<FlowException> { future.getOrThrow() }
    }

    @Test
    fun `ISSUE transaction is signed by the importer, exporter, end user, and MOD`() {
        val flow = IssueFlow.Initiator(eucState)
        val future = importer.startFlow(flow)
        network.runNetwork()
        val signedTx = future.getOrThrow()
        signedTx.verifyRequiredSignatures()
    }

    @Test
    fun `ISSUE flow records a transaction in all signers' transaction storage`() {
        val flow = IssueFlow.Initiator(eucState)
        val future = importer.startFlow(flow)
        network.runNetwork()
        val signedTx = future.getOrThrow()

        for (node in listOf(importer, exporter, endUser, mod)) {
            assertEquals(signedTx, node.services.validatedTransactions.getTransaction(signedTx.id))
        }
    }

    @Test
    fun `ISSUE flow recorded transaction has no inputs and one EUC output`() {
        val flow = IssueFlow.Initiator(eucState)
        val future = importer.startFlow(flow)
        network.runNetwork()
        val signedTx = future.getOrThrow()

        // We check the recorded transaction in all signers' transaction storage
        for (node in listOf(importer, exporter, endUser, mod)) {
            val recordedTx = node.services.validatedTransactions.getTransaction(signedTx.id)
            assert(recordedTx!!.tx.inputs.isEmpty())
            val eucOutputs = recordedTx.tx.outputs.filter { it.data is EUCState }
            require(eucOutputs.size == 1)
            require(eucOutputs.single().data == eucState)
        }
    }

    @Test
    fun `ISSUE flow records the correct EUC state in participants' vaults`() {
        val flow = IssueFlow.Initiator(eucState)
        val future = importer.startFlow(flow)
        network.runNetwork()
        future.getOrThrow()

        importer.transaction {
            val myEUCStates = importer.services.vaultService.queryBy<EUCState>().states
            assertEquals(myEUCStates.size, 1)
            val recordedState = myEUCStates.single().state.data
            assertEquals(recordedState.importer, importer.info.singleIdentity())
            assertEquals(recordedState.exporter, exporter.info.singleIdentity())
            assertEquals(recordedState.endUser, endUser.info.singleIdentity())
            assertEquals(recordedState.itemDesc, itemDesc)
            assertEquals(recordedState.itemQty, itemQty)
            assertEquals(recordedState.totalValue, totalValue)
        }

        exporter.transaction {
            val myEUCStates = exporter.services.vaultService.queryBy<EUCState>().states
            assertEquals(myEUCStates.size, 1)
            val recordedState = myEUCStates.single().state.data
            assertEquals(recordedState.importer, importer.info.singleIdentity())
            assertEquals(recordedState.exporter, exporter.info.singleIdentity())
            assertEquals(recordedState.endUser, endUser.info.singleIdentity())
            assertEquals(recordedState.itemDesc, itemDesc)
            assertEquals(recordedState.itemQty, itemQty)
            assertEquals(recordedState.totalValue, totalValue)
        }

        endUser.transaction {
            val myEUCStates = endUser.services.vaultService.queryBy<EUCState>().states
            assertEquals(myEUCStates.size, 1)
            val recordedState = myEUCStates.single().state.data
            assertEquals(recordedState.importer, importer.info.singleIdentity())
            assertEquals(recordedState.exporter, exporter.info.singleIdentity())
            assertEquals(recordedState.endUser, endUser.info.singleIdentity())
            assertEquals(recordedState.itemDesc, itemDesc)
            assertEquals(recordedState.itemQty, itemQty)
            assertEquals(recordedState.totalValue, totalValue)
        }

        // Note that in our implementation "Ministry of Defence" wasn't part of the participants list
        // (i.e. it wasn't part of the nodes that the finalization flow takes in consideration when saving
        // the state in their vaults); so this means "Ministry of Defence" was only an observer that was required
        // to sign the "Issue" command as a sign of validation and approval.
        // So if we uncomment the below section; the test will fail, because MOD's vault doesn't have this state.

        /*mod.transaction {
            val myEUCStates = mod.services.vaultService.queryBy<EUCState>().states
            assertEquals(myEUCStates.size, 1)
            val recordedState = myEUCStates.single().state.data
            assertEquals(recordedState.importer, importer.info.singleIdentity())
            assertEquals(recordedState.exporter, exporter.info.singleIdentity())
            assertEquals(recordedState.endUser, endUser.info.singleIdentity())
            assertEquals(recordedState.itemDesc, itemDesc)
            assertEquals(recordedState.itemQty, itemQty)
            assertEquals(recordedState.totalValue, totalValue)
        }*/
    }

    @Test
    fun `ISSUE flow cannot be approved for embargoed importers or end users`() {
        val flow = IssueFlow.Initiator(embEUCState)
        val future = embImporter.startFlow(flow)
        network.runNetwork()
        // The flow should throw the following exception: net.corda.core.flows.FlowException
        // Failed requirement: The importer and end user cannot be on the embargoed countries list.
        assertFailsWith<FlowException> { future.getOrThrow() }
    }

}