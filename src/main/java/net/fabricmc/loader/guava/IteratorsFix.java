package net.fabricmc.loader.guava;

import com.google.common.collect.Iterators;
import com.google.common.collect.UnmodifiableListIterator;

public class IteratorsFix {
	
	  public static <T> UnmodifiableListIterator<T> emptyListIterator() {
		  return new UnmodifiableListIterator<T>() {

			@Override
			public boolean hasNext() {
				// TODO Auto-generated method stub
				return false;
			}

			@Override
			public T next() {
				// TODO Auto-generated method stub
				return null;
			}

			@Override
			public boolean hasPrevious() {
				// TODO Auto-generated method stub
				return false;
			}

			@Override
			public T previous() {
				// TODO Auto-generated method stub
				return null;
			}

			@Override
			public int nextIndex() {
				// TODO Auto-generated method stub
				return 0;
			}

			@Override
			public int previousIndex() {
				// TODO Auto-generated method stub
				return 0;
			}
		};
	  }
	
}
