package invokers;

import net.corda.demo.node.exchange.GenericServiceResponse;

public interface HttpInvoker {
    GenericServiceResponse executeRequest(String URL);
}
