/**
 * The contents of this file are subject to the license and copyright
 * detailed in the LICENSE and NOTICE files at the root of the source
 * tree and available online at
 *
 * http://www.dspace.org/license/
 */
package org.dspace.app.rest.test;

import static org.junit.Assert.assertNotNull;

import org.dspace.app.TestApplication;
import org.dspace.app.rest.utils.DSpaceConfigurationInitializer;
import org.dspace.app.rest.utils.DSpaceKernelInitializer;
import org.dspace.content.service.PdfPageCountService;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationContext;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.junit4.SpringRunner;

@RunWith(SpringRunner.class)
@SpringBootTest(classes = TestApplication.class)
@ContextConfiguration(initializers = { DSpaceKernelInitializer.class, DSpaceConfigurationInitializer.class })
@TestPropertySource(locations = "classpath:application-test.properties")
public class BitstreamStatisticsServiceRegistrationIT {

    @Autowired
    private ApplicationContext applicationContext;

    @Test
    public void pdfPageCountServiceShouldBeAvailableInSpringContext() {
        assertNotNull("PdfPageCountService should be registered in the Spring context",
                applicationContext.getBean(PdfPageCountService.class));
    }
}
