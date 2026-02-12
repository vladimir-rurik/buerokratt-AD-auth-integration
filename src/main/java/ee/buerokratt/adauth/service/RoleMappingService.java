package ee.buerokratt.adauth.service;

import ee.buerokratt.adauth.config.ADProperties;
import ee.buerokratt.adauth.model.RoleMappingResult;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.stream.Collectors;

/**
 * Service for mapping AD groups to Bürokratt roles
 *
 * Implements multiple strategies for handling users in multiple groups
 */
@Service
public class RoleMappingService {

    private static final org.slf4j.Logger log = org.slf4j.LoggerFactory.getLogger(RoleMappingService.class);

    @Autowired
    private ADProperties adProperties;

    /**
     * Map AD groups to roles based on configured rules
     *
     * @param adGroups List of AD group Distinguished Names
     * @return RoleMappingResult with mapped roles
     */
    @Cacheable(value = "roleMappings", key = "#adGroups.hashCode()")
    public RoleMappingResult mapGroups(List<String> adGroups) {
        if (adGroups == null || adGroups.isEmpty()) {
            log.debug("No AD groups provided, using default role");
            return new RoleMappingResult(
                Collections.singletonList(adProperties.getRoleMapping().getDefaultRole())
            );
        }

        List<ADProperties.RoleMappingRule> rules = adProperties.getRoleMapping().getRules();

        if (rules == null || rules.isEmpty()) {
            log.warn("No role mapping rules configured, using default role");
            return new RoleMappingResult(
                Collections.singletonList(adProperties.getRoleMapping().getDefaultRole())
            );
        }

        switch (adProperties.getRoleMapping().getMultiGroupStrategy()) {
            case HIGHEST_PRIORITY:
                return mapHighestPriority(adGroups, rules);
            case COMBINE:
                return mapCombine(adGroups, rules);
            case FIRST_MATCH:
                return mapFirstMatch(adGroups, rules);
            default:
                log.warn("Unknown multi-group strategy, using HIGHEST_PRIORITY");
                return mapHighestPriority(adGroups, rules);
        }
    }

    /**
     * Map using highest priority matched group
     * Groups with lower priority value = higher priority
     */
    private RoleMappingResult mapHighestPriority(List<String> adGroups,
                                                  List<ADProperties.RoleMappingRule> rules) {
        // Sort by priority (ascending = higher priority)
        List<ADProperties.RoleMappingRule> sortedRules = rules.stream()
            .filter(rule -> rule.getPriority() != null)
            .sorted(Comparator.comparingInt(ADProperties.RoleMappingRule::getPriority))
            .collect(Collectors.toList());

        for (ADProperties.RoleMappingRule rule : sortedRules) {
            if (adGroups.stream().anyMatch(g -> g.contains(rule.getAdGroup()))) {
                log.debug("Matched AD group {} to role {} (priority {})",
                    rule.getAdGroup(), rule.getRole(), rule.getPriority());
                return new RoleMappingResult(Collections.singletonList(rule.getRole()));
            }
        }

        log.debug("No matching AD groups found, using default role");
        return new RoleMappingResult(
            Collections.singletonList(adProperties.getRoleMapping().getDefaultRole())
        );
    }

    /**
     * Combine all matched roles
     */
    private RoleMappingResult mapCombine(List<String> adGroups,
                                         List<ADProperties.RoleMappingRule> rules) {
        Set<String> roles = new HashSet<>();

        for (ADProperties.RoleMappingRule rule : rules) {
            if (adGroups.stream().anyMatch(g -> g.contains(rule.getAdGroup()))) {
                roles.add(rule.getRole());
                log.debug("Matched AD group {} to role {}", rule.getAdGroup(), rule.getRole());
            }
        }

        if (roles.isEmpty()) {
            roles.add(adProperties.getRoleMapping().getDefaultRole());
        }

        log.debug("Combined roles from AD groups: {}", roles);
        return new RoleMappingResult(new ArrayList<>(roles));
    }

    /**
     * Use first matched group (by configuration order)
     */
    private RoleMappingResult mapFirstMatch(List<String> adGroups,
                                            List<ADProperties.RoleMappingRule> rules) {
        for (ADProperties.RoleMappingRule rule : rules) {
            if (adGroups.stream().anyMatch(g -> g.contains(rule.getAdGroup()))) {
                log.debug("First match: AD group {} to role {}", rule.getAdGroup(), rule.getRole());
                return new RoleMappingResult(Collections.singletonList(rule.getRole()));
            }
        }

        log.debug("No matching AD groups found, using default role");
        return new RoleMappingResult(
            Collections.singletonList(adProperties.getRoleMapping().getDefaultRole())
        );
    }
}
