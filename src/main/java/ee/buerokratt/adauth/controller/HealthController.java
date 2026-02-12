package ee.buerokratt.adauth.controller;

import org.springframework.boot.info.BuildProperties;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

/**
 * Health Check Controller
 *
 * Provides health endpoints for Kubernetes probes and monitoring
 */
@RestController
@RequestMapping("/actuator")
public class HealthController {

    private final Optional<BuildProperties> buildProperties;

    public HealthController(Optional<BuildProperties> buildProperties) {
        this.buildProperties = buildProperties;
    }

    @GetMapping("/health")
    public ResponseEntity<Map<String, Object>> health() {
        Map<String, Object> response = new HashMap<>();
        response.put("status", "UP");
        response.put("application", "AD-Auth Service");
        response.put("version", buildProperties.map(BuildProperties::getVersion).orElse("unknown"));
        return ResponseEntity.ok(response);
    }

    @GetMapping("/health/readiness")
    public ResponseEntity<Map<String, String>> readiness() {
        return ResponseEntity.ok(Map.of("status", "READY"));
    }
}
