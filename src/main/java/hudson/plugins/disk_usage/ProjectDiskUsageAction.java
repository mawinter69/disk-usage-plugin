package hudson.plugins.disk_usage;

import hudson.model.AbstractBuild;
import hudson.model.AbstractProject;
import hudson.model.ItemGroup;
import hudson.model.ProminentProjectAction;
import hudson.model.Run;
import hudson.util.Graph;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.stream.Collectors;
import jenkins.model.Jenkins;
import org.jfree.data.category.DefaultCategoryDataset;
import org.kohsuke.stapler.export.ExportedBean;

/**
 * Disk usage of a project
 * 
 * @author dvrzalik
 */
@ExportedBean(defaultVisibility = 1)
public class ProjectDiskUsageAction implements ProminentProjectAction {

    AbstractProject<? extends AbstractProject<?,?>, ? extends AbstractBuild<?,?>> project;

    public ProjectDiskUsageAction(AbstractProject<? extends AbstractProject<?,?>, ? extends AbstractBuild<?,?>> project) {
        this.project = project;
    }

    @Override
    public String getIconFileName() {
        return null;
    }

    @Override
    public String getDisplayName() {
        return Messages.displayName();
    }

    @Override
    public String getUrlName() {
        return Messages.urlName();
    }

    public Long getDiskUsageWorkspace() {
        DiskUsageProperty property = project.getProperty(DiskUsageProperty.class);
        if(property == null) {
            DiskUsageUtil.addProperty(project);
            property = project.getProperty(DiskUsageProperty.class);
        }
        return property.getAllWorkspaceSize();
    }

    @Deprecated(forRemoval = true)
    public Long getAllSlaveWorkspaces() {
        return getAllAgentWorkspaces();
    }

    public Long getAllAgentWorkspaces() {
        return getAllDiskUsageWorkspace() - getAllCustomOrNonAgentWorkspaces();
    }

    @Deprecated(forRemoval = true)
    public Long getAllCustomOrNonSlaveWorkspaces() {
        return getAllCustomOrNonAgentWorkspaces();
    }

    public Long getAllCustomOrNonAgentWorkspaces() {
        Long diskUsage = 0L;
        DiskUsageProperty property = project.getProperty(DiskUsageProperty.class);
        if(property != null) {
            diskUsage += property.getAllNonAgentOrCustomWorkspaceSize();
        }
        if(project instanceof ItemGroup) {
            ItemGroup<?> group = (ItemGroup<?>) project;
            for(Object i:group.getItems()) {
                if(i instanceof AbstractProject) {
                    AbstractProject<?,?> p = (AbstractProject<?,?>) i;
                    DiskUsageProperty prop = p.getProperty(DiskUsageProperty.class);
                    if(prop != null) {
                        diskUsage += prop.getAllNonAgentOrCustomWorkspaceSize();
                    }
                }
            }
        }
        return diskUsage;
    }

    /**
     * Returns all workspace disku usage including workspace usage its sub-projects
     * 
     * @return disk usage project and its sub-projects
     */
    public Long getAllDiskUsageWorkspace() {
        Long diskUsage = 0L;
        DiskUsageProperty property = project.getProperty(DiskUsageProperty.class);
        if(property != null) {
            diskUsage += property.getAllWorkspaceSize();
        }
        if(project instanceof ItemGroup) {
            ItemGroup<?> group = (ItemGroup<?>) project;
            for(Object i:group.getItems()) {
                if(i instanceof AbstractProject) {
                    AbstractProject<?,?> p = (AbstractProject<?,?>) i;
                    DiskUsageProperty prop = p.getProperty(DiskUsageProperty.class);
                    if(prop != null) {
                        diskUsage += prop.getAllWorkspaceSize();
                    }
                }
            }
        }
        return diskUsage;
    }

    public String getSizeInString(Long size) {
        return DiskUsageUtil.getSizeString(size);
    }

    public Long getDiskUsageWithoutBuilds() {
        DiskUsageProperty property = project.getProperty(DiskUsageProperty.class);
        if(property == null) {
            DiskUsageUtil.addProperty(project);
            property = project.getProperty(DiskUsageProperty.class);
        }
        return property.getDiskUsageWithoutBuilds();
    }

