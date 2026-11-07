package io.taskforge.admin;

import io.taskforge.config.AdminBootstrapProperties;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

/**
 * Promotes the configured bootstrap user to ADMIN once the app is up. Runs on
 * every startup but is idempotent, so it is safe to leave configured.
 */
@Component
public class AdminBootstrap implements ApplicationRunner {

    private final AdminBootstrapProperties props;
    private final UserAdminService userAdmin;

    public AdminBootstrap(AdminBootstrapProperties props, UserAdminService userAdmin) {
        this.props = props;
        this.userAdmin = userAdmin;
    }

    @Override
    public void run(ApplicationArguments args) {
        if (StringUtils.hasText(props.adminEmail())) {
            userAdmin.ensureAdminByEmail(props.adminEmail().trim());
        }
    }
}
