package sa.khaier.verify;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.ZonedDateTime;
import java.util.LinkedHashMap;
import java.util.Map;

@RestController
public class HealthController {

    private final JdbcTemplate jdbcTemplate;

    public HealthController(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @GetMapping("/app-health")
    public Map<String, Object> health() {
        Integer dbResult = jdbcTemplate.queryForObject("SELECT 1", Integer.class);

        Map<String, Object> response = new LinkedHashMap<>();
        response.put("status", "OK");
        response.put("service", "khaier-document-verify");
        response.put("database", dbResult != null && dbResult == 1 ? "OK" : "ERROR");
        response.put("time", ZonedDateTime.now().toString());
        return response;
    }
}
