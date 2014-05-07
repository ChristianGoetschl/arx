/*
 * ARX: Efficient, Stable and Optimal Data Anonymization
 * Copyright (C) 2012 - 2014 Florian Kohlmayer, Fabian Prasser
 * 
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 * 
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU General Public License for more details.
 * 
 * You should have received a copy of the GNU General Public License
 * along with this program. If not, see <http://www.gnu.org/licenses/>.
 */

package org.deidentifier.arx;

import java.io.IOException;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import org.deidentifier.arx.algorithm.AbstractAlgorithm;
import org.deidentifier.arx.algorithm.FLASHAlgorithm;
import org.deidentifier.arx.algorithm.FLASHStrategy;
import org.deidentifier.arx.criteria.KAnonymity;
import org.deidentifier.arx.criteria.LDiversity;
import org.deidentifier.arx.criteria.TCloseness;
import org.deidentifier.arx.framework.check.INodeChecker;
import org.deidentifier.arx.framework.check.NodeChecker;
import org.deidentifier.arx.framework.data.DataManager;
import org.deidentifier.arx.framework.data.Dictionary;
import org.deidentifier.arx.framework.data.GeneralizationHierarchy;
import org.deidentifier.arx.framework.lattice.Lattice;
import org.deidentifier.arx.framework.lattice.LatticeBuilder;
import org.deidentifier.arx.metric.Metric;

/**
 * This class offers several methods to define parameters and execute the ARX
 * algorithm.
 * 
 * @author Fabian Prasser
 * @author Florian Kohlmayer
 */
public class ARXAnonymizer {

    /**
     * Temporary result of the ARX algorithm.
     * 
     * @author Fabian Prasser
 * @author Florian Kohlmayer
     */
    class Result {

        /** The algorithm */
        final AbstractAlgorithm algorithm;

        /** The checker. */
        final INodeChecker      checker;

        /** The lattice. */
        final Lattice           lattice;

        /** The data manager */
        final DataManager       manager;

        /** The metric. */
        final Metric<?>         metric;

        /**
         * Creates a new instance.
         * 
         * @param metric  the metric
         * @param checker the checker
         * @param lattice the lattice
         * @param manager the manager
         */
        Result(final Metric<?> metric,
               final INodeChecker checker,
               final Lattice lattice,
               final DataManager manager,
               final AbstractAlgorithm algorithm) {
            this.metric = metric;
            this.checker = checker;
            this.lattice = lattice;
            this.manager = manager;
            this.algorithm = algorithm;
        }

        /**
         * Creates a final result from this temporary result
         * @param anonymizer
         * @param handle
         * @param time
         * @return
         */
		public ARXResult asResult(ARXConfiguration config, DataHandle handle, long time) {

		    // Create lattice
	        final ARXLattice flattice = new ARXLattice(lattice,
	                                                   manager.getDataQI().getHeader(),
	                                                   config);

			// Create output handle
	        ((DataHandleInput)handle).setLocked(true);
            return new ARXResult(handle.getRegistry(),
                                 this.manager,
                                 this.checker,
                                 handle.getDefinition(),
                                 config,
                                 flattice,
                                 System.currentTimeMillis() - time,
                                 suppressionString,
                                 removeOutliers);      
		}
    }

    /** History size. */
    private int           historySize           = 200;

    /** The listener, if any. */
    private ARXListener   listener              = null;

    /** Remove outliers? */
    private boolean       removeOutliers        = true;

    /** Snapshot size. */
    private double        snapshotSizeDataset   = 0.2d;

    /** Snapshot size snapshot */
    private double        snapshotSizeSnapshot  = 0.8d;

    /** The string to insert for outliers. */
    private String        suppressionString     = "*";


    /**
     * Creates a new anonymizer with the default configuration
     */
    public ARXAnonymizer() {
        // Empty by design
    }

    /**
     * Creates a new anonymizer with the given configuration.
     * 
     * @param historySize The maximum number of snapshots stored in the buffer [default=200]
     * @param snapshotSizeDataset The maximum relative size of a snapshot compared to the dataset [default=0.2]
     * @param snapshotSizeSnapshot The maximum relative size of a snapshot compared to its predecessor [default=0.8]
     */
    public ARXAnonymizer(final int historySize, final double snapshotSizeDataset, final double snapshotSizeSnapshot) {
        if (historySize<0) 
            throw new RuntimeException("History size must be >=0");
        this.historySize = historySize;
        if (snapshotSizeDataset<=0 || snapshotSizeDataset>=1) 
            throw new RuntimeException("SnapshotSizeDataset must be >0 and <1");
        this.snapshotSizeDataset = snapshotSizeDataset;
        if (snapshotSizeSnapshot<=0 || snapshotSizeSnapshot>=1) 
            throw new RuntimeException("snapshotSizeSnapshot must be >0 and <1");
        this.snapshotSizeSnapshot = snapshotSizeSnapshot;
    }

