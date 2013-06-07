/*
 * Copyright (c) 2013 Oracle Corporation.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *    Winston Prakash
 */
package org.eclipse.hudson.security.team;

import com.thoughtworks.xstream.XStream;
import hudson.BulkChange;
import hudson.Util;
import hudson.XmlFile;
import hudson.model.Hudson;
import hudson.model.Item;
import hudson.model.Job;
import hudson.model.Saveable;
import hudson.model.listeners.SaveableListener;
import hudson.security.ACL;
import hudson.security.Permission;
import hudson.security.SecurityRealm;
import hudson.util.FormValidation;
import hudson.util.XStream2;
import java.io.File;
import java.io.IOException;
import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServletResponse;
import org.eclipse.hudson.security.HudsonSecurityEntitiesHolder;
import org.eclipse.hudson.security.HudsonSecurityManager;
import org.kohsuke.stapler.HttpResponse;
import org.kohsuke.stapler.HttpResponses;
import org.kohsuke.stapler.QueryParameter;
import org.kohsuke.stapler.StaplerRequest;
import org.kohsuke.stapler.StaplerResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Manger that manages the teams and their persistence
 *
 * @since 3.1.0
 * @author Winston Prakash
 */
public final class TeamManager implements Saveable {

    private List<String> sysAdmins = new CopyOnWriteArrayList<String>();
    private List<Team> teams = new CopyOnWriteArrayList<Team>();
    private transient final XStream xstream = new XStream2();
    private transient Logger logger = LoggerFactory.getLogger(TeamManager.class);
    private transient File hudsonHomeDir;
    private transient File teamsFolder;
    private transient final String teamsConfigFileName = "teams.xml";
    private transient PublicTeam publicTeam;
    private transient final String TEAMS_FOLDER_NAME = "teams";

    public TeamManager(File homeDir) {
        hudsonHomeDir = homeDir;
        teamsFolder = new File(hudsonHomeDir, TEAMS_FOLDER_NAME);
        if (!teamsFolder.exists()) {
            teamsFolder.mkdirs();
        }
        initializeXstream();
        load();
        ensurePublicTeam();
    }

    public void addSysAdmin(String adminName) throws IOException {
        if (!sysAdmins.contains(adminName)) {
            sysAdmins.add(adminName);
            save();
        }
    }

    public void removeSysAdmin(String adminName) throws IOException {
        if (sysAdmins.contains(adminName)) {
            sysAdmins.remove(adminName);
            save();
        }
    }

    public List<String> getSysAdmins() {
        return sysAdmins;
    }

    public boolean isCurrentUserSysAdmin() {
        String currentUser = HudsonSecurityManager.getAuthentication().getName();
        return isSysAdmin(currentUser);
    }

    public boolean isCurrentUserTeamAdmin() {
        String currentUser = HudsonSecurityManager.getAuthentication().getName();
        if (isCurrentUserSysAdmin()) {
            return true;
        }
        Team team = findCurrentUserTeam();
        if (team != null) {
            return team.isAdmin(currentUser);
        }
        return false;
    }

    public boolean isSysAdmin(String userName) {
        boolean isSysAdmin;
        HudsonSecurityManager hudsonSecurityManager = HudsonSecurityEntitiesHolder.getHudsonSecurityManager();
        SecurityRealm securityRealm = null;
        if (hudsonSecurityManager != null) {
            securityRealm = hudsonSecurityManager.getSecurityRealm();
        }
        if ((securityRealm != null) && securityRealm instanceof TeamAwareSecurityRealm) {
            TeamAwareSecurityRealm teamAwareSecurityRealm = (TeamAwareSecurityRealm) securityRealm;
            isSysAdmin = teamAwareSecurityRealm.isCurrentUserSysAdmin();
        } else {
            isSysAdmin = sysAdmins.contains(userName);
        }
        return isSysAdmin;
    }

    public List<Team> getTeams() {
        return teams;
    }

