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

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.io.StringWriter;

/**
 * Represents a text file.
 *
 * Provides convenience methods for reading and writing to it.
 *
 * @author Kohsuke Kawaguchi
 */
public class TextFile {

    public final File file;

    public TextFile(File file) {
        this.file = file;
    }

    public boolean exists() {
        return file.exists();
    }

    public void delete() {
        file.delete();
    }

    /**
     * Reads the entire contents and returns it.
     */
    public String read() throws IOException {
        StringWriter out = new StringWriter();
        PrintWriter w = new PrintWriter(out);
        BufferedReader in = new BufferedReader(new InputStreamReader(new FileInputStream(file), "UTF-8"));
        try {
            String line;
            while ((line = in.readLine()) != null) {
                w.println(line);
            }
        } finally {
            in.close();
        }
        return out.toString();
    }

    /**
     * Overwrites the file by the given string.
     */
    public void write(String text) throws IOException {
        file.getParentFile().mkdirs();
        AtomicFileWriter w = new AtomicFileWriter(file);
        try {
            w.write(text);
            w.commit();
        } finally {
            w.abort();
            IOUtils.closeQuietly(w);
        }
    }

    public String readTrim() throws IOException {
        return read().trim();
    }

    @Override
    public String toString() {
        return file.toString();
    }
}
