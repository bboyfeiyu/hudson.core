/*******************************************************************************
 *
 * Copyright (c) 2004-2011 Oracle Corporation.
 *
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors: 
 *
 *    Kohsuke Kawaguchi, Jorg Heymans, Stephen Connolly, Tom Huybrechts, Anton Kozak, Nikita Levyankov
 *     
 *
 *******************************************************************************/ 

package hudson.model;

import hudson.Util;
import hudson.diagnosis.OldDataMonitor;
import hudson.model.Descriptor.FormException;
import hudson.tasks.BuildStep;
import hudson.tasks.BuildStepDescriptor;
import hudson.tasks.BuildWrapper;
import hudson.tasks.BuildWrappers;
import hudson.tasks.Builder;
import hudson.tasks.Fingerprinter;
import hudson.tasks.Publisher;
import hudson.tasks.Maven;
import hudson.tasks.Maven.ProjectWithMaven;
import hudson.tasks.Maven.MavenInstallation;
import hudson.triggers.Trigger;
import hudson.util.DescribableList;
import hudson.util.DescribableListUtil;
import net.sf.json.JSONObject;
import org.eclipse.hudson.api.model.IProject;
import org.kohsuke.stapler.StaplerRequest;
import org.kohsuke.stapler.StaplerResponse;

import javax.servlet.ServletException;
import java.io.IOException;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Buildable software project.
 *
 * @author Kohsuke Kawaguchi
 */
