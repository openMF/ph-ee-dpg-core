package org.mifos.pheedpgimporterrdbms;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.jdbc.DataSourceAutoConfiguration;

@SpringBootApplication(exclude = { DataSourceAutoConfiguration.class })
@SuppressWarnings({ "HideUtilityClassConstructor" })
public class PhEeDpgImporterRdbmsApplication {

    public static void main(String[] args) {
        SpringApplication.run(PhEeDpgImporterRdbmsApplication.class, args);
    }

}
