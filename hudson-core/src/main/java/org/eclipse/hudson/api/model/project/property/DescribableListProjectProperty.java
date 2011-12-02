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
package org.eclipse.hudson.api.model.project.property;

import hudson.util.DescribableList;
import org.eclipse.hudson.api.model.IJob;

/**
 * Property represents DescribableList object.
 * <p/>
 * Date: 10/3/11
 *
 * @author Nikita Levyankov
 */
public class DescribableListProjectProperty extends BaseProjectProperty<DescribableList> {
    public DescribableListProjectProperty(IJob job) {
        super(job);
    }

    @Override
    public DescribableList getDefaultValue() {
        return new DescribableList(getJob());
    }
}
