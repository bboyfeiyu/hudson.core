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
*    Kohsuke Kawaguchi, Jorg Heymans, Red Hat, Inc., id:cactusman
 *     
 *
 *******************************************************************************/ 

package hudson.matrix;

import hudson.CopyOnWrite;
import hudson.Extension;
import hudson.Util;
import hudson.XmlFile;
import hudson.model.AbstractProject;
import hudson.model.Action;
import hudson.model.BuildableItemWithBuildWrappers;
import hudson.model.DependencyGraph;
import hudson.model.Descriptor;
import hudson.model.Descriptor.FormException;
import hudson.model.Hudson;
import hudson.model.Item;
import hudson.model.ItemGroup;
import hudson.model.Items;
import hudson.model.JDK;
import hudson.model.Job;
import hudson.model.Label;
import hudson.model.Queue.FlyweightTask;
import hudson.model.Result;
import hudson.model.SCMedItem;
import hudson.model.Saveable;
import hudson.model.TopLevelItem;
import hudson.tasks.BuildStep;
import hudson.tasks.BuildStepDescriptor;
import hudson.tasks.BuildWrapper;
import hudson.tasks.BuildWrappers;
import hudson.tasks.Builder;
import hudson.tasks.Publisher;
import hudson.triggers.Trigger;
import hudson.util.CopyOnWriteMap;
import hudson.util.DescribableList;
import hudson.util.FormValidation;
import hudson.util.FormValidation.Kind;
import net.sf.json.JSONObject;
import org.apache.commons.lang3.ObjectUtils;
import org.eclipse.hudson.api.matrix.IMatrixProject;
import org.kohsuke.stapler.HttpResponse;
import org.kohsuke.stapler.StaplerRequest;
import org.kohsuke.stapler.StaplerResponse;
import org.kohsuke.stapler.TokenList;

import javax.servlet.ServletException;
import java.io.File;
import java.io.FileFilter;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * {@link Job} that allows you to run multiple different configurations
 * from a single setting.
 *
 * @author Kohsuke Kawaguchi
 */
