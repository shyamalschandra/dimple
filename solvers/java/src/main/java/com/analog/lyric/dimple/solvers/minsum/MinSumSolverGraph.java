/*******************************************************************************
*   Copyright 2012-2015 Analog Devices, Inc.
*
*   Licensed under the Apache License, Version 2.0 (the "License");
*   you may not use this file except in compliance with the License.
*   You may obtain a copy of the License at
*
*       http://www.apache.org/licenses/LICENSE-2.0
*
*   Unless required by applicable law or agreed to in writing, software
*   distributed under the License is distributed on an "AS IS" BASIS,
*   WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
*   See the License for the specific language governing permissions and
*   limitations under the License.
********************************************************************************/

package com.analog.lyric.dimple.solvers.minsum;

import java.util.Map;

import org.eclipse.jdt.annotation.Nullable;

import com.analog.lyric.collect.Tuple2;
import com.analog.lyric.dimple.factorfunctions.core.IFactorTable;
import com.analog.lyric.dimple.model.core.EdgeState;
import com.analog.lyric.dimple.model.core.FactorGraph;
import com.analog.lyric.dimple.model.factors.Factor;
import com.analog.lyric.dimple.model.variables.Discrete;
import com.analog.lyric.dimple.model.variables.Variable;
import com.analog.lyric.dimple.options.BPOptions;
import com.analog.lyric.dimple.solvers.core.BPSolverGraph;
import com.analog.lyric.dimple.solvers.core.NoSolverEdge;
import com.analog.lyric.dimple.solvers.core.multithreading.MultiThreadingManager;
import com.analog.lyric.dimple.solvers.interfaces.ISolverEdgeState;
import com.analog.lyric.dimple.solvers.interfaces.ISolverFactor;
import com.analog.lyric.dimple.solvers.interfaces.ISolverFactorGraph;
import com.analog.lyric.dimple.solvers.interfaces.ISolverVariable;
import com.analog.lyric.dimple.solvers.optimizedupdate.CostEstimationTableWrapper;
import com.analog.lyric.dimple.solvers.optimizedupdate.CostType;
import com.analog.lyric.dimple.solvers.optimizedupdate.Costs;
import com.analog.lyric.dimple.solvers.optimizedupdate.FactorTableUpdateSettings;
import com.analog.lyric.dimple.solvers.optimizedupdate.FactorUpdatePlan;
import com.analog.lyric.dimple.solvers.optimizedupdate.IMarginalizationStep;
import com.analog.lyric.dimple.solvers.optimizedupdate.IMarginalizationStepEstimator;
import com.analog.lyric.dimple.solvers.optimizedupdate.ISFactorGraphToOptimizedUpdateAdapter;
import com.analog.lyric.dimple.solvers.optimizedupdate.IUpdateStep;
import com.analog.lyric.dimple.solvers.optimizedupdate.IUpdateStepEstimator;
import com.analog.lyric.dimple.solvers.optimizedupdate.TableWrapper;
import com.analog.lyric.dimple.solvers.optimizedupdate.UpdateApproach;
import com.analog.lyric.dimple.solvers.optimizedupdate.UpdateCostOptimizer;
import com.analog.lyric.options.IOptionKey;

/**
 * Solver-specific factor graph for min-sum solver.
 * <p>
 * <em>Previously was com.analog.lyric.dimple.solvers.minsum.SFactorGraph</em>
 *  <p>
 * @since 0.07
 */
public class MinSumSolverGraph extends BPSolverGraph<ISolverFactor,ISolverVariable,ISolverEdgeState>
{
	protected double _damping = 0;

	public MinSumSolverGraph(FactorGraph factorGraph, @Nullable ISolverFactorGraph parent)
	{
		super(factorGraph, parent);
		setMultithreadingManager(new MultiThreadingManager(this));
	}
	
	@Override
	public boolean hasEdgeState()
	{
		return true;
	}

	@Override
	public void initialize()
	{
		_damping = getOptionOrDefault(BPOptions.damping);
		super.initialize();
		UpdateCostOptimizer optimizer = new UpdateCostOptimizer(_optimizedUpdateAdapter);
		optimizer.optimize(this);
		for (ISolverFactor sf : getSolverFactorsRecursive())
		{
			if (sf instanceof MinSumTableFactor)
			{
				MinSumTableFactor tf = (MinSumTableFactor)sf;
				tf.setupTableFactorEngine();
			}
		}
	}
	
