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

package com.analog.lyric.dimple.model.transform;

import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

import org.eclipse.jdt.annotation.Nullable;

import com.analog.lyric.dimple.model.core.FactorGraph;
import com.analog.lyric.dimple.model.domains.Domain;
import com.analog.lyric.dimple.model.domains.JointDiscreteDomain;
import com.analog.lyric.dimple.model.domains.JointDomainIndexer;
import com.analog.lyric.dimple.model.factors.Factor;
import com.analog.lyric.dimple.model.values.Value;
import com.analog.lyric.dimple.model.variables.Discrete;
import com.analog.lyric.dimple.model.variables.Variable;
import com.analog.lyric.util.misc.Internal;
import com.google.common.collect.Iterables;

/**
 * Junction tree mapping generated by {@link JunctionTreeTransform}.
 * <p>
 * This holds references to the source factor graph, the transformation of the graph into a tree,
 * and information that describes the mapping between the two.
 * <p>
 * @since 0.05
 * @author Christopher Barber
 */
public class JunctionTreeTransformMap
{
	/*-------
	 * State
	 */
	
	private final FactorGraph _sourceModel;
	private final long _sourceVersion;
	private final FactorGraph _targetModel;
	private final @Nullable Map<Factor,Factor> _sourceToTargetFactors;
	private final @Nullable Map<Variable, Variable> _sourceToTargetVariables;
	/**
	 * Newly created joint variables that are deterministically computed from component variables.
	 */
	private final LinkedHashMap<Variable, AddedJointVariable<?>> _addedDeterministicVariables;
	private final Set<Variable> _conditionedVariables;
	
	/**
	 * Represents a variable that joins two or more other variables along an edge between
	 * two factors in the target model to ensure that it is singly connected. There may be
	 * multiple such joined variables for the same set of underlying variables.
	 * 
	 * @param <Var> specifies the variable type. Currently only {@link Discrete} is supported.
	 * @since 0.05
	 * @author Christopher Barber
	 */
	public static abstract class AddedJointVariable<Var extends Variable>
	{
		protected final Var _variable;
		protected final Var[] _inputs;
		
		protected AddedJointVariable(Var newVariable, Var[] inputVariables)
		{
			_variable = newVariable;
			_inputs = inputVariables;
		}

		@Internal
		public abstract void updateGuess();

		/**
		 * The domain of the joined variable.
		 */
		public Domain getDomain()
		{
			return _variable.getDomain();
		}
		
		/**
		 * The joined variable itself.
		 */
		public Var getVariable()
		{
			return _variable;
		}
		
		/**
		 * The i'th variable that is joined into this one.
		 */
		public Var getInput(int i)
		{
			return _inputs[i];
		}
		
		/**
		 * The number of variables that were joined into this one.
		 */
		public final int getInputCount()
		{
			return _inputs.length;
		}
		
		/**
		 * @category internal
		 */
		@Internal
		public abstract void updateValue(Value newVariableValue, Value[] inputs);
	}
	
	public static class AddedJointDiscreteVariable extends AddedJointVariable<Discrete>
	{

		/**
		 * @param newVariable
		 * @param inputVariables
		 */
		public AddedJointDiscreteVariable(Discrete newVariable, Discrete[] inputVariables)
		{
			super(newVariable, inputVariables);
			assert(invariantsHold());
		}
		
		private boolean invariantsHold()
		{
			JointDomainIndexer domain = getDomain().getDomainIndexer();
			assert(domain.size() == _inputs.length);
			for (int i = 0; i < _inputs.length; ++i)
			{
				assert(domain.get(i) == _inputs[i].getDomain());
			}
			return true;
		}

		@Override
		public JointDiscreteDomain<?> getDomain()
		{
			return (JointDiscreteDomain<?>) getVariable().getDomain();
		}
		
