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

package hudson.util;

import hudson.model.AbstractBuild;
import hudson.model.Item;
import hudson.model.Job;
import hudson.model.Node;
import hudson.model.Result;
import hudson.model.Run;
import hudson.model.View;

import java.util.AbstractList;
import java.util.Calendar;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.GregorianCalendar;
import java.util.Iterator;
import java.util.List;

/**
 * {@link List} of {@link Run}s, sorted in the descending date order.
 *
 * TODO: this should be immutable
 *
 * @author Kohsuke Kawaguchi
 */
public class RunList<R extends Run> extends AbstractRunList<R> {

    public RunList() {
    }

    public RunList(Job j) {
        addAll(j.getBuilds());
    }

    @Override
    public R getFirstBuild() {
        return isEmpty() ? null : get(size() - 1);
    }

    @Override
    public R getLastBuild() {
        return isEmpty() ? null : get(0);
    }

    public RunList(View view) {// this is a type unsafe operation
        for (Item item : view.getItems()) {
            for (Job<?, ?> j : item.getAllJobs()) {
                addAll((Collection<R>) j.getBuilds());
            }
        }
        Collections.sort(this, Run.ORDER_BY_DATE);
    }

    public RunList(Collection<? extends Job> jobs) {
        for (Job j : jobs) {
            addAll(j.getBuilds());
        }
        Collections.sort(this, Run.ORDER_BY_DATE);
    }

    private RunList(Collection<? extends R> c, boolean dummy) {
        super(c);
    }

    public static <R extends Run> RunList<R> fromRuns(Collection<? extends R> runs) {
        return new RunList<R>(runs, false);
    }

    /**
     * Filter the list to non-successful builds only.
     */
    @Override
    public RunList<R> failureOnly() {
        for (Iterator<R> itr = iterator(); itr.hasNext();) {
            Run r = itr.next();
            if (r.getResult() == Result.SUCCESS) {
                itr.remove();
            }
        }
        return this;
    }

    /**
     * Filter the list to builds on a single node only
     */
    @Override
    public RunList<R> node(Node node) {
        for (Iterator<R> itr = iterator(); itr.hasNext();) {
            Run r = itr.next();
            if (!(r instanceof AbstractBuild) || ((AbstractBuild) r).getBuiltOn() != node) {
                itr.remove();
            }
        }
        return this;
    }

    /**
     * Filter the list to regression builds only.
     */
    @Override
    public RunList<R> regressionOnly() {
        for (Iterator<R> itr = iterator(); itr.hasNext();) {
            Run r = itr.next();
            if (!r.getBuildStatusSummary().isWorse) {
                itr.remove();
            }
        }
        return this;
    }

    /**
     * Filter the list by timestamp.
     *
     * {@code s&lt=;e}.
     */
    @Override
    public RunList<R> byTimestamp(long start, long end) {
        AbstractList<Long> TIMESTAMP_ADAPTER = new AbstractList<Long>() {
            public Long get(int index) {
                return RunList.this.get(index).getTimeInMillis();
            }

            public int size() {
                return RunList.this.size();
            }
        };
        Comparator<Long> DESCENDING_ORDER = new Comparator<Long>() {
            public int compare(Long o1, Long o2) {
                if (o1 > o2) {
                    return -1;
                }
                if (o1 < o2) {
                    return +1;
                }
                return 0;
            }
        };

        int s = Collections.binarySearch(TIMESTAMP_ADAPTER, start, DESCENDING_ORDER);
        if (s < 0) {
            s = -(s + 1);   // min is inclusive
        }
        int e = Collections.binarySearch(TIMESTAMP_ADAPTER, end, DESCENDING_ORDER);
        if (e < 0) {
            e = -(e + 1);
        } else {
            e++;   // max is exclusive, so the exact match should be excluded
        }
        return fromRuns(subList(e, s));
    }

    /**
     * Reduce the size of the list by only leaving relatively new ones. This
     * also removes on-going builds, as RSS cannot be used to publish
     * information if it changes.
     */
    @Override
    public RunList<R> newBuilds() {
        GregorianCalendar threshold = new GregorianCalendar();
        threshold.add(Calendar.DAY_OF_YEAR, -7);

        int count = 0;

        for (Iterator<R> itr = iterator(); itr.hasNext();) {
            R r = itr.next();
            if (r.isBuilding()) {
                // can't publish on-going builds
                itr.remove();
                continue;
            }
            // at least put 10 items
            if (count < 10) {
                count++;
                continue;
            }
            // anything older than 7 days will be ignored
            if (r.getTimestamp().before(threshold)) {
                itr.remove();
            }
        }
        return this;
    }
}
