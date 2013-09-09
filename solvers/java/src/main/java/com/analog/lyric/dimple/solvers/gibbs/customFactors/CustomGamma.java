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

package com.analog.lyric.dimple.solvers.gibbs.customFactors;

import java.util.ArrayList;
import java.util.Collection;

import com.analog.lyric.dimple.factorfunctions.Gamma;
import com.analog.lyric.dimple.factorfunctions.core.FactorFunctionBase;
import com.analog.lyric.dimple.factorfunctions.core.FactorFunctionWithConstants;
import com.analog.lyric.dimple.model.Factor;
import com.analog.lyric.dimple.model.INode;
import com.analog.lyric.dimple.model.Real;
import com.analog.lyric.dimple.model.VariableBase;
import com.analog.lyric.dimple.solvers.gibbs.SRealVariable;
import com.analog.lyric.dimple.solvers.gibbs.samplers.conjugate.GammaParameters;
import com.analog.lyric.dimple.solvers.gibbs.samplers.conjugate.GammaSampler;
import com.analog.lyric.dimple.solvers.gibbs.samplers.conjugate.IRealConjugateSampler;
import com.analog.lyric.dimple.solvers.gibbs.samplers.conjugate.IRealConjugateSamplerFactory;

public class CustomGamma extends SRealConjugateFactor
{
	private IRealConjugateSampler[] _conjugateSampler;
	private Object[] _outputMsgs;
	private SRealVariable _alphaVariable;
	private SRealVariable _betaVariable;
	private boolean _hasConstantAlpha;
	private boolean _hasConstantBeta;
	private boolean _hasConstantOutputs;
	private boolean _hasFactorFunctionConstants;
	private boolean _hasFactorFunctionConstructorConstants;
	private int _numOutputEdges;
	private int _numParameterEdges;
	private int _alphaParameterPort = -1;
	private int _betaParameterPort = -1;
	private int _constantOutputCount;
	private double _constantAlphaValue;
	private double _constantBetaValue;
	private double _constantOutputSum;
	private static final int NUM_PARAMETERS = 2;
	private static final int ALPHA_PARAMETER_INDEX = 0;
	private static final int BETA_PARAMETER_INDEX = 1;
	private static final int NO_PORT = -1;

	public CustomGamma(Factor factor)
	{
		super(factor);
	}

	@Override
	public void updateEdgeMessage(int outPortNum)
	{
		IRealConjugateSampler conjugateSampler = _conjugateSampler[outPortNum];
		if (conjugateSampler == null)
			super.updateEdgeMessage(outPortNum);
		else if (conjugateSampler instanceof GammaSampler)
		{
			GammaParameters outputMsg = (GammaParameters)_outputMsgs[outPortNum];
			if (outPortNum >= _numParameterEdges)
			{
				// Output port is directed output
				outputMsg.setAlpha(_hasConstantAlpha ? _constantAlphaValue : _alphaVariable.getCurrentSample());
				outputMsg.setBeta(_hasConstantBeta ? _constantBetaValue : _betaVariable.getCurrentSample());
			}
			else
			{
				// Output port must be the beta-parameter input
				// Determine sample alpha and beta parameters
				
				// Start with the ports to variable outputs
				ArrayList<INode> siblings = _factor.getSiblings();
				double sum = 0;
				for (int port = _numParameterEdges; port < _numPorts; port++)
					sum += ((SRealVariable)(((VariableBase)siblings.get(port)).getSolver())).getCurrentSample();
				int count = _numOutputEdges;
				
				// Include any constant outputs also
				if (_hasConstantOutputs)
				{
					sum += _constantOutputSum;
					count += _constantOutputCount;
				}
				
				// Get the current alpha value
				double alpha = _hasConstantAlpha ? _constantAlphaValue : _alphaVariable.getCurrentSample();
				
				outputMsg.setAlpha(count * alpha);			// Sample alpha
				outputMsg.setBeta(sum);						// Sample beta
			}
		}
		else
			super.updateEdgeMessage(outPortNum);
	}
	
	
	@Override
	public Collection<IRealConjugateSamplerFactory> getAvailableSamplers(int portNumber)
	{
		Collection<IRealConjugateSamplerFactory> availableSamplers = new ArrayList<IRealConjugateSamplerFactory>();
		if (!isPortAlphaParameter(portNumber))				// No supported conjugate sampler for alpha parameter
			availableSamplers.add(GammaSampler.factory);	// Either beta parameter or output, which have Gamma distribution
		return availableSamplers;
	}
	
	public boolean isPortAlphaParameter(int portNumber)
	{
		determineParameterConstantsAndEdges();	// Call this here since initialize may not have been called yet
		return (portNumber == _alphaParameterPort);
	}

	
	
