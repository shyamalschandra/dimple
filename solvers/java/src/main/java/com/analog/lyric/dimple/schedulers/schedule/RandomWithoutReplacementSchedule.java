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

package com.analog.lyric.dimple.schedulers.schedule;

import static com.analog.lyric.dimple.environment.DimpleEnvironment.*;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.Objects;

import org.eclipse.jdt.annotation.NonNull;
import org.eclipse.jdt.annotation.Nullable;

import com.analog.lyric.dimple.model.core.EdgeState;
import com.analog.lyric.dimple.model.core.FactorGraph;
import com.analog.lyric.dimple.model.factors.Factor;
import com.analog.lyric.dimple.model.factors.FactorList;
import com.analog.lyric.dimple.schedulers.IScheduler;
import com.analog.lyric.dimple.schedulers.scheduleEntry.EdgeScheduleEntry;
import com.analog.lyric.dimple.schedulers.scheduleEntry.IScheduleEntry;
import com.analog.lyric.dimple.schedulers.scheduleEntry.NodeScheduleEntry;
import com.analog.lyric.math.DimpleRandom;

/**
 * @author jeffb
 * 
 *         This creates a dynamic schedule, which updates factors in a randomly
 *         chosen sequence without replacement. Prior to each factor update, the
 *         corresponding edges of the connected variables are updated. On each
 *         iteration a new random update sequence is generated.
 * 
 *         WARNING: This schedule DOES NOT respect any existing sub-graph
 *         scheduler associations. That is, if any sub-graph already has an
 *         associated scheduler, that scheduler is ignored in creating this
 *         schedule.
 */
public class RandomWithoutReplacementSchedule extends ScheduleBase
{
	private static final long serialVersionUID = 1L;

	/*-------
	 * State
	 */
	
	protected FactorList _factors;
	protected int _numFactors;
	protected int[] _factorIndices;

	/*--------------
	 * Construction
	 */
	
	public RandomWithoutReplacementSchedule(FactorGraph factorGraph)
	{
		this(null, factorGraph);
	}

	@SuppressWarnings("null")
	public RandomWithoutReplacementSchedule(@Nullable IScheduler scheduler, FactorGraph factorGraph)
	{
		super(scheduler, factorGraph);
		initialize();
	}
	
	/*-------------------
	 * ISchedule methods
	 */

	@Override
	public void attach(FactorGraph factorGraph)
	{
		super.attach(factorGraph);
		initialize();
	}

	protected void initialize()
	{
		_factors = getFactorGraph().getNonGraphFactors();
		_numFactors = _factors.size();
		_factorIndices = new int[_numFactors];
		++_version;
	}
	
	@Override
	public @NonNull FactorGraph getFactorGraph()
	{
		return Objects.requireNonNull(_factorGraph);
	}

	@Override
	public Iterator<IScheduleEntry> iterator()
	{
		final DimpleRandom rand = activeRandom();
		
		ArrayList<IScheduleEntry> updateList = new ArrayList<IScheduleEntry>();
		
		// Randomize the sequence
		for (int i = 0; i < _numFactors; i++) _factorIndices[i] = i;
		for (int iFactor = _numFactors - 1; iFactor > 0; iFactor--)
		{
			int randRange = iFactor + 1;
		    int randFactor = rand.nextInt(randRange);
		    int nextIndex = _factorIndices[randFactor];
		    _factorIndices[randFactor] = _factorIndices[iFactor];
		    _factorIndices[iFactor] = nextIndex;
		}

		// One iteration consists of the number of factor updates equaling the total number of factors, even though not all factors will necessarily be updated
		for (int iFactor = 0; iFactor < _numFactors; iFactor++)
		{
			int factorIndex = _factorIndices[iFactor];
			Factor f = ((ArrayList<Factor>)_factors.values()).get(factorIndex);
			final FactorGraph fg = f.requireParentGraph();
			for (int i = 0, n = f.getSiblingCount(); i < n; ++i)
			{
				final EdgeState edge = f.getSiblingEdgeState(i);
				updateList.add(new EdgeScheduleEntry(edge.getVariable(fg), edge.getVariableToFactorEdgeNumber()));
			}
			updateList.add(new NodeScheduleEntry(f));
		}

		return updateList.iterator();
	}

}
