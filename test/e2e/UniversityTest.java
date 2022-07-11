package e2e;

import EcdarProtoBuf.ComponentProtos;
import EcdarProtoBuf.QueryProtos;
import connection.EcdarService;
import io.grpc.ManagedChannel;
import io.grpc.Server;
import io.grpc.inprocess.InProcessChannelBuilder;
import io.grpc.inprocess.InProcessServerBuilder;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;

import static org.junit.Assert.*;

public class UniversityTest {
    private Server server;
    private ManagedChannel channel;
    private EcdarProtoBuf.EcdarBackendGrpc.EcdarBackendBlockingStub stub;

    @Before
    public void beforeEachTest()
            throws NullPointerException, IOException {
        // Finds all the json components in the university component folder
        String baseEcdarUniversity = "./samples/json/EcdarUniversity/Components/";
        File componentsFolder = new File(baseEcdarUniversity);
        File[] componentFiles = componentsFolder.listFiles();

        assertNotNull(componentFiles);
        assertEquals(componentFiles.length, 9);

        // Find all the components stored as json and create a component for it
        List<ComponentProtos.Component> components = new ArrayList<>();
        for (File componentFile : componentFiles) {
            String contents = Files.readString(componentFile.toPath());

            ComponentProtos.Component component = ComponentProtos.Component
                    .newBuilder()
                    .setJson(contents)
                    .build();
            components.add(component);
        }

        // Creates the server and associated channel, which both must have the same name
        String name = this.getClass().getName();
        server = InProcessServerBuilder
                .forName(name)
                .directExecutor()
                .addService(new EcdarService())
                .build()
                .start();
        channel = InProcessChannelBuilder
                .forName(name)
                .directExecutor()
                .usePlaintext()
                .build();

        // Creates the stub which allows us to use function calls for remote procedures
        stub = EcdarProtoBuf.EcdarBackendGrpc.newBlockingStub(channel);

        // Creates all the components such that they can be utilised in the tests later
        EcdarProtoBuf.QueryProtos.ComponentsUpdateRequest request = EcdarProtoBuf.QueryProtos.ComponentsUpdateRequest
                .newBuilder()
                .addAllComponents(components)
                .build();
        stub.updateComponents(request);
    }

    @After
    public void afterEachTest() {
        this.channel.shutdownNow();
        this.server.shutdown();
    }

    private QueryProtos.Query createQuery(String query) {
        return QueryProtos.Query.newBuilder()
                .setQuery(query)
                .build();
    }

    private QueryProtos.QueryResponse query(String query) {
        return stub.sendQuery(
                createQuery(query)
        );
    }

    private boolean consistency(String query) {
        QueryProtos.QueryResponse response = query(query);
        return response.getConsistency().getSuccess();
    }

    private boolean refinement(String query) {
        QueryProtos.QueryResponse response = query(query);
        return response.getRefinement().getSuccess();
    }

    private String getComponent(String query) {
        QueryProtos.QueryResponse response = query(query);
        return response.getComponent().getComponent().getJson();
    }

    @Test
    public void compositionOfAdminMachResIsConsistent() {
        boolean consistent = consistency("consistency: (Administration || Machine || Researcher)");

        assertTrue(consistent);
    }

    @Test
    public void compositionOfAdminMachineResearcherRefinesSpec() {
        boolean refines = refinement("refinement: (Administration || Machine || Researcher) <= Spec");

        assertTrue(refines);
    }
}