    public Team createTeam(String teamName, String description) throws IOException, TeamAlreadyExistsException {
        for (Team team : teams) {
            if (teamName.equals(team.getName())) {
                throw new TeamAlreadyExistsException(teamName);
            }
        }

        Team newTeam = new Team(teamName, description, this);
        teams.add(newTeam);
        save();
        return newTeam;
    }

    public Team createTeam(String teamName) throws IOException, TeamAlreadyExistsException {
        return createTeam(teamName, teamName);
    }

    public void deleteTeam(String teamName) throws TeamNotFoundException, IOException {
        Team team = findTeam(teamName);
        teams.remove(team);
        save();
    }

    public HttpResponse doCreateTeam(@QueryParameter String teamName, @QueryParameter String description) throws IOException {
        if (!isCurrentUserSysAdmin()) {
            return new TeamUtils.ErrorHttpResponse("No permission to create team.");
        }
        if ((teamName == null) || "".equals(teamName.trim())) {
            return new TeamUtils.ErrorHttpResponse("Team name required.");
        }
        try {
            Team team = createTeam(teamName, description);
            return HttpResponses.forwardToView(team, "team.jelly");
        } catch (TeamAlreadyExistsException ex) {
            return new TeamUtils.ErrorHttpResponse(ex.getLocalizedMessage());
        }
    }

    public HttpResponse doDeleteTeam(@QueryParameter String teamName) throws IOException {
        if (!isCurrentUserSysAdmin()) {
            return new TeamUtils.ErrorHttpResponse("No permission to delete team.");
        }
        if ((teamName == null) || "".equals(teamName.trim())) {
            return new TeamUtils.ErrorHttpResponse("Team name required.");
        }
        try {
            deleteTeam(teamName);
            return HttpResponses.ok();
        } catch (TeamNotFoundException ex) {
            return new TeamUtils.ErrorHttpResponse(ex.getLocalizedMessage());
        }
    }

    public HttpResponse doAddTeamMember(@QueryParameter String teamName,
            @QueryParameter String teamMemberSid,
            @QueryParameter boolean isTeamAdmin,
            @QueryParameter boolean canCreate,
            @QueryParameter boolean canDelete,
            @QueryParameter boolean canConfigure) throws IOException {
        if (!isCurrentUserTeamAdmin()) {
            return new TeamUtils.ErrorHttpResponse("No permission to add team member.");
        }
        if ((teamName == null) || "".equals(teamName.trim())) {
            return new TeamUtils.ErrorHttpResponse("Team name required.");
        }
        if ((teamMemberSid == null) || "".equals(teamMemberSid.trim())) {
            return new TeamUtils.ErrorHttpResponse("Team member name required.");
        }
        Team team;
        try {
            team = findTeam(teamName);
        } catch (TeamNotFoundException ex) {
            return new TeamUtils.ErrorHttpResponse(teamName + " is not a valid team.");
        }

        if (team.findTeamMember(teamMemberSid) == null) {
            team.addMember(teamMemberSid, isTeamAdmin, canCreate, canDelete, canConfigure);
            return FormValidation.respond(FormValidation.Kind.OK, TeamUtils.getIcon(teamMemberSid));
        } else {
            return new TeamUtils.ErrorHttpResponse(teamMemberSid + " is already a team member.");
        }
    }
    
