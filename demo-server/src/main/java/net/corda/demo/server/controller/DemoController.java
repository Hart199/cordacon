package net.corda.demo.server.controller;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import net.corda.client.rpc.RPCException;
import net.corda.core.identity.CordaX500Name;
import net.corda.core.messaging.FlowHandle;
import net.corda.core.node.NodeInfo;
import net.corda.core.node.services.Vault;
import net.corda.core.node.services.VaultQueryException;
import net.corda.core.node.services.vault.PageSpecification;
import net.corda.core.node.services.vault.QueryCriteria;
import net.corda.core.transactions.SignedTransaction;
import net.corda.demo.node.contract.HelloContract;
import net.corda.demo.node.flow.SayHelloFlow;
import net.corda.demo.node.state.HelloState;
import net.corda.demo.server.bo.HelloBO;
import net.corda.demo.server.constant.ServerConstant;
import net.corda.demo.server.helper.ControllerHelper;
import net.corda.demo.server.rpc.RPConnector;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static java.util.stream.Collectors.toList;
import static net.corda.core.node.services.vault.QueryCriteriaUtils.DEFAULT_PAGE_SIZE;

@RestController
@RequestMapping("/demo")
public class DemoController {
    private static final Logger logger = LoggerFactory.getLogger(DemoController.class);
    private final List<String> serviceNames = ImmutableList.of(ServerConstant.NOTARY, ServerConstant.ORACLE);
    @Autowired
    private RPConnector connector;

    @GetMapping("/hello")
    public String sayHello() {
        return "Hello! This Api Works! ";
    }

    @GetMapping("/allNodes")
    public Map<String, List<CordaX500Name>> getAllNodes() {
        List<NodeInfo> nodeInfoSnapshot = connector.getRPCops().networkMapSnapshot();
        return ImmutableMap.of("allnodes",
                nodeInfoSnapshot.stream().map(node -> node.getLegalIdentities().get(0).getName()).filter(
                        name -> !name.equals(connector.getRPCops().nodeInfo().getLegalIdentities().get(0).getName())
                                && !serviceNames.contains(name.getOrganisation()))
                        .collect(toList()));
    }

    @GetMapping("me")
    public Map<String, CordaX500Name> getMyIdentity() {
        return ImmutableMap.of(ServerConstant.ME, connector.getRPCops().nodeInfo().getLegalIdentities().get(0).getName());
    }

    @GetMapping("/getAllHello/{pageNumber}")
    public ResponseEntity getAllAccount(@PathVariable("pageNumber") Integer pageNumber) {
        List<HelloBO> states = new ArrayList<>();
        QueryCriteria linearCriteria = new QueryCriteria.LinearStateQueryCriteria(null, null, null, Vault.StateStatus.ALL);
        PageSpecification pageSpecification = new PageSpecification(pageNumber, DEFAULT_PAGE_SIZE);
        try {
            Vault.Page<HelloState> requestStates = connector.getRPCops().vaultQueryByWithPagingSpec(HelloState.class, linearCriteria, pageSpecification);
            for (int i = 0; i < requestStates.getStates().size(); i++) {
                logger.info(requestStates.getStates().get(i).getState().getData().toString());
                states.add(ControllerHelper.convertStateToBO(requestStates.getStates().get(i).getState().getData(), requestStates.getStatesMetadata().get(i).getStatus()));
            }
            if (states.size() > 0)
                return ResponseEntity.ok(states);
            else
                return ResponseEntity.status(HttpStatus.BAD_REQUEST).body("Please specify a page number above 0.");
        } catch (RPCException e) {
            logger.error("Error saying hello!: " + e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(e.getMessage());
        }
    }

    // now read the response from the file and say hello to appropriate parties
    @GetMapping("/sayHelloTo")
    public ResponseEntity sayHelloTo() {
        FlowHandle<SignedTransaction> signedTransactionFlowHandle = connector.getRPCops().startFlowDynamic(SayHelloFlow.class, new HelloContract.Commands.Create());
        try {
            SignedTransaction signedTransaction = signedTransactionFlowHandle.getReturnValue().get();
            if (signedTransaction != null) {
                logger.info("Hello Sent with secureHash: " + signedTransaction.getId());
                return ResponseEntity.ok(signedTransaction.getId());
            } else {
                logger.info("No available Counter Party found! from list");
                return ResponseEntity.status(HttpStatus.BAD_REQUEST).body("No available Counter Party found! from list");
            }
        } catch (Exception e) {
            logger.error("Error saying hello!: " + e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(e.getMessage());
        }
    }
}