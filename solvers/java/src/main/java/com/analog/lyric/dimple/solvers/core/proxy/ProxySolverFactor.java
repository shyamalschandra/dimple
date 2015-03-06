/*******************************************************************************
*   Copyright 2014 Analog Devices, Inc.
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

package com.analog.lyric.dimple.solvers.core.proxy;

import org.eclipse.jdt.annotation.Nullable;

import com.analog.lyric.dimple.exceptions.DimpleException;
import com.analog.lyric.dimple.model.factors.Factor;
import com.analog.lyric.dimple.solvers.core.SFactorBase;
import com.analog.lyric.dimple.solvers.interfaces.ISolverFactor;
import com.analog.lyric.dimple.solvers.interfaces.ISolverFactorGraph;
import com.analog.lyric.dimple.solvers.interfaces.ISolverNode;

/**
 * @since 0.05
 */
public abstract class ProxySolverFactor<Delegate extends ISolverFactor>
	extends SFactorBase implements ISolverFactor, IProxySolverNode<Delegate>
{
	protected final Factor _modelFactor;
	
	/*--------------
	 * Construction
	 */
	
	/**
	 * @param modelFactor
	 */
	protected ProxySolverFactor(Factor modelFactor, ISolverFactorGraph parent)
	{
		super(modelFactor, parent);
		_modelFactor = modelFactor;
	}

	/*---------------------
	 * ISolverNode methods
	 */
	
	@Override
	public double getBetheEntropy()
	{
		return requireDelegate("getBetheEntropy").getBetheEntropy();
	}

	@Override
	public double getInternalEnergy()
	{
		return requireDelegate("getInternalEnergy").getInternalEnergy();
	}

	@Override
	public Object getInputMsg(int portIndex)
	{
		throw unsupported("getInputMsg");
	}

	@Override
	public Object getOutputMsg(int portIndex)
	{
		throw unsupported("getOutputMsg");
	}

	@Override
	public double getScore()
	{
		return requireDelegate("getScore").getScore();
	}

	@Override
	public void initialize()
	{
		clearFlags();
		requireDelegate("initialize").initialize();
	}

	@Override
	public void moveMessages(ISolverNode other, int thisPortNum, int otherPortNum)
	{
		throw unsupported("moveMessages");
	}

	@Override
	public void setInputMsg(int portIndex, Object obj)
	{
		throw unsupported("setInputMsg");
	}

	@Override
	public void setOutputMsg(int portIndex, Object obj)
	{
		throw unsupported("setOutputMsg");
	}

	@Override
	public void setInputMsgValues(int portIndex, Object obj)
	{
		throw unsupported("setInputMsgValues");
	}

	@Override
	public void setOutputMsgValues(int portIndex, Object obj)
	{
		throw unsupported("setOutputMsgValues");
	}

	@Override
	public void update()
	{
		requireDelegate("update").update();
	}

	@Override
	public void updateEdge(int outPortNum)
	{
		throw unsupported("updateEdge");
	}

	/*-----------------------
	 * ISolverFactor methods
	 */
	
	@Override
	public Object getBelief()
	{
		return requireDelegate("getBelief").getBelief();
	}

	@Override
	public int[][] getPossibleBeliefIndices()
	{
		throw unsupported("getPossibleBeliefIndices");
	}

	@Override
	public void setDirectedTo(int[] indices)
	{
		throw unsupported("setDirectedTo");
	}
	
	/*---------------
	 * SNode methods
	 */
	
	@Override
	protected void doUpdateEdge(int edge)
	{
	}
	
	/*---------------
	 * Local methods
	 */
	
	@Override
	public abstract @Nullable Delegate getDelegate();

	/**
	 * Returns non-null delegate or throws an error indicating method requires that
	 * delegate solver has been set.
	 * @since 0.06
	 */
	protected Delegate requireDelegate(String method)
	{
		Delegate delegate = getDelegate();
		if (delegate == null)
		{
			throw new DimpleException("Delegate solver required by '%s' has not been set.", method);
		}
		return delegate;
	}
	
	protected RuntimeException unsupported(String method)
	{
		return DimpleException.unsupportedMethod(getClass(), method,
			"Not supported for proxy solver because graph topology may be different.");
	}
}