public class MatrixProject extends AbstractProject<MatrixProject, MatrixBuild> implements IMatrixProject, TopLevelItem,
    SCMedItem, ItemGroup<MatrixConfiguration>, Saveable, FlyweightTask, BuildableItemWithBuildWrappers {

    protected static final String HAS_COMBINATION_FILTER_PARAM = "hasCombinationFilter";
    protected static final String COMBINATION_FILTER_PARAM = "combinationFilter";
    protected static final String HAS_TOUCH_STONE_COMBINATION_FILTER_PARAM = "hasTouchStoneCombinationFilter";
    protected static final String TOUCH_STONE_COMBINATION_FILTER_PARAM = "touchStoneCombinationFilter";
    protected static final String TOUCH_STONE_RESULT_CONDITION_PARAM = "touchStoneResultCondition";
    protected static final String CUSTOM_WORKSPACE_PARAM = "customWorkspace";
    protected static final String CUSTOM_WORKSPACE_DIRECTORY_PARAM = "customWorkspace.directory";

    /**
     * Configuration axes.
     */
    private volatile AxisList axes; //TODO verify it = new AxisList();

    /**
     * The filter that is applied to combinations. It is a Groovy if condition.
     * This can be null, which means "true".
     * Package visible for the tests only.
     *
     * @see #getCombinationFilter()
     */
    volatile String combinationFilter;

    /**
     * List of active {@link Builder}s configured for this project.
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
     */
    private DescribableList<BuildWrapper,Descriptor<BuildWrapper>> buildWrappers =
            new DescribableList<BuildWrapper,Descriptor<BuildWrapper>>(this);

    /**
     * All {@link MatrixConfiguration}s, keyed by their {@link MatrixConfiguration#getName() names}.
     */
    private transient /*final*/ Map<Combination,MatrixConfiguration> configurations = new CopyOnWriteMap.Tree<Combination,MatrixConfiguration>();

    /**
     * @see #getActiveConfigurations()
     */
    @CopyOnWrite
    private transient /*final*/ Set<MatrixConfiguration> activeConfigurations = new LinkedHashSet<MatrixConfiguration>();

    //package visible for the tests only.
    Boolean runSequentially;

    /**
     * Filter to select a number of combinations to build first.
     * Package visible for the tests only.
     */
    String touchStoneCombinationFilter;

    /**
     * Required result on the touchstone combinations, in order to
     * continue with the rest.
     * Package visible for the tests only.
     */
    Result touchStoneResultCondition;

    /**
     * See {@link #setCustomWorkspace(String)}.
     */
    private String customWorkspace;

    public MatrixProject(String name) {
        this(Hudson.getInstance(), name);
    }

    public MatrixProject(ItemGroup parent, String name) {
        super(parent, name);
    }

    /**
     * @inheritDoc
     */
    public AxisList getAxes() {
        return axes!= null ? axes : (hasCascadingProject()? getCascadingProject().getAxes() : null);
    }

    /**
     * @inheritDoc
     */
    public void setAxes(AxisList axes) throws IOException {
        if (!(hasCascadingProject() && ObjectUtils.equals(getCascadingProject().getAxes(), axes))) {
            this.axes = new AxisList(axes);
        } else {
            this.axes = null;
        }
        // TODO verify me
        rebuildConfigurations();
        save();
    }

    /**
     * @inheritDoc
     */
    public boolean isRunSequentially() {
        return runSequentially!= null ? runSequentially : (hasCascadingProject() && getCascadingProject().isRunSequentially());
    }

    /**
     * @deprecated use #setRunSequentially(Boolean runSequentially) instead
     */
    @Deprecated
    public void setRunSequentially(boolean runSequentially) throws IOException {
        setRunSequentially(Boolean.valueOf(runSequentially));
    }

    /**
     * @inheritDoc
     */
    public void setRunSequentially(Boolean runSequentially) throws IOException {
        if (!(hasCascadingProject() && ObjectUtils.equals(getCascadingProject().isRunSequentially(), runSequentially))) {
            this.runSequentially = runSequentially;
        } else {
            this.runSequentially = null;
        }
        // TODO verify me
        save();
    }

    /**
     * @inheritDoc
     */
    public void setCombinationFilter(String combinationFilter) throws IOException {
        if (!(hasCascadingProject() && ObjectUtils.equals(getCascadingProject().getCombinationFilter(), combinationFilter))) {
            this.combinationFilter = combinationFilter;
        } else {
            this.combinationFilter = null;
        }
        rebuildConfigurations();
        save();
    }

    /**
     * @inheritDoc
     */
    public String getCombinationFilter() {
        return combinationFilter != null ? combinationFilter
            : (hasCascadingProject() ? getCascadingProject().getCombinationFilter() : null);
    }

    /**
     * @inheritDoc
     */
    public String getTouchStoneCombinationFilter() {
        return touchStoneCombinationFilter != null ? touchStoneCombinationFilter
            : (hasCascadingProject() ? getCascadingProject().getTouchStoneCombinationFilter() : null);
    }

    /**
     * @inheritDoc
     */
    public void setTouchStoneCombinationFilter(String touchStoneCombinationFilter) {
        if (!(hasCascadingProject() && ObjectUtils.equals(getCascadingProject().getTouchStoneCombinationFilter(),
            touchStoneCombinationFilter))) {
            this.touchStoneCombinationFilter = touchStoneCombinationFilter;
        } else {
            this.touchStoneCombinationFilter = null;
        }
    }

    /**
     * @inheritDoc
     */
    public Result getTouchStoneResultCondition() {
        return touchStoneResultCondition != null ? touchStoneResultCondition
            : (hasCascadingProject() ? getCascadingProject().getTouchStoneResultCondition() : null);
    }

    /**
     * @inheritDoc
     */
    public void setTouchStoneResultCondition(Result touchStoneResultCondition) {
        if (!(hasCascadingProject() && ObjectUtils.equals(getCascadingProject().getTouchStoneResultCondition(),
            touchStoneResultCondition))) {
            this.touchStoneResultCondition = touchStoneResultCondition;
        } else {
            this.touchStoneResultCondition = null;
        }
    }

    /**
     * @inheritDoc
     */
    public String getCustomWorkspace() {
        return customWorkspace != null ? customWorkspace
            : (hasCascadingProject() ? getCascadingProject().getCustomWorkspace() : null);
    }

    /**
     * @inheritDoc
     */
    public void setCustomWorkspace(String customWorkspace) throws IOException {
        if (!(hasCascadingProject() && ObjectUtils.equals(getCascadingProject().getTouchStoneResultCondition(),
            customWorkspace))) {
            this.customWorkspace = customWorkspace;
        } else {
            this.customWorkspace = null;
        }
    }

    /**
     * @inheritDoc
     */
    public List<Builder> getBuilders() {
        DescribableList<Builder,Descriptor<Builder>> buildersList = getBuildersList();
        return (buildersList != null ? buildersList.toList() : null);
    }

    //TODO improve it
    public DescribableList<Builder,Descriptor<Builder>> getBuildersList() {
        return !(builders == null || builders.isEmpty()) ? builders
            : (hasCascadingProject() ? getCascadingProject().getBuildersList() : null);
    }

    /**
     * @inheritDoc
     */
    public Map<Descriptor<Publisher>,Publisher> getPublishers() {
        DescribableList<Publisher,Descriptor<Publisher>> publishersList = getPublishersList();
        return (publishersList != null ? publishersList.toMap() : null);
    }

    /**
     * @inheritDoc
     */
    //TODO improve it
    public DescribableList<Publisher,Descriptor<Publisher>> getPublishersList() {
        return !(publishers == null || publishers.isEmpty()) ? publishers
            : (hasCascadingProject() ? getCascadingProject().getPublishersList() : null);
    }

    /**
     * @inheritDoc
     */
    //TODO improve it
    public DescribableList<BuildWrapper, Descriptor<BuildWrapper>> getBuildWrappersList() {
        return !(buildWrappers == null || buildWrappers.isEmpty()) ? buildWrappers
            : (hasCascadingProject() ? getCascadingProject().getBuildWrappersList() : null);
    }

    /**
     * @inheritDoc
     */
    public Map<Descriptor<BuildWrapper>,BuildWrapper> getBuildWrappers() {
        DescribableList<BuildWrapper, Descriptor<BuildWrapper>> buildWrappersList = getBuildWrappersList();
        return (buildWrappersList != null ? buildWrappersList.toMap() : null);
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
     * Gets the subset of {@link AxisList} that are not system axes.
     *
     * @deprecated as of 1.373
     *      System vs user difference are generalized into extension point.
     */
    public List<Axis> getUserAxes() {
        List<Axis> r = new ArrayList<Axis>();
        for (Axis a : axes)
            if(!a.isSystem())
                r.add(a);
        return r;
    }

    public Layouter<MatrixConfiguration> getLayouter() {
        return new Layouter<MatrixConfiguration>(axes) {
            protected MatrixConfiguration getT(Combination c) {
                return getItem(c);
            }
        };
    }

    @Override
    public void onLoad(ItemGroup<? extends Item> parent, String name) throws IOException {
        super.onLoad(parent, name);
        Collections.sort(axes); // perhaps the file was edited on disk and the sort order might have been broken
        getBuildersList().setOwner(this);
        getPublishersList().setOwner(this);
        getBuildWrappersList().setOwner(this);
        rebuildConfigurations();
    }

    @Override
    public void logRotate() throws IOException, InterruptedException {
        super.logRotate();
        // perform the log rotation of inactive configurations to make sure
        // their logs get eventually discarded
        for (MatrixConfiguration config : configurations.values()) {
            if(!config.isActiveConfiguration())
                config.logRotate();
        }
    }

    /**
     * Recursively search for configuration and put them to the map
     *
     * <p>
     * The directory structure would be <tt>axis-a/b/axis-c/d/axis-e/f</tt> for
     * combination [a=b,c=d,e=f]. Note that two combinations [a=b,c=d] and [a=b,c=d,e=f]
     * can both co-exist (where one is an archived record and the other is live, for example)
     * so search needs to be thorough.
     *
     * @param dir
     *      Directory to be searched.
     * @param result
     *      Receives the loaded {@link MatrixConfiguration}s.
     * @param combination
     *      Combination of key/values discovered so far while traversing the directories.
     *      Read-only.
     */
    private void loadConfigurations( File dir, CopyOnWriteMap.Tree<Combination,MatrixConfiguration> result, Map<String,String> combination ) {
        File[] axisDirs = dir.listFiles(new FileFilter() {
            public boolean accept(File child) {
                return child.isDirectory() && child.getName().startsWith("axis-");
            }
        });
        if(axisDirs==null)      return;

        for (File subdir : axisDirs) {
            String axis = subdir.getName().substring(5);    // axis name

            File[] valuesDir = subdir.listFiles(new FileFilter() {
                public boolean accept(File child) {
                    return child.isDirectory();
                }
            });
            if(valuesDir==null) continue;   // no values here

            for (File v : valuesDir) {
                Map<String,String> c = new HashMap<String, String>(combination);
                c.put(axis,TokenList.decode(v.getName()));

                try {
                    XmlFile config = Items.getConfigFile(v);
                    if(config.exists()) {
                        Combination comb = new Combination(c);
                        // if we already have this in memory, just use it.
                        // otherwise load it
                        MatrixConfiguration item=null;
                        if(this.configurations!=null)
                            item = this.configurations.get(comb);
                        if(item==null) {
                            item = (MatrixConfiguration) config.read();
                            item.setCombination(comb);
                            item.onLoad(this, v.getName());
                        }
                        result.put(item.getCombination(), item);
                    }
                } catch (IOException e) {
                    LOGGER.log(Level.WARNING, "Failed to load matrix configuration "+v,e);
                }
                loadConfigurations(v,result,c);
            }
        }
    }

    /**
     * Rebuilds the {@link #configurations} list and {@link #activeConfigurations}.
     */
    void rebuildConfigurations() throws IOException {
        {
            // backward compatibility check to see if there's any data in the old structure
            // if so, bring them to the newer structure.
            File[] oldDirs = getConfigurationsDir().listFiles(new FileFilter() {
                public boolean accept(File child) {
                    return child.isDirectory() && !child.getName().startsWith("axis-");
                }
            });
            if(oldDirs!=null) {
                // rename the old directory to the new one
                for (File dir : oldDirs) {
                    try {
                        Combination c = Combination.fromString(dir.getName());
                        dir.renameTo(getRootDirFor(c));
                    } catch (IllegalArgumentException e) {
                        // it's not a configuration dir. Just ignore.
                    }
                }
            }
        }

        CopyOnWriteMap.Tree<Combination,MatrixConfiguration> configurations =
            new CopyOnWriteMap.Tree<Combination,MatrixConfiguration>();
        loadConfigurations(getConfigurationsDir(),configurations,Collections.<String,String>emptyMap());
        this.configurations = configurations;

        // find all active configurations
        Set<MatrixConfiguration> active = new LinkedHashSet<MatrixConfiguration>();
        for (Combination c : axes.list()) {
            if(c.evalGroovyExpression(axes,combinationFilter)) {
        		LOGGER.fine("Adding configuration: " + c);
	            MatrixConfiguration config = configurations.get(c);
	            if(config==null) {
	                config = new MatrixConfiguration(this,c);
	                config.save();
	                configurations.put(config.getCombination(), config);
	            }
	            active.add(config);
        	}
        }
        this.activeConfigurations = active;
    }

    private File getConfigurationsDir() {
        return new File(getRootDir(),"configurations");
    }

    /**
     * Gets all active configurations.
     * <p>
     * In contract, inactive configurations are those that are left for archival purpose
     * and no longer built when a new {@link MatrixBuild} is executed.
     */
    public Collection<MatrixConfiguration> getActiveConfigurations() {
        return activeConfigurations;
    }

    public Collection<MatrixConfiguration> getItems() {
        return configurations.values();
    }

    @Override
    public Collection<? extends Job> getAllJobs() {
        Set<Job> jobs = new HashSet<Job>(getItems());
        jobs.add(this);
        return jobs;
    }

    public String getUrlChildPrefix() {
        return ".";
    }

    public MatrixConfiguration getItem(String name) {
        return getItem(Combination.fromString(name));
    }

    public MatrixConfiguration getItem(Combination c) {
        return configurations.get(c);
    }

    public File getRootDirFor(MatrixConfiguration child) {
        return getRootDirFor(child.getCombination());
    }

    public void onRenamed(MatrixConfiguration item, String oldName, String newName) throws IOException {
        throw new UnsupportedOperationException();
    }

    public void onDeleted(MatrixConfiguration item) throws IOException {
        // noop
    }

    public File getRootDirFor(Combination combination) {
        File f = getConfigurationsDir();
        for (Entry<String, String> e : combination.entrySet())
            f = new File(f,"axis-"+e.getKey()+'/'+Util.rawEncode(e.getValue()));
        f.getParentFile().mkdirs();
        return f;
    }

    /**
     * @see #getJDKs()
     */
    @Override @Deprecated
    public JDK getJDK() {
        return super.getJDK();
    }

    /**
     * Gets the {@link JDK}s where the builds will be run.
     * @return never null but can be empty
     */
    public Set<JDK> getJDKs() {
        Axis a = axes.find("jdk");
        if(a==null)  return Collections.emptySet();
        Set<JDK> r = new HashSet<JDK>();
        for (String j : a) {
            JDK jdk = Hudson.getInstance().getJDK(j);
            if(jdk!=null)
                r.add(jdk);
        }
        return r;
    }

    /**
     * Gets the {@link Label}s where the builds will be run.
     * @return never null
     */
    public Set<Label> getLabels() {
        Set<Label> r = new HashSet<Label>();
        for (Combination c : axes.subList(LabelAxis.class).list())
            r.add(Hudson.getInstance().getLabel(Util.join(c.values(),"&&")));
        return r;
    }

    public Publisher getPublisher(Descriptor<Publisher> descriptor) {
        for (Publisher p : publishers) {
            if(p.getDescriptor()==descriptor)
                return p;
        }
        return null;
    }

    protected Class<MatrixBuild> getBuildClass() {
        return MatrixBuild.class;
    }

    public boolean isFingerprintConfigured() {
        return false;
    }

    protected void buildDependencyGraph(DependencyGraph graph) {
        getPublishersList().buildDependencyGraph(this,graph);
        getBuildersList().buildDependencyGraph(this,graph);
        getBuildWrappersList().buildDependencyGraph(this,graph);
    }

    public MatrixProject asProject() {
        return this;
    }

    @Override
    public Object getDynamic(String token, StaplerRequest req, StaplerResponse rsp) {
        try {
            MatrixConfiguration item = getItem(token);
            if(item!=null)
            return item;
        } catch (IllegalArgumentException _) {
            // failed to parse the token as Combination. Must be something else
        }
        return super.getDynamic(token,req,rsp);
    }

    @Override
    protected void submit(StaplerRequest req, StaplerResponse rsp) throws IOException, ServletException, FormException {
        super.submit(req, rsp);

        JSONObject json = req.getSubmittedForm();

        setCombinationFilter(
            req.getParameter(HAS_COMBINATION_FILTER_PARAM) != null ? Util.nullify(req.getParameter(
                COMBINATION_FILTER_PARAM)) : null);

        if (req.getParameter(HAS_TOUCH_STONE_COMBINATION_FILTER_PARAM)!=null) {
            setTouchStoneCombinationFilter(Util.nullify(req.getParameter(TOUCH_STONE_COMBINATION_FILTER_PARAM)));
            setTouchStoneResultCondition(Result.fromString(req.getParameter(TOUCH_STONE_RESULT_CONDITION_PARAM)));
        } else {
            setTouchStoneCombinationFilter(null);
        }

        setCustomWorkspace(
            req.hasParameter(CUSTOM_WORKSPACE_PARAM) ? req.getParameter(CUSTOM_WORKSPACE_DIRECTORY_PARAM) : null);

        // parse system axes
        DescribableList<Axis,AxisDescriptor> newAxes = new DescribableList<Axis,AxisDescriptor>(this);
        newAxes.rebuildHetero(req, json, Axis.all(),"axis");
        checkAxisNames(newAxes);
        this.axes = new AxisList(newAxes.toList());

        runSequentially = json.has("runSequentially");

        getBuildWrappersList().rebuild(req, json, BuildWrappers.getFor(this));
        getBuildersList().rebuildHetero(req, json, Builder.all(), "builder");
        getPublishersList().rebuild(req, json, BuildStepDescriptor.filter(Publisher.all(),this.getClass()));

        rebuildConfigurations();
    }

    /**
     * Verifies that Axis names are valid and unique.
     */
    private void checkAxisNames(Iterable<Axis> newAxes) throws FormException {
        HashSet<String> axisNames = new HashSet<String>();
        for (Axis a : newAxes) {
            FormValidation fv = a.getDescriptor().doCheckName(a.getName());
            if (fv.kind!=Kind.OK)
                throw new FormException(Messages.MatrixProject_DuplicateAxisName(),fv,"axis.name");

            if (axisNames.contains(a.getName()))
                throw new FormException(Messages.MatrixProject_DuplicateAxisName(),"axis.name");
            axisNames.add(a.getName());
        }
    }

    /**
     * Also delete all the workspaces of the configuration, too.
     */
    @Override
    public HttpResponse doDoWipeOutWorkspace() throws IOException, ServletException, InterruptedException {
        HttpResponse rsp = super.doDoWipeOutWorkspace();
        for (MatrixConfiguration c : configurations.values())
            c.doDoWipeOutWorkspace();
        return rsp;
    }


    public DescriptorImpl getDescriptor() {
        return DESCRIPTOR;
    }

    @Extension
    public static final DescriptorImpl DESCRIPTOR = new DescriptorImpl();

    public static final class DescriptorImpl extends AbstractProjectDescriptor {
        public String getDisplayName() {
            return Messages.MatrixProject_DisplayName();
        }

        public MatrixProject newInstance(ItemGroup parent, String name) {
            return new MatrixProject(parent,name);
        }

        /**
         * All {@link AxisDescriptor}s that contribute to the UI.
         */
        public List<AxisDescriptor> getAxisDescriptors() {
            List<AxisDescriptor> r = new ArrayList<AxisDescriptor>();
            for (AxisDescriptor d : Axis.all()) {
                if (d.isInstantiable())
                    r.add(d);
            }
            return r;
        }
    }

    /**
     * For the unit tests only. Sets cascadingProject for the job.
     *
     * @param cascadingProject parent job
     */
    void setCascadingProject(MatrixProject cascadingProject) {
        this.cascadingProject = cascadingProject;
    }

    /**
     * For the unit tests only.
     *
     * @param allowSave allow set.
     */
    void setAllowSave(Boolean allowSave) {
        this.allowSave.set(allowSave);
    }

    private static final Logger LOGGER = Logger.getLogger(MatrixProject.class.getName());
}
