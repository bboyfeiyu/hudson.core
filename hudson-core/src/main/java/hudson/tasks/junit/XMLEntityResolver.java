/*******************************************************************************
 *
 * Copyright (c) 2004-2009 Oracle Corporation.
 *
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 * 
 *    Kohsuke Kawaguchi, Jorg Heymans
 *
 *
 *******************************************************************************/ 

package hudson.tasks.junit;

import org.xml.sax.EntityResolver;
import org.xml.sax.InputSource;
import org.xml.sax.SAXException;

import java.io.IOException;
import java.net.URL;
import java.util.logging.Logger;

/**
 * As the name suggest: a resolver for XML entities.
 *
 * <p> Basically, it provides the possibility to intercept online DTD lookups
 * and instead do offline lookup by redirecting to a local directory where
 * .dtd's are stored
 *
 * (useful when parsing testng-results.xml - which points to testng.org)
 *
 * @author Mikael Carneholm
 */
class XMLEntityResolver implements EntityResolver {

    private static final String TESTNG_NAMESPACE = "http://testng.org/";

    /**
     * Intercepts the lookup of publicId, systemId
     */
    public InputSource resolveEntity(String publicId, String systemId) throws SAXException, IOException {
        if (systemId != null) {
            LOGGER.fine("Will try to resolve systemId [" + systemId + "]");
            // TestNG system-ids
            if (systemId.startsWith(TESTNG_NAMESPACE)) {
                LOGGER.fine("It's a TestNG document, will try to lookup DTD in classpath");
                String dtdFileName = systemId.substring(TESTNG_NAMESPACE.length());

                URL url = getClass().getClassLoader().getResource(dtdFileName);
                if (url != null) {
                    return new InputSource(url.toString());
                }
            }
        }
        // Default fallback
        return null;
    }
    private static final Logger LOGGER = Logger.getLogger(XMLEntityResolver.class.getName());
}