    public HttpResponse doUpdateTeamMember(@QueryParameter String teamName,
            @QueryParameter String teamMemberSid,
            @QueryParameter boolean isTeamAdmin,
            @QueryParameter boolean canCreate,
            @QueryParameter boolean canDelete,
            @QueryParameter boolean canConfigure) throws IOException {
        if (!isCurrentUserTeamAdmin()) {
            return new TeamUtils.ErrorHttpResponse("No permission to add team member.");
        }
        if ((teamName == null) || "".equals(teamName.trim())) {
            return new TeamUtils.ErrorHttpResponse("Team name required.");
        }
        if ((teamMemberSid == null) || "".equals(teamMemberSid.trim())) {
            return new TeamUtils.ErrorHttpResponse("Team member name required.");
        }
        Team team;
        try {
            team = findTeam(teamName);
        } catch (TeamNotFoundException ex) {
            return new TeamUtils.ErrorHttpResponse(teamName + " is not a valid team.");
        }
        TeamMember currentMember = team.findTeamMember(teamMemberSid);
        if (currentMember != null) {
            team.updateMember(teamMemberSid, isTeamAdmin, canCreate, canDelete, canConfigure);
            return FormValidation.respond(FormValidation.Kind.OK, TeamUtils.getIcon(teamMemberSid));
        } else {
            return new TeamUtils.ErrorHttpResponse(teamMemberSid + " is not a team member.");
        }
    }

    public HttpResponse doRemoveTeamMember(@QueryParameter String teamName, 
            @QueryParameter String teamMemberSid) throws IOException {
        if (!isCurrentUserTeamAdmin()) {
            return new TeamUtils.ErrorHttpResponse("No permission to remove team member");
        }
        if ((teamName == null) || "".equals(teamName.trim())) {
            return new TeamUtils.ErrorHttpResponse("Team name required.");
        }
        if ((teamMemberSid == null) || "".equals(teamMemberSid.trim())) {
            return new TeamUtils.ErrorHttpResponse("Team member name required.");
        }
        Team team;
        try {
            team = findTeam(teamName);
        } catch (TeamNotFoundException ex) {
            return new TeamUtils.ErrorHttpResponse(teamName + " is not a valid team.");
        }
        TeamMember currentMember = team.findTeamMember(teamMemberSid);
        if (currentMember != null) {
            team.removeMember(teamMemberSid);
            return HttpResponses.ok();
        } else {
            return new TeamUtils.ErrorHttpResponse(teamMemberSid + " is not a team member.");
        }
    }

    public HttpResponse doMoveJob(@QueryParameter String jobId, @QueryParameter String teamName) throws IOException {
        if (!isCurrentUserTeamAdmin()) {
            return new TeamUtils.ErrorHttpResponse("No permission to remove team member");
        }
        if ((teamName == null) || "".equals(teamName.trim())) {
            return new TeamUtils.ErrorHttpResponse("Team name required.");
        }
        if ((jobId == null) || "".equals(jobId.trim())) {
            return new TeamUtils.ErrorHttpResponse("Job name required.");
        }
        Team newTeam;
        try {
            newTeam = findTeam(teamName);
        } catch (TeamNotFoundException ex) {
            return new TeamUtils.ErrorHttpResponse(teamName + " is not a valid team.");
        }

        Team oldTeam = findJobOwnerTeam(jobId);
        if (oldTeam == null) {
            return new TeamUtils.ErrorHttpResponse(jobId + " does not belong to any team.");
        }

        Item item = Hudson.getInstance().getItemById(jobId);
        Job job;
        if (item instanceof Job<?, ?>) {
            job = (Job) item;
            if (job.isBuilding()) {
                return new TeamUtils.ErrorHttpResponse(job.getName() + " is building.");
            }
            try {
                if (moveJob(job, oldTeam, newTeam)) {
                    return HttpResponses.ok();
                } else {
                    return new TeamUtils.ErrorHttpResponse("Faile to move the jon " + jobId
                            + " to the team " + teamName);
                }
            } catch (IOException ex) {
                return new TeamUtils.ErrorHttpResponse(teamName + " is not a valid team.");
            }
        } else {
            return new TeamUtils.ErrorHttpResponse(jobId + " not a valid job Id.");
        }
    }

    public HttpResponse doCheckSid(@QueryParameter String sid) throws IOException {
        return FormValidation.respond(FormValidation.Kind.OK, TeamUtils.getIcon(sid));
    }