    public Long getAllDiskUsageWithoutBuilds() {
        DiskUsageProperty property = project.getProperty(DiskUsageProperty.class);
        if(property == null) {
            DiskUsageUtil.addProperty(project);
            property = project.getProperty(DiskUsageProperty.class);
        }
        return property.getAllDiskUsageWithoutBuilds();
    }


    public Long getJobRootDirDiskUsage() {
        try {
            return getBuildsDiskUsage().get("all") + getDiskUsageWithoutBuilds();
        } catch (IOException ex) {
            Logger.getLogger(ProjectDiskUsageAction.class.getName()).log(Level.SEVERE, null, ex);
            return 0L;
        }
    }

    private Map<String, Long> getBuildsDiskUsageAllSubItems(ItemGroup<?> group, Date older, Date yonger) throws IOException {
        Map<String, Long> diskUsage = new TreeMap<>();
        Long buildsDiskUsage = 0L;
        Long locked = 0L;
        Long notLoaded = 0L;
        for(Object item: group.getItems()) {
            if(item instanceof ItemGroup) {
                ItemGroup<?> subGroup = (ItemGroup<?>) item;
                buildsDiskUsage += getBuildsDiskUsageAllSubItems(subGroup, older, yonger).get("all");
                locked += getBuildsDiskUsageAllSubItems(subGroup, older, yonger).get("locked");
                notLoaded += getBuildsDiskUsageAllSubItems(subGroup, older, yonger).get("notLoaded");
            }
            else {
                if(group instanceof AbstractProject) {
                    AbstractProject<?,?> p = (AbstractProject<?,?>) item;
                    DiskUsageProperty property = p.getProperty(DiskUsageProperty.class);
                    if(property == null) {
                        DiskUsageUtil.addProperty(project);
                        property = project.getProperty(DiskUsageProperty.class);
                    }
                    Set<DiskUsageBuildInformation> informations = property.getDiskUsageOfBuilds();
                    for(DiskUsageBuildInformation information: informations) {
                        Date date = new Date(information.getTimestamp());
                        if(older != null && !date.before(older)) {
                            continue;
                        }
                        if(yonger != null && !date.after(yonger)) {
                            continue;
                        }
                        Long size = information.getSize();
                        buildsDiskUsage += size;
                        Collection<AbstractBuild> loadedBuilds = (Collection<AbstractBuild>) p._getRuns().getLoadedBuilds().values();
                        AbstractBuild<?,?> build = null;
                        for(AbstractBuild<?,?> b: loadedBuilds) {
                            if(b.getId().equals(information.getId())) {
                                build = b;
                            }
                        }
                        if(build != null) {
                            if(build.isKeepLog()) {
                                locked += size;
                            }
                        }
                        else {
                            notLoaded += size;
                        }
                    }
                }
            }

        }
        diskUsage.put("all", buildsDiskUsage);
        diskUsage.put("locked", locked);
        diskUsage.put("notLoaded", notLoaded);
        return diskUsage;
    }

    public Map<String, Long> getBuildsDiskUsage() throws IOException {
        return getBuildsDiskUsage(null, null);
    }

    public Long getAllBuildsDiskUsage() throws IOException {
        return getBuildsDiskUsage(null, null).get("all");
    }

    /**
     * @return Disk usage for all builds
     */
    public Map<String, Long> getBuildsDiskUsage(Date older, Date yonger) throws IOException {
        DiskUsageProperty property = project.getProperty(DiskUsageProperty.class);
        if(property == null) {
            DiskUsageUtil.addProperty(project);
            property = project.getProperty(DiskUsageProperty.class);
        }
        Map<String, Long> diskUsage = new TreeMap<>();
        Long buildsDiskUsage = 0L;
        Long locked = 0L;
        Long notLoaded = 0L;

        if(project instanceof ItemGroup) {
            ItemGroup<?> group = (ItemGroup<?>) project;
            Map<String, Long> sizes = getBuildsDiskUsageAllSubItems(group, older, yonger);
            buildsDiskUsage += sizes.get("all");
            locked += sizes.get("locked");
            notLoaded += sizes.get("notLoaded");
        }
        Set<DiskUsageBuildInformation> informations = property.getDiskUsageOfBuilds();
        for(DiskUsageBuildInformation information: informations) {
            Date date = new Date(information.getTimestamp());
            if(older != null && !date.before(older)) {
                continue;
            }
            if(yonger != null && !date.after(yonger)) {
                continue;
            }
            Long size = information.getSize();
            buildsDiskUsage += size;
            Collection<AbstractBuild<?,?>> loadedBuilds = (Collection<AbstractBuild<?,?>>) project._getRuns().getLoadedBuilds().values();
            AbstractBuild<?,?> build = null;
            for(AbstractBuild<?,?> b: loadedBuilds) {
                if(b.getId().equals(information.getId())) {
                    build = b;
                }
            }
            if(build != null) {
                if(build.isKeepLog()) {
                    locked += size;
                }
            }
            else {
                notLoaded += size;
            }
        }

        diskUsage.put("all", buildsDiskUsage);
        diskUsage.put("locked", locked);
        diskUsage.put("notLoaded", notLoaded);
        return diskUsage;
    }