    /**
     * Creates a new anonymizer with the given configuration.
     * 
     * @param suppressionString The string inserted for suppressed values
     */
    public ARXAnonymizer(final String suppressionString) {
        this.suppressionString = suppressionString;
    }

    /**
     * Creates a new anonymizer with the given configuration.
     * 
     * @param suppressionString The string inserted for suppressed values
     * @param historySize The maximum number of snapshots stored in the buffer [default=200]
     * @param snapshotSizeDataset The maximum relative size of a snapshot compared to the dataset [default=0.2]
     * @param snapshotSizeSnapshot The maximum relative size of a snapshot compared to its predecessor [default=0.8]
     */
    public ARXAnonymizer(final String suppressionString, final int historySize, final double snapshotSizeDataset, final double snapshotSizeSnapshot) {
        this.suppressionString = suppressionString;
        if (historySize<0) 
            throw new RuntimeException("History size must be >=0");
        this.historySize = historySize;
        if (snapshotSizeDataset<=0 || snapshotSizeDataset>=1) 
            throw new RuntimeException("SnapshotSizeDataset must be >0 and <1");
        this.snapshotSizeDataset = snapshotSizeDataset;
        if (snapshotSizeSnapshot<=0 || snapshotSizeSnapshot>=1) 
            throw new RuntimeException("snapshotSizeSnapshot must be >0 and <1");
        this.snapshotSizeSnapshot = snapshotSizeSnapshot;
    }

    /**
     * Performs data anonymization
     * @param data The data
     * @param config The privacy config
     * @return ARXResult
     * @throws IOException
     */
    public ARXResult anonymize(final Data data, ARXConfiguration config) throws IOException {
        
        if (((DataHandleInput)data.getHandle()).isLocked()){
            throw new RuntimeException("This data handle is locked. Please release it first");
        }
        
        
        if (data.getDefinition().getSensitiveAttributes().size() > 1 && config.isProtectSensitiveAssociations()) {
            throw new UnsupportedOperationException("Currently not supported!");
        }
        
        DataHandle handle = data.getHandle();
        
        final long time = System.currentTimeMillis();
        checkBeforeEncoding(handle, config);
        handle.getRegistry().reset();
        handle.getRegistry().createInputSubset(config);

        // Execute
        return anonymizeInternal(handle, handle.getDefinition(), config).asResult(config, handle, time);
    }
    
    /**
     * Returns the maximum number of snapshots allowed to store in the history.
     * 
     * @return The size
     */
    public int getHistorySize() {
        return historySize;
    }
    
    /**
     * Gets the snapshot size.
     * 
     * @return The maximum size of a snapshot relative to the dataset size
     */
    public double getMaximumSnapshotSizeDataset() {
        return snapshotSizeDataset;
    }

    /**
     * Gets the snapshot size.
     * 
     * @return The maximum size of a snapshot relative to the previous snapshot
     *         size
     */
    public double getMaximumSnapshotSizeSnapshot() {
        return snapshotSizeSnapshot;
    }

    /**
     * Returns the string with which outliers are replaced.
     * 
     * @return the relativeMaxOutliers string
     */
    public String getSuppressionString() {
        return suppressionString;
    }

    /**
     * Does the anonymizer remove outliers from the dataset?
     * 
     * @return
     */
    public boolean isRemoveOutliers() {
        return removeOutliers;
    }
    
    /**
     * Sets the maximum number of snapshots allowed to store in the history.
     * 
     * @param historySize
     *            The size
     */
    public void setHistorySize(final int historySize) {
        if (historySize < 1) { throw new IllegalArgumentException("history size must be positive and not 0"); }
        this.historySize = historySize;
    }

    /**
     * Sets a listener.
     * 
     * @param listener
     *            the new listener, if any
     */
    public void setListener(final ARXListener listener) {
        this.listener = listener;
    }

    /**
     * Sets the maximum size of a snapshot relative to the dataset size.
     * 
     * @param snapshotSizeDataset
     *            The size
     */
    public void setMaximumSnapshotSizeDataset(final double snapshotSize) {
        // Perform sanity checks
        if ((snapshotSize <= 0d) || (snapshotSize > 1d)) { throw new IllegalArgumentException("Snapshot size " + snapshotSize + "must be in [0,1]"); }
        snapshotSizeDataset = snapshotSize;
    }

    /**
     * Sets the maximum size of a snapshot relative to the previous snapshot
     * 
     * @param snapshotSizeSnapshot
     *            The size
     */
    public void setMaximumSnapshotSizeSnapshot(final double snapshotSizeSnapshot) {
        // Perform sanity checks
        if ((snapshotSizeSnapshot <= 0d) || (snapshotSizeSnapshot > 1d)) { throw new IllegalArgumentException("Snapshot size " + snapshotSizeSnapshot + "must be in [0,1]"); }
        this.snapshotSizeSnapshot = snapshotSizeSnapshot;
    }

