/*******************************************************************************
 *   Copyright 2013 Analog Devices, Inc.
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

package com.analog.lyric.dimple.solvers.gibbs;

import java.util.ArrayList;

import com.analog.lyric.dimple.factorfunctions.core.FactorFunction;
import com.analog.lyric.dimple.factorfunctions.core.FactorFunctionUtilities;
import com.analog.lyric.dimple.model.DimpleException;
import com.analog.lyric.dimple.model.Factor;
import com.analog.lyric.dimple.model.INode;
import com.analog.lyric.dimple.model.RealDomain;
import com.analog.lyric.dimple.model.RealJoint;
import com.analog.lyric.dimple.model.RealJointDomain;
import com.analog.lyric.dimple.model.VariableBase;
import com.analog.lyric.dimple.solvers.core.SVariableBase;
import com.analog.lyric.dimple.solvers.core.SolverRandomGenerator;
import com.analog.lyric.dimple.solvers.core.proposalKernels.IProposalKernel;
import com.analog.lyric.dimple.solvers.gibbs.sample.RealJointSample;
import com.analog.lyric.dimple.solvers.gibbs.samplers.DefaultRealSampler;
import com.analog.lyric.dimple.solvers.gibbs.samplers.IRealSampler;
import com.analog.lyric.dimple.solvers.gibbs.samplers.ISampleScorer;
import com.analog.lyric.dimple.solvers.gibbs.samplers.MHSampler;
import com.analog.lyric.dimple.solvers.gibbs.samplers.RealSamplerRegistry;
import com.analog.lyric.dimple.solvers.interfaces.ISolverFactor;
import com.analog.lyric.dimple.solvers.interfaces.ISolverNode;

/**** WARNING: Whenever editing this class, also make the corresponding edit to SRealVariable.
 * The two are nearly identical, but unfortunately couldn't easily be shared due to the class hierarchy
 *
 */

public class SRealJointVariable extends SVariableBase implements ISolverVariableGibbs, ISampleScorer
{
	protected RealJoint _varReal;
	protected RealJointSample _outputMsg;
	protected double[] _sampleValue;
	protected double[] _initialSampleValue;
	protected FactorFunction[] _input;
	protected RealJointDomain _domain;
	protected IRealSampler _sampler = new DefaultRealSampler();
	protected ArrayList<double[]> _sampleArray;
	protected double[] _bestSampleValue;
	protected double _beta = 1;
	protected boolean _holdSampleValue = false;
	protected boolean _isDeterministicDepdentent = false;
	protected boolean _hasDeterministicDependents = false;
	protected int _numRealVars;
    protected double[] _guessValue;
    protected boolean _guessWasSet = false;
    protected int _tempIndex = 0;


	public SRealJointVariable(VariableBase var)  
	{
		super(var);

		if (!(var.getDomain() instanceof RealJointDomain))
			throw new DimpleException("expected real joint domain");

		_varReal = (RealJoint)_var;
		_domain = (RealJointDomain)var.getDomain();
		
		_numRealVars = _domain.getNumVars();
		_sampleValue = new double[_numRealVars];
		_initialSampleValue = new double[_numRealVars];
		_bestSampleValue = new double[_numRealVars];
		for (int i = 0; i < _numRealVars; i++)
		{
			_sampleValue[i] = 0;
			_initialSampleValue[i] = 0;
			_bestSampleValue[i] = 0;
		}
	}
	
	
	public void updateEdge(int outPortNum)
	{
		throw new DimpleException("Method not supported in Gibbs sampling solver.");
	}

