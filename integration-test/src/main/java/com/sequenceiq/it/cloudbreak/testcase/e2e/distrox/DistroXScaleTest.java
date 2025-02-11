package com.sequenceiq.it.cloudbreak.testcase.e2e.distrox;

import static com.sequenceiq.cloudbreak.api.endpoint.v4.stacks.base.InstanceStatus.DELETED_ON_PROVIDER_SIDE;

import java.util.List;
import java.util.Optional;
import java.util.stream.IntStream;

import javax.inject.Inject;

import org.testng.ITestContext;
import org.testng.annotations.Test;

import com.sequenceiq.it.cloudbreak.client.DistroXTestClient;
import com.sequenceiq.it.cloudbreak.context.Description;
import com.sequenceiq.it.cloudbreak.context.TestContext;
import com.sequenceiq.it.cloudbreak.dto.distrox.DistroXTestDto;
import com.sequenceiq.it.cloudbreak.exception.TestFailException;
import com.sequenceiq.it.cloudbreak.testcase.e2e.AbstractE2ETest;
import com.sequenceiq.it.cloudbreak.util.CloudFunctionality;
import com.sequenceiq.it.cloudbreak.util.DistroxUtil;

public class DistroXScaleTest extends AbstractE2ETest {

    @Inject
    private DistroXTestClient distroXTestClient;

    @Inject
    private DistroxUtil distroxUtil;

    @Override
    protected void setupTest(TestContext testContext) {
        testContext.getCloudProvider().getCloudFunctionality().cloudStorageInitialize();
        createDefaultUser(testContext);
        createDefaultCredential(testContext);
        initializeDefaultBlueprints(testContext);
        createDefaultDatahub(testContext);
    }

    @Test(dataProvider = TEST_CONTEXT, description = "Resilient Scaling: " +
            "UseCase1: " +
            "- Start upscale on running cluster " +
            "- Delete a non CM server host from provider other hostgroup during upscale " +
            "- Upscale should complete ")
    @Description(
            given = "there is a running default Distrox cluster",
            when = "deleted a Compute instance while cluster was upscaling (by Worker nodes)",
            then = "cluster can be resiliently scaled up then down with higher node count")
    public void testCreateAndScaleDistroX(TestContext testContext, ITestContext iTestContext) {
        DistroXScaleTestParameters params = new DistroXScaleTestParameters(iTestContext.getCurrentXmlTest().getAllParameters());

        if (params.getTimes() < 1) {
            throw new TestFailException("Test should execute at least 1 round of scaling");
        }

        testContext.given(DistroXTestDto.class)
                .when(distroXTestClient.scale(params.getHostGroup(), params.getScaleUpTarget()))
                .then((tc, testDto, client) -> {
                    CloudFunctionality cloudFunctionality = tc.getCloudProvider().getCloudFunctionality();
                    Optional<String> anInstanceToDelete = distroxUtil.getInstanceIds(testDto, client, params.getIrrelevantHostGroup()).stream().findFirst();
                    if (anInstanceToDelete.isEmpty()) {
                        throw new TestFailException(String.format(
                                "At least 1 instance needed from group %s to test delete it and test targeted upscale.", params.getIrrelevantHostGroup()));
                    }
                    cloudFunctionality.deleteInstances(testDto.getName(), List.of(anInstanceToDelete.get()));
                    testDto.setInstanceIdsForActions(List.of(anInstanceToDelete.get()));
                    return testDto;
                })
                .awaitForFlow()
                // removing deleted instance since downscale still validates if stack is available
                .awaitForActionedInstances(DELETED_ON_PROVIDER_SIDE)
                .when(distroXTestClient.removeInstances())
                .awaitForFlow()
                .when(distroXTestClient.scale(params.getHostGroup(), params.getScaleDownTarget()))
                .awaitForFlow()
                .validate();
        IntStream.range(1, params.getTimes()).forEach(i -> testContext.given(DistroXTestDto.class)
                .when(distroXTestClient.scale(params.getHostGroup(), params.getScaleUpTarget()))
                .awaitForFlow()
                .when(distroXTestClient.scale(params.getHostGroup(), params.getScaleDownTarget()))
                .awaitForFlow()
                .validate());
    }

}
