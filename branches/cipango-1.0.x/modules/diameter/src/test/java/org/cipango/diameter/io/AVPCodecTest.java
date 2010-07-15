package org.cipango.diameter.io;

import java.io.IOException;

import org.cipango.diameter.AVP;
import org.cipango.diameter.base.Base;
import org.cipango.diameter.ims.Cx;
import org.mortbay.io.Buffer;
import org.mortbay.io.ByteArrayBuffer;
import org.mortbay.io.View;

import junit.framework.TestCase;

public class AVPCodecTest extends TestCase
{
	@SuppressWarnings("unchecked")
	public void testAVPCodec() throws IOException
	{
		AVP avp = new AVP(Cx.PUBLIC_IDENTITY, "sip:alice@cipango.org");
		Buffer buffer = new ByteArrayBuffer(64);
		Codecs.__avp.encode(buffer, avp);
	
		AVP decoded = Codecs.__avp.decode(buffer);
		
		assertEquals(avp.getType().getCode(), decoded.getType().getCode());
		assertEquals(avp.getType().getVendorId(), decoded.getType().getVendorId());
		
	}
	
	@SuppressWarnings("unchecked")
	public void testPadding() throws IOException
	{
		byte[] b = { 13 };
		AVP<byte[]> avp = new AVP<byte[]>(Cx.INTEGRITY_KEY, b);
		Buffer buffer = new ByteArrayBuffer(64);
		for (int i = 0; i < 64; i++)
			buffer.put((byte) 44);
		buffer.setPutIndex(0);
		Codecs.__avp.encode(buffer, avp);
		View view = new View(buffer);
		view.setGetIndex(view.putIndex() - 3);
		for (int i = 0; i < 3; i++)
			assertEquals(0, view.get());
		
		AVP<byte[]> decoded = (AVP<byte[]>) Codecs.__avp.decode(buffer);
		
		assertEquals(avp.getType().getCode(), decoded.getType().getCode());
		assertEquals(avp.getType().getVendorId(), decoded.getType().getVendorId());
		assertEquals(avp.getValue()[0], decoded.getValue()[0]);
		
	}
}