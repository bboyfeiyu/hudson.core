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

import hudson.console.ConsoleNote;
import hudson.console.HyperlinkNote;
import hudson.util.AbstractTaskListener;
import hudson.util.NullStream;
import hudson.util.StreamTaskListener;

import java.io.IOException;
import java.io.PrintStream;
import java.io.PrintWriter;
import java.io.Serializable;
import java.util.Formatter;

/**
 * Receives events that happen during some lengthy operation that has some
 * chance of failures, such as a build, SCM change polling, slave launch, and so
 * on.
 *
 * <p> This interface is implemented by Hudson core and passed to extension
 * points so that they can record the progress of the operation without really
 * knowing how those information and handled/stored by Hudson.
 *
 * <p> The information is one way or the other made available to users, and so
 * the expectation is that when something goes wrong, enough information shall
 * be written to a {@link TaskListener} so that the user can diagnose what's
 * going wrong.
 *
 * <p> {@link StreamTaskListener} is the most typical implementation of this
 * interface. All the {@link TaskListener} implementations passed to plugins
 * from Hudson core are remotable.
 *
 * @see AbstractTaskListener
 * @author Kohsuke Kawaguchi
 */
public interface TaskListener extends Serializable {

    /**
     * This writer will receive the output of the build
     *
     * @return must be non-null.
     */
    PrintStream getLogger();

    /**
     * Annotates the current position in the output log by using the given
     * annotation. If the implementation doesn't support annotated output log,
     * this method might be no-op.
     *
     * @since 1.349
     */
    void annotate(ConsoleNote ann) throws IOException;

    /**
     * Places a {@link HyperlinkNote} on the given text.
     *
     * @param url If this starts with '/', it's interpreted as a path within the
     * context path.
     */
    void hyperlink(String url, String text) throws IOException;

    /**
     * An error in the build.
     *
     * @return A writer to receive details of the error. Not null.
     */
    PrintWriter error(String msg);

    /**
     * {@link Formatter#format(String, Object[])} version of
     * {@link #error(String)}.
     */
    PrintWriter error(String format, Object... args);

    /**
     * A fatal error in the build.
     *
     * @return A writer to receive details of the error. Not null.
     */
    PrintWriter fatalError(String msg);

    /**
     * {@link Formatter#format(String, Object[])} version of
     * {@link #fatalError(String)}.
     */
    PrintWriter fatalError(String format, Object... args);
    /**
     * {@link TaskListener} that discards the output.
     */
    public static final TaskListener NULL = new StreamTaskListener(new NullStream());
}
