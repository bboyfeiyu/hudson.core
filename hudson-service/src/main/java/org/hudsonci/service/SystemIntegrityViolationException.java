/*******************************************************************************
 *
 * Copyright (c) 2010-2011 Sonatype, Inc.
 *
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *
 *
 *
 *
 *******************************************************************************/ 

package org.hudsonci.service;

/**
 * Thrown when the integrity of the system would be compromised if a requested
 * operation was allowed to proceed.
 *
 * @author plynch
 * @since 2.1.0
 */
public class SystemIntegrityViolationException
        extends ServiceRuntimeException {

    public SystemIntegrityViolationException() {
    }

    public SystemIntegrityViolationException(String message) {
        super(message);
    }

    public SystemIntegrityViolationException(String message, Throwable cause) {
        super(message, cause);
    }

    public SystemIntegrityViolationException(Throwable cause) {
        super(cause);
    }
}
