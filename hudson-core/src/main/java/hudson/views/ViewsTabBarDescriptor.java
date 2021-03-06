/*******************************************************************************
 *
 * Copyright (c) 2010, Oracle Corporation.
 *
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *
 *
 *      Winston Prakash
 *
 *******************************************************************************/ 

package hudson.views;

import hudson.model.Descriptor;

/**
 * {@link Descriptor} for {@link ViewsTabBar}.
 *
 * @author Winston Prakash
 * @since 1.381
 */
public abstract class ViewsTabBarDescriptor extends Descriptor<ViewsTabBar> {
    // so far nothing different from plain Descriptor
    // but it may prove useful for future expansion
}