	@SuppressWarnings("deprecation") // TODO remove when SVariable removed
	@Override
	public ISolverVariable createVariable(Variable var)
	{
		if (var instanceof Discrete)
		{
			return new SVariable((Discrete)var, this);
		}
		
		throw unsupportedVariableType(var);
	}


	@Override
	public ISolverFactor createFactor(Factor factor)
	{
		return MinSumOptions.customFactors.createFactor(factor, this);
	}
	
	@Override
	public ISolverEdgeState createEdgeState(EdgeState edge)
	{
		final Variable var = edge.getVariable(_model);

		if (var instanceof Discrete)
		{
			return new MinSumDiscreteEdge((Discrete)var);
		}
		
		return NoSolverEdge.INSTANCE;
	}
	
	@SuppressWarnings("deprecation") // TODO remove when SFactorGraph removed
	@Override
	public ISolverFactorGraph createSubgraph(FactorGraph subgraph)
	{
		return new SFactorGraph(subgraph, this);
	}
	
	// For backward compatibility only; preferable to use "Xor" factor function, which can
	// be evaluated for scoring or other purposes, but still uses the custom factor.  This may be removed at some point.
	// This should return true only for custom factors that do not have a corresponding FactorFunction of the same name
	@Override
	public boolean customFactorExists(String funcName)
	{
		if (funcName.equals("CustomXor") || funcName.equals("customXor"))
			return true;
		else
			return false;
	}

	/*
	 * Set the global solver damping parameter.  We have to go through all factor graphs
	 * and update the damping parameter on all existing table functions in that graph.
	 */
	public void setDamping(double damping)
	{
		setOption(BPOptions.damping, damping);
		_damping = damping;
	}
	
	public double getDamping()
	{
		return _damping;
	}

	/**
	 * Indicates if this solver supports the optimized update algorithm.
	 * 
	 * @since 0.06
	 */
	public boolean isOptimizedUpdateSupported()
	{
		return true;
	}

	/*
	 * 
	 */
	@Override
	protected void doUpdateEdge(int edge)
	{
	}

	@Override
	protected String getSolverName()
	{
		return "min-sum";
	}
	
	private final ISFactorGraphToOptimizedUpdateAdapter _optimizedUpdateAdapter = new SFactorGraphToOptimizedUpdateAdapter(this);

	private static class SFactorGraphToOptimizedUpdateAdapter implements ISFactorGraphToOptimizedUpdateAdapter
	{
		final private MinSumSolverGraph _minSumSolverGraph;
		
		SFactorGraphToOptimizedUpdateAdapter(MinSumSolverGraph minSumSolverGraph)
		{
			_minSumSolverGraph = minSumSolverGraph;
		}
		
		@Override
		public IUpdateStepEstimator createSparseOutputStepEstimator(CostEstimationTableWrapper tableWrapper)
		{
			return new TableFactorEngineOptimized.SparseOutputStepEstimator(tableWrapper);
		}

		@Override
		public IUpdateStepEstimator createDenseOutputStepEstimator(CostEstimationTableWrapper tableWrapper)
		{
			return new TableFactorEngineOptimized.DenseOutputStepEstimator(tableWrapper);
		}

		@Override
		public IMarginalizationStepEstimator
			createSparseMarginalizationStepEstimator(CostEstimationTableWrapper tableWrapper,
				int inPortNum,
				int dimension,
				CostEstimationTableWrapper g)
		{
			return new TableFactorEngineOptimized.SparseMarginalizationStepEstimator(tableWrapper, inPortNum,
				dimension, g);
		}

		@Override
		public IMarginalizationStepEstimator
			createDenseMarginalizationStepEstimator(CostEstimationTableWrapper tableWrapper,
				int inPortNum,
				int dimension,
				CostEstimationTableWrapper g)
		{
			return new TableFactorEngineOptimized.DenseMarginalizationStepEstimator(tableWrapper, inPortNum,
				dimension, g);
		}

		@Override
		public Costs estimateCostOfNormalUpdate(IFactorTable factorTable)
		{
			Costs result = new Costs();
			final int size = factorTable.countNonZeroWeights();
			final int dimensions = factorTable.getDimensions();
			// Coefficients determined experimentally
			double executionTime = 3.30461648566;
			executionTime += 1.51472189501 * (size - 2397282.13878) / 4990159.0;
			executionTime += 12.0304854157 * (dimensions * size - 24636832.1724) / 114805021.0;
			result.put(CostType.EXECUTION_TIME, executionTime);
			return result;
		}

