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

import hudson.model.Item;
import hudson.model.JobProperty;
import hudson.model.JobPropertyDescriptor;
import hudson.tasks.LogRotator;
import java.util.Map;

/**
 * Interface that represents Job.
 * <p/>
 * Date: 9/15/11
 *
 * @author Nikita Levyankov
 */
public interface IJob<T extends IJob> extends Item {

    /**
     * Returns cascading project name.
     *
     * @return cascading project name.
     */
    String getCascadingProjectName();

    /**
     * Returns selected cascading project.
     *
     * @return cascading project.
     */
    T getCascadingProject();

    /**
     * Returns job property by specified key.
     *
     * @param key key.
     * @param clazz IProperty subclass.
     * @return {@link IProjectProperty} instance or null.
     */
    IProjectProperty getProperty(String key, Class<? extends IProjectProperty> clazz);

    /**
     * Checks whether current job is inherited from other project.
     *
     * @return boolean.
     */
    boolean hasCascadingProject();

    /**
     * @return whether the name of this job can be changed by user.
     */
    boolean isNameEditable();

    /**
     * Returns the log rotator for this job, or null if none.
     *
     * @return {@link LogRotator} instance.
     */
    LogRotator getLogRotator();

    /**
     * @return true if this instance supports log rotation configuration.
     */
    boolean supportsLogRotator();

    /**
     * Gets all the job properties configured for this job.
     *
     * @return Map of properties.
     */
    Map<JobPropertyDescriptor, JobProperty<?>> getProperties();
}
