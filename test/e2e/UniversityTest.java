package e2e;

import org.junit.Test;

import static org.junit.Assert.assertTrue;

public class UniversityTest extends GrpcE2EBase {
    public UniversityTest() {
        super("./samples/json/EcdarUniversity/Components/");
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