		@Override
		public void updateGuess()
		{
			final JointDomainIndexer indexer = getDomain().getDomainIndexer();
			final int[] indices = indexer.allocateIndices(null);
			boolean allWereSet = true;
			for (int i = 0; i < _inputs.length; ++i)
			{
				Discrete input = getInput(i);
				allWereSet &= input.guessWasSet() || input.hasFixedValue();
				indices[i] = getInput(i).getGuessIndex();
			}
			Discrete var = getVariable();
			if (allWereSet)
			{
				var.setGuessIndex(indexer.jointIndexFromIndices(indices));
			}
			else
			{
				var.setGuess(null);
			}
		}
		
		@Override
		public void updateValue(Value newVariableValue, Value[] inputs)
		{
			JointDomainIndexer indexer = getDomain().getDomainIndexer();
			newVariableValue.setIndex(indexer.jointIndexFromValues(inputs));
		}
		
	}
	
	/*--------------
	 * Construction
	 */
	
	protected JunctionTreeTransformMap(FactorGraph source, FactorGraph target)
	{
		final boolean identity = (source == target);
		_sourceModel = source;
		_sourceVersion = source.structureVersion();
		_targetModel = target;
		_sourceToTargetVariables = identity? null : new HashMap<Variable,Variable>(source.getVariableCount());
		_sourceToTargetFactors = identity? null : new HashMap<Factor,Factor>(source.getFactorCount());
		_addedDeterministicVariables = new LinkedHashMap<Variable, AddedJointVariable<?>>();
		_conditionedVariables = new LinkedHashSet<Variable>();
	}
	
	protected JunctionTreeTransformMap(FactorGraph source)
	{
		this(source, source);
	}
	
	static JunctionTreeTransformMap create(FactorGraph source, FactorGraph target)
	{
		return new JunctionTreeTransformMap(source, target);
	}
	
	static JunctionTreeTransformMap identity(FactorGraph model)
	{
		return new JunctionTreeTransformMap(model);
	}
	
	/*---------
	 * Methods
	 */
	
	public Iterable<AddedJointVariable<?>> addedJointVariables()
	{
		return Iterables.unmodifiableIterable(_addedDeterministicVariables.values());
	}
	
	public @Nullable <Var extends Variable> AddedJointVariable<Var> getAddedDeterministicVariable(Var targetVariable)
	{
		@SuppressWarnings("unchecked")
		AddedJointVariable<Var> var = (AddedJointVariable<Var>) _addedDeterministicVariables.get(targetVariable);
		return var;
	}
	
	/**
	 * Unmodifiable set of source variables that have been conditioned out of
	 * the target graph.
	 */
	public Set<Variable> conditionedVariables()
	{
		return Collections.unmodifiableSet(_conditionedVariables);
	}
	
	/**
	 * True if mapping is the identity mapping, which is a simple copy of the graph.
	 */
	public boolean isIdentity()
	{
		return _sourceToTargetVariables == null;
	}
	
	/**
	 * True if the current mapping is up-to-date with respect to the current state of
	 * the {@link #source()} model (and therefore can be reused for inference).
	 */
	public boolean isValid()
	{
		if (_sourceVersion != _sourceModel.structureVersion())
		{
			return false;
		}
		
		for (Variable sourceVar : _conditionedVariables)
		{
			if (!sourceVar.hasFixedValue())
				return false;
			Variable targetVar = sourceToTargetVariable(sourceVar);
			if (!targetVar.hasFixedValue())
				return false;
			if (!Objects.equals(sourceVar.getPrior(), targetVar.getPrior()))
				return false;
		}
		
		return true;
	}

	/**
	 * The original model from which the transformation was generated.
	 */
	public FactorGraph source()
	{
		return _sourceModel;
	}
	
	/**
	 * Returns the target factor that subsumes the given {@code sourceFactor}.
	 * <p>
	 * As long as the transform {@link #isValid()} this is guaranteed to return a
	 * non-null variable in {@link #target()} for every variable in {@link #source()}.
	 * Note that unlike {@link #sourceToTargetVariable(Variable)} the target factor
	 * may not exactly correspond to the source factor. Instead it may represent the
	 * product of multiple factors.
	 * <p>
	 * @see #sourceToTargetFactors()
	 */
	public Factor sourceToTargetFactor(Factor sourceFactor)
	{
		final Map<Factor,Factor> sourceToTargetFactors = _sourceToTargetFactors;
		if (sourceToTargetFactors == null)
		{
			return sourceFactor;
		}
		return sourceToTargetFactors.get(sourceFactor);
	}
	
