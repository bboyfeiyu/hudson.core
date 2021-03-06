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
 *    Kohsuke Kawaguchi
 *
 *
 *******************************************************************************/ 

package hudson.triggers;

import hudson.model.Descriptor;
import hudson.model.Item;
import hudson.scm.SCM;

/**
 * {@link Descriptor} for Trigger.
 *
 * @author Kohsuke Kawaguchi
 */
public abstract class TriggerDescriptor extends Descriptor<Trigger<?>> {

    protected TriggerDescriptor(Class<? extends Trigger<?>> clazz) {
        super(clazz);
    }

    /**
     * Infers the type of the corresponding {@link SCM} from the outer class.
     * This version works when you follow the common convention, where a
     * descriptor is written as the static nested class of the describable
     * class.
     *
     * @since 1.278
     */
    protected TriggerDescriptor() {
    }

    /**
     * Returns true if this trigger is applicable to the given {@link Item}.
     *
     * @return true to allow user to configure a trigger for this item.
     */
    public abstract boolean isApplicable(Item item);
}