    /**
     * Set whether the anonymizer should remove outliers
     * 
     * @param value
     */
    public void setRemoveOutliers(final boolean value) {
        removeOutliers = value;
    }

    /**
     * Sets the string with which suppressed values are to be replaced.
     * 
     * @param suppressionString
     *            The relativeMaxOutliers string
     */
    public void setSuppressionString(final String suppressionString) {
        if (suppressionString == null) { throw new NullPointerException("suppressionString must not be null"); }
        this.suppressionString = suppressionString;
    }

    /**
     * Performs some sanity checks.
     * 
     * @param manager
     *            the manager
     */
    private void checkAfterEncoding(final ARXConfiguration config, final DataManager manager) {

        if (config.containsCriterion(KAnonymity.class)){
            KAnonymity c = config.getCriterion(KAnonymity.class);
            if ((c.getK() > manager.getDataQI().getDataLength()) || (c.getK() < 1)) { 
                throw new IllegalArgumentException("Parameter k (" + c.getK() + ") musst be positive and less or equal than the number of rows (" + manager.getDataQI().getDataLength()+")"); 
            }
        }
        if (config.containsCriterion(LDiversity.class)){
            for (LDiversity c : config.getCriteria(LDiversity.class)){
	            if ((c.getL() > manager.getDataQI().getDataLength()) || (c.getL() < 1)) { 
	                throw new IllegalArgumentException("Parameter l (" + c.getL() + ") musst be positive and less or equal than the number of rows (" + manager.getDataQI().getDataLength()+")"); 
	            }
            }
        }
        
        // Check whether all hierarchies are monotonic
        for (final GeneralizationHierarchy hierarchy : manager.getHierarchies()) {
            if (!hierarchy.isMonotonic()) { throw new IllegalArgumentException("The hierarchy for the attribute '" + hierarchy.getName() + "' is not monotonic!"); }
        }

        // check min and max sizes
        final int[] hierarchyHeights = manager.getHierachyHeights();
        final int[] minLevels = manager.getMinLevels();
        final int[] maxLevels = manager.getMaxLevels();

        for (int i = 0; i < hierarchyHeights.length; i++) {
            if (minLevels[i] > (hierarchyHeights[i] - 1)) { throw new IllegalArgumentException("Invalid minimum generalization for attribute '" + manager.getHierarchies()[i].getName() + "': " +
                                                                                               minLevels[i] + " > " + (hierarchyHeights[i] - 1)); }
            if (minLevels[i] < 0) { throw new IllegalArgumentException("The minimum generalization for attribute '" + manager.getHierarchies()[i].getName() + "' has to be positive!"); }
            if (maxLevels[i] > (hierarchyHeights[i] - 1)) { throw new IllegalArgumentException("Invalid maximum generalization for attribute '" + manager.getHierarchies()[i].getName() + "': " +
                                                                                               maxLevels[i] + " > " + (hierarchyHeights[i] - 1)); }
            if (maxLevels[i] < minLevels[i]) { throw new IllegalArgumentException("The minimum generalization for attribute '" + manager.getHierarchies()[i].getName() +
                                                                                  "' has to be lower than or requal to the defined maximum!"); }
        }
    }