    private boolean moveJob(Job job, Team oldTeam, Team newTeam) throws IOException {
        try {
            File jobRootDir = job.getRootDir();
            File newJobRootDir = new File(getJobsFolder(newTeam), job.getName());
            newJobRootDir.mkdirs();
            if (Util.moveDirectory(jobRootDir, newJobRootDir)) {
                String oldJobId = job.getId();
                oldTeam.removeJob(job.getId());
                String newJobId = getTeamQualifiedJobId(newTeam, job.getName());
                job.setId(newJobId);
                job.save();
                newTeam.addJob(newJobId);
                Hudson.getInstance().replaceItemId(oldJobId, newJobId);
                return true;
            }
            return false;
        } catch (Exception exc) {
            throw new IOException(exc);
        }
    }

    /**
     * Get the name of the teams as JSON
     *
     * @return HttpResponse with JSON as content type
     */
    public HttpResponse doGetTeamsJson() {
        return new HttpResponse() {
            @Override
            public void generateResponse(StaplerRequest sr, StaplerResponse rsp, Object o) throws IOException, ServletException {
                rsp.setStatus(HttpServletResponse.SC_OK);
                rsp.setContentType("application/json");
                PrintWriter w = new PrintWriter(rsp.getWriter());
                w.println("{");
                for (int i = 0; i < teams.size(); i++) {
                    w.print("\"" + teams.get(i).getName() + "\":\"" + teams.get(i).getName() + "\"");
                    if (i < teams.size() - 1) {
                        w.println(",");
                    }
                }
                w.println("}");
                w.close();
            }
        };

    }

    /* For Unit Test */
    void addUser(String teamName, String userName) throws TeamNotFoundException, IOException {
        Team team = findTeam(teamName);
        team.addMember(userName, false, false, false, false);
        save();
    }

    public Team findTeam(String teamName) throws TeamNotFoundException {
        for (Team team : teams) {
            if (teamName.equals(team.getName())) {
                return team;
            }
        }
        throw new TeamNotFoundException(teamName);
    }

    public void removeTeam(String teamName) throws IOException, TeamNotFoundException {
        Team team = findTeam(teamName);
        teams.remove(team);
        save();
    }

    public Team findCurrentUserTeam() {
        Team team;
        HudsonSecurityManager hudsonSecurityManager = HudsonSecurityEntitiesHolder.getHudsonSecurityManager();
        SecurityRealm securityRealm = null;
        if (hudsonSecurityManager != null) {
            securityRealm = hudsonSecurityManager.getSecurityRealm();
        }
        if ((securityRealm != null) && securityRealm instanceof TeamAwareSecurityRealm) {
            TeamAwareSecurityRealm teamAwareSecurityRealm = (TeamAwareSecurityRealm) securityRealm;
            team = teamAwareSecurityRealm.GetCurrentUserTeam();
        } else {
            String currentUser = HudsonSecurityManager.getAuthentication().getName();
            team = findUserTeam(currentUser);
        }
        return team;
    }

    public boolean isCurrentUserHasAccess(String jobId) {
        Team userTeam = findCurrentUserTeam();
        if (userTeam != null) {
            if (userTeam.isJobOwner(jobId)) {
                return true;
            } else {
                return isAnonymousJob(jobId);
            }
        } else {
            return isAnonymousJob(jobId);
        }
    }

    public boolean isUserHasAccess(String userName, String jobId) {
        Team userTeam = findUserTeam(userName);
        if (userTeam != null) {
            return userTeam.isJobOwner(jobId);
        } else {
            for (Team team : teams) {
                if (team.isJobOwner(jobId)) {
                    // Job belongs to a team so has no access
                    return false;
                }
            }
            // Job does not belong to any team, so has access
            return true;
        }
    }

