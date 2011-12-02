/*******************************************************************************
 *
 * Copyright (c) 20011 Oracle Corporation.
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
package hudson.model;

import hudson.tasks.BuildWrapper;
import hudson.tasks.Builder;
import hudson.tasks.Publisher;
import java.util.List;
import java.util.Map;

/**
 * Project interface
 * <p/>
 * Date: 9/15/11
 *
 * @author Nikita Levyankov
 */
public interface IProject extends IAbstractProject {
    /**
     * @return list of project {@link Builder}
     */
    List<Builder> getBuilders();

    /**
     * @return map of project {@link BuildWrapper}
     */
    Map<Descriptor<BuildWrapper>, BuildWrapper> getBuildWrappers();

    /**
     * @return map of project {@link Publisher}
     */
    Map<Descriptor<Publisher>, Publisher> getPublishers();
}
