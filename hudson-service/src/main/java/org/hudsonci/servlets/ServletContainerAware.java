/*******************************************************************************
 *
 * Copyright (c) 2010-2011 Sonatype, Inc.
 *
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *
 *
 *
 *
 *******************************************************************************/ 

package org.hudsonci.servlets;

import hudson.ExtensionPoint;

/**
 * Extension to allow plugins to be aware of the {@link ServletContainer} and
 * register servlets.
 *
 * @author <a href="mailto:jason@planet57.com">Jason Dillon</a>
 * @since 2.1.0
 */
public interface ServletContainerAware
        extends ExtensionPoint {

    void setServletContainer(ServletContainer container) throws Exception;
}
