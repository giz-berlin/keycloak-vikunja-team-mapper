package berlin.giz.keycloak.mappers.vikunjateam;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.jboss.logging.Logger;
import org.keycloak.models.ClientSessionContext;
import org.keycloak.models.GroupModel;
import org.keycloak.models.KeycloakSession;
import org.keycloak.models.ProtocolMapperModel;
import org.keycloak.models.UserModel;
import org.keycloak.models.UserSessionModel;
import org.keycloak.protocol.oidc.mappers.AbstractOIDCProtocolMapper;
import org.keycloak.protocol.oidc.mappers.OIDCAttributeMapperHelper;
import org.keycloak.protocol.oidc.mappers.OIDCIDTokenMapper;
import org.keycloak.provider.ProviderConfigProperty;
import org.keycloak.representations.IDToken;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

/* Vikunja team mapper
 *
 * See https://vikunja.io/docs/openid/ for documentation on how the team should get mapped into the tokens.
 *
 */
public class OIDCMapper extends AbstractOIDCProtocolMapper implements OIDCIDTokenMapper {

    static final String PROVIDER_ID = "vikunja-team-mapper";

    static final String GROUP_ATTRIBUTE_NAME_TEAM_NAME_CONFIG = "group-attribute-name-team-name";
    static final String GROUP_ATTRIBUTE_NAME_TEAM_DESCRIPTION_CONFIG = "group-attribute-name-description";
    static final String DEFAULT_IS_PUBLIC_CONFIG = "default-is-public";
    static final String GROUP_ATTRIBUTE_NAME_TEAM_IS_PUBLIC_CONFIG = "group-attribute-name-is-public";

    static final List<ProviderConfigProperty> CONFIG_PROPERTIES;

    private static final Logger LOGGER = Logger.getLogger(OIDCMapper.class);

    static {
        List<ProviderConfigProperty> configProperties = new ArrayList<>();
        OIDCAttributeMapperHelper.addIncludeInTokensConfig(configProperties, OIDCMapper.class);

        configProperties.add(new ProviderConfigProperty(
            GROUP_ATTRIBUTE_NAME_TEAM_NAME_CONFIG,
            "Group Attribute for Name",
            "Attribute name on Keycloak Groups, which get mapped to Vikunja for team name. A group will only be sent to Vikunja if the attribute defined here is defined for this group.",
            ProviderConfigProperty.STRING_TYPE,
            "vikunjaTeamName",
            false,
            true
        ));
        configProperties.add(new ProviderConfigProperty(
            GROUP_ATTRIBUTE_NAME_TEAM_DESCRIPTION_CONFIG,
            "Group Attribute for Description",
            "Attribute name on Keycloak Groups, which get mapped to Vikunja for team description.",
            ProviderConfigProperty.STRING_TYPE,
            "vikunjaTeamDescription",
            false,
            true
        ));
        configProperties.add(new ProviderConfigProperty(
            GROUP_ATTRIBUTE_NAME_TEAM_IS_PUBLIC_CONFIG,
            "Group Attribute for is public",
            "Attribute name on Keycloak Groups, which get mapped to Vikunja to decide whether a team is public.",
            ProviderConfigProperty.STRING_TYPE,
            "vikunjaTeamIsPublic"
        ));
        configProperties.add(new ProviderConfigProperty(
            DEFAULT_IS_PUBLIC_CONFIG,
            "Teams are public by default",
            "Configures whether a Vikunja Team will be public by default if the corresponding attribute is not set for the Keycloak group.",
            ProviderConfigProperty.BOOLEAN_TYPE,
            "false"
        ));

        CONFIG_PROPERTIES = configProperties;
    }

    @Override
    public String getDisplayCategory() {
        return TOKEN_MAPPER_CATEGORY;
    }

    @Override
    public String getDisplayType() {
        return "Vikunja team mapper";
    }

    @Override
    public String getId() {
        return PROVIDER_ID;
    }

    @Override
    public String getHelpText() {
        return "An OIDC mapper mapping Keycloak Groups to Vikunja teams in ID token.";
    }