    public Team findUserTeam(String userName) {

        for (Team team : teams) {
            if (team.isMember(userName)) {
                return team;
            }
        }
        HudsonSecurityManager hudsonSecurityManager = HudsonSecurityEntitiesHolder.getHudsonSecurityManager();
        SecurityRealm securityRealm = null;
        if (hudsonSecurityManager != null) {
            securityRealm = hudsonSecurityManager.getSecurityRealm();
        }
        if ((securityRealm != null) && securityRealm instanceof TeamAwareSecurityRealm) {
            TeamAwareSecurityRealm teamAwareSecurityRealm = (TeamAwareSecurityRealm) securityRealm;
            return teamAwareSecurityRealm.GetCurrentUserTeam();
        }
        return null;
    }

    public Team findJobOwnerTeam(String jobId) {
        for (Team team : teams) {
            if (team.isJobOwner(jobId)) {
                return team;
            }
        }
        return null;
    }

    public void addJobToCurrentUserTeam(String jobId) throws IOException {
        addJob(findCurrentUserTeam(), jobId);
    }

    public void addJobToUserTeam(String userName, String jobId) throws IOException {
        addJob(findUserTeam(userName), jobId);

    }

    public void addJob(Team team, String jobId) throws IOException {
        if (team != null) {
            team.addJob(jobId);
            save();
        }
    }

    public void removeJobFromCurrentUserTeam(String jobId) throws IOException {
        removeJob(findCurrentUserTeam(), jobId);
    }

    public void removeJobFromUserTeam(String userName, String jobId) throws IOException {
        removeJob(findUserTeam(userName), jobId);
    }

    public void removeJob(Team team, String jobId) throws IOException {
        if (team != null) {
            team.removeJob(jobId);
            save();
        }
    }

    public void renameJobInCurrentUserTeam(String oldJobName, String newJobName) throws IOException {
        renameJob(findCurrentUserTeam(), oldJobName, newJobName);
    }

    public void renameJobInUserTeam(String userName, String oldJobName, String newJobName) throws IOException {
        renameJob(findUserTeam(userName), oldJobName, newJobName);
    }

    public void renameJob(Team team, String oldJobName, String newJobName) throws IOException {
        if (team != null) {
            team.renameJob(oldJobName, newJobName);
            save();
        }
    }

    /**
     * Save the settings to the configuration file.
     */
    @Override
    public synchronized void save() throws IOException {
        if (useBulkSaveFlag && BulkChange.contains(this)) {
            return;
        }
        getConfigFile().write(this);
        if (useBulkSaveFlag) {
            SaveableListener.fireOnChange(this, getConfigFile());
        }
    }

    /**
     * Get the current user team qualified Id for the job name
     *
     * @param jobName
     * @return String, Qualified Job ID
     */
    public String getTeamQualifiedJobId(String jobName) {
        if (publicTeam.isJobOwner(jobName)) {
            return jobName;
        }

        Team team = findCurrentUserTeam();
        if ((team != null) && !Team.PUBLIC_TEAM_NAME.equals(team.getName())) {
            jobName = team.getName() + "." + jobName;
        }
        return jobName;
    }

    /**
     * Get the current user team qualified Id for the job name
     *
     * @param team
     * @param jobName
     * @return String, Team qualified Job ID
     */
    public String getTeamQualifiedJobId(Team team, String jobName) {
        if (team == publicTeam) {
            return jobName;
        }
        return team.getName() + "." + jobName;
    }

    /**
     * The Folder where all the jobs of the team to which this jobId belongs to
     * are stored
     *
     * @param jobId
     * @return String, path to the team jobs folder
     */
    public String getJobsFolderName(String jobId) {
        Team team = findJobOwnerTeam(jobId);
        // May be just created job
        if (team == null) {
            team = findCurrentUserTeam();
        }
        if ((team != null) && !Team.PUBLIC_TEAM_NAME.equals(team.getName())) {
            return TEAMS_FOLDER_NAME + "/" + team.getName() + "/" + Team.JOBS_FOLDER_NAME;
        }
        return "jobs";
    }

