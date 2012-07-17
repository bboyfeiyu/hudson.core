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

import org.apache.commons.jelly.JellyTagException;
import org.springframework.security.SpringSecurityException;
import org.springframework.security.ui.ExceptionTranslationFilter;

import javax.servlet.Filter;
import javax.servlet.FilterConfig;
import javax.servlet.ServletException;
import javax.servlet.ServletRequest;
import javax.servlet.ServletResponse;
import javax.servlet.FilterChain;
import java.io.IOException;

/**
 * If {@link SpringSecurityException} caused {@link JellyTagException}, rethrow
 * it accordingly so that {@link ExceptionTranslationFilter} can pick it up and
 * initiate the redirection.
 *
 * @author Kohsuke Kawaguchi
 */
public class UnwrapSecurityExceptionFilter implements Filter {

    public void init(FilterConfig filterConfig) throws ServletException {
    }

    public void doFilter(ServletRequest request, ServletResponse response, FilterChain chain) throws IOException, ServletException {
        try {
            chain.doFilter(request, response);
        } catch (ServletException e) {
            Throwable t = e.getRootCause();
            if (t instanceof JellyTagException) {
                JellyTagException jte = (JellyTagException) t;
                Throwable cause = jte.getCause();
                if (cause instanceof SpringSecurityException) {
                    SpringSecurityException se = (SpringSecurityException) cause;
                    throw new ServletException(se);
                }
            }
            throw e;
        }
    }

    public void destroy() {
    }
}
