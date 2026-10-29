package tech.kayys.numou.support.api;

import org.junit.jupiter.api.Test;
import tech.kayys.numou.support.application.api.command.CreatePortalTicketCommand;
import tech.kayys.numou.support.application.api.command.CreateTicketCommand;

import java.lang.reflect.Method;
import java.util.Arrays;

import static org.assertj.core.api.Assertions.assertThat;

class TicketResourceTest {

    @Test
    void publicCreateEndpointAcceptsOnlyPortalCommand() throws NoSuchMethodException {
        Method endpoint = TicketResource.class.getMethod("createTicket", CreatePortalTicketCommand.class);

        assertThat(endpoint).isNotNull();
        assertThat(Arrays.stream(TicketResource.class.getMethods())
                .filter(method -> method.getName().equals("createTicket"))
                .map(method -> method.getParameterTypes()[0]))
                .containsExactly(CreatePortalTicketCommand.class)
                .doesNotContain(CreateTicketCommand.class);
    }
}
