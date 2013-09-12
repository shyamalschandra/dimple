package com.analog.lyric.dimple.parameters;


import net.jcip.annotations.ThreadSafe;

/**
 * Abstract base class for {@link IParameterList} implementation with two parameters.
 */
@ThreadSafe
public abstract class ParameterList2<Key extends IParameterKey> extends SmallParameterListBase<Key>
{
	/*--------
	 * State
	 */
	
	private static final long serialVersionUID = 1L;
	
	protected volatile ParameterValue _parameter0;
	protected volatile ParameterValue _parameter1;
	
	/*--------------
	 * Construction
	 */
	
	protected ParameterList2()
	{
		this(Double.NaN, Double.NaN);
	}
	
	protected ParameterList2(double value0, double value1)
	{
		super(false);
		_parameter0 = new ParameterValue(value0);
		_parameter1 = new ParameterValue(value1);
	}
	
	protected ParameterList2(SharedParameterValue value0, SharedParameterValue value1)
	{
		super(false);
		_parameter0 = value0;
		_parameter1 = value1;
	}
	
	protected ParameterList2(ParameterList2<Key> that)
	{
		super(that);
		_parameter0 = that._parameter0.cloneOrShare();
		_parameter1 = that._parameter1.cloneOrShare();
	}
	
	/*----------------
	 * Object methods
	 */
	
	@Override
	public abstract ParameterList2<Key> clone();
	
	/*------------------------
	 * IParameterList methods
	 */

	@Override
	public ParameterValue getParameterValue(int index)
	{
		switch (index)
		{
		case 0:
			return _parameter0;
		case 1:
			return _parameter1;
		default:
			throw indexOutOfRange(index);
		}
	}
	
	@Override
	public void setParameterValue(int index, ParameterValue value)
	{
		switch (index)
		{
		case 0:
			_parameter0 = value;
			break;
		case 1:
			_parameter1 = value;
			break;
		default:
			throw indexOutOfRange(index);
		}
	}
	
	@Override
	public int size()
	{
		return 2;
	}
}
