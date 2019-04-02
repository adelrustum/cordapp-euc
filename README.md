# End User Certificates

This CorDapp is built on top of the Kotlin template that is provided by **Corda** team.  
You can find the full article about the purpose of this application here:

# Pre-Requisites

See https://docs.corda.net/getting-set-up.html.

# Usage

## Running tests inside IntelliJ

On the top right side of **InelliJ** select **Unit Tests** gradle task then click the start button.

## Running the nodes

Below is a summary of the instructions to run the nodes from an **Ubuntu** terminal (for other operating systems; see https://docs.corda.net/tutorial-cordapp.html#running-the-example-cordapp):  
1. Browse to the CordApp folder: `cd cordapp-euc`  
2. Deploy the nodes: `./gradlew deployNodes`  
3. Run the nodes: `./build/nodes/runnodes`  
**Do not click or change focus until all five additional terminals have opened, or some nodes may fail to start; wait until they fully load before proceeding to the next section.**

You can add more nodes by modifying the `deployNodes` task inside `build.gradle` file. 

## Interacting with the nodes

### Shell

When started via the command line; each node will display an interactive shell:  
1. Navigate to the **Importer** terminal and start the **Issue EUC** flow with the below command (to paste it in the terminal; click the mouse wheel button):  
`flow start IssueFlow$Initiator importer: "O=Importer,L=Brussels,C=BE", exporter: "O=Exporter,L=Stockholm,C=SE", endUser: "O=End User,L=Brussels,C=BE", itemDesc: "AK-47", itemQty: 1000, totalValue: 100000.0`
2. Navigate to the **Exporter** terminal and run the below command; you should see **one** new **EUC** state:  
`run vaultQuery contractStateType: com.template.states.EUCState`  
3. Navigate to the **MOD** terminal and run the below command; you should see **zero** states, because by design **MOD** is only an observer that verifies and signs the transaction:  
`run vaultQuery contractStateType: com.template.states.EUCState`  
4. Navigate to the **Exporter** terminal and start the **Issue EUC** flow with the below command (to paste it in the terminal; click the mouse wheel button):  
`flow start IssueFlow$Initiator importer: "O=Importer,L=Brussels,C=BE", exporter: "O=Exporter,L=Stockholm,C=SE", endUser: "O=End User,L=Brussels,C=BE", itemDesc: "AK-47", itemQty: 1000, totalValue: 100000.0`  
You should get an error: `Only the importer can initiate the flow.`  
5. Type `bye` to shutdown a certain terminal.