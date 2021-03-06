/*******************************************************************************
*   Copyright 2015 Analog Devices, Inc.
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

package com.analog.lyric.dimple.data;

import static java.util.Objects.*;

import java.util.AbstractMap;
import java.util.AbstractSet;
import java.util.Collections;
import java.util.Iterator;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

import org.eclipse.jdt.annotation.NonNullByDefault;
import org.eclipse.jdt.annotation.Nullable;

import com.analog.lyric.collect.ExtendedArrayList;
import com.analog.lyric.dimple.model.core.FactorGraph;
import com.analog.lyric.dimple.model.core.FactorGraphIterables;
import com.analog.lyric.dimple.model.core.IFactorGraphChild;
import com.analog.lyric.dimple.model.core.Ids;
import com.analog.lyric.dimple.model.values.Value;
import com.google.common.collect.UnmodifiableIterator;

/**
 * Base implementation for DataLayer abstraction.
 * <p>
 * @since 0.08
 * @author Christopher Barber
 */
public abstract class DataLayerBase<K extends IFactorGraphChild, D extends IDatum>
	extends AbstractMap<K, D> implements Cloneable
{
	/*-------
	 * State
	 */
	
	private final Class<K> _keyType;
	private final Class<D> _baseType;
	private final FactorGraph _rootGraph;
	/**
	 * Per-graph data indexed by graph tree index.
	 */
	private final ExtendedArrayList<FactorGraphData<K,D>> _data;
	private final FactorGraphData.Constructor<K,D> _constructor;
	protected final int _keyTypeIndex;
	private final boolean _createDataOnRead;
	
	/*--------------
	 * Construction
	 */
	
	protected DataLayerBase(
		FactorGraph graph,
		FactorGraphData.Constructor<K,D> constructor,
		Class<K> keyType,
		Class<D> baseType)
	{
		_keyType = keyType;
		_keyTypeIndex = Ids.typeIndexForInstanceClass(keyType);
		_baseType = baseType;
		_rootGraph = graph.getRootGraph();
		_data = new ExtendedArrayList<>();
		_constructor = constructor;
		_createDataOnRead = constructor.createOnRead();
	}
	
	protected DataLayerBase(FactorGraph graph, FactorGraphData.Constructor<K,D> constructor)
	{
		this(graph, constructor, constructor.keyType(), constructor.baseType());
	}
	
	protected DataLayerBase(FactorGraph graph, DataDensity density, Class<K> keyType, Class<D> baseType)
	{
		this(graph, FactorGraphData.constructorForType(density, keyType, baseType));
	}
	
	protected DataLayerBase(DataLayerBase<K,D> other)
	{
		this(other._rootGraph, other._constructor, other._keyType, other._baseType);
		for (int i = other._data.size(); --i>=0;)
		{
			FactorGraphData<K,D> data = other._data.getOrNull(i);
			if (data != null)
			{
				_data.set(i, data.clone(this));
			}
		}
	}
	
	@Override
	public abstract DataLayerBase<K,D> clone();

	/*----------------
	 * Object methods
	 */
	
	/**
	 * Compares contents with another object.
	 * <p>
	 * This differs from the {@linkplain AbstractMap#equals default implementation} only in the
	 * case when comparing two empty {@link FactorGraphData} objects, which will only be considered
	 * equal if they both refer to the same {@link #rootGraph root graph}.
	 */
	@Override
	public boolean equals(@Nullable Object obj)
	{
		if (this == obj)
		{
			return true;
		}
		
		if (obj instanceof DataLayerBase<?,?>)
		{
			DataLayerBase<?,?> other = (DataLayerBase<?,?>)obj;
		
			if (_rootGraph != other._rootGraph)
			{
				return false;
			}
	
			for (FactorGraph graph : FactorGraphIterables.subgraphs(_rootGraph))
			{
				Map<K,D> data = getDataForGraph(graph);
				if (data == null)
				{
					data = Collections.emptyMap();
				}
				Map<?,?> otherData = other.getDataForGraph(graph);
				if (otherData == null)
				{
					otherData = Collections.emptyMap();
				}
				
				if (!data.equals(otherData))
				{
					return false;
				}
			}

			return true;
		}
		
		return super.equals(obj);
	}
	
	/*-----------------
	 * IEquals methods
	 */
	
	public boolean objectEquals(@Nullable Object obj)
	{
		return equals(obj);
	}

	/*-------------
	 * Map methods
	 */
	
	@Override
	public void clear()
	{
		_data.clear();
	}
	
	@Override
	public boolean containsKey(@Nullable Object key)
	{
		final IFactorGraphChild child = _rootGraph.getChild(key);
		return _keyType.isInstance(key) && containsDataFor(_keyType.cast(child));
	}
	
	@Override
	public Set<Map.Entry<K, D>> entrySet()
	{
		return new EntrySet();
	}
	
	@Override
	public @Nullable D get(@Nullable Object key)
	{
		IFactorGraphChild child = _rootGraph.getChild(key);
		if (_keyType.isInstance(child))
		{
			return get(_keyType.cast(child));
		}
		
		return null;
	}
	
	public @Nullable D get (K var)
	{
		final FactorGraphData<K,D> data = getDataForChild(var);
		return data != null ? data.get(var) : null;
	}
	
	@Override
	public Set<K> keySet()
	{
		return new KeySet();
	}
	
	@NonNullByDefault(false)
	@Override
	public @Nullable D put(K var, @Nullable D value)
	{
		assertSharesRoot(var);
		final FactorGraph graph = requireNonNull(var.getParentGraph());
		FactorGraphData<K,D> data = _data.getOrNull(graph.getGraphTreeIndex());

		if (data != null)
		{
			return data.put(var, value);
		}
		else if (value != null)
		{
			return createDataForGraph(graph).put(var, value);
		}
		
		return null;
	}
	
	@Override
	public @Nullable D remove(@Nullable Object key)
	{
		IFactorGraphChild child = _rootGraph.getChild(key);
		if (_keyType.isInstance(child))
		{
			return remove(_keyType.cast(child));
		}
		
		return null;
	}
	
	public @Nullable D remove(K key)
	{
		final FactorGraphData<K,D> data = getDataForChild(key);
		return data != null ? data.remove(key) : null;
	}
	
	@Override
	public int size()
	{
		int n = 0;
		for (FactorGraphData<?,?> data : _data)
		{
			if (data != null)
			{
				n += data.size();
			}
		}
		return n;
	}
	
	/*-----------------------
	 * DataLayerBase methods
	 */
	
	/**
	 * True if layer supports {@link Value} objects.
	 * <p>
	 * True if {@link Value} is a subclass or superclass of {@link #baseType()}.
	 * @since 0.08
	 */
	public boolean allowsValues()
	{
		return (_baseType.isAssignableFrom(Value.class)) || Value.class.isAssignableFrom(_baseType);
	}
	
	/**
	 * Base type instance for data held in this layer.
	 * <p>
	 * @since 0.08
	 */
	public final Class<D> baseType()
	{
		return _baseType;
	}
	
	/**
	 * True if data layer contains a non-null datum for given {@code key}.
	 * <p>
	 * This is the same as {@link #containsKey(Object)} but requires an argument with declared key type {@code K}.
	 * <p>
	 * @since 0.08
	 */
	public boolean containsDataFor(K key)
	{
		final FactorGraphData<K,D> data = getDataForChild(key);
		return data != null && data.containsKey(key);
	}
	
	/**
	 * Force instantiation of {@link FactorGraphData} for given {@code graph} for this layer.
	 * <p>
	 * Unlike {@link #getDataForGraph(FactorGraph)} this will create a new instance if necessary.
	 * <p>
	 * @param graph must be a graph in the same graph tree as this layer.
	 * @since 0.08
	 */
	public FactorGraphData<K,D> createDataForGraph(FactorGraph graph)
	{
		assertSharesRoot(graph);
		
		FactorGraphData<K,D> data = _data.getOrNull(graph.getGraphTreeIndex());
		if (data == null)
		{
			FactorGraphData<K,D> newData = _constructor.apply(this, graph);
			setDataForGraph(newData);
			data = newData;
		}

		return data;
	}
	
	/**
	 * The {@linkplain FactorGraphData.Constructor constructor object} used to create new {@link FactorGraphData}
	 * instances.
	 * <p>
	 * This is used by {@link #createDataForGraph(FactorGraph)} to create new instances.
	 * <p>
	 * @since 0.08
	 */
	public final FactorGraphData.Constructor<K,D> dataConstructor()
	{
		return _constructor;
	}
	
	/**
	 * Lookup data for child with given graph tree id.
	 * @since 0.08
	 * @see #get(Object)
	 */
	public @Nullable D getByGraphTreeId(long id)
	{
		int localId = Ids.localIdFromGraphTreeId(id);
		int graphTreeIndex = Ids.graphTreeIndexFromGraphTreeId(id);
		
		if (Ids.typeIndexFromLocalId(localId) == _keyTypeIndex)
		{
			return getByGraphTreeAndLocalIndices(graphTreeIndex, Ids.indexFromLocalId(localId));
		}
		
		return null;
	}
	
	/**
	 * Lookup data for key with given graph tree and local indices.
	 * @since 0.08
	 * @see #getByGraphTreeId(long)
	 */
	public @Nullable D getByGraphTreeAndLocalIndices(int graphTreeIndex, int localIndex)
	{
		FactorGraphData<K,D> data = _data.getOrNull(graphTreeIndex);
		return data != null ? data.getByLocalIndex(localIndex) : null;
	}
	
	public @Nullable FactorGraphData<K,D> getDataForGraph(FactorGraph graph)
	{
		FactorGraphData<K,D> data = null;
		
		if (sharesRoot(graph))
		{
			data = _data.getOrNull(graph.getGraphTreeIndex());
			if (data == null &&	_createDataOnRead)
			{
				FactorGraphData<K,D> newData = _constructor.apply(this, graph);
				setDataForGraph(newData);
				data = newData;
			}
		}
		
		return data;
	}
	
	public Iterable<? extends FactorGraphData<K,D>> getData()
	{
		return new Iterable<FactorGraphData<K,D>>() {
			@Override
			public Iterator<FactorGraphData<K, D>> iterator()
			{
				return new DataIterator();
			}
		};
	}
	
	public Class<K> keyType()
	{
		return _keyType;
	}
	
	/**
	 * If true, this is a view of data held in other objects.
	 * <p>
	 * When true, cloning this object will not result in a distinct copy of the data.
	 * <p>
	 * The default implementation returns false.
	 * @since 0.08
	 */
	public boolean isView()
	{
		return false;
	}
	
	public @Nullable FactorGraphData<K,D> removeDataForGraph(FactorGraph graph)
	{
		return sharesRoot(graph) ? _data.set(graph.getGraphTreeIndex(), null) : null;
	}
	
	/**
	 * Root {@link FactorGraph} of the graph tree represented by this data layer.
	 * @since 0.08
	 */
	public FactorGraph rootGraph()
	{
		return _rootGraph;
	}
	
	public @Nullable FactorGraphData<K,D> setDataForGraph(FactorGraphData<K,D> data)
	{
		if (data.layer() != this)
		{
			throw new IllegalArgumentException(String.format("Data belongs to a different layer"));
		}
		
		return _data.set(data.graph().getGraphTreeIndex(), data);
	}
	
	/**
	 * True if {@code child} is in the same graph tree represented by this data layer.
	 * @since 0.08
	 */
	public boolean sharesRoot(IFactorGraphChild child)
	{
		return child.getRootGraph() == _rootGraph;
	}
	
	/*--------------------------
	 * Non-public inner classes
	 */

	private class DataIterator extends UnmodifiableIterator<FactorGraphData<K,D>>
	{
		private int _graphTreeIndex;
		private final int _maxGraphTreeIndex;
		private @Nullable FactorGraphData<K,D> _next;
		
		DataIterator()
		{
			_graphTreeIndex = -1;
			_maxGraphTreeIndex = _rootGraph.getMaxGraphTreeIndex();
			if (_data.size() <= _maxGraphTreeIndex)
			{
				_data.setSize(_maxGraphTreeIndex + 1);
			}
		}
		
		@Override
		public boolean hasNext()
		{
			return advance() != null;
		}
	
		@Override
		public @Nullable FactorGraphData<K, D> next()
		{
			FactorGraphData<K,D> data = advance();
			_next = null;
			return data;
		}
		
		private @Nullable FactorGraphData<K,D> advance()
		{
			FactorGraphData<K,D> data = _next;
			
			if (data == null)
			{
				while (++_graphTreeIndex <= _maxGraphTreeIndex)
				{
					data = _data.get(_graphTreeIndex);
					if (data != null)
					{
						break;
					}
	
					if (_createDataOnRead)
					{
						FactorGraph graph = _rootGraph.getGraphByTreeIndex(_graphTreeIndex);
						if (graph != null)
						{
							data = createDataForGraph(graph);
							break;
						}
					}
				}
				_next = data;
			}
			
			return data;
		}
	}

	private class KeyIter extends UnmodifiableIterator<K>
	{
		private Iterator<FactorGraphData<K,D>> _dataIter = new DataIterator();
		private Iterator<K> _iter = Collections.emptyIterator();
	
		@Override
		public boolean hasNext()
		{
			while (!_iter.hasNext())
			{
				if (_dataIter.hasNext())
				{
					_iter = _dataIter.next().keySet().iterator();
				}
				else
				{
					return false;
				}
			}
			
			return true;
		}
		
		@Override
		public @Nullable K next()
		{
			hasNext();
			return _iter.next();
		}
	}

	private class KeySet extends AbstractSet<K>
	{
		@Override
		public void clear()
		{
			DataLayerBase.this.clear();
		}
		
		@Override
		public boolean contains(@Nullable Object obj)
		{
			return DataLayerBase.this.containsKey(obj);
		}
		
		@Override
		public Iterator<K> iterator()
		{
			return new KeyIter();
		}
		
		@Override
		public boolean remove(@Nullable Object obj)
		{
			return DataLayerBase.this.remove(obj) != null;
		}
		
		@Override
		public int size()
		{
			return DataLayerBase.this.size();
		}
	}

	private class EntryIter extends UnmodifiableIterator<Map.Entry<K,D>>
	{
		private Iterator<FactorGraphData<K,D>> _dataIter = new DataIterator();
		private Iterator<Map.Entry<K,D>> _iter = Collections.emptyIterator();
		
		@Override
		public boolean hasNext()
		{
			while (!_iter.hasNext())
			{
				if (_dataIter.hasNext())
				{
					_iter = _dataIter.next().entrySet().iterator();
				}
				else
				{
					return false;
				}
			}
			
			return true;
		}
		
		@Override
		public @Nullable Map.Entry<K, D> next()
		{
			hasNext();
			return _iter.next();
		}
	}

	private class EntrySet extends AbstractSet<Map.Entry<K, D>>
	{
		@NonNullByDefault(false)
		@Override
		public boolean add(Map.Entry<K, D> entry)
		{
			final K key = entry.getKey();
			final D value = entry.getValue();
			return !Objects.equals(value, DataLayerBase.this.put(key, value));
		}
		
		@Override
		public void clear()
		{
			DataLayerBase.this.clear();
		}
		
		@Override
		public boolean contains(@Nullable Object obj)
		{
			if (obj instanceof Map.Entry)
			{
				Map.Entry<?,?> entry = (Map.Entry<?,?>)obj;
				return Objects.equals(DataLayerBase.this.get(entry.getKey()), entry.getValue());
			}
			
			return false;
		}
		
		@Override
		public Iterator<Map.Entry<K, D>> iterator()
		{
			return new EntryIter();
		}
		
		@Override
		public boolean remove(@Nullable Object obj)
		{
			if (obj instanceof Map.Entry)
			{
				Map.Entry<?,?> entry = (Map.Entry<?,?>)obj;
				IDatum value = DataLayerBase.this.get(entry.getKey());
				if (Objects.equals(value, entry.getValue()))
				{
					DataLayerBase.this.remove(entry.getKey());
					return true;
				}
			}
			
			return false;
		}
		
		@Override
		public int size()
		{
			return DataLayerBase.this.size();
		}
	}

	/*--------------------
	 * Non-public methods
	 */
	
	void assertSharesRoot(IFactorGraphChild child)
	{
		if (!sharesRoot(child))
		{
			throw new IllegalArgumentException(String.format("%s does not share root graph with %s", child, this));
		}
	}
	
	private @Nullable FactorGraphData<K,D> getDataForChild(IFactorGraphChild key)
	{
		FactorGraph graph = key.getParentGraph();
		return graph != null ? getDataForGraph(graph) : null;
	}
	
}
