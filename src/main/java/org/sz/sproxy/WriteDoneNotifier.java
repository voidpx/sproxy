package org.sz.sproxy;

public interface WriteDoneNotifier {
	default void addWN(WriteDoneNoticeable wn) {};
}
