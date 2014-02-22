/*******************************************************************************
*   Copyright 2012 Analog Devices, Inc.
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

package com.analog.lyric.dimple.solvers.sumproduct;

import java.util.Random;

import com.analog.lyric.dimple.exceptions.DimpleException;
import com.analog.lyric.dimple.factorfunctions.core.FactorFunctionWithConstants;
import com.analog.lyric.dimple.factorfunctions.core.IFactorTable;
import com.analog.lyric.dimple.model.core.FactorGraph;
import com.analog.lyric.dimple.model.core.INode;
import com.analog.lyric.dimple.model.factors.Factor;
import com.analog.lyric.dimple.model.variables.Real;
import com.analog.lyric.dimple.model.variables.RealJoint;
import com.analog.lyric.dimple.model.variables.VariableBase;
import com.analog.lyric.dimple.solvers.core.ParameterEstimator;
import com.analog.lyric.dimple.solvers.core.SFactorGraphBase;
import com.analog.lyric.dimple.solvers.core.SolverRandomGenerator;
import com.analog.lyric.dimple.solvers.core.multithreading.MultiThreadingManager;
import com.analog.lyric.dimple.solvers.interfaces.ISolverFactor;
import com.analog.lyric.dimple.solvers.interfaces.ISolverVariable;
import com.analog.lyric.dimple.solvers.sumproduct.customFactors.CustomComplexGaussianPolynomial;
import com.analog.lyric.dimple.solvers.sumproduct.customFactors.CustomFiniteFieldAdd;
import com.analog.lyric.dimple.solvers.sumproduct.customFactors.CustomFiniteFieldConstantMult;
import com.analog.lyric.dimple.solvers.sumproduct.customFactors.CustomFiniteFieldMult;
import com.analog.lyric.dimple.solvers.sumproduct.customFactors.CustomFiniteFieldProjection;
import com.analog.lyric.dimple.solvers.sumproduct.customFactors.CustomGaussianLinear;
import com.analog.lyric.dimple.solvers.sumproduct.customFactors.CustomGaussianProduct;
import com.analog.lyric.dimple.solvers.sumproduct.customFactors.CustomGaussianSum;
import com.analog.lyric.dimple.solvers.sumproduct.customFactors.CustomMultiplexer;
import com.analog.lyric.dimple.solvers.sumproduct.customFactors.CustomMultivariateGaussianProduct;
import com.analog.lyric.dimple.solvers.sumproduct.customFactors.CustomMultivariateGaussianSum;
import com.analog.lyric.dimple.solvers.sumproduct.sampledfactor.SampledFactor;
import com.analog.lyric.util.misc.IMapList;

public class SFactorGraph extends SFactorGraphBase
{
	private double _damping = 0;
	private IFactorTable _currentFactorTable = null;
	private int _sampledFactorSamplesPerUpdate = SampledFactor.DEFAULT_SAMPLES_PER_UPDATE;
	private int _sampledFactorBurnInScansPerUpdate = SampledFactor.DEFAULT_BURN_IN_SCANS_PER_UPDATE;
	private int _sampledFactorScansPerSample = SampledFactor.DEFAULT_SCANS_PER_SAMPLE;
	private static Random _rand = new Random();


	public SFactorGraph(com.analog.lyric.dimple.model.core.FactorGraph factorGraph)
	{
		super(factorGraph);
		setMultithreadingManager(new MultiThreadingManager(getModelObject()));
	}
	

	@Override
	public ISolverVariable createVariable(VariableBase var)
	{
		if (var.getModelerClassName().equals("FiniteFieldVariable"))
			return new SFiniteFieldVariable(var);
		else if (var instanceof RealJoint)
			return new SRealJointVariable(var);
		else if (var instanceof Real)
			return new SRealVariable(var);
		else
			return new SVariable(var);
	}

	

	@Override
	public ISolverFactor createFactor(Factor factor)
	{
		if (customFactorExists(factor))
		{
			return createCustomFactor(factor);
		}
		else if (!factor.isDiscrete())
		{
			// For non-discrete factor that doesn't have a custom factor, create a sampled factor
			SampledFactor sf = new SampledFactor(factor);
			sf.setSamplesPerUpdate(_sampledFactorSamplesPerUpdate);
			return sf;
		}
		else
		{
			STableFactor tf = new STableFactor(factor);
			if (_damping != 0)
				setDampingForTableFactor(tf);
			return tf;
		}
	}
	

	@Override
	public boolean customFactorExists(String funcName)
	{
		if (funcName.equals("FiniteFieldAdd") || funcName.equals("finiteFieldAdd"))						// Lower case version for backward compatibility
			return true;
		else if (funcName.equals("FiniteFieldMult") || funcName.equals("finiteFieldMult"))				// Lower case version for backward compatibility
			return true;
		else if (funcName.equals("FiniteFieldProjection") || funcName.equals("finiteFieldProjection"))	// Lower case version for backward compatibility
			return true;
		else if (funcName.equals("multiplexerCPD"))														// For backward compatibility; should use "Multiplexer" instead
			return true;
		else if (funcName.equals("add"))
			return true;
		else if (funcName.equals("constmult"))
			return true;
		else if (funcName.equals("linear"))
			return true;
		else if (funcName.equals("polynomial"))
			return true;
		else
			return false;
	}
	
	// For internal use, we check more than just the name
	// This allows detection of factors used by overloaded operators to be used, but only if used in the right way
	// And for these factors, they will use the actual corresponding factor function rather than a NOPFactorFunction
	public boolean customFactorExists(Factor factor)
	{
		String funcName = factor.getModelerFunctionName();
		if (customFactorExists(funcName))
			return true;
		else if (funcName.equals("Sum"))
			return CustomGaussianSum.isFactorCompatible(factor);
		else if (funcName.equals("Product"))
			return CustomGaussianProduct.isFactorCompatible(factor);
		else if (funcName.equals("ComplexSum"))
			return CustomMultivariateGaussianSum.isFactorCompatible(factor);
		else if (funcName.equals("Multiplexer"))
			return true;
		else
			return false;
	}

	public ISolverFactor createCustomFactor(Factor factor)
	{
		String funcName = factor.getFactorFunction().getName();
		if (funcName.equals("FiniteFieldAdd") || funcName.equals("finiteFieldAdd"))						// Lower case version for backward compatibility
			return new CustomFiniteFieldAdd(factor);
		else if (funcName.equals("FiniteFieldMult") || funcName.equals("finiteFieldMult"))				// Lower case version for backward compatibility
		{
			if (factor.getFactorFunction() instanceof FactorFunctionWithConstants)
				return new CustomFiniteFieldConstantMult(factor);
			else
				return new CustomFiniteFieldMult(factor);
		}
		else if (funcName.equals("FiniteFieldProjection") || funcName.equals("finiteFieldProjection"))	// Lower case version for backward compatibility
			return new CustomFiniteFieldProjection(factor);
		else if (funcName.equals("Multiplexer") || funcName.equals("multiplexerCPD"))					// "multiplexerCPD" for backward compatibility
			return new CustomMultiplexer(factor);
		else if (funcName.equals("Sum"))
			return new CustomGaussianSum(factor);
		else if (funcName.equals("ComplexSum"))
			return new CustomMultivariateGaussianSum(factor);
		else if (funcName.equals("Product"))
			return new CustomGaussianProduct(factor);
		else if (funcName.equals("add"))
		{
			if (isMultivariate(factor))
				return new CustomMultivariateGaussianSum(factor);
			else
				return new CustomGaussianSum(factor);
		}
		else if (funcName.equals("constmult"))
		{
			if (isMultivariate(factor))
				return new CustomMultivariateGaussianProduct(factor);
			else
				return new CustomGaussianProduct(factor);
		}
		else if (funcName.equals("linear"))
			return new CustomGaussianLinear(factor);
		else if (funcName.equals("polynomial"))
			return new CustomComplexGaussianPolynomial(factor);
		else
			throw new DimpleException("Not implemented");
	}
	
	private boolean isMultivariate(Factor factor)
	{
		if (factor.getSiblingCount() > 0 && (factor.getSibling(0) instanceof RealJoint))
			return true;
		else
			return false;
	}


	public static Random getRandom()
	{
		return _rand;
	}
	
	public void setSeed(long seed)
	{
		_rand = new Random(seed);				// Used for parameter estimation
		SolverRandomGenerator.setSeed(seed);	// Used for sampled factors
	}
	

	/*
	 * Set the global solver damping parameter.  We have to go through all factor graphs
	 * and update the damping parameter on all existing table functions in that graph.
	 */
	public void setDamping(double damping)
	{
		_damping = damping;
		for (Factor f : _factorGraph.getNonGraphFactors())
		{
			if (f.getSolver() instanceof STableFactor)
			{
				// TODO: Damping currently works only on table factors, should work on all cases
				STableFactor tf = (STableFactor)f.getSolver();
				setDampingForTableFactor(tf);
			}
		}
	}
	
	public double getDamping()
	{
		return _damping;
	}

	/*
	 * This method applies the global damping parameter to all of the table factor's ports
	 * and all of the variable ports connected to it.  This might cause problems in the future
	 * when we support different damping parameters per edge.
	 */
	protected void setDampingForTableFactor(STableFactor tf)
	{
		Factor factor = tf.getFactor();
		IMapList<INode> nodes = factor.getConnectedNodesFlat();
		
		for (int i = 0, endi = factor.getSiblingCount(); i < endi; i++)
		{
			tf.setDamping(i,_damping);
			VariableBase var = (VariableBase)nodes.getByIndex(i);
			for (int j = 0, endj = var.getSiblingCount(); j < endj;j++)
			{
				SVariable svar = (SVariable)var.getSolver();
				svar.setDamping(j,_damping);
			}
		}

	}
	
	public void setSampledFactorSamplesPerUpdate(int samplesPerUpdate)
	{
		_sampledFactorSamplesPerUpdate = samplesPerUpdate;
		for (Factor f : _factorGraph.getNonGraphFactors())
		{
			ISolverFactor s = f.getSolver();
			if (s instanceof SampledFactor)
				((SampledFactor)s).setSamplesPerUpdate(samplesPerUpdate);
		}
	}
	public int getSampledFactorSamplesPerUpdate()
	{
		return _sampledFactorSamplesPerUpdate;
	}
	
	public void setSampledFactorBurnInScansPerUpdate(int burnInSamples)
	{
		_sampledFactorBurnInScansPerUpdate = burnInSamples;
		for (Factor f : _factorGraph.getNonGraphFactors())
		{
			ISolverFactor s = f.getSolver();
			if (s instanceof SampledFactor)
				((SampledFactor)s).setSamplesPerUpdate(burnInSamples);
		}
	}
	public int getSampledFactorBurnInScansPerUpdate()
	{
		return _sampledFactorBurnInScansPerUpdate;
	}

	public void setSampledFactorScansPerSample(int scansPerSample)
	{
		_sampledFactorScansPerSample = scansPerSample;
		for (Factor f : _factorGraph.getNonGraphFactors())
		{
			ISolverFactor s = f.getSolver();
			if (s instanceof SampledFactor)
				((SampledFactor)s).setSamplesPerUpdate(scansPerSample);
		}
	}
	public int getSampledFactorScansPerSample()
	{
		return _sampledFactorScansPerSample;
	}

	

	
	@Override
	public void baumWelch(IFactorTable [] fts, int numRestarts, int numSteps)
	{
		ParameterEstimator pe = new ParameterEstimator.BaumWelch(_factorGraph, fts, SFactorGraph.getRandom());
		pe.run(numRestarts, numSteps);
	}
	
	
	public class GradientDescent extends ParameterEstimator
	{
		private double _scaleFactor;

		public GradientDescent(FactorGraph fg, IFactorTable[] tables, Random r, double scaleFactor)
		{
			super(fg, tables, r);
			_scaleFactor = scaleFactor;
		}

		@Override
		public void runStep(FactorGraph fg)
		{
			//_factorGraph.solve();
			for (IFactorTable ft : getTables())
			{
				double [] weights = ft.getWeightsSparseUnsafe();
			      //for each weight
				for (int i = 0; i < weights.length; i++)
				{
			           //calculate the derivative
					double derivative = calculateDerivativeOfBetheFreeEnergyWithRespectToWeight(ft, i);
					
			        //move the weight in that direction scaled by epsilon
					ft.setWeightForSparseIndex(weights[i] - weights[i]*derivative*_scaleFactor,i);
				}
			}
		}
		
	}
	
	public void pseudoLikelihood(IFactorTable [] fts,
			VariableBase [] vars,
			Object [][] data,
			int numSteps,
			double stepScaleFactor)
	{
		
	}
	
	public static int [][] convertObjects2Indices(VariableBase [] vars, Object [][] data)
	{
		
		return null;
	}

	
	@Override
	public void estimateParameters(IFactorTable [] fts, int numRestarts, int numSteps, double stepScaleFactor)
	{
		new GradientDescent(_factorGraph, fts, getRandom(), stepScaleFactor).run(numRestarts, numSteps);
	}

	
	public double calculateDerivativeOfBetheFreeEnergyWithRespectToWeight(IFactorTable ft,
			int weightIndex)
	{
		//BFE = InternalEnergy - BetheEntropy
		//InternalEnergy = Sum over all factors (Internal Energy of Factor)
		//                   + Sum over all variables (Internal Energy of Variable)
		//BetheEntropy = Sum over all factors (BetheEntropy(factor))
		//                  + sum over all variables (BetheEntropy(variable)
		//So derivative of BFE = Sum over all factors that contain the weight
		//                                              (derivative of Internal Energy of Factor
		//                                              - derivative of BetheEntropy of Factor)
		//
		
		_currentFactorTable = ft;
		
				
		for (Factor f : _factorGraph.getFactorsFlat())
		{
			((STableFactor)f.getSolver()).initializeDerivativeMessages(ft.sparseSize());
		}
		for (VariableBase vb : _factorGraph.getVariablesFlat())
			((SVariable)vb.getSolver()).initializeDerivativeMessages(ft.sparseSize());
		
		setCalculateDerivative(true);
		
		double result = 0;
		try
		{
			_factorGraph.solve();
			for (Factor f : _factorGraph.getFactorsFlat())
			{
				STableFactor stf = (STableFactor)f.getSolver();
				result += stf.calculateDerivativeOfInternalEnergyWithRespectToWeight(weightIndex);
				result -= stf.calculateDerivativeOfBetheEntropyWithRespectToWeight(weightIndex);
						
			}
			for (VariableBase v : _factorGraph.getVariablesFlat())
			{
				SVariable sv = (SVariable)v.getSolver();
				result += sv.calculateDerivativeOfInternalEnergyWithRespectToWeight(weightIndex);
				result += sv.calculateDerivativeOfBetheEntropyWithRespectToWeight(weightIndex);
			}
		}
		finally
		{
			setCalculateDerivative(false);
		}
		
		return result;
	}
	
	public void setCalculateDerivative(boolean val)
	{
		for (Factor f : _factorGraph.getFactorsFlat())
		{
			STableFactor stf = (STableFactor)f.getSolver();
			stf.setUpdateDerivative(val);
		}
		for (VariableBase vb : _factorGraph.getVariablesFlat())
		{
			SVariable sv = (SVariable)vb.getSolver();
			sv.setCalculateDerivative(val);
		}
	}
	
	
	// REFACTOR: make this package-protected?
	public IFactorTable getCurrentFactorTable()
	{
		return _currentFactorTable;
	}


	@Override
	public void initialize()
	{
		super.initialize();
		for (Factor f : getModelObject().getFactors())
		{
			if (f.getSolver() instanceof STableFactor)
			{
				STableFactor tf = (STableFactor)(f.getSolver());
				tf.getFactorTable().getIndicesSparseUnsafe();
				tf.getFactorTable().getWeightsSparseUnsafe();
			}
		}
		
	}
	


}
