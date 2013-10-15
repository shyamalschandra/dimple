package com.analog.lyric.test.util.misc;

import static org.junit.Assert.*;

import java.util.ArrayList;
import java.util.List;

import org.junit.Test;

import com.analog.lyric.collect.tests.CollectionTester;
import com.analog.lyric.util.misc.IGetId;
import com.analog.lyric.util.misc.IMapList;
import com.analog.lyric.util.misc.MapList;

public class TestMapList
{

	private static class Value implements IGetId
	{
		private int _id;
		
		private Value(int id)
		{
			_id = id;
		}
		
		@Override
		public int getId()
		{
			return _id;
		}
	}
	
	@Test
	public void test()
	{
		MapList<Value> maplist = new MapList<Value>();
		assertInvariants(maplist);
		
		final Value v1 = new Value(1);
		final Value v2 = new Value(2);
		final Value v3 = new Value(3);
		
		assertTrue(maplist.add(v1));
		assertFalse(maplist.add(v1));
		assertSame(v1, maplist.getByKey(1));
		assertSame(v1, maplist.getByIndex(0));
		assertEquals(1, maplist.size());
		assertInvariants(maplist);
		
		assertFalse(maplist.addAll(new ArrayList<Value>()));
		ArrayList<Value> list = new ArrayList<Value>(2);
		list.add(v1);
		list.add(v2);
		list.add(v3);
		assertTrue(maplist.addAll(list));
		assertFalse(maplist.addAll(list));
		assertEquals(3, maplist.size());
		assertTrue(maplist.contains(v2));
		assertTrue(maplist.contains(v3));
		assertInvariants(maplist);
		
		maplist.clear();
		assertEquals(0, maplist.size());
		assertFalse(maplist.contains(v1));
		assertInvariants(maplist);
		
		assertFalse(maplist.remove(v1));
		assertFalse(maplist.remove("bogus"));
		
		maplist.addAll((Value[])null); // doesn't blow up
		maplist.addAll(new Value[] { v3, v2, v1});
		assertEquals(3, maplist.size());
		assertSame(v3, maplist.getByIndex(0));
		assertInvariants(maplist);
		
		assertTrue(maplist.remove(v2));
		assertFalse(maplist.remove(v2));
		assertFalse(maplist.contains(v2));
		assertSame(v1, maplist.getByIndex(1));
		assertInvariants(maplist);
		
		maplist = new MapList<Value>(list);
		assertTrue(maplist.containsAll(list));
		assertEquals(list.size(), maplist.size());
		assertInvariants(maplist);
		
		Value v4 = new Value(4);
		assertTrue(maplist.add(v4));
		assertTrue(maplist.removeAll(list));
		assertFalse(maplist.removeAll(list));
		assertEquals(1, maplist.size());
		
		assertTrue(maplist.addAll(list));
		assertSame(list.get(0), maplist.getByIndex(1));
		
		assertTrue(maplist.retainAll(list));
		assertFalse(maplist.retainAll(list));
		assertEquals(list.size(), maplist.size());
		assertInvariants(maplist);
	}

	public static <T extends IGetId> void assertInvariants(IMapList<T> maplist)
	{
		CollectionTester<T> collectionTester = new CollectionTester<T>();
		collectionTester.validateCollection(maplist);
		
		List<T> values = maplist.values();
		assertEquals(maplist.size(), values.size());
		for (int i = 0, endi = values.size(); i <endi; ++ i)
		{
			T value = values.get(i);
			int id = value.getId();
			
			assertSame(value, maplist.getByIndex(i));
			assertSame(value, maplist.getByKey(id));
			assertTrue(maplist.contains(value));
		}
	}
}