    @Override
    public List<ProviderConfigProperty> getConfigProperties() {
        return CONFIG_PROPERTIES;
    }

    private Stream<Map<String, Object>> getVikunjaTeams(ProtocolMapperModel mappingModel, UserSessionModel userSession) {
        String teamNameAttributeName = mappingModel.getConfig().get(GROUP_ATTRIBUTE_NAME_TEAM_NAME_CONFIG);
        String teamDescriptionAttributeName = mappingModel.getConfig().get(GROUP_ATTRIBUTE_NAME_TEAM_DESCRIPTION_CONFIG);
        String defaultTeamIsPublicString = mappingModel.getConfig().get(DEFAULT_IS_PUBLIC_CONFIG);
        String teamIsPublicAttributeName = mappingModel.getConfig().get(GROUP_ATTRIBUTE_NAME_TEAM_IS_PUBLIC_CONFIG);

        // Ensure all required settings are properly set
        if (teamNameAttributeName == null || defaultTeamIsPublicString == null) {
            LOGGER.debug("Some Vikunja team mapper settings are not properly set");
            return Stream.empty();
        }

        Boolean defaultIsPublic = Boolean.valueOf(defaultTeamIsPublicString);

        UserModel user = userSession.getUser();

        // Unfortunately, there is no easy possibility to retrieve all groups a user is member of.
        // However, we would like to resemble the role hierarchy behavior built into Keycloak.
        // Keycloak's UserModel.getGroupsStream() only returns direct group memberships,
        // so we need to manually traverse the hierarchy to include parent groups.
        Map<String, GroupModel> teamsUserIsMemberOf = new HashMap<>();
        user.getGroupsStream().forEach(currentUserGroup -> {
            GroupModel currentGroup = currentUserGroup;
            while (currentGroup != null && !teamsUserIsMemberOf.containsKey(currentGroup.getId())) {
                teamsUserIsMemberOf.put(currentGroup.getId(), currentGroup);
                currentGroup = currentGroup.getParent();
            }
        });

        Stream<GroupModel> groupStream = teamsUserIsMemberOf.values().stream();
        return groupStream.map(group -> {
            Map<String, Object> res = new HashMap<>();

            String teamName = group.getFirstAttribute(teamNameAttributeName);
            if (teamName == null) {
                // do not add Keycloak group to Vikunja if no name is explicitly set
                return null;
            }
            res.put("oidcID", group.getId());
            res.put("name", teamName);

            if (teamIsPublicAttributeName != null) {
                String isPublicString = group.getFirstAttribute(teamIsPublicAttributeName);
                Boolean isPublic = isPublicString == null ? defaultIsPublic : Boolean.valueOf(isPublicString);
                res.put("isPublic", isPublic);
            } else {
                res.put("isPublic", defaultIsPublic);
            }

            if (teamDescriptionAttributeName != null) {
                String teamDescription = group.getFirstAttribute(teamDescriptionAttributeName);
                if (teamDescription != null) {
                    res.put("description", teamDescription);
                }
            }

            return res;
        }).filter(Objects::nonNull);
    }

    @Override
    protected void setClaim(IDToken token, ProtocolMapperModel mappingModel, UserSessionModel userSession,
        KeycloakSession keycloakSession, ClientSessionContext clientSessionCtx
    ) {
        Map<String, String> mappingConfig = mappingModel.getConfig();
        // This is a hardcoded claim as vikunja does not support other claim names at the time of writing this.
        // If this changes, feel free to change / open an issue.
        mappingConfig.put(OIDCAttributeMapperHelper.TOKEN_CLAIM_NAME, "vikunja_groups");

        ObjectMapper objectMapper = new ObjectMapper();
        List<Map<String, Object>> teams = this.getVikunjaTeams(mappingModel, userSession).collect(Collectors.toList());
        JsonNode claimNode = objectMapper.valueToTree(teams);

        // Map claim into token
        OIDCAttributeMapperHelper.mapClaim(token, mappingModel, claimNode);
    }

    @Override
    public int getPriority() {
        // Use default priority to allow manual overwriting
        return 0;
    }
}