	public void update()
	{
		// Don't bother to re-sample deterministic dependent variables (those that are the output of a directional deterministic factor)
		if (_isDeterministicDepdentent) return;

		// If the sample value is being held, don't modify the value
		if (_holdSampleValue) return;
		
		// Also return if the variable is set to a fixed value
		if (_var.hasFixedValue()) return;

		// Get the next sample value from the sampler
		for (int i = 0; i < _numRealVars; i++)
		{
			_tempIndex = i;		// Save this to be used by the call-back to getSampleScore
			double sampleValue = _sampleValue[i];
			double nextSampleValue = _sampler.nextSample(sampleValue, this);
			if (nextSampleValue != _sampleValue[i])	// Would be exactly equal if not changed since last value tested
				setCurrentSample(i, nextSampleValue);
		}
	}
	
	
	@Override
	public double getCurrentSampleScore()
	{
		if (!_domain.inDomain(_sampleValue))
			return Double.POSITIVE_INFINITY;		// Outside the domain
		
		int numPorts = _var.getSiblings().size();
		double potential = 0;

		// Sum up the potentials from the input and all connected factors
		if (_input != null)
		{
			for (int i = 0; i < _numRealVars; i++)
				potential += _input[i].evalEnergy(_sampleValue[i]);
		}
		ArrayList<INode> siblings = _var.getSiblings();
		for (int portIndex = 0; portIndex < numPorts; portIndex++)
		{
			INode factorNode = siblings.get(portIndex);
			ISolverFactorGibbs factor = (ISolverFactorGibbs)(factorNode.getSolver());
			int factorPortNumber = factorNode.getPortNum(_var);
			potential += factor.getConditionalPotential(factorPortNumber);
		}
		
		return potential * _beta;	// Incorporate current temperature
	}


	@Override
	public double getSampleScore(double sampleValue)
	{
		// WARNING: Side effect is that the current sample value changes to this sample value
		// Could change back but less efficient to do this, since we'll be updating the sample value anyway
		setCurrentSample(_tempIndex, sampleValue);

		return getCurrentSampleScore();
	}



	public void randomRestart()
	{
		// If the sample value is being held, don't modify the value
		if (_holdSampleValue) return;

		// If the variable has a fixed value, then set the current sample to that value and return
		if (_var.hasFixedValue())
		{
			setCurrentSample(_varReal.getFixedValue());
			return;
		}

		// TODO -- sample from the prior if specified, not just the bounds
		for (int i = 0; i < _numRealVars; i++)
		{
			RealDomain realDomain = _domain.getRealDomain(i);
			double hi = realDomain.getUpperBound();
			double lo = realDomain.getLowerBound();
			if (hi < Double.POSITIVE_INFINITY && lo > Double.NEGATIVE_INFINITY)
				setCurrentSample(i, SolverRandomGenerator.rand.nextDouble() * (hi - lo) + lo);
		}
	}

	public void updateBelief()
	{
		// TODO -- not clear if it's practical to compute beliefs for real variables, or if so, how they should be represented
	}

	public Object getBelief() 
	{
		return 0d;
	}

	@Override
	public void setInputOrFixedValue(Object input, Object fixedValue, boolean hasFixedValue) 
	{
		if (input == null)
			_input = null;
		else
		{
			_input = new FactorFunction[_numRealVars];
			for (int i = 0; i < _numRealVars; i++)
				_input[i] = (FactorFunction)((Object[])input)[i];
		}

		if (hasFixedValue)
			setCurrentSample(fixedValue);
	}

	
	@Override 
	public void updateDirectedCache()
	{
		_hasDeterministicDependents = hasDeterministicDependents();
		_isDeterministicDepdentent = isDeterministicDependent();
	}
	
	@Override
	public void postAddFactor(Factor f)
	{
		updateDirectedCache();
		if (_var.hasFixedValue())
		{
			setCurrentSample(_var.getFixedValueObject());
		}
		else
		{
			setCurrentSample(_sampleValue);
		}
	}

	
	@Override
	public final double getScore()
	{
		double[] value;
		if (_var.hasFixedValue())
			return 0;
		else if (_input == null)
			return 0;
		else if (_guessWasSet)
			value = _guessValue;
		else
			value = _sampleValue;
		
		double score = 0;
		for (int i = 0; i < _numRealVars; i++)
			score += _input[i].evalEnergy(value[i]);
		return score;
	}
	
	@Override
	public Object getGuess()
	{
		if (_guessWasSet)
			return _guessValue;
		else if (_var.hasFixedValue())
			return _varReal.getFixedValue();
		else
			return _sampleValue;
	}
	
	@Override
	public void setGuess(Object guess) 
	{
		_guessValue = (double[])guess;

		// Make sure the number is within the domain of the variable
		if (!_domain.inDomain(_guessValue))
			throw new DimpleException("Guess is not within the domain of the variable");
		
		_guessWasSet = true;
	}



