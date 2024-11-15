package org.sz.sproxy;

public interface WriteDoneNoticeable {
	default void writeDone(Writable w) {};
}
