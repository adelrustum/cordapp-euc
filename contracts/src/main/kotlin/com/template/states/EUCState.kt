package com.template.states

import com.template.contracts.EUCContract
import net.corda.core.contracts.BelongsToContract
import net.corda.core.contracts.ContractState
import net.corda.core.identity.AbstractParty

// *********
// * State *
// *********
@BelongsToContract(EUCContract::class)
data class EUCState(val importer: AbstractParty,
                    val exporter: AbstractParty,
                    val endUser: AbstractParty,
                    val itemDesc: String,
                    val itemQty: Int,
                    val totalValue: Double,
                    // Participants is a list of all the parties who should
                    // be notified of the creation or consumption of this state.
                    override val participants: List<AbstractParty> =
                            listOf(importer, exporter, endUser)) : ContractState