	public final void saveAllSamples()
	{
		_sampleArray = new ArrayList<double[]>();
	}

	public final void saveCurrentSample()
	{
		if (_sampleArray != null)
			_sampleArray.add(_sampleValue.clone());
	}

	public final void saveBestSample()
	{
		_bestSampleValue = _sampleValue;
	}
	
	public double getConditionalPotential(int portIndex)
	{
		double result = getPotential();		// Start with the local potential
		
		// Propagate the request through the other neighboring factors and sum up the results
		ArrayList<INode> siblings = _var.getSiblings();
		int numPorts = siblings.size();
		for (int port = 0; port < numPorts; port++)	// Plus each input message value
			if (port != portIndex)
				result += ((ISolverFactorGibbs)siblings.get(port).getSolver()).getConditionalPotential(_var.getSiblingPortIndex(port));

		return result;
	}

	public final double getPotential()
	{
		if (_var.hasFixedValue())
			return 0;
		else if (_input == null)
			return 0;
		else
		{
			double potential = 0;
			for (int i = 0; i < _numRealVars; i++)
				potential += _input[i].evalEnergy(_sampleValue[i]);
			return potential;
		}
	}

	public final void setCurrentSample(Object value) {setCurrentSample((double[])value);}
	public final void setCurrentSample(double[] value)
	{
		_sampleValue = value;
		_outputMsg.setValue(value);
		
		// If this variable has deterministic dependents, then set their values
		setDependentValues();
	}
    public final void setCurrentSample(int index, Object value) {setCurrentSample(index, FactorFunctionUtilities.toDouble(value));}
	public final void setCurrentSample(int index, double value)
	{
		_sampleValue[index] = value;
		_outputMsg.setValue(index, value);
		
		// If this variable has deterministic dependents, then set their values
		setDependentValues();
	}
	
	public final void setDependentValues()
	{
		if (_hasDeterministicDependents)
		{
			ArrayList<INode> siblings = _var.getSiblings();
			int numPorts = siblings.size();
			for (int port = 0; port < numPorts; port++)	// Plus each input message value
			{
				Factor f = (Factor)siblings.get(port);
				if (f.getFactorFunction().isDeterministicDirected() && !f.isDirectedTo(_var))
					((ISolverFactorGibbs)f.getSolver()).updateNeighborVariableValue(_var.getSiblingPortIndex(port));
			}
		}
	}


	public final double[] getCurrentSample()
	{
		return _sampleValue;
	}

	public final double[] getBestSample()
	{
		return _bestSampleValue;
	}

	public final double[][] getAllSamples()
	{
		if (_sampleArray == null)
			throw new DimpleException("No samples saved. Must call saveAllSamples on variable or entire graph prior to solving");
		int length = _sampleArray.size();
		double[][] retval = new double[length][];
		for (int i = 0; i < length; i++)
			retval[i] = _sampleArray.get(i);
		return retval;
	}

	public final void setAndHoldSampleValue(double[] value)
	{
		setCurrentSample(value);
		holdSampleValue();
	}

	public final void holdSampleValue()
	{
		_holdSampleValue = true;
	}

	public final void releaseSampleValue()
	{
		_holdSampleValue = false;
	}




	// FIXME: REMOVE
	// There should be a way to call these directly via the samplers
	// If so, they should be removed from here since this makes this sampler-specific
	public final void setProposalStandardDeviation(double stdDev)
	{
		if (_sampler instanceof MHSampler)
			((MHSampler)_sampler).getProposalKernel().setParameters(stdDev);
	}
	public final double getProposalStandardDeviation()
	{
		if (_sampler instanceof MHSampler)
			return (Double)((MHSampler)_sampler).getProposalKernel().getParameters()[0];
		else
			return 0;
	}
	// Set the proposal kernel parameters more generally
	public final void setProposalKernelParameters(Object... parameters)
	{
		if (_sampler instanceof MHSampler)
			((MHSampler)_sampler).getProposalKernel().setParameters(parameters);
	}
	
