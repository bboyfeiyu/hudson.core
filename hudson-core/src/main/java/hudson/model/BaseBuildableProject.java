/*******************************************************************************
 *
 * Copyright (c) 2011 Oracle Corporation.
 *
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *
 *    Anton Kozak
 *
 *******************************************************************************/
package hudson.model;

import hudson.Functions;
import hudson.model.Descriptor.FormException;
import hudson.tasks.BuildStep;
import hudson.tasks.BuildWrapper;
import hudson.tasks.Builder;
import hudson.tasks.Publisher;
import hudson.triggers.Trigger;
import hudson.util.DescribableList;
import hudson.util.DescribableListUtil;
import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.logging.Level;
import java.util.logging.Logger;
import net.sf.json.JSONObject;
import org.eclipse.hudson.api.model.IProject;
import org.eclipse.hudson.api.model.project.property.BaseProjectProperty;
import org.eclipse.hudson.api.model.project.property.ExternalProjectProperty;
import org.kohsuke.stapler.StaplerRequest;

/**
 * Base buildable project.
 *
 * @author Anton Kozak.
 */
public abstract class BaseBuildableProject<P extends BaseBuildableProject<P,B>,B extends Build<P,B>>
    extends AbstractProject<P, B>
    implements Saveable, BuildableItemWithBuildWrappers, IProject {

    public static final String BUILDERS_PROPERTY_NAME = "builders";
    public static final String BUILD_WRAPPERS_PROPERTY_NAME = "buildWrappers";

    private static final Logger LOGGER = Logger.getLogger(BaseBuildableProject.class.getName());
    /**
     * List of active {@link Builder}s configured for this project.
     *
     * @deprecated as of 2.2.0
     *             don't use this field directly, logic was moved to {@link org.eclipse.hudson.api.model.IProjectProperty}.
     *             Use getter/setter for accessing to this field.
     */
    @Deprecated
    private DescribableList<Builder,Descriptor<Builder>> builders =
            new DescribableList<Builder,Descriptor<Builder>>(this);

    /**
     * List of active {@link Publisher}s configured for this project.
     *
     * @deprecated as of 2.2.0
     *             don't use this field directly, logic was moved to {@link org.eclipse.hudson.api.model.IProjectProperty}.
     *             Use getter/setter for accessing to this field.
     */
    @Deprecated
    private DescribableList<Publisher,Descriptor<Publisher>> publishers =
            new DescribableList<Publisher,Descriptor<Publisher>>(this);

    /**
     * List of active {@link BuildWrapper}s configured for this project.
     *
     * @deprecated as of 2.2.0
     *             don't use this field directly, logic was moved to {@link org.eclipse.hudson.api.model.IProjectProperty}.
     *             Use getter/setter for accessing to this field.
     */
    @Deprecated
    private DescribableList<BuildWrapper,Descriptor<BuildWrapper>> buildWrappers =
            new DescribableList<BuildWrapper,Descriptor<BuildWrapper>>(this);

    /**
     * Creates a new project.
     * @param parent parent {@link ItemGroup}.
     * @param name the name of the project.
     */
    public BaseBuildableProject(ItemGroup parent, String name) {
        super(parent, name);
    }

    @Override
    public void onLoad(ItemGroup<? extends Item> parent, String name) throws IOException {
        super.onLoad(parent, name);

        getBuildersList().setOwner(this);
        getPublishersList().setOwner(this);
        getBuildWrappersList().setOwner(this);
    }

    @Override
    protected void buildProjectProperties() throws IOException {
        super.buildProjectProperties();
        convertBuildersProjectProperty();
        convertBuildWrappersProjectProperties();
        convertPublishersProperties();
    }

    protected void buildDependencyGraph(DependencyGraph graph) {
        getPublishersList().buildDependencyGraph(this,graph);
        getBuildersList().buildDependencyGraph(this,graph);
        getBuildWrappersList().buildDependencyGraph(this,graph);
    }

    @Override
    protected List<Action> createTransientActions() {
        List<Action> r = super.createTransientActions();

        for (BuildStep step : getBuildersList())
            r.addAll(step.getProjectActions(this));
        for (BuildStep step : getPublishersList())
            r.addAll(step.getProjectActions(this));
        for (BuildWrapper step : getBuildWrappersList())
            r.addAll(step.getProjectActions(this));
        for (Trigger trigger : getTriggersList())
            r.addAll(trigger.getProjectActions());

        return r;
    }

    /**
     * @inheritDoc
     */
    public List<Builder> getBuilders() {
        return getBuildersList().toList();
    }

    /**
     * @inheritDoc
     */
    @SuppressWarnings("unchecked")
    public DescribableList<Builder,Descriptor<Builder>> getBuildersList() {
        return getDescribableListProjectProperty(BUILDERS_PROPERTY_NAME).getValue();
    }

    /**
     * @inheritDoc
     */
    public void setBuilders(DescribableList<Builder,Descriptor<Builder>> builders) {
        getDescribableListProjectProperty(BUILDERS_PROPERTY_NAME).setValue(builders);
    }

    /**
     * @inheritDoc
     */
    public Map<Descriptor<Publisher>,Publisher> getPublishers() {
        return getPublishersList().toMap();
    }

    public Publisher getPublisher(Descriptor<Publisher> descriptor) {
        return (Publisher) getExternalProjectProperty(descriptor.getJsonSafeClassName()).getValue();
    }
    /**
     * Returns the list of the publishers available in the hudson.
     *
     * @return the list of the publishers available in the hudson.
     */
    @SuppressWarnings("unchecked")
    public DescribableList<Publisher, Descriptor<Publisher>> getPublishersList() {
        List<Descriptor<Publisher>> descriptors = Functions.getPublisherDescriptors(this);
        List<Publisher> publisherList = new CopyOnWriteArrayList<Publisher>();
        DescribableList<Publisher, Descriptor<Publisher>> result
            = new DescribableList<Publisher, Descriptor<Publisher>>(this);
        for (Descriptor<Publisher> descriptor : descriptors) {
            ExternalProjectProperty<Publisher> property = getExternalProjectProperty(descriptor.getJsonSafeClassName());
            if (null != property.getValue()) {
                publisherList.add(property.getValue());
            }
        }
        try {
            result.addAll(publisherList);
        } catch (IOException e) {
            LOGGER.log(Level.WARNING, "Failed to add publishers", e);
        }
        return result;
    }

    /**
     * @inheritDoc
     */
    public Map<Descriptor<BuildWrapper>,BuildWrapper> getBuildWrappers() {
        return getBuildWrappersList().toMap();
    }

    /**
     * @inheritDoc
     */
    @SuppressWarnings("unchecked")
    public DescribableList<BuildWrapper, Descriptor<BuildWrapper>> getBuildWrappersList() {
        return getDescribableListProjectProperty(BUILD_WRAPPERS_PROPERTY_NAME).getValue();
    }

    /**
     * Sets build wrappers.
     *
     * @param buildWrappers buildWrappers.
     */
    public void setBuildWrappers(DescribableList<BuildWrapper, Descriptor<BuildWrapper>> buildWrappers) {
        getDescribableListProjectProperty(BUILD_WRAPPERS_PROPERTY_NAME).setValue(buildWrappers);
    }

    /**
     * Builds publishers.
     * @param req {@link StaplerRequest}
     * @param json {@link JSONObject}
     * @param descriptors list of descriptors.
     * @throws hudson.model.Descriptor.FormException if any.
     */
    @SuppressWarnings("unchecked")
    protected void buildPublishers( StaplerRequest req, JSONObject json, List<Descriptor<Publisher>> descriptors) throws FormException{
        for (Descriptor<Publisher> d : descriptors) {
            String name = d.getJsonSafeClassName();
            ExternalProjectProperty<Publisher> baseProperty = getExternalProjectProperty(name);
            Publisher publisher = null;
            if (json.has(name)) {
                publisher = d.newInstance(req, json.getJSONObject(name));
            }
            baseProperty.setValue(publisher);
        }
    }

    protected void convertPublishersProperties() {
        if (null != publishers) {
            putAllProjectProperties(DescribableListUtil.convertToProjectProperties(publishers, this), false);
            publishers = null;
        }
    }

    protected void convertBuildWrappersProjectProperties() {
        if (null == getProperty(BUILD_WRAPPERS_PROPERTY_NAME)) {
            setBuildWrappers(buildWrappers);
            buildWrappers = null;
        }
    }

    protected void convertBuildersProjectProperty() {
        if (null != builders && null == getProperty(BUILDERS_PROPERTY_NAME)) {
            setBuilders(builders);
            builders = null;
        }
    }
}
