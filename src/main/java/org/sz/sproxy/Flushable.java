package org.sz.sproxy;

import java.io.IOException;

import org.sz.sproxy.Writable.WR;

public interface Flushable {
	
	default WR flush() throws IOException {return WR.DONE;}

}