	@Override
	public void initialize()
	{
		super.initialize();
		
		// Determine if any ports can use a conjugate sampler
		_conjugateSampler = new IRealConjugateSampler[_numPorts];
		for (int port = 0; port < _numPorts; port++)
		{
			INode var = _factor.getSiblings().get(port);
			if (var instanceof Real)
				_conjugateSampler[port] = ((SRealVariable)var.getSolver()).getConjugateSampler();
			else
				_conjugateSampler[port] = null;
		}
		
		
		// Determine what parameters are constants or edges, and save the state
		determineParameterConstantsAndEdges();
		
		
		// Pre-compute statistics associated with any constant output values
		_hasConstantOutputs = false;
		if (_hasFactorFunctionConstants)
		{
			FactorFunctionWithConstants	constantFactorFunction = (FactorFunctionWithConstants)(_factor.getFactorFunction());
			Object[] constantValues = constantFactorFunction.getConstants();
			int[] constantIndices = constantFactorFunction.getConstantIndices();
			_constantOutputCount = 0;
			_constantOutputSum = 0;
			for (int i = 0; i < constantIndices.length; i++)
			{
				if (_hasFactorFunctionConstructorConstants || constantIndices[i] >= NUM_PARAMETERS)
				{
					_constantOutputSum += (Double)constantValues[i];
					_constantOutputCount++;
				}
			}
			_hasConstantOutputs = true;
		}
	}
	
	
	private void determineParameterConstantsAndEdges()
	{
		// Get the factor function and related state
		FactorFunctionBase factorFunction = _factor.getFactorFunction();
		FactorFunctionWithConstants constantFactorFunction = null;
		_hasFactorFunctionConstants = false;
		if (factorFunction instanceof FactorFunctionWithConstants)	// In case the factor function is wrapped, get the specific factor function within
		{
			_hasFactorFunctionConstants = true;
			constantFactorFunction = (FactorFunctionWithConstants)factorFunction;
			factorFunction = constantFactorFunction.getContainedFactorFunction();
		}
		Gamma specificFactorFunction = (Gamma)factorFunction;
		
		
		// Pre-determine whether or not the parameters are constant; if so save the value; if not save reference to the variable
		_hasFactorFunctionConstructorConstants = specificFactorFunction.hasConstantParameters();
		if (_hasFactorFunctionConstructorConstants)
		{
			// The factor function has fixed parameters provided in the factor-function constructor
			_hasConstantAlpha = true;
			_hasConstantBeta = true;
			_alphaParameterPort = NO_PORT;
			_betaParameterPort = NO_PORT;
			_constantAlphaValue = specificFactorFunction.getAlpha();
			_constantBetaValue = specificFactorFunction.getBeta();
			_numParameterEdges = 0;
		}
		else // Variable or constant parameters
		{
			_numParameterEdges = 0;
			ArrayList<INode> siblings = _factor.getSiblings();
			if (_hasFactorFunctionConstants && constantFactorFunction.isConstantIndex(ALPHA_PARAMETER_INDEX))
			{
				_hasConstantAlpha = true;
				_alphaParameterPort = NO_PORT;
				_constantAlphaValue = (Double)constantFactorFunction.getConstantByIndex(ALPHA_PARAMETER_INDEX);
				_alphaVariable = null;
			}
			else
			{
				_hasConstantAlpha = false;
				_alphaParameterPort = _numParameterEdges++;
				_alphaVariable = (SRealVariable)(((VariableBase)siblings.get(_alphaParameterPort)).getSolver());
			}
			if (_hasFactorFunctionConstants && constantFactorFunction.isConstantIndex(BETA_PARAMETER_INDEX))
			{
				_hasConstantBeta = true;
				_betaParameterPort = NO_PORT;
				_constantBetaValue = (Double)constantFactorFunction.getConstantByIndex(BETA_PARAMETER_INDEX);
				_betaVariable = null;
			}
			else
			{
				_hasConstantBeta = false;
				_betaParameterPort = _numParameterEdges++;
				_betaVariable = (SRealVariable)(((VariableBase)siblings.get(_betaParameterPort)).getSolver());
			}
		}
		_numOutputEdges = _numPorts - _numParameterEdges;
	}
	
	
	@Override
	public void createMessages() 
	{
		super.createMessages();
		_outputMsgs = new Object[_numPorts];
		for (int i = 0; i < _numPorts; i++)
			_outputMsgs[i] = new GammaParameters();
	}
	
	@Override
	public Object getOutputMsg(int portIndex) 
	{
		return _outputMsgs[portIndex];
	}

}
