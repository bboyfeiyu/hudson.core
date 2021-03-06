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

package hudson.search;

import java.util.Arrays;
import java.util.Collection;
import java.util.List;

/**
 * Set of {@link SearchItem}s that are statically known upfront.
 *
 * @author Kohsuke Kawaguchi
 */
public class FixedSet implements SearchIndex {

    private final Collection<? extends SearchItem> items;

    public FixedSet(Collection<? extends SearchItem> items) {
        this.items = items;
    }

    public FixedSet(SearchItem... items) {
        this(Arrays.asList(items));
    }

    public void find(String token, List<SearchItem> result) {
        for (SearchItem i : items) {
            if (token.equals(i.getSearchName())) {
                result.add(i);
            }
        }
    }

    public void suggest(String token, List<SearchItem> result) {
        for (SearchItem i : items) {
            if (i.getSearchName().contains(token)) {
                result.add(i);
            }
        }
    }
}
