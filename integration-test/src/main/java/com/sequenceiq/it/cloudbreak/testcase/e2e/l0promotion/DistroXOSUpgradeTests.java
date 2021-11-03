package com.sequenceiq.it.cloudbreak.testcase.e2e.l0promotion;

import static com.sequenceiq.it.cloudbreak.context.RunningParameter.key;

import javax.inject.Inject;

import org.testng.annotations.Test;

import com.sequenceiq.common.api.type.Tunnel;
import com.sequenceiq.distrox.api.v1.distrox.model.upgrade.DistroXUpgradeShowAvailableImages;
import com.sequenceiq.environment.api.v1.environment.model.response.EnvironmentStatus;
import com.sequenceiq.freeipa.api.v1.operation.model.OperationState;
import com.sequenceiq.it.cloudbreak.client.DistroXTestClient;
import com.sequenceiq.it.cloudbreak.client.EnvironmentTestClient;
import com.sequenceiq.it.cloudbreak.client.FreeIpaTestClient;
import com.sequenceiq.it.cloudbreak.client.SdxTestClient;
import com.sequenceiq.it.cloudbreak.cloud.v4.CommonClusterManagerProperties;
import com.sequenceiq.it.cloudbreak.context.Description;
import com.sequenceiq.it.cloudbreak.context.TestContext;
import com.sequenceiq.it.cloudbreak.dto.distrox.DistroXTestDto;
import com.sequenceiq.it.cloudbreak.dto.distrox.cluster.DistroXUpgradeTestDto;
import com.sequenceiq.it.cloudbreak.dto.environment.EnvironmentTestDto;
import com.sequenceiq.it.cloudbreak.dto.freeipa.FreeIpaUserSyncTestDto;
import com.sequenceiq.it.cloudbreak.dto.sdx.SdxTestDto;
import com.sequenceiq.it.cloudbreak.dto.sdx.SdxUpgradeTestDto;
import com.sequenceiq.it.cloudbreak.dto.telemetry.TelemetryTestDto;
import com.sequenceiq.it.cloudbreak.testcase.e2e.AbstractE2ETest;
import com.sequenceiq.it.cloudbreak.util.spot.UseSpotInstances;
import com.sequenceiq.sdx.api.model.SdxClusterStatusResponse;
import com.sequenceiq.sdx.api.model.SdxUpgradeReplaceVms;

public class DistroXOSUpgradeTests extends AbstractE2ETest {

    @Inject
    private SdxTestClient sdxTestClient;

    @Inject
    private DistroXTestClient distroXTestClient;

    @Inject
    private CommonClusterManagerProperties commonClusterManagerProperties;

    @Inject
    private FreeIpaTestClient freeIpaTestClient;

    @Inject
    private EnvironmentTestClient environmentTestClient;

    @Override
    protected void setupTest(TestContext testContext) {
        testContext.getCloudProvider().getCloudFunctionality().cloudStorageInitialize();
        createDefaultUser(testContext);
        initializeDefaultBlueprints(testContext);
        createDefaultCredential(testContext);
    }

    @Test(dataProvider = TEST_CONTEXT)
    @UseSpotInstances
    @Description(
            given = "there is a running environment with SDX (light duty) and base DistroX (5 nodes with master, compute and workers) in available state",
            when = "DistroX runtime upgrade done successfully (this can be done after a successful SDX upgrade)",
            and = "OS upgrade called on DistroX",
            then = "DistroX upgrade should be successful, the cluster should be up and running")
    public void testBaseDistroXOSUpgrade(TestContext testContext) {
        String sdxName = resourcePropertyProvider().getName();
        String distroXName = resourcePropertyProvider().getName();
        String currentRuntimeVersion = commonClusterManagerProperties.getUpgrade().getDistroXUpgradeCurrentVersion();
        String targetRuntimeVersion = commonClusterManagerProperties.getUpgrade().getDistroXUpgradeTargetVersion();

        testContext
                .given("telemetry", TelemetryTestDto.class)
                    .withLogging()
                    .withReportClusterLogs()
                .given(EnvironmentTestDto.class)
                    .withNetwork()
                    .withTelemetry("telemetry")
                    .withTunnel(Tunnel.CLUSTER_PROXY)
                    .withCreateFreeIpa(Boolean.TRUE)
                    .withFreeIpaImage(commonCloudProperties().getImageValidation().getFreeIpaImageCatalog(),
                        commonCloudProperties().getImageValidation().getFreeIpaImageUuid())
                .when(environmentTestClient.create())
                .validate();
        testContext.given(EnvironmentTestDto.class)
                .await(EnvironmentStatus.AVAILABLE)
                .when(environmentTestClient.describe())
                .validate();
        testContext.given(FreeIpaUserSyncTestDto.class)
                .when(freeIpaTestClient.getLastSyncOperationStatus())
                .await(OperationState.COMPLETED)
                .validate();

        testContext
                .given(sdxName, SdxTestDto.class)
                    .withCloudStorage()
                    .withRuntimeVersion(currentRuntimeVersion)
                .when(sdxTestClient.create(), key(sdxName))
                .await(SdxClusterStatusResponse.RUNNING, key(sdxName))
                .awaitForHealthyInstances()
                .validate();

        testContext
                .given(distroXName, DistroXTestDto.class)
                    .withTemplate(String.format(commonClusterManagerProperties.getInternalDistroXBlueprintType(), currentRuntimeVersion))
                .when(distroXTestClient.create(), key(distroXName))
                .await(STACK_AVAILABLE)
                .awaitForHealthyInstances()
                .validate();

        testContext
                .given(distroXName, DistroXTestDto.class)
                .when(distroXTestClient.stop(), key(distroXName))
                .await(STACK_STOPPED)
                .validate();
        testContext
                .given(SdxUpgradeTestDto.class)
                    .withReplaceVms(SdxUpgradeReplaceVms.ENABLED)
                    .withRuntime(targetRuntimeVersion)
                .given(sdxName, SdxTestDto.class)
                .when(sdxTestClient.upgrade(), key(sdxName))
                .await(SdxClusterStatusResponse.DATALAKE_UPGRADE_IN_PROGRESS, key(sdxName).withWaitForFlow(Boolean.FALSE))
                .await(SdxClusterStatusResponse.RUNNING, key(sdxName))
                .awaitForHealthyInstances()
                .validate();

        testContext
                .given(distroXName, DistroXTestDto.class)
                .when(distroXTestClient.start(), key(distroXName))
                .await(STACK_AVAILABLE)
                .validate();
        testContext
                .given(DistroXUpgradeTestDto.class)
                    .withRuntime(targetRuntimeVersion)
                .given(distroXName, DistroXTestDto.class)
                .when(distroXTestClient.upgrade(), key(distroXName))
                .await(STACK_AVAILABLE, key(distroXName))
                .awaitForHealthyInstances()
                .validate();
        testContext
                .given(DistroXUpgradeTestDto.class)
                    .withShowLatestAvailableImagePerRuntime(DistroXUpgradeShowAvailableImages.LATEST_ONLY)
                .given(distroXName, DistroXTestDto.class)
                .when(distroXTestClient.upgrade(), key(distroXName))
                .validate();
        testContext
                .given(DistroXUpgradeTestDto.class)
                    .withLockComponents(Boolean.TRUE)
                .given(distroXName, DistroXTestDto.class)
                .when(distroXTestClient.upgrade(), key(distroXName))
                .await(STACK_AVAILABLE, key(distroXName))
                .awaitForHealthyInstances()
                .validate();
    }
}