public abstract class Project<P extends Project<P,B>,B extends Build<P,B>>
    extends AbstractProject<P,B> implements SCMedItem, Saveable, ProjectWithMaven, BuildableItemWithBuildWrappers,
    IProject {

    public static final String BUILDERS_PROPERTY_NAME = "builders";
    public static final String BUILD_WRAPPERS_PROPERTY_NAME = "buildWrappers";

    /**
     * List of active {@link Builder}s configured for this project.
     * @deprecated as of 2.2.0
     *             don't use this field directly, logic was moved to {@link org.eclipse.hudson.api.model.IProjectProperty}.
     *             Use getter/setter for accessing to this field.
     */
    private DescribableList<Builder,Descriptor<Builder>> builders =
            new DescribableList<Builder,Descriptor<Builder>>(this);

    /**
     * List of active {@link Publisher}s configured for this project.
     */
    private DescribableList<Publisher,Descriptor<Publisher>> publishers =
            new DescribableList<Publisher,Descriptor<Publisher>>(this);

    /**
     * List of active {@link BuildWrapper}s configured for this project.
     * @deprecated as of 2.2.0
     *             don't use this field directly, logic was moved to {@link org.eclipse.hudson.api.model.IProjectProperty}.
     *             Use getter/setter for accessing to this field.
     */
    private DescribableList<BuildWrapper,Descriptor<BuildWrapper>> buildWrappers =
            new DescribableList<BuildWrapper,Descriptor<BuildWrapper>>(this);

    /**
     * Creates a new project.
     */
    public Project(ItemGroup parent,String name) {
        super(parent,name);
    }

    @Override
    public void onLoad(ItemGroup<? extends Item> parent, String name) throws IOException {
        super.onLoad(parent, name);

        getBuildersList().setOwner(this);
        publishers.setOwner(this);
        getBuildWrappersList().setOwner(this);
    }

    @Override
    protected void buildProjectProperties() throws IOException {
        super.buildProjectProperties();
        if (null == getProperty(BUILDERS_PROPERTY_NAME)) {
            setBuilders(builders);
            builders = null;
        }
        if (null == getProperty(BUILD_WRAPPERS_PROPERTY_NAME)) {
            setBuildWrappers(buildWrappers);
            buildWrappers = null;
        }
    }

    public AbstractProject<?, ?> asProject() {
        return this;
    }

    public List<Builder> getBuilders() {
        return getBuildersList().toList();
    }

    public Map<Descriptor<Publisher>,Publisher> getPublishers() {
        return publishers.toMap();
    }

    public void setBuilders(DescribableList<Builder,Descriptor<Builder>> builders) {
        getDescribableListProjectProperty(BUILDERS_PROPERTY_NAME).setValue(builders);
    }

    public DescribableList<Builder,Descriptor<Builder>> getBuildersList() {
        return getDescribableListProjectProperty(BUILDERS_PROPERTY_NAME).getValue();
    }
    
    public DescribableList<Publisher,Descriptor<Publisher>> getPublishersList() {
        return publishers;
    }

    public Map<Descriptor<BuildWrapper>,BuildWrapper> getBuildWrappers() {
        return getBuildWrappersList().toMap();
    }

    public DescribableList<BuildWrapper, Descriptor<BuildWrapper>> getBuildWrappersList() {
        return getDescribableListProjectProperty(BUILD_WRAPPERS_PROPERTY_NAME).getValue();
    }

    public void setBuildWrappers(DescribableList<BuildWrapper, Descriptor<BuildWrapper>> buildWrappers) {
        getDescribableListProjectProperty(BUILD_WRAPPERS_PROPERTY_NAME).setValue(buildWrappers);
    }

    @Override
    protected Set<ResourceActivity> getResourceActivities() {
        final Set<ResourceActivity> activities = new HashSet<ResourceActivity>();

        activities.addAll(super.getResourceActivities());
        activities.addAll(Util.filter(getBuildersList(), ResourceActivity.class));
        activities.addAll(Util.filter(publishers,ResourceActivity.class));
        activities.addAll(Util.filter(getBuildWrappersList(), ResourceActivity.class));

        return activities;
    }

    /**
     * Adds a new {@link BuildStep} to this {@link Project} and saves the configuration.
     *
     * @deprecated as of 1.290
     *      Use {@code getPublishersList().add(x)}
     */
    public void addPublisher(Publisher buildStep) throws IOException {
        publishers.add(buildStep);
    }

    /**
     * Removes a publisher from this project, if it's active.
     *
     * @deprecated as of 1.290
     *      Use {@code getPublishersList().remove(x)}
     */
    public void removePublisher(Descriptor<Publisher> descriptor) throws IOException {
        publishers.remove(descriptor);
    }

    public Publisher getPublisher(Descriptor<Publisher> descriptor) {
        for (Publisher p : publishers) {
            if(p.getDescriptor()==descriptor)
                return p;
        }
        return null;
    }

    protected void buildDependencyGraph(DependencyGraph graph) {
        publishers.buildDependencyGraph(this,graph);
        getBuildersList().buildDependencyGraph(this, graph);
        getBuildWrappersList().buildDependencyGraph(this, graph);
    }

    @Override
    public boolean isFingerprintConfigured() {
        return getPublishersList().get(Fingerprinter.class)!=null;
    }

    public MavenInstallation inferMavenInstallation() {
        Maven m = getBuildersList().get(Maven.class);
        if (m!=null)    return m.getMaven();
        return null;
    }

//
//
// actions
//
//
    @Override
    protected void submit( StaplerRequest req, StaplerResponse rsp ) throws IOException, ServletException, FormException {
        super.submit(req,rsp);
        JSONObject json = req.getSubmittedForm();
        setBuildWrappers(DescribableListUtil.buildFromJson(this, req, json, BuildWrappers.getFor(this)));
        setBuilders(DescribableListUtil.buildFromHetero(this, req, json, "builder", Builder.all()));
        publishers.rebuild(req, json, BuildStepDescriptor.filter(Publisher.all(), this.getClass()));
    }

    @Override
    protected List<Action> createTransientActions() {
        List<Action> r = super.createTransientActions();

        for (BuildStep step : getBuildersList())
            r.addAll(step.getProjectActions(this));
        for (BuildStep step : getPublishersList())
            r.addAll(step.getProjectActions(this));
        for (BuildWrapper step : getBuildWrappers().values())
            r.addAll(step.getProjectActions(this));
        for (Trigger trigger : getTriggers().values())
            r.addAll(trigger.getProjectActions());

        return r;
    }

    /**
     * @deprecated since 2006-11-05.
     *      Left for legacy config file compatibility
     */
    @Deprecated
    private transient String slave;

    private Object readResolve() {
        if (slave != null) OldDataMonitor.report(this, "1.60");
        return this;
    }
}
