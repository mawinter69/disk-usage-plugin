package hudson.plugins.disk_usage;

import antlr.ANTLRException;
import hudson.Extension;
import hudson.model.AbstractBuild;
import hudson.model.AbstractProject;
import hudson.model.AperiodicWork;
import hudson.model.Item;
import hudson.model.ItemGroup;
import hudson.model.RunMap;
import hudson.model.TaskListener;
import hudson.scheduler.CronTab;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.logging.Level;
import jenkins.model.Jenkins;


/**
 * A Thread responsible for gathering disk usage information
 * 
 * @author dvrzalik
 */
@Extension
public class BuildDiskUsageCalculationThread extends DiskUsageCalculation {

    // last scheduled task;
    private static DiskUsageCalculation currentTask;

    public BuildDiskUsageCalculationThread() {
        super("Calculation of builds disk usage");
    }

    @Override
    public void execute(TaskListener listener) throws IOException, InterruptedException {
        if(!isCancelled() && startExecution()) {
            try {
                ItemGroup<? extends Item> itemGroup = Jenkins.get();
                List<Item> items = new ArrayList<>(DiskUsageUtil.getAllProjects(itemGroup));

                for(Object item: items) {
                    if(item instanceof AbstractProject) {
                        AbstractProject<?,?> project = (AbstractProject<?,?>) item;
                        DiskUsageProperty property = project.getProperty(DiskUsageProperty.class);
                        if(property == null) {
                            property = new DiskUsageProperty();
                            project.addProperty(property);
                        }
                        ProjectDiskUsage diskUsage = property.getProjectDiskUsage();
                        for(DiskUsageBuildInformation information: diskUsage.getBuildDiskUsage(true)) {
                            final RunMap<?> runMap = project._getRuns();
                            Map<Integer, ?> loadedBuilds = runMap.getLoadedBuilds();
                            AbstractBuild<?,?> build = (AbstractBuild<?, ?>) loadedBuilds.get(information.getNumber());
                            // do not calculat builds in progress
                            if(build != null && build.isBuilding()) {
                                continue;
                            }
                            try {
                                DiskUsageUtil.calculateDiskUsageForBuild(information.getId(), project);
                            }
                            catch (Exception e) {
                                logger.log(Level.WARNING, "Error when recording disk usage for " + project.getName(), e);
                            }
                        }
                    }
                }
            } catch (Exception ex) {
                logger.log(Level.WARNING, "Error when recording disk usage for builds", ex);
            }
        }
        else {
            DiskUsagePlugin plugin = Jenkins.get().getPlugin(DiskUsagePlugin.class);
            if(plugin.getConfiguration().isCalculationBuildsEnabled()) {
                logger.log(Level.FINER, "Calculation of builds is already in progress.");
            }
            else {
                logger.log(Level.FINER, "Calculation of builds is disabled.");
            }
        }
    }

    @Override
    public CronTab getCronTab() throws ANTLRException {
        String cron = Jenkins.get().getPlugin(DiskUsagePlugin.class).getConfiguration().getCountIntervalForBuilds();
        return new CronTab(cron);
    }

    @Override
    public AperiodicWork getNewInstance() {
        if(currentTask != null) {
            currentTask.cancel();
        }
        else {
            cancel();
        }
        currentTask = new BuildDiskUsageCalculationThread();
        return currentTask;
    }

    @Override
    public DiskUsageCalculation getLastTask() {
        return currentTask;
    }

    private boolean startExecution() {
        DiskUsagePlugin plugin = Jenkins.get().getPlugin(DiskUsagePlugin.class);
        if(!plugin.getConfiguration().isCalculationBuildsEnabled()) {
            return false;
        }
        return !isExecutingMoreThenOneTimes();
    }

}
