package org.sz.sproxy;

public interface WriteDoneNotifier {
	default void addWN(WriteDoneNoticeable wn) {};
	default void removeWN(WriteDoneNoticeable wn) {};
}
