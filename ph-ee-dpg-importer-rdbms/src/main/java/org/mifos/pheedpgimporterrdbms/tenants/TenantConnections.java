package org.mifos.pheedpgimporterrdbms.tenants;

import java.util.List;
import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Configuration
@ConfigurationProperties("tenants")
@Data
public class TenantConnections {

    List<TenantConnectionProperties> connections;

}
