/*******************************************************************************
 *
 * Copyright (c) 2004-2010, Oracle Corporation.
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

package hudson.cli;

import hudson.model.Hudson;
import hudson.Extension;
import hudson.Functions;
import hudson.model.Item;
import hudson.model.Items;
import hudson.model.Job;
import hudson.model.TopLevelItem;
import hudson.util.IOUtils;
import java.io.File;
import java.io.IOException;
import java.io.PrintStream;
import org.eclipse.hudson.security.team.Team;
import org.eclipse.hudson.security.team.TeamManager;
import org.eclipse.hudson.security.team.TeamManager.TeamNotFoundException;
import org.kohsuke.args4j.Argument;

/**
 * Updates or creates a job by reading stdin as a configuration XML file.
 *
 * @author Henrik Lynggaard Hansen
 */
@Extension
public class UpdateJobCommand extends CLICommand {

    @Override
    public String getShortDescription() {
        return "Updates and potentionally creates a job by reading stdin as a configuration XML file.";
    }
    @Argument(metaVar = "NAME", usage = "Name of the job to update. Provide team qualified name if Team Management is enabled. Ex: team1.job1.", required = true)
    public String name;
    @Argument(metaVar = "CREATE", usage = "Create the job if needed", index = 1, required = true)
    public Boolean create;
    @Argument(metaVar = "TEAM", usage = "Team to create the job in", index = 1, required = false)
    public String team;

    protected int run() throws Exception {
        Hudson h = Hudson.getInstance();
        TeamManager teamManager = Hudson.getInstance().getTeamManager();
        TopLevelItem item = h.getItem(name);
        Team targetTeam = validateTeam(team, create, stderr);

        if (team != null && targetTeam == null) {
            return -1;
        }

        if (item == null && !create) {
            stderr.println("Job '" + name + "' does not exist and create is set to false");
            return -1;
        }

        if (item == null) {
            h.checkPermission(Item.CREATE);
            TopLevelItem newItem = h.createProjectFromXML(name, stdin);
            ensureJobInTeam(newItem, targetTeam, name, stderr);
        } else {
            try {
                h.checkPermission(Job.CONFIGURE);
                File rootDirOfJob = teamManager.getRootFolderForJob(item.getName()); 
                // place it as config.xml
                File configXml = Items.getConfigFile(rootDirOfJob).getFile();
                IOUtils.copy(stdin, configXml);

                h.reloadProjectFromDisk(configXml.getParentFile());
            } catch (IOException e) {
                throw e;
            }
        }
        return 0;
    }
    
    /**
     * If job is not in targetTeam, move it there.
     * 
     * @param item job
     * @param targetTeam desired team or null if none
     * @param name job name requested
     * @param stderr
     * @throws Exception 
     */
    public static void ensureJobInTeam(TopLevelItem item, Team targetTeam, String name, PrintStream stderr) throws Exception {
        if (targetTeam != null) {
            try {
                Hudson.getInstance().getTeamManager().ensureJobInTeam(item, targetTeam, name);
            } catch (IOException e) {
                item.delete();
                stderr.println("Unable to create job "+ name +" in team " + targetTeam.getName() + " due to IOException");
                throw e;
            }
        }
    }
    
    /**
     * Validate team exists and user can access it.
     * 
     * @param team team name
     * @param create true if create new job
     * @param stderr
     * @return 
     */
    public static Team validateTeam(String team, boolean create, PrintStream stderr) {
        Hudson h = Hudson.getInstance();
        TeamManager teamManager = h.getTeamManager();
        Team targetTeam = null;
        if (team != null) {
            if (!create) {
                stderr.println("team may only be used for create - for update use fully qualified name");
            } else if (!teamManager.isTeamManagementEnabled()) {
                stderr.println("team may not be specified unless team management is enabled");
            } else {
                try {
                    // check team exists first for better error message
                    targetTeam = teamManager.findTeam(team);
                    if (!teamManager.isCurrentUserHasAccessToTeam(team)) {
                        stderr.println("Current user does not have access to team "+team);
                        targetTeam = null;
                    }
                } catch (TeamNotFoundException e) {
                    stderr.println("Team "+team+" does not exist");
                }
            }
        }
        return targetTeam;
    }
}
