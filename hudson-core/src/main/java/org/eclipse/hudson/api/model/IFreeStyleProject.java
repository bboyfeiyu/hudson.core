/*******************************************************************************
 *
 * Copyright (c) 2011 Oracle Corporation.
 *
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *
 *    Nikita Levyankov
 *
 *******************************************************************************/
package org.eclipse.hudson.api.model;

import java.io.IOException;

/**
 * FreeStyle project interface.
 * <p/>
 * Date: 9/15/11
 *
 * @author Nikita Levyankov
 */
public interface IFreeStyleProject extends IProject {

    /**
     * Returns user-specified workspace directory, or null if it's up to Hudson
     *
     * @return string representation of directory.
     * @throws IOException if any.
     */
    String getCustomWorkspace() throws IOException;
}
