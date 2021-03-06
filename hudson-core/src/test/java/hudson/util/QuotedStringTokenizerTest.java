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

import junit.framework.TestCase;

import java.util.Arrays;

/**
 * @author Kohsuke Kawaguchi
 */
public class QuotedStringTokenizerTest extends TestCase {
    private void check(String src, String... expected) {
        String[] r = QuotedStringTokenizer.tokenize(src);
        System.out.println(Arrays.asList(r));
        assertTrue(Arrays.equals(r, expected));
    }

    public void test1() {
        check("foo bar",
              "foo","bar");
    }


    public void test2() {
        check("foo \"bar zot\"",
              "foo","bar zot");
    }

    public void test3() {
        check("foo bar=\"quote zot\"",
              "foo","bar=quote zot");
    }

    public void test4() {
        check("foo\\\"",
              "foo\"");
    }

    public void test5() {
        check("foo\\ bar",
              "foo bar");
    }

    public void test6() {
        check("foo\\\\ bar",
              "foo\\","bar");
    }

    // see http://www.nabble.com/Error-parsing-%22-in-msbuild-task-to20535754.html
    public void test7() {
        check("foo=\"bar\\zot\"",
              "foo=bar\\zot");
    }
}