		@Override
		public Costs estimateCostOfOptimizedUpdate(IFactorTable factorTable, final double sparseThreshold)
		{
			final Costs costs = FactorUpdatePlan.estimateOptimizedUpdateCosts(factorTable, this, sparseThreshold);
			double dmf = costs.get(CostType.DENSE_MARGINALIZATION_SIZE);
			double smf = costs.get(CostType.SPARSE_MARGINALIZATION_SIZE);
			double fo = costs.get(CostType.OUTPUT_SIZE);
			final double size = factorTable.countNonZeroWeights();
			// Coefficients determined experimentally
			double executionTime = 1.29764000525;
			executionTime += 6.92791055163 * (dmf - 3705266.58065) / 25293812.93;
			executionTime += 4.29121266133 * (smf - 3224351.19011) / 14900000.0;
			executionTime += -0.330453110368 * (fo - 12588.026109) / 724853.0;
			executionTime += -1.36970402596 * (size - 2397282.13878) / 4990159.0;
			final Costs result = new Costs();
			result.put(CostType.MEMORY, costs.get(CostType.MEMORY));
			result.put(CostType.EXECUTION_TIME, executionTime);
			return result;
		}
		
		@Override
		public ISolverFactorGraph getSolverGraph()
		{
			return _minSumSolverGraph;
		}

		@Override
		public int getWorkers(ISolverFactorGraph sfactorGraph)
		{
			MinSumSolverGraph sfg = (MinSumSolverGraph) sfactorGraph;
			if (sfg.useMultithreading())
			{
				return sfg.getMultithreadingManager().getNumWorkers();
			}
			else
			{
				return 1;
			}
		}

		@Override
		public void
			putFactorTableUpdateSettings(Map<IFactorTable, FactorTableUpdateSettings> optionsValueByFactorTable)
		{
			_minSumSolverGraph._factorTableUpdateSettings = optionsValueByFactorTable;
		}

		@Override
		public double[] getSparseValues(IFactorTable factorTable)
		{
			return factorTable.getEnergiesSparseUnsafe();
		}

		@Override
		public double[] getDenseValues(IFactorTable factorTable)
		{
			return factorTable.getEnergiesDenseUnsafe();
		}

		@Override
		public IUpdateStep createSparseOutputStep(int outPortNum, TableWrapper tableWrapper)
		{
			return new TableFactorEngineOptimized.SparseOutputStep(outPortNum, tableWrapper);
		}

		@Override
		public IUpdateStep createDenseOutputStep(int outPortNum, TableWrapper tableWrapper)
		{
			return new TableFactorEngineOptimized.DenseOutputStep(outPortNum, tableWrapper);
		}

		@Override
		public IMarginalizationStep createSparseMarginalizationStep(TableWrapper tableWrapper,
			int inPortNum,
			int dimension,
			IFactorTable g_factorTable,
			Tuple2<int[][], int[]> g_and_msg_indices)
		{
			return new TableFactorEngineOptimized.SparseMarginalizationStep(tableWrapper, this, inPortNum,
				dimension, g_factorTable, g_and_msg_indices);
		}

		@Override
		public IMarginalizationStep createDenseMarginalizationStep(TableWrapper tableWrapper,
			int inPortNum,
			int dimension,
			IFactorTable g_factorTable)
		{
			return new TableFactorEngineOptimized.DenseMarginalizationStep(tableWrapper, this, inPortNum,
				dimension, g_factorTable);
		}

		@Override
		public IOptionKey<UpdateApproach> getUpdateApproachOptionKey()
		{
			return BPOptions.updateApproach;
		}

		@Override
		public IOptionKey<Double> getOptimizedUpdateSparseThresholdKey()
		{
			return BPOptions.optimizedUpdateSparseThreshold;
		}

		@Override
		public IOptionKey<Double> getAutomaticExecutionTimeScalingFactorKey()
		{
			return BPOptions.automaticExecutionTimeScalingFactor;
		}

		@Override
		public IOptionKey<Double> getAutomaticMemoryAllocationScalingFactorKey()
		{
			return BPOptions.automaticMemoryAllocationScalingFactor;
		}
	}
	
	private @Nullable Map<IFactorTable, FactorTableUpdateSettings> _factorTableUpdateSettings;

	@Nullable FactorTableUpdateSettings getFactorTableUpdateSettings(Factor factor)
	{
		final Map<IFactorTable, FactorTableUpdateSettings> map = _factorTableUpdateSettings;
		FactorTableUpdateSettings result = null;
		if (map != null && factor.hasFactorTable())
		{
			IFactorTable factorTable = factor.getFactorTable();
			result = map.get(factorTable);
		}
		return result;
	}

}
