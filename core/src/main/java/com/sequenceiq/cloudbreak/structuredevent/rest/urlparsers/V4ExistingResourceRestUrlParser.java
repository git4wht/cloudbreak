package com.sequenceiq.cloudbreak.structuredevent.rest.urlparsers;

import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.springframework.stereotype.Component;

import com.sequenceiq.cloudbreak.structuredevent.rest.urlparser.LegacyRestUrlParser;

@Component
public class V4ExistingResourceRestUrlParser extends LegacyRestUrlParser {

    public static final int WORKSPACE_ID_GROUP_NUMBER = 1;

    public static final int RESOURCE_NAME_GROUP_NUMBER = 4;

    public static final int RESOURCE_TYPE_GROUP_NUMBER = 2;

    // Irregular requests with resource ID instead of resource name: v4/{workspaceId}/audits/{auditId}
    // Irregular GET requests with event but no resource name: v4/{workspaceId}/audits/zip and remaining patterns
    private static final Pattern ANTI_PATTERN = Pattern.compile(
            "v4/\\d+/(audits/.*"
            + "|blueprints/recommendation"
            + "|blueprints_util/.*"
            + "|image_catalogs/image(s\\b|\\b)"
            + "|image_catalogs/default_runtime_versions"
            + "|connectors/[a-z_]+"
            + "|recipes/internal"
            + "|file_systems/[a-z_]+)");

    private static final Pattern PATTERN = Pattern.compile("v4/(\\d+)/([a-z_]+)(/internal)?/([^/]+)");

    @Override
    protected List<String> parsedMethods() {
        return List.of("DELETE", "PUT", "GET");
    }

    @Override
    protected Pattern getAntiPattern() {
        return ANTI_PATTERN;
    }

    @Override
    public Pattern getPattern() {
        return PATTERN;
    }

    @Override
    protected String getWorkspaceId(Matcher matcher) {
        return matcher.group(WORKSPACE_ID_GROUP_NUMBER);
    }

    @Override
    protected String getResourceName(Matcher matcher) {
        return matcher.group(RESOURCE_NAME_GROUP_NUMBER);
    }

    @Override
    protected String getResourceCrn(Matcher matcher) {
        return null;
    }

    @Override
    protected String getResourceId(Matcher matcher) {
        return null;
    }

    @Override
    protected String getResourceType(Matcher matcher) {
        return matcher.group(RESOURCE_TYPE_GROUP_NUMBER);
    }

    @Override
    protected String getResourceEvent(Matcher matcher) {
        return null;
    }

}
