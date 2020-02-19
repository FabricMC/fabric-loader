package net.fabricmc.loader.guava;

import com.google.common.collect.UnmodifiableListIterator;

public class IteratorsFix {
	
	  public static <T> UnmodifiableListIterator<T> emptyListIterator() {
		  return new UnmodifiableListIterator<T>() {

			@Override
			public boolean hasNext() {
				return false;
			}

			@Override
			public T next() {
				return null;
			}

			@Override
			public boolean hasPrevious() {
				return false;
			}

			@Override
			public T previous() {
				return null;
			}

			@Override
			public int nextIndex() {
				return 0;
			}

			@Override
			public int previousIndex() {
				return 0;
			}
		};
	  }
	
}