    /**
     * Get the folder where the jobs of this team are stored
     *
     * @param team
     * @return
     */
    public File getJobsFolder(Team team) {
        if ((team != null) && !Team.PUBLIC_TEAM_NAME.equals(team.getName())) {
            return new File(teamsFolder, "/" + team.getName() + "/" + Team.JOBS_FOLDER_NAME);
        } else {
            return new File(hudsonHomeDir, "jobs");
        }
    }

    /**
     * Get the root folders of all the jobs known to this Team manager
     *
     * @return
     */
    public File[] getJobsRootFolders() {
        List<File> jobsRootFolders = new ArrayList<File>();
        for (Team team : teams) {
            if (Team.PUBLIC_TEAM_NAME.equals(team.getName())) {
                jobsRootFolders.addAll(team.getJobsRootFolders(hudsonHomeDir));
            } else {
                jobsRootFolders.addAll(team.getJobsRootFolders(teamsFolder));
            }
        }
        return jobsRootFolders.toArray(new File[jobsRootFolders.size()]);
    }

    public static class TeamNotFoundException extends Exception {

        public TeamNotFoundException(String teamName) {
            super("Team " + teamName + " does not exist.");
        }
    }

    public static class TeamAlreadyExistsException extends Exception {

        public TeamAlreadyExistsException(String teamName) {
            super("Team " + teamName + " already exists.");
        }
    }

    void setUseBulkSaveFlag(boolean flag) {
        useBulkSaveFlag = flag;
    }

    /**
     * Get an ACL that provides system wide authorization
     *
     * @return TeamBasedACL with SYSTEM scope
     */
    ACL getRoolACL() {
        return new TeamBasedACL(this, TeamBasedACL.SCOPE.GLOBAL);
    }

    /**
     * Get an ACL that provides job specific authorization
     *
     * @return TeamBasedACL with JOB scope
     */
    ACL getACL(Job<?, ?> job) {
        return new TeamBasedACL(this, TeamBasedACL.SCOPE.JOB, job);
    }

    /**
     * Get an ACL that provides team specific authorization
     *
     * @return TeamBasedACL with JOB scope
     */
    ACL getACL(Team team) {
        return new TeamBasedACL(this, TeamBasedACL.SCOPE.JOB, team);
    }

    Team getPublicTeam() throws TeamNotFoundException {
        return findTeam(PublicTeam.PUBLIC_TEAM_NAME);
    }

    private boolean isAnonymousJob(String jobId) {
        for (Team team : teams) {
            if (team.isJobOwner(jobId)) {
                // job belongs to another team so has no access
                return false;
            }
        }
        // Not belong to any team, so has access
        return true;
    }

    /**
     * The file where the teams settings are saved.
     */
    private XmlFile getConfigFile() {
        return new XmlFile(xstream, new File(teamsFolder, teamsConfigFileName));
    }
    // This is purely fo unit test. Since Hudson is not fully loaded during
    // test BulkChange saving mode is not available
    private transient boolean useBulkSaveFlag = true;

    /**
     * Load the settings from the configuration file
     */
    private void load() {
        XmlFile config = getConfigFile();
        try {
            if (config.exists()) {
                config.unmarshal(this);
            }
        } catch (IOException e) {
            logger.error("Failed to load " + config, e);
        }
    }

    private void ensurePublicTeam() {
        publicTeam = new PublicTeam(this);
        try {
            Team team = findTeam(PublicTeam.PUBLIC_TEAM_NAME);
            teams.remove(team);
        } catch (TeamNotFoundException ex) {
            // It's ok, we are going to remove it any way
        }
        try {
            publicTeam.loadExistingJobs(hudsonHomeDir);
        } catch (IOException ex) {
            logger.error("Failed to load existing jobs", ex);
        }
        teams.add(publicTeam);
    }

    private void initializeXstream() {
        xstream.alias("teamManager", TeamManager.class);
        xstream.alias("team", Team.class);
        xstream.alias("publicTeam", PublicTeam.class);
    }
}
