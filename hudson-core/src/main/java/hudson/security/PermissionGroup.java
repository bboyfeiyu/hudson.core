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

package hudson.security;

import hudson.CopyOnWrite;
import hudson.model.Hudson;

import java.util.List;
import java.util.Collections;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.ConcurrentHashMap;

import org.jvnet.localizer.Localizable;

/**
 * Group of {@link Permission}s that share the same
 * {@link Permission#owner owner}.
 *
 * Sortable by the owner class name.
 */
public final class PermissionGroup implements Iterable<Permission>, Comparable<PermissionGroup> {

    private final List<Permission> permisisons = new CopyOnWriteArrayList<Permission>();
    private final List<Permission> permisisonsView = Collections.unmodifiableList(permisisons);
    //TODO: review and check whether we can do it private
    public final Class owner;
    /**
     * Human readable title of this permission group. This should be short.
     */
    //TODO: review and check whether we can do it private
    public final Localizable title;

    public Class getOwner() {
        return owner;
    }

    public Localizable getTitle() {
        return title;
    }

    public PermissionGroup(Class owner, Localizable title) {
        this.owner = owner;
        this.title = title;

        synchronized (PermissionGroup.class) {
            List<PermissionGroup> allGroups = new ArrayList<PermissionGroup>(ALL);
            allGroups.add(this);
            Collections.sort(allGroups);
            ALL = Collections.unmodifiableList(allGroups);
        }

        PERMISSIONS.put(owner, this);
    }

    public Iterator<Permission> iterator() {
        return permisisons.iterator();
    }

    /*package*/ void add(Permission p) {
        permisisons.add(p);
    }

    /**
     * Lists up all the permissions in this group.
     */
    public List<Permission> getPermissions() {
        return permisisonsView;
    }

    /**
     * Finds a permission that has the given name.
     */
    public Permission find(String name) {
        for (Permission p : permisisons) {
            if (p.name.equals(name)) {
                return p;
            }
        }
        return null;
    }

    public int compareTo(PermissionGroup that) {
        // first, sort by the 'compare order' number. This is so that
        // we can put Hudson.PERMISSIONS first.
        int r = this.compareOrder() - that.compareOrder();
        if (r != 0) {
            return r;
        }

        // among the permissions of the same group, just sort by their names
        // so that the sort order is consistent regardless of classloading order.
        return this.owner.getName().compareTo(that.owner.getName());
    }

    private int compareOrder() {
        if (owner == Hudson.class) {
            return 0;
        }
        return 1;
    }

    public int size() {
        return permisisons.size();
    }
    /**
     * All groups. Sorted.
     */
    @CopyOnWrite
    private static List<PermissionGroup> ALL = Collections.emptyList();

    /**
     * Returns all the {@link PermissionGroup}s available in the system.
     *
     * @return always non-null. Read-only.
     */
    public static List<PermissionGroup> getAll() {
        return ALL;
    }

    /**
     * Gets the {@link PermissionGroup} whose {@link PermissionGroup#owner} is
     * the given class.
     *
     * @return null if not found.
     */
    public static PermissionGroup get(Class owner) {
        return PERMISSIONS.get(owner);
    }
    /**
     * All the permissions in the system, keyed by their owners.
     */
    private static final Map<Class, PermissionGroup> PERMISSIONS = new ConcurrentHashMap<Class, PermissionGroup>();
}
