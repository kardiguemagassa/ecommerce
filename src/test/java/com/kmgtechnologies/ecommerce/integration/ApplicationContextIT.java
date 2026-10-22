package com.kmgtechnologies.ecommerce.integration;

import org.junit.jupiter.api.Test;

@IntegrationTest
class ApplicationContextIT {

    @Test
    void contextLoads() {
    }

    /*
        Pour chaque fichier, le grep doit montrer le package ...ecommerce.integration,
        l’annotation @IntegrationTest et le nouveau nom de classe, sans @SpringBootTest.

        EcommerceApplicationTests devient ApplicationContextIT : ce test démarre tout le contexte Spring,
        c’est donc aussi un test d’intégration.

        Le plugin Failsafe

        Dans pom.xml, dans <build><plugins>, sous spring-boot-maven-plugin :
        <plugin>
             <groupId>org.apache.maven.plugins</groupId>
             <artifactId>maven-failsafe-plugin</artifactId>
         </plugin>
     */

}
