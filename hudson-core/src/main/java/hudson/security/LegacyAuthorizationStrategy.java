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

import hudson.model.Descriptor;
import hudson.model.Hudson;
import hudson.Extension;
import org.springframework.security.acls.sid.GrantedAuthoritySid;
import org.kohsuke.stapler.StaplerRequest;
import net.sf.json.JSONObject;

import java.util.Collection;
import java.util.Collections;

/**
 * {@link AuthorizationStrategy} implementation that emulates the legacy behavior.
 * @author Kohsuke Kawaguchi
 */
public final class LegacyAuthorizationStrategy extends AuthorizationStrategy {
    private static final ACL LEGACY_ACL = new SparseACL(null) {{
        add(EVERYONE,Permission.READ,true);
        add(new GrantedAuthoritySid("admin"), Hudson.ADMINISTER,true);
    }};

    public ACL getRootACL() {
        return LEGACY_ACL;
    }

    public Collection<String> getGroups() {
        return Collections.singleton("admin");
    }

    @Extension
    public static final class DescriptorImpl extends Descriptor<AuthorizationStrategy> {
        public String getDisplayName() {
            return Messages.LegacyAuthorizationStrategy_DisplayName();
        }

        public String getHelpFile() {
            return "/help/security/legacy-auth-strategy.html";
        }

        public LegacyAuthorizationStrategy newInstance(StaplerRequest req, JSONObject formData) throws FormException {
            return new LegacyAuthorizationStrategy();
        }
    }
}