    public BuildDiskUsageAction getLastBuildAction() {
        Run<?,?> run = project.getLastBuild();
        if(run != null) {
            return run.getAction(BuildDiskUsageAction.class);
        }

        return null;
    }

    public Set<DiskUsageBuildInformation> getBuildsInformation() throws IOException {
        DiskUsageProperty property = project.getProperty(DiskUsageProperty.class);
        if(property == null) {
            DiskUsageUtil.addProperty(project);
            property = project.getProperty(DiskUsageProperty.class);
        }
        return property.getDiskUsageOfBuilds();
    }

    /**
     * Generates a graph with disk usage trend
     * 
     */
    public Graph getGraph() throws IOException {
        // TODO if(nothing_changed) return;
        List<Object[]> usages = new ArrayList<>();
        long maxValue = 0;
        long maxValueWorkspace = 0;
        DiskUsageProperty property = project.getProperty(DiskUsageProperty.class);
        maxValueWorkspace = Math.max(getAllCustomOrNonAgentWorkspaces(), getAllAgentWorkspaces());
        maxValue = getJobRootDirDiskUsage();
        // First iteration just to get scale of the y-axis
        final var builds = property.getDiskUsageOfBuilds()
                                   .stream()
                                   .sorted((a, b) -> b.getNumber() - a.getNumber())
                                   .collect(Collectors.toList());
        // do it in reverse order
        for(int i = builds.size() - 1; i >= 0; i--) {
            DiskUsageBuildInformation build = builds.get(i);
            Long diskUsage = property.getDiskUsageOfBuild(build.getId());
            usages.add(new Object[]{build.getNumber(), getJobRootDirDiskUsage(), diskUsage, getAllAgentWorkspaces(), getAllCustomOrNonAgentWorkspaces()});
            maxValue = Math.max(maxValue, diskUsage);
        }

        int floor = (int) DiskUsageUtil.getScale(maxValue);
        String unit = DiskUsageUtil.getUnitString(floor);
        int workspaceFloor = (int) DiskUsageUtil.getScale(maxValueWorkspace);
        String workspaceUnit = DiskUsageUtil.getUnitString(workspaceFloor);
        double base = Math.pow(1024, floor);
        double workspaceBase = Math.pow(1024, workspaceFloor);
        DefaultCategoryDataset dataset = new DefaultCategoryDataset();
        DefaultCategoryDataset dataset2 = new DefaultCategoryDataset();
        for (Object[] usage : usages) {
            String label = "#" + usage[0];
            dataset.addValue(((Long) usage[1]) / base, Messages.DiskUsage_Graph_JobDirectory(), label);
            dataset.addValue(((Long) usage[2]) / base, Messages.DiskUsage_Graph_BuildDirectory(), label);
            dataset2.addValue(((Long) usage[3]) / workspaceBase, Messages.DiskUsage_Graph_AgentWorkspaces(), label);
            dataset2.addValue(((Long) usage[4]) / workspaceBase, Messages.DiskUsage_Graph_NonAgentWorkspaces(), label);
        }
        return new DiskUsageGraph(dataset, unit, dataset2, workspaceUnit);
    }

    /** Shortcut for the jelly view */
    public boolean showGraph() {
        return Jenkins.get().getPlugin(DiskUsagePlugin.class).getConfiguration().isShowGraph();
    }
}
