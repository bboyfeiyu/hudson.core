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

package hudson.model;

import hudson.CopyOnWrite;
import org.kohsuke.stapler.export.ExportedBean;
import org.kohsuke.stapler.export.Exported;

/**
 * Scalar value that changes over the time (such as load average, Q length, # of
 * executors, etc.)
 *
 * <p> This class computes <a
 * href="http://en.wikipedia.org/wiki/Moving_average#Exponential_moving_average">
 * the exponential moving average</a> from the raw data (to be supplied by
 * {@link #update(float)}).
 *
 * @author Kohsuke Kawaguchi
 */
@ExportedBean
public final class TimeSeries {

    /**
     * Decay ratio. Normally 1-e for some small e.
     */
    private final float decay;
    /**
     * Historical exponential moving average data. Newer ones first.
     */
    @CopyOnWrite
    private volatile float[] history;
    /**
     * Maximum history size.
     */
    private final int historySize;

    public TimeSeries(float initialValue, float decay, int historySize) {
        this.history = new float[]{initialValue};
        this.decay = decay;
        this.historySize = historySize;
    }

    /**
     * Pushes a new data point.
     *
     * <p> Exponential moving average is calculated, and the {@link #history} is
     * updated. This method needs to be called periodically and regularly, and
     * it represents the raw data stream.
     */
    public void update(float newData) {
        float data = history[0] * decay + newData * (1 - decay);

        float[] r = new float[Math.min(history.length + 1, historySize)];
        System.arraycopy(history, 0, r, 1, Math.min(history.length, r.length - 1));
        r[0] = data;
        history = r;
    }

    /**
     * Gets the history data of the exponential moving average. The returned
     * array should be treated as read-only and immutable.
     *
     * @return Always non-null, contains at least one entry.
     */
    @Exported
    public float[] getHistory() {
        return history;
    }

    /**
     * Gets the most up-to-date data point value. {@code getHistory[0]}.
     */
    @Exported
    public float getLatest() {
        return history[0];
    }

    @Override
    public String toString() {
        return Float.toString(history[0]);
    }
}