	// FIXME: REMOVE
	// There should be a way to call these directly via the samplers
	// If so, they should be removed from here since this makes this sampler-specific
	public final void setProposalKernel(IProposalKernel proposalKernel)					// IProposalKernel object
	{
		if (_sampler instanceof MHSampler)
			((MHSampler)_sampler).setProposalKernel(proposalKernel);
	}
	public final void setProposalKernel(String proposalKernelName)						// Name of proposal kernel
	{
		if (_sampler instanceof MHSampler)
			((MHSampler)_sampler).setProposalKernel(proposalKernelName);
	}
	public final IProposalKernel getProposalKernel()
	{
		if (_sampler instanceof MHSampler)
			return ((MHSampler)_sampler).getProposalKernel();
		else
			return null;
	}
	
	// Set/get the sampler to be used for this variable
	public final void setSampler(IRealSampler sampler)
	{
		_sampler = sampler;
	}
	public final void setSampler(String samplerName)
	{
		_sampler = RealSamplerRegistry.get(samplerName);
	}
	public final IRealSampler getSampler()
	{
		return _sampler;
	}
	public final String getSamplerName()
	{
		return _sampler.getClass().getSimpleName();
	}

	public final void setInitialSampleValue(double[] initialSampleValue) {_initialSampleValue = initialSampleValue;}
	public final double[] getInitialSampleValue() {return _initialSampleValue;}


	public final void setBeta(double beta)	// beta = 1/temperature
	{
		_beta = beta;
	}
	
	// Determine whether or not this variable is a deterministic dependent variable; that is, one that corresponds
	// to the output of a directed deterministic factor
	public boolean isDeterministicDependent()
	{
		for (INode f : _var.getSiblings())
		{
			Factor factor = (Factor)f;
			if (factor.getFactorFunction().isDeterministicDirected() && factor.isDirectedTo(_var))
				return true;
		}
		return false;
	}
	
	// Determine whether or not this variable has variables that are deterministic dependents of this variable
	public boolean hasDeterministicDependents()
	{
		for (INode f : _var.getSiblings())
		{
			Factor factor = (Factor)f;
			if (factor.getFactorFunction().isDeterministicDirected() && !factor.isDirectedTo(_var))
				return true;
		}
		return false;
	}



	public RealJointSample createDefaultMessage() 
	{
		if (_var.hasFixedValue())
			return new RealJointSample(_varReal.getFixedValue());
		else
			return new RealJointSample(_initialSampleValue);
	}

	@Override
	public Object resetInputMessage(Object message) 
	{
		((RealJointSample)message).setValue(_var.hasFixedValue() ? _varReal.getFixedValue() : _initialSampleValue);
		return message;
	}

	@Override
	public void resetEdgeMessages(int portNum) 
	{
	}

	@Override
	public Object getInputMsg(int portIndex) 
	{
		throw new DimpleException("Not supported by: " + this);	
	}

	@Override
	public Object getOutputMsg(int portIndex) 
	{
		return _outputMsg;	
	}

	@Override
	public void setInputMsg(int portIndex, Object obj) 
	{
				
	}

	@Override
	public void moveMessages(ISolverNode other, int thisPortNum, int otherPortNum) 
	{
		
	}
	

	public void initialize()
	{
		super.initialize();

		if (!_isDeterministicDepdentent)
		{
			double[] initialSampleValue = _var.hasFixedValue() ? _varReal.getFixedValue() : _initialSampleValue;
			if (!_holdSampleValue)
				setCurrentSample(initialSampleValue);
		}
		
		_bestSampleValue = _sampleValue;
		if (_sampleArray != null) _sampleArray.clear();
	}

	@Override
	public Object[] createMessages(ISolverFactor factor) 
	{
		return new Object[] {null,_outputMsg};
	}
	
	public void createNonEdgeSpecificState()
	{
		_outputMsg = createDefaultMessage();
		_sampleValue = _outputMsg.getValue();
	}
	
	@Override
    public void moveNonEdgeSpecificState(ISolverNode other)
    {
		SRealJointVariable ovar = ((SRealJointVariable)other);
		_outputMsg = ovar._outputMsg;
		_sampleValue = ovar._sampleValue;
		_initialSampleValue = ovar._initialSampleValue;
		_sampleArray = ovar._sampleArray;
		_bestSampleValue = ovar._bestSampleValue;
		_beta = ovar._beta;
		_sampler = ovar._sampler;
		_holdSampleValue = ovar._holdSampleValue;
		_numRealVars = ovar._numRealVars;
    }

}