    /**
     * Performs some sanity checks.
     * 
     * @param handle
     *            the data handle
     * @param config
     *            the configuration
     */
    private void checkBeforeEncoding(final DataHandle handle, final ARXConfiguration config) {


        // Lots of checks
        if (handle == null) { throw new NullPointerException("Data cannot be null!"); }
        if (config.containsCriterion(LDiversity.class) ||
            config.containsCriterion(TCloseness.class)){
            if (handle.getDefinition().getSensitiveAttributes().size() == 0) { throw new IllegalArgumentException("You need to specify a sensitive attribute!"); }
        }
        for (String attr : handle.getDefinition().getSensitiveAttributes()){
            boolean found = false;
            for (LDiversity c : config.getCriteria(LDiversity.class)) {
                if (c.getAttribute().equals(attr)) {
                    found = true;
                    break;
                }
            }
            if (!found) {
                for (TCloseness c : config.getCriteria(TCloseness.class)) {
                    if (c.getAttribute().equals(attr)) {
                        found = true;
                        break;
                    }
                }
            }
            if (!found) {
                throw new IllegalArgumentException("No criterion defined for sensitive attribute: '"+attr+"'!");
            }
        }
        for (LDiversity c : config.getCriteria(LDiversity.class)) {
            if (handle.getDefinition().getAttributeType(c.getAttribute()) != AttributeType.SENSITIVE_ATTRIBUTE) {
                throw new RuntimeException("L-Diversity criterion defined for non-sensitive attribute '"+c.getAttribute()+"'!");
            }
        }
        for (TCloseness c : config.getCriteria(TCloseness.class)) {
            if (handle.getDefinition().getAttributeType(c.getAttribute()) != AttributeType.SENSITIVE_ATTRIBUTE) {
                throw new RuntimeException("T-Closeness criterion defined for non-sensitive attribute '"+c.getAttribute()+"'!");
            }
        }

        // Check handle
        if (!(handle instanceof DataHandleInput)) { throw new IllegalArgumentException("Invalid data handle provided!"); }

        // Check if all defines are correct
        Set<String> attributes = new HashSet<String>();
        for (int i=0; i<handle.getNumColumns(); i++){
            attributes.add(handle.getAttributeName(i));
        }
        for (String attribute : handle.getDefinition().getSensitiveAttributes()){
            if (!attributes.contains(attribute)) {
                throw new IllegalArgumentException("Sensitive attribute '"+attribute+"' is not contained in the dataset");
            }
        }
        for (String attribute : handle.getDefinition().getInsensitiveAttributes()){
            if (!attributes.contains(attribute)) {
                throw new IllegalArgumentException("Insensitive attribute '"+attribute+"' is not contained in the dataset");
            }
        }
        for (String attribute : handle.getDefinition().getIdentifyingAttributes()){
            if (!attributes.contains(attribute)) {
                throw new IllegalArgumentException("Identifying attribute '"+attribute+"' is not contained in the dataset");
            }
        }
        for (String attribute : handle.getDefinition().getQuasiIdentifyingAttributes()){
            if (!attributes.contains(attribute)) {
                throw new IllegalArgumentException("Quasi-identifying attribute '"+attribute+"' is not contained in the dataset");
            }
        }
        
        // Perform sanity checks
        Map<String, String[][]> hierarchies = handle.getDefinition().getHierarchies();
        if ((config.getMaxOutliers() < 0d) || (config.getMaxOutliers() >= 1d)) { throw new IllegalArgumentException("Suppression rate " + config.getMaxOutliers() + "must be in [0, 1["); }
        if (hierarchies.size() > 15) { throw new IllegalArgumentException("The curse of dimensionality strikes. Too many quasi-identifiers: " + hierarchies.size()); }
        if (hierarchies.size() == 0) { throw new IllegalArgumentException("You need to specify at least one quasi-identifier"); }
    }
    
    /**
     * Prepares the data manager.
     * 
     * @param handle
     *            the handle
     * @param config
     *            the config
     * @param definition
     * 			  the definition
     * @return the data manager
     * @throws IOException
     *             Signals that an I/O exception has occurred.
     */
    private DataManager prepareDataManager(final DataHandle handle, final DataDefinition definition, final ARXConfiguration config) throws IOException {

        // Extract data
        final String[] header = ((DataHandleInput) handle).header;
        final int[][] dataArray = ((DataHandleInput) handle).data;
        final Dictionary dictionary = ((DataHandleInput) handle).dictionary;
        final DataManager manager = new DataManager(header, dataArray, dictionary, definition, config.getCriteria());
        return manager;
    }

    /**
     * Reset a previous lattice and run the algorithm 
     * @param handle
     * @param definition
     * @param config
     * @param lattice
     * @param algorithm
     * @return
     * @throws IOException
     */
    protected Result anonymizeInternal(final DataHandle handle,
                                       final DataDefinition definition,
                                       final ARXConfiguration config) throws IOException {

        // Encode
        final DataManager manager = prepareDataManager(handle, definition, config);
        
        // Attach arrays to data handle
        ((DataHandleInput)handle).update(manager.getDataQI().getArray(), 
                                         manager.getDataSE().getArray(),
                                         manager.getDataIS().getArray());

        // Initialize
        config.initialize(manager);

        // Check
        checkAfterEncoding(config, manager);

        // Build or clean the lattice
        Lattice lattice = new LatticeBuilder(manager.getMaxLevels(), manager.getMinLevels(), manager.getHierachyHeights()).build();
        lattice.setListener(listener);

        // Build a node checker
        final INodeChecker checker = new NodeChecker(manager, config.getMetric(), config, historySize, snapshotSizeDataset, snapshotSizeSnapshot);

        // Initialize the metric
        config.getMetric().initialize(manager.getDataQI(), manager.getHierarchies(), config);

        // Create an algorithm instance
        AbstractAlgorithm algorithm = FLASHAlgorithm.create(lattice, checker, 
                                              new FLASHStrategy(lattice, manager.getHierarchies()));
        // Execute
        algorithm.traverse();
        
        // Deactivate history to prevent bugs when sorting data
        checker.getHistory().reset();
        checker.getHistory().setSize(0);
        
        // Return the result
        return new Result(config.getMetric(), checker, lattice, manager, algorithm);
    }
}