	/**
	 * Returns a read-only mapping from factors in {@link #source()} to factors
	 * in {@link #target()}.
	 * 
	 * @see #sourceToTargetFactor(Factor)
	 */
	public Map<Factor,Factor> sourceToTargetFactors()
	{
		if (_sourceToTargetFactors == null)
		{
			return Collections.emptyMap();
		}
		return Collections.unmodifiableMap(_sourceToTargetFactors);
	}
	
	/**
	 * Returns the target variable corresponding to the given {@code sourceVariable}.
	 * <p>
	 * As long as the transform {@link #isValid()} this is guaranteed to return a
	 * non-null variable in {@link #target()} for every variable in {@link #source()}.
	 * <p>
	 * @see #sourceToTargetVariables()
	 */
	public Variable sourceToTargetVariable(Variable sourceVariable)
	{
		final Map<Variable, Variable> sourceToTargetVariables = _sourceToTargetVariables;
		if (sourceToTargetVariables == null)
		{
			return sourceVariable;
		}
		return sourceToTargetVariables.get(sourceVariable);
	}
	
	/**
	 * Returns a read-only mapping from variables in {@link #source()} to variables
	 * in {@link #target()}.
	 * 
	 * @see #sourceToTargetVariable(Variable)
	 */
	public Map<Variable,Variable> sourceToTargetVariables()
	{
		if (_sourceToTargetVariables == null)
		{
			return Collections.emptyMap();
		}
		return Collections.unmodifiableMap(_sourceToTargetVariables);
	}
	
	/**
	 * Value of {@link FactorGraph#getVersionId()} of {@link #source()} when
	 * transform map was created.
	 */
	public long sourceVersion()
	{
		return _sourceVersion;
	}
	
	/**
	 * The generated target model generated from {@link #source()} by {@link JunctionTreeTransform}.
	 * <p>
	 * As long as {@link #isValid()} this will have variables corresponding to the ones in the source model.
	 * <p>
	 * @see #sourceToTargetVariable(Variable)
	 * @see #sourceToTargetFactor(Factor)
	 */
	public FactorGraph target()
	{
		return _targetModel;
	}
	
	/*------------------
	 * Internal methods
	 */
	
	/**
	 * @category internal
	 */
	@Internal
	public void updateGuesses()
	{
		
		for (Map.Entry<Variable,Variable> entry : sourceToTargetVariables().entrySet())
		{
			Variable sourceVar = entry.getKey();
			Variable targetVar = entry.getValue();
			
			if (!sourceVar.guessWasSet())
			{
				targetVar.setGuess(null);
			}
			else
			{
				if (sourceVar instanceof Discrete)
				{
					((Discrete)targetVar).setGuessIndex(((Discrete)sourceVar).getGuessIndex());
				}
				else
				{
					targetVar.setGuess(sourceVar.getGuess());
				}
			}
		}
		
		for (AddedJointVariable<?> added : addedJointVariables())
		{
			added.updateGuess();
		}
	}

	/*-----------------
	 * Package methods
	 */

	void addConditionedVariable(Variable variable)
	{
		assert(variable.hasFixedValue());
		_conditionedVariables.add(variable);
	}

	void addDeterministicVariable(AddedJointVariable<?> addedVar)
	{
		_addedDeterministicVariables.put(addedVar.getVariable(), addedVar);
	}

	void addFactorMapping(Factor sourceFactor, Factor targetFactor)
	{
		Objects.requireNonNull(_sourceToTargetFactors).put(sourceFactor, targetFactor);
	}

	void addVariableMapping(Variable sourceVariable, Variable targetVariable)
	{
		Objects.requireNonNull(_sourceToTargetVariables).put(sourceVariable, targetVariable);
	}
	
}
