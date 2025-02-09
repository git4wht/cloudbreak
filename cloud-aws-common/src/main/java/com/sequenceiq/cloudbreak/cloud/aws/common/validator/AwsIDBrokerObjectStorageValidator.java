package com.sequenceiq.cloudbreak.cloud.aws.common.validator;

import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.SortedSet;
import java.util.TreeSet;

import javax.inject.Inject;

import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Component;

import com.amazonaws.services.identitymanagement.model.InstanceProfile;
import com.amazonaws.services.identitymanagement.model.Role;
import com.sequenceiq.cloudbreak.cloud.aws.common.client.AmazonIdentityManagementClient;
import com.sequenceiq.cloudbreak.cloud.aws.common.util.AwsIamService;
import com.sequenceiq.cloudbreak.cloud.model.SpiFileSystem;
import com.sequenceiq.cloudbreak.cloud.model.filesystem.CloudFileSystemView;
import com.sequenceiq.cloudbreak.cloud.model.filesystem.CloudS3View;
import com.sequenceiq.cloudbreak.validation.ValidationResult;
import com.sequenceiq.cloudbreak.validation.ValidationResult.ValidationResultBuilder;
import com.sequenceiq.common.api.cloudstorage.AccountMappingBase;
import com.sequenceiq.common.model.CloudIdentityType;

@Component
public class AwsIDBrokerObjectStorageValidator {

    @Inject
    private AwsInstanceProfileEC2TrustValidator awsInstanceProfileEC2TrustValidator;

    @Inject
    private AwsIDBrokerAssumeRoleValidator awsIDBrokerAssumeRoleValidator;

    @Inject
    private AwsDataAccessRolePermissionValidator awsDataAccessRolePermissionValidator;

    @Inject
    private AwsRangerAuditRolePermissionValidator awsRangerAuditRolePermissionValidator;

    @Inject
    private AwsIamService awsIamService;

    @Inject
    private AwsLogRolePermissionValidator awsLogRolePermissionValidator;

    public ValidationResult validateObjectStorage(AmazonIdentityManagementClient iam,
            SpiFileSystem spiFileSystem,
            String logsLocationBase,
            String backupLocationBase,
            ValidationResultBuilder resultBuilder) {
        List<CloudFileSystemView> cloudFileSystems = spiFileSystem.getCloudFileSystems();
        for (CloudFileSystemView cloudFileSystemView : cloudFileSystems) {
            CloudS3View cloudFileSystem = (CloudS3View) cloudFileSystemView;
            String instanceProfileArn = cloudFileSystem.getInstanceProfile();
            InstanceProfile instanceProfile = awsIamService.getInstanceProfile(iam, instanceProfileArn,
                    cloudFileSystem.getCloudIdentityType(), resultBuilder);
            if (instanceProfile != null) {
                CloudIdentityType cloudIdentityType = cloudFileSystem.getCloudIdentityType();
                if (CloudIdentityType.ID_BROKER.equals(cloudIdentityType)) {
                    validateIDBroker(iam, instanceProfile, cloudFileSystem, resultBuilder);
                } else if (CloudIdentityType.LOG.equals(cloudIdentityType)) {
                    validateLog(iam, instanceProfile, cloudFileSystem, logsLocationBase, backupLocationBase, resultBuilder);
                }
            }
        }
        return resultBuilder.build();
    }

    private void validateIDBroker(AmazonIdentityManagementClient iam, InstanceProfile instanceProfile,
            CloudS3View cloudFileSystem, ValidationResultBuilder resultBuilder) {
        awsInstanceProfileEC2TrustValidator.isTrusted(instanceProfile, cloudFileSystem.getCloudIdentityType(), resultBuilder);
        Set<Role> allMappedRoles = getAllMappedRoles(iam, cloudFileSystem, resultBuilder);
        awsIDBrokerAssumeRoleValidator.canAssumeRoles(iam, instanceProfile, allMappedRoles, resultBuilder);
        awsDataAccessRolePermissionValidator.validate(iam, cloudFileSystem, resultBuilder);
        awsRangerAuditRolePermissionValidator.validate(iam, cloudFileSystem, resultBuilder);
    }

    private void validateLog(AmazonIdentityManagementClient iam, InstanceProfile instanceProfile, CloudS3View cloudFileSystem,
            String logsLocationBase, String backupLocationBase, ValidationResultBuilder resultBuilder) {
        awsInstanceProfileEC2TrustValidator.isTrusted(instanceProfile, cloudFileSystem.getCloudIdentityType(), resultBuilder);
        awsLogRolePermissionValidator.validate(iam, instanceProfile, cloudFileSystem, logsLocationBase, resultBuilder);
        if (StringUtils.isNotEmpty(backupLocationBase) && !backupLocationBase.equals(logsLocationBase)) {
            awsLogRolePermissionValidator.validate(iam, instanceProfile, cloudFileSystem, backupLocationBase, resultBuilder);
        }
    }

    private Set<Role> getAllMappedRoles(AmazonIdentityManagementClient iam, CloudFileSystemView cloudFileSystemView,
            ValidationResultBuilder resultBuilder) {
        Set<Role> roles = Collections.emptySet();
        AccountMappingBase accountMappings = cloudFileSystemView.getAccountMapping();
        if (accountMappings != null) {
            SortedSet<String> roleArns = new TreeSet<>();
            roleArns.addAll(accountMappings.getUserMappings().values());
            roleArns.addAll(accountMappings.getGroupMappings().values());
            roles = awsIamService.getValidRoles(iam, roleArns, resultBuilder);
        }
        return roles;
    }
}
